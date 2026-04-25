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
                
                // Check if this log matches ANY step in the current sequence
                val matchingStepIndex = sequence.steps.indexOfFirst { matches[it.pattern] != null }
                if (matchingStepIndex != -1) {
                    val matchedLog = matches[sequence.steps[matchingStepIndex].pattern]!!
                    
                    var processed = false

                    if (activeTrace != null) {
                        val state = calculateState(activeTrace.logs, sequence)
                        val currentStep = sequence.steps.getOrNull(state.stepIndex)
                        val nextStep = sequence.steps.getOrNull(state.stepIndex + 1)
                        
                        val isMatchedCurrent = matchingStepIndex == state.stepIndex
                        val isMatchedNext = nextStep != null && matchingStepIndex == (state.stepIndex + 1) && state.matchCount >= currentStep!!.minCount
                        
                        if (isMatchedNext || isMatchedCurrent) {
                            // Valid progression or repetition
                            // Special case: Step 0 restart
                            if (matchingStepIndex == 0 && state.matchCount > 0 && sequence.steps[0].maxCount == 1) {
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
                            // STRICT RULE: Belongs to sequence but NOT current/next step -> FAIL
                            val failedTrace = activeTrace.copy(
                                status = FlowStatus.Failed("Unexpected step '${sequence.steps[matchingStepIndex].pattern.name}' appeared out of order")
                            )
                            seqFlows[seqFlows.indexOf(activeTrace)] = failedTrace
                            activeFlowBySeq[sequence.id] = null
                            // Continue to see if this log can start a NEW flow
                        }
                    }

                    // Start new flow if not processed (or if we just failed the previous one)
                    if (!processed && matchingStepIndex == 0) {
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
            val currentStep = steps.getOrNull(currentStepIndex) ?: break
            val nextStep = steps.getOrNull(currentStepIndex + 1)

            if (log.pattern.name == currentStep.pattern.name) {
                currentStepMatchCount++
                if (currentStep.maxCount != -1 && currentStepMatchCount >= currentStep.maxCount) {
                    currentStepIndex++
                    currentStepMatchCount = 0
                }
            } else if (nextStep != null && log.pattern.name == nextStep.pattern.name) {
                if (currentStepMatchCount >= currentStep.minCount) {
                    currentStepIndex++
                    currentStepMatchCount = 1
                    if (nextStep.maxCount != -1 && currentStepMatchCount >= nextStep.maxCount) {
                        currentStepIndex++
                        currentStepMatchCount = 0
                    }
                } else {
                    return FlowState(currentStepIndex, currentStepMatchCount, FlowStatus.Failed("Step '${currentStep.pattern.name}' expected at least ${currentStep.minCount} matches"))
                }
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
