package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antdev.logpulse.domain.model.LogPulseConfig

@Composable
fun ImportSelectionDialog(
    config: LogPulseConfig,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit
) {
    var importFlows by remember { mutableStateOf(true) }
    var importFilters by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Configuration") },
        text = {
            Column {
                Text(
                    "Select components to import from the file:",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = importFlows,
                        onCheckedChange = { importFlows = it }
                    )
                    Text("Flow Patterns (${config.sequences.size} items)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = importFilters,
                        onCheckedChange = { importFilters = it }
                    )
                    Text("Filter Settings (${config.filters.size} items)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(importFlows, importFilters) },
                enabled = importFlows || importFilters
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
