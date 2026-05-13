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
    private var logCount = 0

    private val timestampRegex = Regex("""^(\d{2}-\d{2}|\d{4}-\d{2}-\d{2})\s+\d{2}:\d{2}:\d{2}\.\d{3,6}""")

    fun parseLine(line: String, idPrefix: String = "", source: String = ""): LogEvent? {
        if (line.isBlank()) return null
        
        // Filter out Logcat markers like "--------- beginning of system"
        if (line.startsWith("---------")) return null
        
        // Use find instead of matchEntire for more flexibility with trailing spaces
        val matchResult = regex.find(line)
        val isFullMatch = matchResult != null && matchResult.range.start == 0
        
        if (isFullMatch) {
            // New log detected. Return the previous one if exists.
            val completedEvent = flush()
            
            val groups = matchResult!!.groupValues

            fun safeGroup(index: Int?): String {
                if (index == null || index < 0 || index >= groups.size) return ""
                return groups[index].trim()
            }

            val levelStr = safeGroup(format.levelGroup)
            val level = if (levelStr.isNotEmpty()) LogLevel.fromChar(levelStr.first().toString()) else LogLevel.UNKNOWN

            // Start new pending log
            val currentLogIndex = logCount++
            pendingId = "${idPrefix}_${currentLogIndex}"
            pendingLineIndex = currentLogIndex
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
            // Check if it looks like a new log by timestamp even if full regex failed
            val looksLikeNewLog = timestampRegex.find(line) != null
            
            if (looksLikeNewLog) {
                val completedEvent = flush()
                
                // Fallback for lines that have a timestamp but didn't match the specific format
                val currentLogIndex = logCount++
                pendingId = "${idPrefix}_${currentLogIndex}"
                pendingLineIndex = currentLogIndex
                pendingTimestamp = timestampRegex.find(line)?.value ?: ""
                pendingPid = ""
                pendingTid = ""
                pendingLevel = LogLevel.UNKNOWN
                pendingTag = "UNPARSED"
                pendingSource = source
                
                messageBuilder.setLength(0)
                messageBuilder.append(line.substring(pendingTimestamp.length).trim())
                
                rawDataBuilder.setLength(0)
                rawDataBuilder.append(line)
                
                hasPending = true
                return completedEvent
            }
            
            // Unmatched line -> continuation
            if (hasPending) {
                messageBuilder.append("\n").append(line)
                rawDataBuilder.append("\n").append(line)
                return null
            } else {
                // First line doesn't match and no timestamp -> create a fallback UNPARSED event
                if (line.length < 5) return null
                
                val currentLogIndex = logCount++
                pendingId = "${idPrefix}_${currentLogIndex}"
                pendingLineIndex = currentLogIndex
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
