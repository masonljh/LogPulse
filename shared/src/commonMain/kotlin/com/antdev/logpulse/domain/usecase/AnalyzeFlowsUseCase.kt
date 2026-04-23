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

        // Optimize: Pre-collect unique patterns to match each only once per log line
        val allPatterns = sequences.flatMap { it.steps.map { s -> s.pattern } }.distinct()

        // Organize existing traces by Sequence and then by ID (List of traces)
        val flowsBySeqAndId = existingFlows.groupBy { it.sequence.id }
            .mapValues { (_, traces) -> 
                traces.groupBy { it.id }.mapValues { (_, list) -> list.toMutableList() }.toMutableMap()
            }.toMutableMap()

        // Index by Sequence and TID for fast lookup of InProgress flows on a thread
        val activeFlowsByTid = sequences.associate { it.id to mutableMapOf<String, FlowTrace>() }
        
        // Latest active flow for SEQUENTIAL strategy (One per sequence)
        val latestActiveSeqFlow = sequences.associate { it.id to (null as FlowTrace?) }.toMutableMap()

        existingFlows.filter { it.status is FlowStatus.InProgress }.forEach { trace ->
            if (trace.sequence.strategy == AnalysisStrategy.STRICT) {
                val lastTid = trace.logs.lastOrNull()?.log?.tid
                if (lastTid != null) {
                    activeFlowsByTid[trace.sequence.id]!![lastTid] = trace
                }
            } else {
                latestActiveSeqFlow[trace.sequence.id] = trace
            }
        }

        val activeSequences = sequences.filter { it.isEnabled }
        if (activeSequences.isEmpty()) {
            onProgress?.invoke(1.0f)
            return existingFlows
        }

        newLogs.forEachIndexed { logIndex, log ->
            // Report progress
            if (logIndex % 1000 == 0) {
                onProgress?.invoke(logIndex.toFloat() / newLogs.size)
            }

            // Match all unique patterns for this log line once
            val matches = allPatterns.associate { it.name to matcher.match(log, it) }

            activeSequences.forEach { sequence ->
                val seqMap = flowsBySeqAndId.getOrPut(sequence.id) { mutableMapOf() }
                val seqActiveTidMap = activeFlowsByTid[sequence.id]!!
                
                var processed = false

                // 1. If it matches Step 0 and we are in SEQUENTIAL mode, 
                // we prioritize starting a NEW flow over appending to an old one.
                if (sequence.strategy == AnalysisStrategy.SEQUENTIAL) {
                    val step0 = sequence.steps[0]
                    val matched0 = matches[step0.pattern.name]
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
                            latestActiveSeqFlow[sequence.id] = newTrace
                        } else {
                            latestActiveSeqFlow[sequence.id] = null
                        }
                        processed = true
                    }
                }

                // 2. Try to append to active flow if not already processed by Step 0 (SEQUENTIAL)
                if (!processed) {
                    val traceToAppend: FlowTrace? = when (sequence.strategy) {
                        AnalysisStrategy.STRICT -> seqActiveTidMap[log.tid]
                        AnalysisStrategy.SEQUENTIAL -> latestActiveSeqFlow[sequence.id]
                    }

                    if (traceToAppend != null) {
                        for (stepIndex in 0 until sequence.steps.size) {
                            val step = sequence.steps[stepIndex]
                            val matched = matches[step.pattern.name]
                            if (matched != null) {
                                // If it matches step 0 but we already have an active flow in SEQUENTIAL mode,
                                // we should have handled it in step 1 above. But for STRICT mode, 
                                // we might want to start a new flow if it's Step 0.
                                if (stepIndex == 0 && sequence.strategy == AnalysisStrategy.SEQUENTIAL && traceToAppend.logs.isNotEmpty()) {
                                    break 
                                }

                                // Remove old trace reference (reference equality)
                                val currentList = seqMap[traceToAppend.id]
                                if (currentList != null) {
                                    val it = currentList.iterator()
                                    while (it.hasNext()) {
                                        if (it.next() === traceToAppend) {
                                            it.remove()
                                            break
                                        }
                                    }
                                }

                                val updatedLogs = traceToAppend.logs + matched
                                val finalId = if (traceToAppend.id.startsWith("thread_") && matched.extractedId != null) matched.extractedId else traceToAppend.id
                                
                                val updatedTrace = traceToAppend.copy(
                                    id = finalId,
                                    logs = updatedLogs,
                                    status = calculateStatus(updatedLogs, sequence)
                                )
                                
                                seqMap.getOrPut(finalId) { mutableListOf() }.add(updatedTrace)
                                
                                if (updatedTrace.status is FlowStatus.InProgress) {
                                    if (sequence.strategy == AnalysisStrategy.STRICT) seqActiveTidMap[log.tid] = updatedTrace
                                    else latestActiveSeqFlow[sequence.id] = updatedTrace
                                } else {
                                    if (sequence.strategy == AnalysisStrategy.STRICT) seqActiveTidMap.remove(log.tid)
                                    else latestActiveSeqFlow[sequence.id] = null
                                }
                                processed = true
                                break
                            }
                        }
                    }
                }

                // 3. Fallback: Start new flow (Step 0)
                if (!processed) {
                    val step0 = sequence.steps[0]
                    val matched0 = matches[step0.pattern.name]
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
                            if (sequence.strategy == AnalysisStrategy.STRICT) seqActiveTidMap[log.tid] = newTrace
                            else latestActiveSeqFlow[sequence.id] = newTrace
                        }
                        processed = true
                    }
                }
                
                // 4. Fallback: Partial start (ID match)
                if (!processed) {
                    for (stepIndex in 1 until sequence.steps.size) {
                        val step = sequence.steps[stepIndex]
                        val matched = matches[step.pattern.name]
                        if (matched != null && matched.extractedId != null) {
                            val newTrace = FlowTrace(
                                id = matched.extractedId,
                                sequence = sequence,
                                logs = listOf(matched),
                                status = calculateStatus(listOf(matched), sequence)
                            )
                            seqMap.getOrPut(matched.extractedId) { mutableListOf() }.add(newTrace)
                            if (newTrace.status is FlowStatus.InProgress) {
                                if (sequence.strategy == AnalysisStrategy.STRICT) seqActiveTidMap[log.tid] = newTrace
                                else latestActiveSeqFlow[sequence.id] = newTrace
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

    private fun calculateStatus(
        logs: List<MatchedLog>,
        sequence: SequencePattern
    ): FlowStatus {
        val steps = sequence.steps
        if (steps.isEmpty()) return FlowStatus.Complete

        var currentStepIndex = 0
        var currentStepMatchCount = 0

        for (log in logs) {
            val currentStep = steps.getOrNull(currentStepIndex) ?: break
            val nextStep = steps.getOrNull(currentStepIndex + 1)

            // Check if matches current step pattern
            if (log.pattern.name == currentStep.pattern.name) {
                currentStepMatchCount++
                
                // If max reached (and not infinite), move to next
                if (currentStep.maxCount != -1 && currentStepMatchCount >= currentStep.maxCount) {
                    currentStepIndex++
                    currentStepMatchCount = 0
                }
            } 
            // Else check if matches next step pattern (and current min met)
            else if (nextStep != null && log.pattern.name == nextStep.pattern.name) {
                if (currentStepMatchCount >= currentStep.minCount) {
                    currentStepIndex++
                    currentStepMatchCount = 1
                    
                    // Immediately check next step's constraints
                    if (nextStep.maxCount != -1 && currentStepMatchCount >= nextStep.maxCount) {
                        currentStepIndex++
                        currentStepMatchCount = 0
                    }
                } else {
                    return FlowStatus.Failed("Step '${currentStep.pattern.name}' expected at least ${currentStep.minCount} matches, but only got $currentStepMatchCount")
                }
            }
        }

        // Final verification: are we at the end, or are all remaining steps optional?
        var tempIdx = currentStepIndex
        var tempCount = currentStepMatchCount
        
        while (tempIdx < steps.size) {
            val step = steps[tempIdx]
            if (tempCount >= step.minCount) {
                tempIdx++
                tempCount = 0
            } else {
                break
            }
        }

        return if (tempIdx >= steps.size) {
            FlowStatus.Complete
        } else {
            FlowStatus.InProgress
        }
    }
}
