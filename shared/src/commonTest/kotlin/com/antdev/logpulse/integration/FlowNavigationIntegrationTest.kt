package com.antdev.logpulse.integration

import com.antdev.logpulse.data.parser.AndroidLogParser
import com.antdev.logpulse.domain.model.*
import com.antdev.logpulse.domain.usecase.AnalyzeFlowsUseCase
import com.antdev.logpulse.domain.usecase.MergeLogsUseCase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FlowNavigationIntegrationTest {
    private val parser = AndroidLogParser()
    private val analyzeUseCase = AnalyzeFlowsUseCase()
    private val mergeUseCase = MergeLogsUseCase()

    @Test
    fun testSequentialFlowNavigationAccuracy() {
        val sequence = SequencePattern(
            id = "sys_task",
            name = "System Task",
            steps = listOf(
                FlowStep(LogPattern("Init", "Initializing...")),
                FlowStep(LogPattern("Done", "Completed."))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        // Scenario: Two identical flows separated by time and other logs
        val logData = """
            04-22 00:00:10.000  1000  1000 I Tag: Initializing...
            04-22 00:00:11.000  1000  1000 I Tag: Working...
            04-22 00:00:12.000  1000  1000 I Tag: Initializing...
            04-22 00:00:13.000  1000  1000 I Tag: Completed.
            04-22 00:50:00.000  1000  1000 I Tag: Initializing...
            04-22 00:50:01.000  1000  1000 I Tag: Completed.
        """.trimIndent()

        val rawLogs = parser.parse(logData, "test_file")
        
        // Simulating the ViewModel flow: Merge -> Analyze
        val mergedLogs = mergeUseCase(mapOf("test_file" to rawLogs))
        val traces = runBlocking { analyzeUseCase.analyzeIncremental(mergedLogs, emptyList(), listOf(sequence)) }

        // We expect 3 traces in SEQUENTIAL mode because of the restarts:
        // 1. 00:00:10 (InProgress, because 00:00:12 restarted it)
        // 2. 00:00:12 -> 00:00:13 (Complete)
        // 3. 00:50:00 -> 00:50:01 (Complete)
        
        // Verify Sorting: The traces must be in chronological order
        assertEquals("04-22 00:00:10.000", traces[0].logs[0].log.timestamp)
        assertEquals("04-22 00:00:12.000", traces[1].logs[0].log.timestamp)
        assertEquals("04-22 00:50:00.000", traces[2].logs[0].log.timestamp)

        val secondTrace = traces[1] // The one starting at 00:00:12
        val firstLogOfSecondTrace = secondTrace.logs[0].log
        
        assertEquals("04-22 00:00:12.000", firstLogOfSecondTrace.timestamp)
        assertEquals("test_file_2", firstLogOfSecondTrace.id, "ID should reflect line index 2 (0-based)")

        // Check index lookup (as done in ViewModel)
        val index = mergedLogs.indexOfFirst { it.id == firstLogOfSecondTrace.id }
        assertEquals(2, index, "Index should be 2 for the 00:00:12 log")
        
        val wrongLog = mergedLogs.find { it.timestamp == "04-22 00:50:00.000" }!!
        assertNotEquals(wrongLog.id, firstLogOfSecondTrace.id, "IDs must be different even for same patterns")
    }
}
