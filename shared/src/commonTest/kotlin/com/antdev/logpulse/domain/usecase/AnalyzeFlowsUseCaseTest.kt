package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.data.parser.AndroidLogParser
import com.antdev.logpulse.domain.model.*
import kotlin.test.*

class AnalyzeFlowsUseCaseTest {

    private val parser = AndroidLogParser()
    private val analyzeUseCase = AnalyzeFlowsUseCase()

    private val orderProcessingSequence = SequencePattern(
        name = "Order Processing Flow",
        steps = listOf(
            FlowStep(LogPattern("Start", "Order {id} started")),
            FlowStep(LogPattern("Verify", "Transaction verified for {id}")),
            FlowStep(LogPattern("Payment", "Payment for {id} processed")),
            FlowStep(LogPattern("Shipped", "Order {id} shipped")),
            FlowStep(LogPattern("Completed", "Order {id} completed"))
        ),
        isEnabled = true
    )

    @Test
    fun testCompleteFlowAnalysis() {
        val logContent = """
            04-17 00:00:01.123  1234  5678 I OrderEngine: Order flow_001 started
            04-17 00:00:02.456  1234  5678 D PaymentProcessor: Transaction verified for flow_001
            04-17 00:00:03.789  1234  5678 I PaymentProcessor: Payment for flow_001 processed
            04-17 00:00:06.222  1234  5678 I LogPulse: Order flow_001 shipped
            04-17 00:00:07.333  1234  5678 I LogPulse: Order flow_001 completed
        """.trimIndent()
        
        val logs = parser.parse(logContent)
        val traces = analyzeUseCase.analyzeIncremental(
            newLogs = logs,
            existingFlows = emptyList(),
            sequences = listOf(orderProcessingSequence)
        )
        
        assertEquals(1, traces.size, "Should find one flow trace for flow_001")
        val trace = traces[0]
        assertEquals("flow_001", trace.id)
        assertTrue(trace.status is FlowStatus.Complete, "Status should be Complete. Current: ${trace.status}")
        assertEquals(5, trace.logs.size, "Should have 5 matched logs in the trace")
    }

    @Test
    fun testInProgressFlowAnalysis() {
        val logContent = """
            04-17 00:00:01.123  1234  5678 I OrderEngine: Order flow_002 started
            04-17 00:00:02.456  1234  5678 D PaymentProcessor: Transaction verified for flow_002
        """.trimIndent()
        
        val logs = parser.parse(logContent)
        val traces = analyzeUseCase.analyzeIncremental(
            newLogs = logs,
            existingFlows = emptyList(),
            sequences = listOf(orderProcessingSequence)
        )
        
        assertEquals(1, traces.size)
        val trace = traces[0]
        assertTrue(trace.status is FlowStatus.InProgress, "Status should be InProgress")
    }

    @Test
    fun testMultiInstanceSameId() {
        // flow_001 starts, finishes, then starts again
        val logContent = """
            04-17 00:00:01.000  1234  5678 I OrderEngine: Order flow_001 started
            04-17 00:00:02.000  1234  5678 D PaymentProcessor: Transaction verified for flow_001
            04-17 00:00:03.000  1234  5678 I PaymentProcessor: Payment for flow_001 processed
            04-17 00:00:04.000  1234  5678 I LogPulse: Order flow_001 shipped
            04-17 00:00:05.000  1234  5678 I LogPulse: Order flow_001 completed
            04-17 00:00:10.000  1234  5678 I OrderEngine: Order flow_001 started
            04-17 00:00:11.000  1234  5678 D PaymentProcessor: Transaction verified for flow_001
            04-17 00:00:12.000  1234  5678 I PaymentProcessor: Payment for flow_001 processed
            04-17 00:00:13.000  1234  5678 I LogPulse: Order flow_001 shipped
            04-17 00:00:14.000  1234  5678 I LogPulse: Order flow_001 completed
        """.trimIndent()
        
        val logs = parser.parse(logContent)
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(orderProcessingSequence))
        
        // Should have 2 separate traces for flow_001
        assertEquals(2, traces.size, "Should have 2 separate instances for flow_001")
        assertTrue(traces.all { it.id == "flow_001" })
        assertTrue(traces.all { it.status is FlowStatus.Complete }, "All traces should be complete")
        assertEquals(5, traces[0].logs.size)
        assertEquals(5, traces[1].logs.size)
    }
    @Test
    fun testLargeGapAnalysis() {
        val sequence = SequencePattern(
            name = "Gap Flow",
            steps = listOf(
                FlowStep(LogPattern("Start", "Start")),
                FlowStep(LogPattern("End", "End"))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        val sb = StringBuilder()
        sb.append("04-17 00:00:00.000  1000  5000 I Tag: Start\n")
        // Add 3300 lines of noise
        for (i in 1..3300) {
            sb.append("04-17 00:00:01.000  1000  5000 D Noise: Line $i\n")
        }
        sb.append("04-17 00:00:02.000  1000  5000 I Tag: End\n")

        val logs = parser.parse(sb.toString())
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(sequence))

        assertEquals(1, traces.size, "Should detect flow despite 3300 lines of gap")
        assertTrue(traces[0].status is FlowStatus.Complete)
        assertEquals(2, traces[0].logs.size)
    }

    @Test
    fun testInterleavedFlowsNoId() {
        val sequence = SequencePattern(
            name = "Interleaved",
            steps = listOf(
                FlowStep(LogPattern("Start", "Start")),
                FlowStep(LogPattern("End", "End"))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        // Flow 1 Start, Flow 2 Start, Flow 1 End, Flow 2 End
        val logData = """
            04-17 00:00:01.000  1000  5001 I Tag: Start
            04-17 00:00:02.000  1000  5002 I Tag: Start
            04-17 00:00:03.000  1000  5001 I Tag: End
            04-17 00:00:04.000  1000  5002 I Tag: End
        """.trimIndent()

        val logs = parser.parse(logData)
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(sequence))

        assertEquals(2, traces.size, "Should handle interleaved flows correctly")
        assertTrue(traces.all { it.status is FlowStatus.Complete })
    }

    @Test
    fun testIdTransition() {
        val sequence = SequencePattern(
            name = "Transition Flow",
            steps = listOf(
                FlowStep(LogPattern("Start", "Start task")),
                FlowStep(LogPattern("Assign", "Assign ID {id}"))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        val logContent = """
            04-17 00:00:01.000  1000  5000 I Tag: Start task
            04-17 00:00:02.000  1000  5000 I Tag: Assign ID task_99
        """.trimIndent()

        val logs = parser.parse(logContent)
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(sequence))

        assertEquals(1, traces.size)
        assertEquals("task_99", traces[0].id, "ID should have transitioned from thread_... to task_99")
        assertTrue(traces[0].status is FlowStatus.Complete)
    }

    @Test
    fun testDuplicationFix() {
        val sequence = SequencePattern(
            name = "Seq",
            steps = listOf(
                FlowStep(LogPattern("A", "A")),
                FlowStep(LogPattern("B", "B"))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        val logA = parser.parse("04-17 00:00:01.000  1000  5000 I T: A")
        val logB = parser.parse("04-17 00:00:02.000  1000  5000 I T: B")

        // First analysis with Log A
        val traces1 = analyzeUseCase.analyzeIncremental(logA, emptyList(), listOf(sequence))
        assertEquals(1, traces1.size)
        assertEquals(1, traces1[0].logs.size)

        // Second analysis with Log B (Passing traces1 as existing)
        val traces2 = analyzeUseCase.analyzeIncremental(logB, traces1, listOf(sequence))
        
        // CRITICAL: traces2 should ONLY have one trace (the updated one)
        assertEquals(1, traces2.size, "Should NOT have duplicate entries for the same flow instance")
        assertEquals(2, traces2[0].logs.size)
        assertTrue(traces2[0].status is FlowStatus.Complete)
    }

    @Test
    fun testSystemTaskFlowRepetition() {
        val sequence = SequencePattern(
            name = "System Task Flow",
            steps = listOf(
                FlowStep(LogPattern("Init", "Initializing component...")),
                FlowStep(LogPattern("Conn", "Connection established to server.")),
                FlowStep(LogPattern("Fetch", "Fetching data from database...")),
                FlowStep(LogPattern("Sync", "Synchronization complete."))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        // Generate 10 repeated cycles
        val sb = StringBuilder()
        for (i in 0 until 10) {
            val min = i / 10
            val sec = (i % 10) * 4
            val time = "04-17 00:0${min}:${sec.toString().padStart(2, '0')}"
            sb.append("$time.000  1000  5000 I Tag: Initializing component...\n")
            sb.append("$time.100  1000  5000 I Tag: Connection established to server.\n")
            sb.append("$time.200  1000  5000 I Tag: Fetching data from database...\n")
            sb.append("$time.300  1000  5000 I Tag: Synchronization complete.\n")
        }

        val logs = parser.parse(sb.toString())
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(sequence))

        println("--- Found ${traces.size} traces ---")
        traces.forEachIndexed { index, trace ->
            println("Trace $index: id=${trace.id}, status=${trace.status}, logs=${trace.logs.size}")
        }

        assertEquals(10, traces.size, "Should find exactly 10 flows, but found ${traces.size}")
        val completeCount = traces.count { it.status is FlowStatus.Complete }
        assertEquals(10, completeCount, "All 10 flows should be Complete. Found $completeCount complete and ${traces.size - completeCount} others")
    }

    @Test
    fun testInitRestartBug() {
        val sequence = SequencePattern(
            name = "System Task Flow",
            steps = listOf(
                FlowStep(LogPattern("Init", "Initializing component...")),
                FlowStep(LogPattern("Sync", "Synchronization complete."))
            ),
            strategy = AnalysisStrategy.SEQUENTIAL,
            isEnabled = true
        )

        val logData = """
            04-17 00:00:01.000  1000  5000 I Tag: Initializing component...
            04-17 00:00:02.000  1000  5000 I Tag: Initializing component...
            04-17 00:00:03.000  1000  5000 I Tag: Synchronization complete.
        """.trimIndent()

        val logs = parser.parse(logData)
        val traces = analyzeUseCase.analyzeIncremental(logs, emptyList(), listOf(sequence))

        // There should be 2 traces:
        // 1. Just "Init" (InProgress or potentially Failed if we define it so, but here it's just InProgress)
        // 2. "Init" + "Sync" (Complete)
        assertEquals(2, traces.size, "Should have 2 traces due to restart")
        
        val completeCount = traces.count { it.status is FlowStatus.Complete }
        assertEquals(1, completeCount, "Only the second flow should be Complete")
        
        val inProgressCount = traces.count { it.status is FlowStatus.InProgress }
        assertEquals(1, inProgressCount, "The first flow should stay InProgress")
    }
}
