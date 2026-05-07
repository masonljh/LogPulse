package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.domain.model.LogEvent

class MergeLogsUseCase {
    operator fun invoke(sources: Map<String, List<LogEvent>>): List<LogEvent> {
        val normalizedSources = sources.mapValues { (_, logs) ->
            normalizeTimestamps(logs)
        }
        
        return normalizedSources.values.flatten().sortedWith(
            compareBy(
                { it.normalizedTimestamp ?: it.timestamp },
                { it.source },
                { it.lineIndex }
            )
        )
    }

    private fun normalizeTimestamps(logs: List<LogEvent>): List<LogEvent> {
        if (logs.isEmpty()) return logs
        
        val result = logs.toMutableList()
        var i = 0
        while (i < result.size) {
            if (isInvalidDate(result[i].timestamp)) {
                val start = i
                while (i < result.size && isInvalidDate(result[i].timestamp)) {
                    i++
                }
                val end = i // exclusive
                
                // Block of invalid dates from [start, end)
                val prevValid = if (start > 0) result[start - 1].timestamp else null
                val nextValid = if (end < result.size) result[end].timestamp else null
                
                if (nextValid != null) {
                    // Use nextValid as base and subtract small increments to keep order
                    // Or if prevValid exists, interpolate.
                    // For simplicity and matching user's intent:
                    // If we have nextValid, we can treat 01-01 logs as happening just before it.
                    val baseTime = nextValid
                    for (k in start until end) {
                        // Create a "virtual" timestamp that's lexicographically smaller than baseTime
                        // but maintains order among the invalid block.
                        // We append a suffix that's handled by the comparator or just slightly modify the string.
                        // Actually, let's just use the nextValid time but assign it to normalizedTimestamp.
                        // The secondary sort by lineIndex will keep them in order.
                        result[k] = result[k].copy(normalizedTimestamp = baseTime)
                    }
                } else if (prevValid != null) {
                    val baseTime = prevValid
                    for (k in start until end) {
                        result[k] = result[k].copy(normalizedTimestamp = baseTime)
                    }
                }
            } else {
                i++
            }
        }
        return result
    }

    private fun isInvalidDate(timestamp: String): Boolean {
        return timestamp.startsWith("01-01")
    }
}
