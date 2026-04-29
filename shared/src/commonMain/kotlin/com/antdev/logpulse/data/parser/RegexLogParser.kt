package com.antdev.logpulse.data.parser

import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogFormat
import com.antdev.logpulse.domain.model.LogLevel

class RegexLogParser(private val format: LogFormat) {
    private val regex = try {
        Regex(format.pattern)
    } catch (e: Exception) {
        // If pattern is invalid, fallback to something that won't crash
        Regex(".*")
    }

    private var previousLog: LogEvent? = null

    fun parseLine(line: String, idPrefix: String = "", source: String = "", lineIndex: Int = 0): LogEvent? {
        val matchResult = regex.matchEntire(line)
        
        if (matchResult == null) {
            // Unmatched line -> typically a multi-line log like stack traces
            // We append to the previous log
            val prev = previousLog
            if (prev != null) {
                val updatedLog = prev.copy(
                    message = prev.message + "\n" + line,
                    rawData = prev.rawData + "\n" + line
                )
                previousLog = updatedLog
                return updatedLog
            }
            return null // Completely unparsable and no previous log
        }
        
        val groups = matchResult.groupValues

        fun safeGroup(index: Int?): String {
            if (index == null || index < 0 || index >= groups.size) return ""
            return groups[index].trim()
        }

        val levelStr = safeGroup(format.levelGroup)
        val level = if (levelStr.isNotEmpty()) LogLevel.fromChar(levelStr.first().toString()) else LogLevel.UNKNOWN

        val event = LogEvent(
            id = "${idPrefix}_${lineIndex}",
            lineIndex = lineIndex,
            timestamp = safeGroup(format.timestampGroup),
            pid = safeGroup(format.pidGroup),
            tid = safeGroup(format.tidGroup),
            level = level,
            tag = safeGroup(format.tagGroup),
            message = safeGroup(format.messageGroup),
            rawData = line,
            source = source
        )
        
        previousLog = event
        return event
    }

    fun getPendingPreviousLog(): LogEvent? {
        return previousLog
    }

    fun reset() {
        previousLog = null
    }
}
