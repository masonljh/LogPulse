package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.antdev.logpulse.data.parser.RegexLogParser
import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogFormat
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParseConfigDialog(
    filePath: String,
    customFormats: List<LogFormat> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (LogFormat) -> Unit,
    onSaveFormat: (LogFormat) -> Unit = {}
) {
    var selectedPreset by remember { mutableStateOf(LogFormat.ANDROID_LOGCAT) }
    var customPattern by remember { mutableStateOf(selectedPreset.pattern) }
    
    var timestampGroup by remember { mutableStateOf(selectedPreset.timestampGroup?.toString() ?: "") }
    var levelGroup by remember { mutableStateOf(selectedPreset.levelGroup?.toString() ?: "") }
    var tagGroup by remember { mutableStateOf(selectedPreset.tagGroup?.toString() ?: "") }
    var pidGroup by remember { mutableStateOf(selectedPreset.pidGroup?.toString() ?: "") }
    var tidGroup by remember { mutableStateOf(selectedPreset.tidGroup?.toString() ?: "") }
    var messageGroup by remember { mutableStateOf(selectedPreset.messageGroup?.toString() ?: "") }
    var customPresetName by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }
    
    // Preview state
    var previewLines by remember { mutableStateOf(listOf<String>()) }
    var parsedEvents by remember { mutableStateOf(listOf<LogEvent>()) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Load first N lines for preview
    LaunchedEffect(filePath) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                previewLines = file.useLines { it.take(20).toList() }
            }
        } catch (e: Exception) {
            parseError = "Failed to load preview: ${e.message}"
        }
    }

    // Parse preview whenever format changes
    LaunchedEffect(customPattern, timestampGroup, levelGroup, tagGroup, pidGroup, tidGroup, messageGroup, previewLines) {
        val format = LogFormat(
            id = "custom_or_preset",
            name = "Custom",
            pattern = customPattern,
            timestampGroup = timestampGroup.toIntOrNull(),
            levelGroup = levelGroup.toIntOrNull(),
            tagGroup = tagGroup.toIntOrNull(),
            pidGroup = pidGroup.toIntOrNull(),
            tidGroup = tidGroup.toIntOrNull(),
            messageGroup = messageGroup.toIntOrNull()
        )
        
        try {
            val parser = RegexLogParser(format)
            val events = mutableListOf<LogEvent>()
            previewLines.forEachIndexed { index, line ->
                val event = parser.parseLine(line, lineIndex = index)
                if (event != null && event === parser.getPendingPreviousLog() && index == event.lineIndex) {
                    events.add(event)
                } else if (event != null && event.lineIndex < index) {
                    val idx = events.indexOfFirst { it.lineIndex == event.lineIndex }
                    if (idx != -1) events[idx] = event
                }
            }
            parsedEvents = events
            parseError = null
        } catch (e: Exception) {
            parseError = "Invalid Regex or Mapping: ${e.message}"
            parsedEvents = emptyList()
        }
    }

    fun applyPreset(preset: LogFormat) {
        selectedPreset = preset
        customPattern = preset.pattern
        timestampGroup = preset.timestampGroup?.toString() ?: ""
        levelGroup = preset.levelGroup?.toString() ?: ""
        tagGroup = preset.tagGroup?.toString() ?: ""
        pidGroup = preset.pidGroup?.toString() ?: ""
        tidGroup = preset.tidGroup?.toString() ?: ""
        messageGroup = preset.messageGroup?.toString() ?: ""
        customPresetName = if (LogFormat.PRESETS.contains(preset)) "" else preset.name
    }

    val allPresets = LogFormat.PRESETS + customFormats

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(800.dp).height(650.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Log Parser Configuration", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preset:")
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(selectedPreset.name)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            allPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name + if (customFormats.contains(preset)) " (Custom)" else "") },
                                    onClick = { 
                                        applyPreset(preset)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = customPattern,
                    onValueChange = { customPattern = it },
                    label = { Text("Regex Pattern") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedTextField(value = timestampGroup, onValueChange = { timestampGroup = it }, label = { Text("Timestamp Grp") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = levelGroup, onValueChange = { levelGroup = it }, label = { Text("Level Grp") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = tagGroup, onValueChange = { tagGroup = it }, label = { Text("Tag Grp") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(value = messageGroup, onValueChange = { messageGroup = it }, label = { Text("Msg Grp") }, modifier = Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Preview (First 20 lines)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E)).padding(8.dp)) {
                    if (parseError != null) {
                        Text(parseError!!, color = Color.Red)
                    } else if (parsedEvents.isEmpty()) {
                        Text("No lines matched the regex.", color = Color.Gray)
                    } else {
                        LazyColumn {
                            items(parsedEvents) { event ->
                                Text(
                                    text = "[${event.timestamp}] ${event.level} [${event.tag}] ${event.message}",
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customPresetName,
                        onValueChange = { customPresetName = it },
                        label = { Text("Custom Preset Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val formatName = customPresetName.takeIf { it.isNotBlank() } ?: "Custom Format ${System.currentTimeMillis()}"
                            val format = LogFormat(
                                id = "custom_${System.currentTimeMillis()}",
                                name = formatName,
                                pattern = customPattern,
                                timestampGroup = timestampGroup.toIntOrNull(),
                                levelGroup = levelGroup.toIntOrNull(),
                                tagGroup = tagGroup.toIntOrNull(),
                                pidGroup = pidGroup.toIntOrNull(),
                                tidGroup = tidGroup.toIntOrNull(),
                                messageGroup = messageGroup.toIntOrNull()
                            )
                            onSaveFormat(format)
                            selectedPreset = format
                            customPresetName = format.name
                        },
                        enabled = customPattern.isNotBlank()
                    ) {
                        Text("Save Preset")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val format = LogFormat(
                            id = selectedPreset.id.takeIf { customPattern == selectedPreset.pattern } ?: "custom",
                            name = selectedPreset.name.takeIf { customPattern == selectedPreset.pattern } ?: "Custom Format",
                            pattern = customPattern,
                            timestampGroup = timestampGroup.toIntOrNull(),
                            levelGroup = levelGroup.toIntOrNull(),
                            tagGroup = tagGroup.toIntOrNull(),
                            pidGroup = pidGroup.toIntOrNull(),
                            tidGroup = tidGroup.toIntOrNull(),
                            messageGroup = messageGroup.toIntOrNull()
                        )
                        onConfirm(format)
                    }) {
                        Text("Load with Format")
                    }
                }
            }
        }
    }
}
