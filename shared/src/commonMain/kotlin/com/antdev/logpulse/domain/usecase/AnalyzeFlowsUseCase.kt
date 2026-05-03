package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.domain.engine.LogPatternMatcher
import com.antdev.logpulse.domain.model.*
import kotlinx.coroutines.yield

class AnalyzeFlowsUseCase(
    private val matcher: LogPatternMatcher = LogPatternMatcher()
) {
    /**
     * Incremental analysis: Analyzes new logs and merges into existing flows.
     * Supports multiple instances per ID.
     */
    suspend fun analyzeIncremental(
        newLogs: List<LogEvent>,
        existingFlows: List<FlowTrace>,
        sequences: List<SequencePattern>,
        onProgress: ((Float) -> Unit)? = null
    ): List<FlowTrace> {
        if (sequences.isEmpty()) return emptyList()

        // 1. Group existing flows by sequence only (no ID grouping)
        val allFlowsBySeq = sequences.associate { it.id to mutableListOf<FlowTrace>() }
        existingFlows.forEach { allFlowsBySeq[it.sequence.id]?.add(it) }

        // 2. Track the latest active flow for each sequence
        val activeFlowBySeq = sequences.associate { it.id to allFlowsBySeq[it.id]?.lastOrNull { t -> t.status is FlowStatus.InProgress } }.toMutableMap()

        val activeSequences = sequences.filter { it.isEnabled }
        if (activeSequences.isEmpty()) {
            onProgress?.invoke(1.0f)
            return existingFlows
        }

        val allPatterns = sequences.flatMap { it.steps.map { s -> s.pattern } }.distinct()

        newLogs.forEachIndexed { logIndex, log ->
            if (logIndex % 1000 == 0) {
                yield()
                onProgress?.invoke(logIndex.toFloat() / newLogs.size)
            }

            val matches = allPatterns.associateWith { matcher.match(log, it) }

            activeSequences.forEach { sequence ->
                val activeTrace = activeFlowBySeq[sequence.id]
                val seqFlows = allFlowsBySeq[sequence.id]!!
                
                var processed = false

                if (activeTrace != null) {
                    val state = calculateState(activeTrace.logs, sequence)
                    val currentStep = sequence.steps.getOrNull(state.stepIndex)
                    
                    if (currentStep != null) {
                        var validNextStepIndex = -1
                        
                        if (matches[currentStep.pattern] != null && (currentStep.maxCount == -1 || state.matchCount < currentStep.maxCount)) {
                            validNextStepIndex = state.stepIndex
                        } else if (state.matchCount >= currentStep.minCount) {
                            for (i in (state.stepIndex + 1) until sequence.steps.size) {
                                val stepPattern = sequence.steps[i].pattern
                                if (matches[stepPattern] != null) {
                                    validNextStepIndex = i
                                    break
                                }
                                if (sequence.steps[i].minCount > 0) {
                                    break // Cannot skip mandatory step
                                }
                            }
                        }

                        if (validNextStepIndex != -1) {
                            val matchedLog = matches[sequence.steps[validNextStepIndex].pattern]!!
                            
                            // Check for Step 0 restart
                            if (validNextStepIndex == 0 && state.matchCount >= sequence.steps[0].minCount && (sequence.steps[0].maxCount != -1 && state.matchCount >= sequence.steps[0].maxCount)) {
                                // Mark old as failed/terminated and start new
                                val failedTrace = activeTrace.copy(
                                    status = FlowStatus.Failed("Restarted by new '${sequence.steps[0].pattern.name}'")
                                )
                                seqFlows[seqFlows.indexOf(activeTrace)] = failedTrace
                                
                                val newTrace = FlowTrace(
                                    id = matchedLog.extractedId ?: "trace_${log.timestamp}",
                                    sequence = sequence,
                                    logs = listOf(matchedLog),
                                    status = calculateStatus(listOf(matchedLog), sequence)
                                )
                                seqFlows.add(newTrace)
                                activeFlowBySeq[sequence.id] = if (newTrace.status is FlowStatus.InProgress) newTrace else null
                            } else {
                                // Append
                                val updatedLogs = activeTrace.logs + matchedLog
                                val newState = calculateState(updatedLogs, sequence)
                                val updatedTrace = activeTrace.copy(
                                    id = matchedLog.extractedId ?: activeTrace.id, // Keep extracted ID
                                    logs = updatedLogs,
                                    status = newState.status
                                )
                                seqFlows[seqFlows.indexOf(activeTrace)] = updatedTrace
                                activeFlowBySeq[sequence.id] = if (updatedTrace.status is FlowStatus.InProgress) updatedTrace else null
                            }
                            processed = true
                        } else {
                            // Does it match ANY step out of order?
                            val anyMatchIndex = sequence.steps.indexOfFirst { matches[it.pattern] != null }
                            if (anyMatchIndex != -1) {
                                // It could be a valid restart from step 0 (or skipped optional initial steps)
                                var isRestart = false
                                var restartIndex = -1
                                for (i in sequence.steps.indices) {
                                    if (matches[sequence.steps[i].pattern] != null) {
                                        isRestart = true
                                        restartIndex = i
                                        break
                                    }
                                    if (sequence.steps[i].minCount > 0) {
                                        break
                                    }
                                }

                                if (isRestart && anyMatchIndex == restartIndex) {
                                    val failedTrace = activeTrace.copy(
                                        status = FlowStatus.Failed("Restarted by new '${sequence.steps[restartIndex].pattern.name}'")
                                    )
                                    seqFlows[seqFlows.indexOf(activeTrace)] = failedTrace
                                    activeFlowBySeq[sequence.id] = null
                                    // Let the 'Start new flow' block handle creating the new one
                                } else {
                                    val failedTrace = activeTrace.copy(
                                        status = FlowStatus.Failed("Unexpected step '${sequence.steps[anyMatchIndex].pattern.name}' appeared out of order")
                                    )
                                    seqFlows[seqFlows.indexOf(activeTrace)] = failedTrace
                                    activeFlowBySeq[sequence.id] = null
                                }
                            }
                        }
                    }
                }

                // Start new flow if not processed
                if (!processed) {
                    var validStartIndex = -1
                    for (i in sequence.steps.indices) {
                        if (matches[sequence.steps[i].pattern] != null) {
                            validStartIndex = i
                            break
                        }
                        if (sequence.steps[i].minCount > 0) {
                            break
                        }
                    }

                    if (validStartIndex != -1) {
                        val matchedLog = matches[sequence.steps[validStartIndex].pattern]!!
                        val newTrace = FlowTrace(
                            id = matchedLog.extractedId ?: "trace_${log.timestamp}",
                            sequence = sequence,
                            logs = listOf(matchedLog),
                            status = calculateStatus(listOf(matchedLog), sequence)
                        )
                        seqFlows.add(newTrace)
                        activeFlowBySeq[sequence.id] = if (newTrace.status is FlowStatus.InProgress) newTrace else null
                    }
                }
            }
        }

        onProgress?.invoke(1.0f)
        return allFlowsBySeq.values.flatten()
            .sortedBy { it.logs.firstOrNull()?.log?.timestamp ?: "" }
    }

    private data class FlowState(
        val stepIndex: Int,
        val matchCount: Int,
        val status: FlowStatus
    )

    private fun calculateState(logs: List<MatchedLog>, sequence: SequencePattern): FlowState {
        val steps = sequence.steps
        if (steps.isEmpty()) return FlowState(0, 0, FlowStatus.Complete)

        var currentStepIndex = 0
        var currentStepMatchCount = 0

        for (log in logs) {
            if (currentStepIndex >= steps.size) {
                return FlowState(currentStepIndex, currentStepMatchCount, FlowStatus.Failed("Extra logs after sequence completion"))
            }

            val currentStep = steps[currentStepIndex]

            var matchedIndex = -1
            if (log.pattern.name == currentStep.pattern.name) {
                matchedIndex = currentStepIndex
            } else {
                if (currentStepMatchCount >= currentStep.minCount) {
                    // Look ahead for matching step, skipping optional ones
                    for (i in (currentStepIndex + 1) until steps.size) {
                        if (log.pattern.name == steps[i].pattern.name) {
                            matchedIndex = i
                            break
                        }
                        if (steps[i].minCount > 0) {
                            break // Reached a mandatory step, cannot skip further
                        }
                    }
                }
            }

            if (matchedIndex == currentStepIndex) {
                currentStepMatchCount++
                if (currentStep.maxCount != -1 && currentStepMatchCount >= currentStep.maxCount) {
                    currentStepIndex++
                    currentStepMatchCount = 0
                }
            } else if (matchedIndex > currentStepIndex) {
                currentStepIndex = matchedIndex
                currentStepMatchCount = 1
                val newStep = steps[currentStepIndex]
                if (newStep.maxCount != -1 && currentStepMatchCount >= newStep.maxCount) {
                    currentStepIndex++
                    currentStepMatchCount = 0
                }
            } else {
                return FlowState(currentStepIndex, currentStepMatchCount, FlowStatus.Failed("Unexpected step '${log.pattern.name}'"))
            }
        }

        var tempIdx = currentStepIndex
        var tempCount = currentStepMatchCount
        while (tempIdx < steps.size) {
            val step = steps[tempIdx]
            if (tempCount >= step.minCount) {
                tempIdx++
                tempCount = 0
            } else break
        }

        val status = if (tempIdx >= steps.size) FlowStatus.Complete else FlowStatus.InProgress
        return FlowState(currentStepIndex, currentStepMatchCount, status)
    }

    private fun calculateStatus(logs: List<MatchedLog>, sequence: SequencePattern): FlowStatus {
        return calculateState(logs, sequence).status
    }
}
