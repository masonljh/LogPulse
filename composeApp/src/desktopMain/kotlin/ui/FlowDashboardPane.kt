package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antdev.logpulse.domain.model.AnalysisStrategy
import com.antdev.logpulse.domain.model.FlowStatus
import com.antdev.logpulse.domain.model.FlowTrace
import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.SequencePattern

@Composable
fun FlowDashboardPane(
    registeredSequences: List<SequencePattern>,
    flowTraces: List<FlowTrace>,
    onAddFlow: () -> Unit,
    onEditFlow: (SequencePattern) -> Unit,
    onRemoveFlow: (String) -> Unit,
    onJumpToLog: (LogEvent) -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onToggleFlow: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    var selectedSequenceId by remember { mutableStateOf<String?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) } // null means "All"
    
    val selectedSequence = registeredSequences.find { it.id == selectedSequenceId }
    val tracesForSelected = remember(flowTraces, selectedSequenceId) {
        flowTraces.filter { it.sequence.id == selectedSequenceId }
    }
    
    val filteredTraces = remember(tracesForSelected, selectedStatusFilter) {
        if (selectedStatusFilter == null) tracesForSelected
        else tracesForSelected.filter { trace ->
            when (selectedStatusFilter) {
                "Complete" -> trace.status is FlowStatus.Complete
                "InProgress" -> trace.status is FlowStatus.InProgress
                "Failed" -> trace.status is FlowStatus.Failed
                else -> true
            }
        }
    }

    val completeCount = remember(tracesForSelected) { tracesForSelected.count { it.status is FlowStatus.Complete } }
    val inProgressCount = remember(tracesForSelected) { tracesForSelected.count { it.status is FlowStatus.InProgress } }
    val failedCount = remember(tracesForSelected) { tracesForSelected.count { it.status is FlowStatus.Failed } }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1E1E1E))
            .border(0.5.dp, Color.DarkGray)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FLOW DASHBOARD",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isEnabled) 1.0f else 0.5f)) {
                IconButton(onClick = onImportConfig, modifier = Modifier.size(24.dp), enabled = isEnabled) {
                    Icon(Icons.Default.Share, contentDescription = "Import", tint = if (isEnabled) Color.LightGray else Color.Gray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onExportConfig, modifier = Modifier.size(24.dp), enabled = isEnabled) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Export", tint = if (isEnabled) Color.LightGray else Color.Gray, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onAddFlow, modifier = Modifier.size(24.dp), enabled = isEnabled) {
                    Icon(Icons.Default.Add, contentDescription = "Add Flow", tint = if (isEnabled) Color(0xFF4FC3F7) else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Registered Flows", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(8.dp))

        // Sequence List
        Column(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
            if (registeredSequences.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No flows registered", color = Color.DarkGray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(registeredSequences) { sequence ->
                        val traces = flowTraces.filter { it.sequence.id == sequence.id }
                        SequenceCard(
                            sequence = sequence,
                            traces = traces,
                            isSelected = selectedSequenceId == sequence.id,
                            onSelect = { 
                                if (isEnabled) {
                                    selectedSequenceId = it 
                                    selectedStatusFilter = null 
                                }
                            },
                            onEdit = { onEditFlow(sequence) },
                            onDelete = { onRemoveFlow(it) },
                            onToggle = { onToggleFlow(it) },
                            isEnabled = isEnabled
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.5f))

        // Instance List for selected sequence
        Column(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            Text(
                text = if (selectedSequence != null) "Instances: ${selectedSequence.name}" else "Select a flow",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (selectedSequenceId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Status Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (isEnabled) 1.0f else 0.5f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniStatusTab("All", tracesForSelected.size, selectedStatusFilter == null, Color.Gray, isEnabled) { selectedStatusFilter = null }
                    MiniStatusTab("Success", completeCount, selectedStatusFilter == "Complete", Color(0xFF43A047), isEnabled) { selectedStatusFilter = "Complete" }
                    MiniStatusTab("Warning", inProgressCount, selectedStatusFilter == "InProgress", Color(0xFFFFB300), isEnabled) { selectedStatusFilter = "InProgress" }
                    MiniStatusTab("Error", failedCount, selectedStatusFilter == "Failed", Color(0xFFE53935), isEnabled) { selectedStatusFilter = "Failed" }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedSequenceId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                }
            } else if (filteredTraces.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No instances match filter", color = Color.DarkGray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filteredTraces) { trace ->
                        FlowInstanceItem(trace, onJumpToLog)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniStatusTab(
    label: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = if (isEnabled) onClick else ({}),
        enabled = isEnabled,
        color = if (isSelected) color.copy(alpha = 0.25f) else Color(0xFF2D2D2D),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) color else Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    color = if (isSelected) color else Color.DarkGray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SequenceCard(
    sequence: SequencePattern,
    traces: List<FlowTrace>,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String) -> Unit,
    isEnabled: Boolean = true
) {
    val successCount = traces.count { it.status is FlowStatus.Complete }
    val failedCount = traces.count { it.status is FlowStatus.Failed }
    val progressCount = traces.count { it.status is FlowStatus.InProgress }

    Surface(
        onClick = { if (isEnabled) onSelect(sequence.id) },
        enabled = isEnabled,
        color = if (isSelected) Color(0xFF2D2D2D) else Color(0xFF252525),
        shape = RoundedCornerShape(4.dp),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF4FC3F7)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sequence.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isEnabled) 1.0f else 0.5f)) {
                    Switch(
                        checked = sequence.isEnabled,
                        onCheckedChange = { onToggle(sequence.id) },
                        enabled = isEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4FC3F7),
                            checkedTrackColor = Color(0xFF0288D1),
                            disabledCheckedThumbColor = Color.Gray,
                            disabledCheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.scale(0.7f).size(32.dp)
                    )
                    
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp), enabled = isEnabled) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (isEnabled) Color.Gray else Color.DarkGray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onDelete(sequence.id) }, modifier = Modifier.size(24.dp), enabled = isEnabled) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (isEnabled) Color(0xFFE53935) else Color.DarkGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            val alpha = if (sequence.isEnabled) 1.0f else 0.4f
            Row(
                modifier = Modifier.padding(top = 4.dp).alpha(alpha), 
                horizontalArrangement = Arrangement.spacedBy(8.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge("✅ $successCount", Color(0xFF43A047))
                StatusBadge("⚠️ $progressCount", Color(0xFFFFB300))
                StatusBadge("❌ $failedCount", Color(0xFFE53935))
                
                if (sequence.strategy == AnalysisStrategy.SEQUENTIAL) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SEQ",
                        color = Color(0xFF4FC3F7),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF01579B), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FlowInstanceItem(
    trace: FlowTrace,
    onJumpToLog: (LogEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525), RoundedCornerShape(4.dp))
            .padding(8.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val status = trace.status
            val icon = when (status) {
                is FlowStatus.Complete -> Icons.Default.CheckCircle
                is FlowStatus.Failed -> Icons.Default.Warning
                is FlowStatus.InProgress -> Icons.Default.Refresh
            }
            val color = when (status) {
                is FlowStatus.Complete -> Color(0xFF43A047)
                is FlowStatus.Failed -> Color(0xFFE53935)
                is FlowStatus.InProgress -> Color(0xFFFFB300)
            }
            
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "ID: ${trace.id}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            
            trace.sequence.steps.forEach { step ->
                val matchesForStep = trace.logs.filter { it.pattern.name == step.pattern.name }
                val count = matchesForStep.size
                val isMet = count >= step.minCount
                val isOverLimit = step.maxCount != -1 && count > step.maxCount
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = matchesForStep.isNotEmpty()) { 
                            onJumpToLog(matchesForStep.first().log)
                        }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isMet && !isOverLimit) Color(0xFF43A047) else Color(0xFFE53935),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${step.pattern.name}: $count",
                        color = if (isMet) Color.LightGray else Color(0xFFE53935),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (matchesForStep.isNotEmpty()) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Jump",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(12.dp).padding(end = 4.dp)
                        )
                    }
                    if (step.minCount > 1 || step.maxCount != 1) {
                         Text(
                             " (Req: ${step.minCount}${if (step.maxCount == -1) "+" else " to ${step.maxCount}"})",
                             color = Color.DarkGray,
                             fontSize = 10.sp
                         )
                    }
                }
            }

            val status = trace.status
            if (status is FlowStatus.Failed) {
                Text(
                    text = "Reason: ${status.reason}",
                    color = Color(0xFFE53935),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
