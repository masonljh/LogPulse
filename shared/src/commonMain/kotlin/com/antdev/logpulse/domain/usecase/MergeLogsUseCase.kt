package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.domain.model.LogEvent

class MergeLogsUseCase {
    /**
     * Merges multiple lists of LogEvent and sorts them by timestamp.
     * Note: Assumes timestamp format is lexicographically sortable (e.g., MM-DD HH:MM:SS.mmm).
     */
    operator fun invoke(sources: Map<String, List<LogEvent>>): List<LogEvent> {
        return sources.values.flatten().sortedWith(compareBy({ it.timestamp }, { it.id }))
    }
}
