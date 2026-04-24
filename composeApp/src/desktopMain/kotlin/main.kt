import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.DragData
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antdev.logpulse.presentation.viewmodel.LogViewModel
import kotlinx.coroutines.launch
import ui.LogGrid
import ui.LogMinimap
import ui.FilterBar
import ui.AddFlowDialog
import ui.FlowDashboardPane
import ui.ImportSelectionDialog
import com.antdev.logpulse.domain.model.*
import java.awt.FileDialog
import java.io.File
import java.net.URLDecoder
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(viewModel: LogViewModel = viewModel { LogViewModel() }) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    
    // Dialog state
    var showAddFlowDialog by remember { mutableStateOf(false) }
    var editingSequence by remember { mutableStateOf<SequencePattern?>(null) }

    if (showAddFlowDialog || editingSequence != null) {
        AddFlowDialog(
            initialSequence = editingSequence,
            onDismiss = { 
                showAddFlowDialog = false
                editingSequence = null
            },
            onConfirm = {
                viewModel.registerSequence(it)
                showAddFlowDialog = false
                editingSequence = null
            }
        )
    }

    // Import state
    var importingConfig by remember { mutableStateOf<LogPulseConfig?>(null) }

    if (importingConfig != null) {
        ImportSelectionDialog(
            config = importingConfig!!,
            onDismiss = { importingConfig = null },
            onConfirm = { importFlows, importFilters ->
                viewModel.importConfig(importingConfig!!, importFlows, importFilters)
                importingConfig = null
            }
        )
    }
    
    // UI state derived from ViewModel
    val currentFlowTrace = remember(viewModel.selectedLog) {
        viewModel.selectedLog?.let { viewModel.logToFlowIndex[it] }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4FC3F7),
            background = Color(0xFF1E1E1E),
            onBackground = Color.White,
            surface = Color(0xFF252526),
            onSurface = Color.White,
            onSurfaceVariant = Color.LightGray,
            outline = Color.Gray
        )
    ) {
        // Root Box for Window-wide interaction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && 
                        (event.isCtrlPressed || event.isMetaPressed) && 
                        event.key == Key.C) {
                        val text = viewModel.getSelectedLogsText()
                        if (text.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(text))
                        }
                        true
                    } else {
                        false
                    }
                }
                .onExternalDrag(
                    onDrop = { state ->
                        val item = state.dragData
                        try {
                            if (item is DragData.FilesList) {
                                val files = item.readFiles()
                                val rawPath = files.firstOrNull()?.toString()
                                if (rawPath != null) {
                                    var path = URLDecoder.decode(rawPath, "UTF-8")
                                    path = path.removePrefix("file://").removePrefix("file:")
                                    if (path.startsWith("/") && path.getOrNull(2) == ':') {
                                        path = path.substring(1)
                                    }
                                    
                                    val file = File(path)
                                    if (file.exists()) {
                                        viewModel.loadLogFile(file.absolutePath)
                                    } else if (File(rawPath).exists()) {
                                        viewModel.loadLogFile(File(rawPath).absolutePath)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            val dialog = FileDialog(null as java.awt.Frame?, "Select Log File", FileDialog.LOAD)
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                val path = File(dialog.directory, dialog.file).absolutePath
                                viewModel.loadLogFile(path)
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Log File")
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = viewModel.statusMessage,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (viewModel.analysisProgress < 1f) {
                                LinearProgressIndicator(
                                    progress = viewModel.analysisProgress,
                                    modifier = Modifier.width(200.dp).padding(top = 4.dp),
                                    color = Color(0xFF4FC3F7),
                                    trackColor = Color.DarkGray
                                )
                            }
                        }
                        
                        Spacer(Modifier.weight(1f))
                        
                        if (viewModel.loadedFiles.isNotEmpty()) {
                            Text(
                                text = "${viewModel.loadedFiles.size} files loaded",
                                color = Color(0xFF4FC3F7),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }

                // Main Content
                Row(modifier = Modifier.fillMaxSize()) {
                    FlowDashboardPane(
                        registeredSequences = viewModel.registeredSequences,
                        flowTraces = viewModel.flowTraces,
                        onAddFlow = { showAddFlowDialog = true },
                        onEditFlow = { editingSequence = it },
                        onRemoveFlow = { viewModel.unregisterSequence(it) },
                        onJumpToLog = { log ->
                            scope.launch {
                                val index = viewModel.getLogIndexInFiltered(log)
                                if (index != -1) {
                                    lazyListState.animateScrollToItem(index)
                                    viewModel.jumpToLog(log)
                                    viewModel.onLogSelected(log) // Also select it
                                }
                            }
                        },
                        onExportConfig = {
                            val dialog = FileDialog(null as java.awt.Frame?, "Export Configuration", FileDialog.SAVE)
                            dialog.file = "logpulse_config.json"
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                val path = File(dialog.directory, dialog.file).absolutePath
                                viewModel.exportConfig(path)
                            }
                        },
                        onImportConfig = {
                            val dialog = FileDialog(null as java.awt.Frame?, "Import Configuration", FileDialog.LOAD)
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                val path = File(dialog.directory, dialog.file).absolutePath
                                val config = viewModel.loadConfigFromFile(path)
                                if (config != null) {
                                    importingConfig = config
                                }
                            }
                        },
                        onToggleFlow = { viewModel.toggleSequence(it) },
                        modifier = Modifier.width(320.dp).fillMaxHeight()
                    )

                    VerticalDivider(color = Color.DarkGray)

                    Column(modifier = Modifier.weight(1f)) {
                        FilterBar(
                            filters = viewModel.filters,
                            loadedFiles = viewModel.loadedFiles,
                            onAddFilter = { text, type, field -> viewModel.addFilter(text, type, field) },
                            onRemoveFilter = { id -> viewModel.removeFilter(id) },
                            onUpdateFilter = { viewModel.updateFilter(it.id, it) },
                            onRemoveFile = { viewModel.removeLogFile(it) },
                            onAddFile = {
                                val dialog = FileDialog(null as java.awt.Frame?, "Select Log File", FileDialog.LOAD)
                                dialog.isVisible = true
                                if (dialog.file != null) {
                                    val path = File(dialog.directory, dialog.file).absolutePath
                                    viewModel.addLogFile(path)
                                }
                            }
                        )
                        
                        Row(modifier = Modifier.fillMaxSize()) {
                            LogGrid(
                                logs = viewModel.filteredLogs,
                                selectedLogIds = viewModel.selectedLogIds.toSet(),
                                highlightedLog = viewModel.highlightedLog,
                                onLogClicked = { log, ctrl, shift -> viewModel.onLogClicked(log, ctrl, shift) },
                                state = lazyListState,
                                logToFlowIndex = viewModel.logToFlowIndex,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LogPulse - MVVM Clean Architecture",
        state = rememberWindowState(width = 1400.dp, height = 900.dp)
    ) {
        App()
    }
}
