package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.antdev.logpulse.domain.model.FlowStatus
import com.antdev.logpulse.domain.model.FlowTrace
import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogLevel

@Composable
fun LogMinimap(
    logs: List<LogEvent>,
    logToFlowIndex: Map<LogEvent, FlowTrace>,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Identify critical indices efficiently (only recalculate when logs change)
    val criticalBitSet by remember(logs) {
        derivedStateOf {
            val bitSet = java.util.BitSet(logs.size)
            logs.forEachIndexed { index, log ->
                if (log.level == LogLevel.WARN || 
                    log.level == LogLevel.ERROR || 
                    log.level == LogLevel.ASSERT) {
                    bitSet.set(index)
                }
            }
            bitSet
        }
    }

    // 2. Identify flow indices efficiently
    val flowBitSet by remember(logs, logToFlowIndex) {
        derivedStateOf {
            val bitSet = java.util.BitSet(logs.size)
            logs.forEachIndexed { index, log ->
                if (logToFlowIndex.containsKey(log)) {
                    bitSet.set(index)
                }
            }
            bitSet
        }
    }

    Box(
        modifier = modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(Color(0xFF252526))
            .pointerInput(logs) {
                detectTapGestures { offset ->
                    val clickedPercent = offset.y / size.height
                    val targetIndex = (clickedPercent * logs.size).toInt().coerceIn(0, logs.size - 1)
                    onScrollTo(targetIndex)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalLogs = logs.size
            if (totalLogs == 0) return@Canvas

            val canvasHeight = size.height
            val canvasWidth = size.width
            
            // Optimization: Downsample to buckets (one bucket per 2 pixels for performance)
            val bucketSizePx = 2f
            val numBuckets = (canvasHeight / bucketSizePx).toInt().coerceAtLeast(1)
            val logsPerBucket = totalLogs.toFloat() / numBuckets

            for (i in 0 until numBuckets) {
                val startIdx = (i * logsPerBucket).toInt()
                val endIdx = ((i + 1) * logsPerBucket).toInt().coerceAtMost(totalLogs)
                
                if (startIdx >= endIdx) continue

                // Check for critical logs in this bucket
                // BitSet.nextSetBit is very efficient
                val firstCritical = criticalBitSet.nextSetBit(startIdx)
                if (firstCritical != -1 && firstCritical < endIdx) {
                    val y = (i * bucketSizePx)
                    drawRect(
                        color = logs[firstCritical].level.toColor(),
                        topLeft = Offset(0f, y),
                        size = Size(canvasWidth * 0.3f, bucketSizePx)
                    )
                }

                // Check for flow logs in this bucket
                val firstFlow = flowBitSet.nextSetBit(startIdx)
                if (firstFlow != -1 && firstFlow < endIdx) {
                    val y = (i * bucketSizePx)
                    val trace = logToFlowIndex[logs[firstFlow]]
                    val color = when (trace?.status) {
                        is FlowStatus.Complete -> Color(0xFF43A047)
                        is FlowStatus.Failed -> Color(0xFFE53935)
                        else -> Color(0xFFFFB300)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(canvasWidth * 0.4f, y),
                        size = Size(canvasWidth * 0.6f, bucketSizePx)
                    )
                }
            }
        }
    }
}
