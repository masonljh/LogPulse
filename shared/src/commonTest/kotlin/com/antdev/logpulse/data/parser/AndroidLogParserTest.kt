package com.antdev.logpulse.data.parser

import com.antdev.logpulse.domain.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AndroidLogParserTest {

    private val parser = AndroidLogParser()

    @Test
    fun testParseValidLogLine() {
        val line = "12-11 15:47:01.321 12345 12567 I ActivityManager: Starting: Intent { act=android.intent.action.MAIN ... }"
        val event = parser.parseLine(line)

        assertNotNull(event)
        assertEquals("12-11 15:47:01.321", event.timestamp)
        assertEquals("12345", event.pid)
        assertEquals("12567", event.tid)
        assertEquals(LogLevel.INFO, event.level)
        assertEquals("ActivityManager", event.tag)
        assertEquals("Starting: Intent { act=android.intent.action.MAIN ... }", event.message)
    }

    @Test
    fun testParseMultipleLines() {
        val content = """
            12-11 15:47:01.321 12345 12567 I ActivityManager: Message 1
            12-11 15:47:02.123 12345 12567 D TestTag: Message 2
        """.trimIndent()

        val events = parser.parse(content)
        assertEquals(2, events.size)
        assertEquals("Message 1", events[0].message)
        assertEquals("Message 2", events[1].message)
        assertEquals(LogLevel.DEBUG, events[1].level)
    }
}
