package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.domain.engine.LogPatternMatcher
import com.antdev.logpulse.domain.model.*

class AnalyzeFlowsUseCase(
    private val matcher: LogPatternMatcher = LogPatternMatcher()
) {
    /**
     * Incremental analysis: Analyzes new logs and merges into existing flows.
     * Supports multiple instances per ID.
     */
    fun analyzeIncremental(
        newLogs: List<LogEvent>,
        existingFlows: List<FlowTrace>,
        sequences: List<SequencePattern>,
        onProgress: ((Float) -> Unit)? = null
    ): List<FlowTrace> {
        if (sequences.isEmpty()) return emptyList()

        val allPatterns = sequences.flatMap { it.steps.map { s -> s.pattern } }.distinct()

        val flowsBySeqAndId = existingFlows.groupBy { it.sequence.id }
            .mapValues { (_, traces) -> 
                traces.groupBy { it.id }.mapValues { (_, list) -> list.toMutableList() }.toMutableMap()
            }.toMutableMap()

        val activeFlowsBySeq = sequences.associate { it.id to mutableListOf<FlowTrace>() }
        
        existingFlows.filter { it.status is FlowStatus.InProgress }.forEach { trace ->
            activeFlowsBySeq[trace.sequence.id]?.add(trace)
        }

        val activeSequences = sequences.filter { it.isEnabled }
        if (activeSequences.isEmpty()) {
            onProgress?.invoke(1.0f)
            return existingFlows
        }

        newLogs.forEachIndexed { logIndex, log ->
            if (logIndex % 1000 == 0) {
                onProgress?.invoke(logIndex.toFloat() / newLogs.size)
            }

            val matches = allPatterns.associateWith { matcher.match(log, it) }

            activeSequences.forEach { sequence ->
                val seqMap = flowsBySeqAndId.getOrPut(sequence.id) { mutableMapOf() }
                val activeFlows = activeFlowsBySeq[sequence.id]!!
                
                var processed = false

                // 1. Try to append to existing active flows (latest first)
                // We ONLY append if the log matches what the flow is currently waiting for.
                val it = activeFlows.asReversed().iterator()
                while (it.hasNext()) {
                    val traceToAppend = it.next()
                    val state = calculateState(traceToAppend.logs, sequence)
                    
                    val currentStep = sequence.steps.getOrNull(state.stepIndex)
                    val nextStep = sequence.steps.getOrNull(state.stepIndex + 1)
                    
                    // Check if it matches current step (repetition) or next step
                    val matchedCurrent = if (currentStep != null) matches[currentStep.pattern] else null
                    val matchedNext = if (nextStep != null && state.matchCount >= currentStep!!.minCount) matches[nextStep.pattern] else null
                    
                    // Prioritize next step if both match
                    val bestMatched = matchedNext ?: matchedCurrent
                    
                    if (bestMatched != null) {
                        // If it's Step 0 and we are at the very beginning, 
                        // we should usually start a NEW flow instead of appending to current Step 0,
                        // UNLESS Step 0 is repeatable (maxCount != 1).
                        if (state.stepIndex == 0 && state.matchCount > 0 && sequence.steps[0].maxCount == 1 && matchedCurrent != null && matchedNext == null) {
                            // This is a "Restart" case. Don't append here, let Step 2 (Start new) handle it.
                            continue
                        }

                        // Append!
                        val updatedLogs = traceToAppend.logs + bestMatched
                        val newState = calculateState(updatedLogs, sequence)
                        
                        val currentList = seqMap[traceToAppend.id]
                        currentList?.removeIf { it === traceToAppend }

                        val finalId = if (traceToAppend.id.startsWith("thread_") && bestMatched.extractedId != null) {
                            bestMatched.extractedId 
                        } else {
                            traceToAppend.id
                        }
                        
                        val updatedTrace = traceToAppend.copy(
                            id = finalId,
                            logs = updatedLogs,
                            status = newState.status
                        )
                        
                        seqMap.getOrPut(finalId) { mutableListOf() }.add(updatedTrace)
                        
                        it.remove() // Remove from active (reversed)
                        if (updatedTrace.status is FlowStatus.InProgress) {
                            activeFlows.add(updatedTrace)
                        }
                        
                        processed = true
                        break
                    }
                }

                // 2. Start new flow (Step 0)
                if (!processed) {
                    val step0 = sequence.steps[0]
                    val matched0 = matches[step0.pattern]
                    if (matched0 != null) {
                        val finalId = matched0.extractedId ?: "thread_${log.pid}_${log.tid}"
                        val newTrace = FlowTrace(
                            id = finalId,
                            sequence = sequence,
                            logs = listOf(matched0),
                            status = calculateStatus(listOf(matched0), sequence)
                        )
                        seqMap.getOrPut(finalId) { mutableListOf() }.add(newTrace)
                        
                        if (newTrace.status is FlowStatus.InProgress) {
                            activeFlows.add(newTrace)
                        }
                        processed = true
                    }
                }
                
                // 3. Fallback: Partial start (ID match)
                if (!processed) {
                    for (stepIndex in 1 until sequence.steps.size) {
                        val step = sequence.steps[stepIndex]
                        val matched = matches[step.pattern]
                        if (matched != null && matched.extractedId != null) {
                            val newTrace = FlowTrace(
                                id = matched.extractedId,
                                sequence = sequence,
                                logs = listOf(matched),
                                status = calculateStatus(listOf(matched), sequence)
                            )
                            seqMap.getOrPut(matched.extractedId) { mutableListOf() }.add(newTrace)
                            if (newTrace.status is FlowStatus.InProgress) {
                                activeFlows.add(newTrace)
                            }
                            processed = true
                            break
                        }
                    }
                }
            }
        }

        onProgress?.invoke(1.0f)
        return flowsBySeqAndId.values.flatMap { it.values.flatten() }
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
