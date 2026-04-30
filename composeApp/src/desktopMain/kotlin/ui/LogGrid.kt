package ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antdev.logpulse.domain.model.FlowStatus
import com.antdev.logpulse.domain.model.FlowTrace
import com.antdev.logpulse.domain.model.LogEvent
import java.awt.Cursor

@Composable
fun LogGrid(
    logs: List<LogEvent>,
    selectedLogIds: Set<String>,
    highlightedLog: LogEvent? = null,
    onLogClicked: (LogEvent, Boolean, Boolean) -> Unit,
    state: LazyListState,
    logToFlowIndex: Map<LogEvent, FlowTrace>,
    modifier: Modifier = Modifier,
    showSourceColumn: Boolean = true
) {
    var lineNumWidth by remember { mutableStateOf(50.dp) }
    var timeWidth by remember { mutableStateOf(140.dp) }
    var pidTidWidth by remember { mutableStateOf(90.dp) }
    var levelWidth by remember { mutableStateOf(20.dp) }
    var tagWidth by remember { mutableStateOf(110.dp) }
    var sourceWidth by remember { mutableStateOf(100.dp) }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        LogGridHeader(
            lineNumWidth = lineNumWidth, onLineNumWidthChange = { lineNumWidth = it },
            timeWidth = timeWidth, onTimeWidthChange = { timeWidth = it },
            pidTidWidth = pidTidWidth, onPidTidWidthChange = { pidTidWidth = it },
            levelWidth = levelWidth, onLevelWidthChange = { levelWidth = it },
            tagWidth = tagWidth, onTagWidthChange = { tagWidth = it },
            sourceWidth = sourceWidth, onSourceWidthChange = { sourceWidth = it },
            showSourceColumn = showSourceColumn
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            index = log.lineIndex + 1,
                            log = log,
                            isSelected = selectedLogIds.contains(log.id),
                            isHighlighted = log == highlightedLog,
                            flowTrace = logToFlowIndex[log],
                            onClicked = { ctrl, shift -> onLogClicked(log, ctrl, shift) },
                            showSourceColumn = showSourceColumn,
                            lineNumWidth = lineNumWidth,
                            timeWidth = timeWidth,
                            pidTidWidth = pidTidWidth,
                            levelWidth = levelWidth,
                            tagWidth = tagWidth,
                            sourceWidth = sourceWidth
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(state),
                    style = LocalScrollbarStyle.current.copy(
                        unhoverColor = Color.White.copy(alpha = 0.4f),
                        hoverColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
fun LogGridHeader(
    lineNumWidth: Dp, onLineNumWidthChange: (Dp) -> Unit,
    timeWidth: Dp, onTimeWidthChange: (Dp) -> Unit,
    pidTidWidth: Dp, onPidTidWidthChange: (Dp) -> Unit,
    levelWidth: Dp, onLevelWidthChange: (Dp) -> Unit,
    tagWidth: Dp, onTagWidthChange: (Dp) -> Unit,
    sourceWidth: Dp, onSourceWidthChange: (Dp) -> Unit,
    showSourceColumn: Boolean
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF252525))
            .drawBehind {
                drawLine(
                    color = Color(0xFF333333),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        ResizableHeaderCell("Line", lineNumWidth, onLineNumWidthChange, alignment = Alignment.End)
        Spacer(modifier = Modifier.width(8.dp))
        ResizableHeaderCell("Time", timeWidth, onTimeWidthChange)
        ResizableHeaderCell("PID/TID", pidTidWidth, onPidTidWidthChange, alignment = Alignment.End)
        Spacer(modifier = Modifier.width(12.dp))
        ResizableHeaderCell("L", levelWidth, onLevelWidthChange, minWidth = 20.dp, alignment = Alignment.CenterHorizontally)
        Spacer(modifier = Modifier.width(8.dp))
        ResizableHeaderCell("Tag", tagWidth, onTagWidthChange)
        Spacer(modifier = Modifier.width(8.dp))
        
        Text("Message", modifier = Modifier.weight(1f).padding(horizontal = 4.dp), color = Color(0xFF999999), fontSize = 12.sp)
        
        if (showSourceColumn) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(modifier = Modifier.width(sourceWidth).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR)))
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val deltaDp = with(density) { delta.toDp() }
                                onSourceWidthChange(maxOf(30.dp, sourceWidth - deltaDp))
                            }
                        )
                )
                Text(
                    text = "Source",
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    color = Color(0xFF999999),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
fun ResizableHeaderCell(
    title: String,
    width: Dp,
    onWidthChange: (Dp) -> Unit,
    minWidth: Dp = 30.dp,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier.width(width).fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title, 
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp), 
            color = Color(0xFF999999), 
            fontSize = 12.sp, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            textAlign = when(alignment) {
                Alignment.CenterHorizontally -> androidx.compose.ui.text.style.TextAlign.Center
                Alignment.End -> androidx.compose.ui.text.style.TextAlign.End
                else -> androidx.compose.ui.text.style.TextAlign.Start
            }
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        onWidthChange(maxOf(minWidth, width + deltaDp))
                    }
                )
        )
    }
}

@Composable
fun LogTableRow(
    index: Int,
    log: LogEvent,
    isSelected: Boolean,
    isHighlighted: Boolean,
    flowTrace: FlowTrace?,
    onClicked: (Boolean, Boolean) -> Unit,
    showSourceColumn: Boolean,
    lineNumWidth: Dp,
    timeWidth: Dp,
    pidTidWidth: Dp,
    levelWidth: Dp,
    tagWidth: Dp,
    sourceWidth: Dp
) {
    val levelColor = log.level.toColor()
    
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

    val flowColor = when {
        flowTrace == null -> Color.Transparent
        flowTrace.status is FlowStatus.Complete -> Color(0xFF43A047).copy(alpha = 0.15f)
        flowTrace.status is FlowStatus.Failed -> Color(0xFFE53935).copy(alpha = 0.15f)
        else -> Color(0xFFFFB300).copy(alpha = 0.15f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
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

        Text(
            text = index.toString(),
            color = Color(0xFF777777),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(lineNumWidth),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.timestamp,
            color = Color(0xFFBBBBBB),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(timeWidth)
        )

        Text(
            text = "${log.pid}/${log.tid}",
            color = Color(0xFF999999),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(pidTidWidth),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = log.level.toLabel(),
            color = levelColor,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.width(levelWidth),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.tag,
            color = levelColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(tagWidth)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.message,
            color = levelColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (showSourceColumn) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.source,
                color = Color(0xFF64B5F6),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(sourceWidth)
            )
        }
    }
}
