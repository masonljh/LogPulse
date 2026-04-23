package com.antdev.logpulse.domain.engine

import com.antdev.logpulse.data.parser.AndroidLogParser
import com.antdev.logpulse.domain.model.LogPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LogPatternMatcherTest {

    private val parser = AndroidLogParser()
    private val matcher = LogPatternMatcher()

    @Test
    fun testMatchOrderStarted() {
        val line = "04-17 00:00:01.123  1234  5678 I OrderEngine: Order flow_001 started"
        val event = parser.parseLine(line)!!
        
        val pattern = LogPattern("Start", "Order {id} started")
        val match = matcher.match(event, pattern)
        
        assertNotNull(match, "Should match the order started line")
        assertEquals("flow_001", match.extractedId)
    }

    @Test
    fun testMatchWithExtraSpaces() {
        // Logcat often has varying spaces for alignment
        val line = "04-17 00:00:01.123  1234  5678 I OrderEngine: Order    flow_001    started"
        val event = parser.parseLine(line)!!
        
        val pattern = LogPattern("Start", "Order {id} started")
        val match = matcher.match(event, pattern)
        
        // If this fails, we know we need flexible whitespace handling
        assertNotNull(match, "Should match even with extra spaces")
        assertEquals("flow_001", match.extractedId)
    }

    @Test
    fun testNoMatch() {
        val line = "04-17 00:00:01.123  1234  5678 I OrderEngine: Something else happened"
        val event = parser.parseLine(line)!!
        
        val pattern = LogPattern("Start", "Order {id} started")
        val match = matcher.match(event, pattern)
        
        assertNull(match, "Should not match unrelated lines")
    }
    
    @Test
    fun testMatchCaseInsensitive() {
        val line = "04-17 00:00:01.123  1234  5678 I OrderEngine: ORDER flow_001 STARTED"
        val event = parser.parseLine(line)!!
        
        val pattern = LogPattern("Start", "Order {id} started")
        val match = matcher.match(event, pattern)
        
        assertNotNull(match, "Should handle case insensitivity if needed (checking current behavior)")
    }
}
