package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antdev.logpulse.domain.model.FlowStep
import com.antdev.logpulse.domain.model.LogPattern
import com.antdev.logpulse.domain.model.SequencePattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlowDialog(
    initialSequence: SequencePattern? = null,
    onDismiss: () -> Unit,
    onConfirm: (SequencePattern) -> Unit
) {
    // Fill from initial sequence if editing
    var name by remember { mutableStateOf(initialSequence?.name ?: "") }
    val steps = remember { 
        mutableStateListOf<EditableStep>().apply {
            if (initialSequence != null) {
                addAll(initialSequence.steps.map { EditableStep.fromFlowStep(it) })
            } else {
                add(EditableStep())
            }
        }
    }
    
    // Common Colors for TextField
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFF4FC3F7),
        unfocusedLabelColor = Color.LightGray,
        focusedBorderColor = Color(0xFF4FC3F7),
        unfocusedBorderColor = Color.Gray,
        cursorColor = Color.White
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text(
                    if (initialSequence == null) "Register New Log Flow" else "Edit Log Flow",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Flow Name (e.g. Login Flow)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Steps", color = Color.White, fontWeight = FontWeight.Bold)
                
                LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    itemsIndexed(steps) { index, step ->
                        StepItem(
                            step = step,
                            colors = textFieldColors,
                            isFirst = index == 0,
                            isLast = index == steps.size - 1,
                            onRemove = { if (steps.size > 1) steps.removeAt(index) },
                            onUpdate = { steps[index] = it },
                            onMoveUp = {
                                if (index > 0) {
                                    val item = steps.removeAt(index)
                                    steps.add(index - 1, item)
                                }
                            },
                            onMoveDown = {
                                if (index < steps.size - 1) {
                                    val item = steps.removeAt(index)
                                    steps.add(index + 1, item)
                                }
                            }
                        )
                        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { steps.add(EditableStep()) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4FC3F7))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Step")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.LightGray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank() && steps.all { it.isValid() }) {
                                    val finalSteps = steps.map { it.toFlowStep() }
                                    // Preserve ID if editing
                                    val result = if (initialSequence != null) {
                                        initialSequence.copy(name = name, steps = finalSteps)
                                    } else {
                                        SequencePattern(name = name, steps = finalSteps)
                                    }
                                    onConfirm(result)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
                        ) {
                            Text(if (initialSequence == null) "Register Flow" else "Apply Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepItem(
    step: EditableStep,
    colors: TextFieldColors,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onUpdate: (EditableStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = step.name,
                onValueChange = { onUpdate(step.copy(name = it)) },
                label = { Text("Step Name") },
                modifier = Modifier.weight(1f),
                colors = colors,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp)
            )
            
            Row {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = if (isFirst) Color.DarkGray else Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = if (isLast) Color.DarkGray else Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Step", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                }
            }
        }
        
        OutlinedTextField(
            value = step.pattern,
            onValueChange = { onUpdate(step.copy(pattern = it)) },
            label = { Text("Log Pattern (use {id} for variables)") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            colors = colors,
            placeholder = { Text("e.g. Processing item {id}", color = Color.LightGray, fontSize = 11.sp) }
        )

        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = step.minCount,
                onValueChange = { if (it.all { c -> c.isDigit() }) onUpdate(step.copy(minCount = it)) },
                label = { Text("Min Count") },
                modifier = Modifier.width(100.dp),
                colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = step.maxCount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '-' }) onUpdate(step.copy(maxCount = it)) },
                label = { Text("Max (-1=Inf)") },
                modifier = Modifier.width(100.dp),
                colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(Modifier.weight(1f))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (step.maxCount == "-1") "Repetitive (Infinite)" else if (step.minCount == "0") "Optional" else "Standard",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

data class EditableStep(
    val name: String = "",
    val pattern: String = "",
    val minCount: String = "1",
    val maxCount: String = "1"
) {
    fun isValid() = name.isNotBlank() && pattern.isNotBlank() && minCount.isNotBlank() && maxCount.isNotBlank()
    
    fun toFlowStep() = FlowStep(
        pattern = LogPattern(name, pattern),
        minCount = minCount.toIntOrNull() ?: 1,
        maxCount = maxCount.toIntOrNull() ?: 1
    )

    companion object {
        fun fromFlowStep(step: FlowStep) = EditableStep(
            name = step.pattern.name,
            pattern = step.pattern.patternString,
            minCount = step.minCount.toString(),
            maxCount = step.maxCount.toString()
        )
    }
}
