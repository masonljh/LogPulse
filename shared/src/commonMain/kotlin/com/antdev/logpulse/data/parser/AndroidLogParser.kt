package com.antdev.logpulse.data.parser

import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogLevel

class AndroidLogParser {
    // Regex for standard logcat: MM-DD HH:MM:SS.mmm PID TID Level Tag: Message
    private val regex = Regex("""^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEA])\s+([^:]+):\s+(.*)$""")

    fun parseLine(line: String, idPrefix: String = "", source: String = "", lineIndex: Int = 0): LogEvent? {
        val matchResult = regex.matchEntire(line) ?: return null
        val groups = matchResult.groupValues

        return LogEvent(
            id = "${idPrefix}_${lineIndex}",
            timestamp = groups[1],
            pid = groups[2],
            tid = groups[3],
            level = LogLevel.fromChar(groups[4]),
            tag = groups[5].trim(),
            message = groups[6],
            rawData = line,
            source = source
        )
    }

    fun parse(content: String, source: String = ""): List<LogEvent> {
        return content.lines().asSequence()
            .mapIndexedNotNull { index, line -> parseLine(line, idPrefix = source, source = source, lineIndex = index) }
            .toList()
    }
}
