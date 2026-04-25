package ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antdev.logpulse.domain.model.FilterField
import com.antdev.logpulse.domain.model.FilterType
import com.antdev.logpulse.domain.model.LogFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    filters: List<LogFilter>,
    loadedFiles: List<String>,
    onAddFilter: (String, FilterType, FilterField) -> Unit,
    onRemoveFilter: (String) -> Unit,
    onUpdateFilter: (LogFilter) -> Unit,
    onRemoveFile: (String) -> Unit,
    onAddFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .padding(12.dp)
            .animateContentSize()
    ) {
        // File Management Section
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Files",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )
            
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                loadedFiles.forEach { fileName ->
                    FileChip(fileName = fileName, onRemove = { onRemoveFile(fileName) })
                }
                
                Surface(
                    color = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onAddFile() }.height(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add File", color = Color(0xFF4FC3F7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))

        // PID and TID share a line
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterSection("PID", FilterField.PID, filters, onAddFilter, onRemoveFilter, onUpdateFilter, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(20.dp))
            FilterSection("TID", FilterField.TID, filters, onAddFilter, onRemoveFilter, onUpdateFilter, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // TAG takes full width
        FilterSection("TAG", FilterField.TAG, filters, onAddFilter, onRemoveFilter, onUpdateFilter)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Message takes full width
        FilterSection("Msg", FilterField.MESSAGE, filters, onAddFilter, onRemoveFilter, onUpdateFilter)
    }
}

@Composable
fun FilterSection(
    label: String,
    field: FilterField,
    allFilters: List<LogFilter>,
    onAddFilter: (String, FilterType, FilterField) -> Unit,
    onRemoveFilter: (String) -> Unit,
    onUpdateFilter: (LogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp).padding(top = 8.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            FilterArea(
                placeholder = "Include",
                type = FilterType.INCLUDE,
                field = field,
                filters = allFilters.filter { it.field == field && it.type == FilterType.INCLUDE },
                color = Color(0xFF43A047),
                onAdd = onAddFilter,
                onRemove = onRemoveFilter,
                onUpdate = onUpdateFilter
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilterArea(
                placeholder = "Exclude",
                type = FilterType.EXCLUDE,
                field = field,
                filters = allFilters.filter { it.field == field && it.type == FilterType.EXCLUDE },
                color = Color(0xFFE53935),
                onAdd = onAddFilter,
                onRemove = onRemoveFilter,
                onUpdate = onUpdateFilter
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterArea(
    placeholder: String,
    type: FilterType,
    field: FilterField,
    filters: List<LogFilter>,
    color: Color,
    onAdd: (String, FilterType, FilterField) -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (LogFilter) -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filters.forEach { filter ->
                InlineFilterChip(filter, onRemove, onUpdate)
            }
            
            Box(modifier = Modifier.widthIn(min = 60.dp).height(28.dp).padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty() && filters.isEmpty()) {
                    Text(text = placeholder, color = Color(0xFFBBBBBB), fontSize = 11.sp)
                }
                
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                            if (text.isNotBlank()) {
                                onAdd(text, type, field)
                                text = ""
                            }
                            true
                        } else false
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White, 
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                )
            }
        }

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text, type, field)
                    text = ""
                }
            },
            modifier = Modifier.size(24.dp),
            enabled = text.isNotBlank()
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Filter",
                tint = if (text.isNotBlank()) color else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun InlineFilterChip(
    filter: LogFilter,
    onRemove: (String) -> Unit,
    onUpdate: (LogFilter) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(filter.text) }
    val backgroundColor = if (filter.type == FilterType.INCLUDE) Color(0xFF2E7D32) else Color(0xFFC62828)
    
    Surface(
        color = backgroundColor.copy(alpha = 0.8f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .height(26.dp) // Slightly taller
            .clickable { if (!isEditing) isEditing = true }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f, fill = false),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isEditing) {
                    BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.widthIn(min = 40.dp, max = 250.dp).onKeyEvent {
                            if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                if (editText.isNotBlank()) {
                                    onUpdate(filter.copy(text = editText))
                                    isEditing = false
                                }
                                true
                            } else if (it.key == Key.Escape) {
                                editText = filter.text
                                isEditing = false
                                true
                            } else false
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 11.sp, // Slightly larger
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                    )
                } else {
                    Text(
                        text = filter.text,
                        color = Color.White,
                        fontSize = 11.sp, // Slightly larger
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onRemove(filter.id) }
            )
        }
    }
}

@Composable
fun FileChip(
    fileName: String,
    onRemove: () -> Unit
) {
    Surface(
        color = Color(0xFF1976D2),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fileName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onRemove() }
            )
        }
    }
}

private fun Modifier.heightIn(min: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.sizeIn(minHeight = min))
