package com.antdev.logpulse.domain.engine

import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogPattern
import com.antdev.logpulse.domain.model.MatchedLog

class LogPatternMatcher {
    
    /**
     * Matches a [LogEvent] against a [LogPattern] and extracts the identifier if present.
     * Uses pre-compiled regex from the pattern for performance.
     */
    fun match(log: LogEvent, pattern: LogPattern): MatchedLog? {
        return try {
            val matchResult = pattern.compiledRegex.find(log.message) ?: return null
            
            val extractedId = if (pattern.patternString.contains(pattern.idPlaceholder)) {
                // Accessing groups by name can be slow in some JVMs, but generally fine.
                // Wrapped in runCatching for safety against invalid patterns.
                runCatching { matchResult.groups["id"]?.value }.getOrNull()
            } else {
                null
            }
            
            MatchedLog(log, pattern, extractedId)
        } catch (e: Exception) {
            // Log the error or handle it silently to prevent crash on invalid user input
            null
        }
    }
}
