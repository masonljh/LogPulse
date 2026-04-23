package ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.antdev.logpulse.domain.model.FlowStatus
import com.antdev.logpulse.domain.model.FlowTrace
import com.antdev.logpulse.domain.model.LogEvent

@Composable
fun LogGrid(
    logs: List<LogEvent>,
    selectedLogIds: Set<String>,
    highlightedLog: LogEvent? = null,
    onLogClicked: (LogEvent, Boolean, Boolean) -> Unit,
    state: LazyListState,
    logToFlowIndex: Map<LogEvent, FlowTrace>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp)
            ) {
                items(
                    count = logs.size,
                    key = { index -> logs[index].id }
                ) { index ->
                    val log = logs[index]
                    LogTableRow(
                        log = log,
                        isSelected = selectedLogIds.contains(log.id),
                        isHighlighted = log == highlightedLog,
                        flowTrace = logToFlowIndex[log],
                        onClicked = { ctrl, shift -> onLogClicked(log, ctrl, shift) }
                    )
                }
            }
            
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(state)
            )
        }
    }
}


@Composable
fun LogTableRow(
    log: LogEvent,
    isSelected: Boolean,
    isHighlighted: Boolean,
    flowTrace: FlowTrace?,
    onClicked: (Boolean, Boolean) -> Unit
) {
    val levelColor = log.level.toColor()
    
    // Optimized Highlight Animation
    val highlightColor = if (isHighlighted) {
        val infiniteTransition = rememberInfiniteTransition()
        infiniteTransition.animateColor(
            initialValue = Color(0xFF4FC3F7).copy(alpha = 0.4f),
            targetValue = Color.Transparent,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        ).value
    } else {
        Color.Transparent
    }

    // Flow indicator color
    val flowColor = when {
        flowTrace == null -> Color.Transparent
        flowTrace.status is FlowStatus.Complete -> Color(0xFF43A047).copy(alpha = 0.15f)
        flowTrace.status is FlowStatus.Failed -> Color(0xFFE53935).copy(alpha = 0.15f)
        else -> Color(0xFFFFB300).copy(alpha = 0.15f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp) // Slightly tighter height for more data
            .background(if (isSelected) Color(0xFF3F3F3F) else Color.Transparent)
            .background(flowColor)
            .background(highlightColor)
            .pointerInput(log.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                            val isShift = event.keyboardModifiers.isShiftPressed
                            onClicked(isCtrl, isShift)
                        }
                    }
                }
            }
            .drawBehind {
                // Bottom divider border - much cheaper than HorizontalDivider composable
                drawLine(
                    color = Color(0xFF333333),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))

        // Time
        Text(
            text = log.timestamp,
            color = Color(0xFFBBBBBB),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(140.dp)
        )

        // PID/TID (Grouped)
        Text(
            text = "${log.pid}/${log.tid}",
            color = Color(0xFF999999),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(90.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Level Label
        Text(
            text = log.level.toLabel(),
            color = levelColor,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.width(16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Tag
        Text(
            text = log.tag,
            color = levelColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Source
        Text(
            text = log.source,
            color = Color(0xFF64B5F6),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(100.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Message
        Text(
            text = log.message,
            color = levelColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
