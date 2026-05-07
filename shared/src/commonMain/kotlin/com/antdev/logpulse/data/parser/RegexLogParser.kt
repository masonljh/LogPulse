package com.antdev.logpulse.data.parser

import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogFormat
import com.antdev.logpulse.domain.model.LogLevel

class RegexLogParser(private val format: LogFormat) {
    private val regex = try {
        Regex(format.pattern)
    } catch (e: Exception) {
        Regex(".*")
    }

    private var pendingId: String = ""
    private var pendingLineIndex: Int = 0
    private var pendingTimestamp: String = ""
    private var pendingPid: String = ""
    private var pendingTid: String = ""
    private var pendingLevel: LogLevel = LogLevel.UNKNOWN
    private var pendingTag: String = ""
    private val messageBuilder = StringBuilder()
    private val rawDataBuilder = StringBuilder()
    private var pendingSource: String = ""
    
    private var hasPending = false

    fun parseLine(line: String, idPrefix: String = "", source: String = "", lineIndex: Int = 0): LogEvent? {
        if (line.isBlank()) return null
        
        // Filter out Logcat markers like "--------- beginning of system"
        if (line.startsWith("---------")) return null
        
        val matchResult = regex.matchEntire(line)
        
        if (matchResult != null) {
            // New log detected. Return the previous one if exists.
            val completedEvent = flush()
            
            val groups = matchResult.groupValues

            fun safeGroup(index: Int?): String {
                if (index == null || index < 0 || index >= groups.size) return ""
                return groups[index].trim()
            }

            val levelStr = safeGroup(format.levelGroup)
            val level = if (levelStr.isNotEmpty()) LogLevel.fromChar(levelStr.first().toString()) else LogLevel.UNKNOWN

            // Start new pending log
            pendingId = "${idPrefix}_${lineIndex}"
            pendingLineIndex = lineIndex
            pendingTimestamp = safeGroup(format.timestampGroup)
            pendingPid = safeGroup(format.pidGroup)
            pendingTid = safeGroup(format.tidGroup)
            pendingLevel = level
            pendingTag = safeGroup(format.tagGroup)
            pendingSource = source
            
            messageBuilder.setLength(0)
            messageBuilder.append(safeGroup(format.messageGroup))
            
            rawDataBuilder.setLength(0)
            rawDataBuilder.append(line)
            
            hasPending = true
            return completedEvent
        } else {
            // Unmatched line -> continuation
            if (hasPending) {
                messageBuilder.append("\n").append(line)
                rawDataBuilder.append("\n").append(line)
                return null
            } else {
                // First line doesn't match -> create a fallback UNPARSED event
                if (line.length < 5) return null
                
                pendingId = "${idPrefix}_${lineIndex}"
                pendingLineIndex = lineIndex
                pendingTimestamp = ""
                pendingPid = ""
                pendingTid = ""
                pendingLevel = LogLevel.UNKNOWN
                pendingTag = "UNPARSED"
                pendingSource = source
                
                messageBuilder.setLength(0)
                messageBuilder.append(line)
                
                rawDataBuilder.setLength(0)
                rawDataBuilder.append(line)
                
                hasPending = true
                return null
            }
        }
    }

    fun flush(): LogEvent? {
        if (!hasPending) return null
        
        val event = LogEvent(
            id = pendingId,
            lineIndex = pendingLineIndex,
            timestamp = pendingTimestamp,
            pid = pendingPid,
            tid = pendingTid,
            level = pendingLevel,
            tag = pendingTag,
            message = messageBuilder.toString(),
            rawData = rawDataBuilder.toString(),
            source = pendingSource
        )
        
        hasPending = false
        return event
    }

    fun reset() {
        hasPending = false
        messageBuilder.setLength(0)
        rawDataBuilder.setLength(0)
    }
}
