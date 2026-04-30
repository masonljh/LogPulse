package com.antdev.logpulse.presentation.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antdev.logpulse.domain.model.*
import com.antdev.logpulse.domain.usecase.AnalyzeFlowsUseCase
import com.antdev.logpulse.domain.usecase.ParseLogFileUseCase
import com.antdev.logpulse.domain.usecase.MergeLogsUseCase
import com.antdev.logpulse.data.storage.SequenceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class LogViewModel(
    private val parseUseCase: ParseLogFileUseCase = ParseLogFileUseCase(),
    private val analyzeUseCase: AnalyzeFlowsUseCase = AnalyzeFlowsUseCase(),
    private val mergeUseCase: MergeLogsUseCase = MergeLogsUseCase()
) : ViewModel() {

    // Persistence
    private val storage = SequenceStorage()

    // State
    private val sourceMap = mutableMapOf<String, MutableList<LogEvent>>()
    val logs = mutableStateListOf<LogEvent>()
    
    val loadedFiles = mutableStateListOf<String>()
    
    var selectedLog by mutableStateOf<LogEvent?>(null)
        private set
    
    val selectedLogIds = mutableStateListOf<String>()
    private var lastSelectedIndex: Int = -1
    
    var statusMessage by mutableStateOf("Ready")
        private set
    
    var analysisProgress by mutableStateOf(1.0f)
        private set
        
    var highlightedLog by mutableStateOf<LogEvent?>(null)
        private set

    var currentFileName by mutableStateOf<String?>(null)
        private set

    // Filtering State
    val filters = mutableStateListOf<LogFilter>()
    
    // Asynchronous filtered logs state
    private val _filteredLogs = mutableStateOf<List<LogEvent>>(emptyList())
    val filteredLogs: List<LogEvent> by _filteredLogs

    // Flow Analysis State
    val registeredSequences = mutableStateListOf<SequencePattern>()
    var flowTraces by mutableStateOf(listOf<FlowTrace>())
        private set
    
    val customFormats = mutableStateListOf<LogFormat>()
    
    private var analysisJob: Job? = null
        
    val logToFlowIndex = mutableStateMapOf<LogEvent, FlowTrace>()

    private var loadJob: Job? = null
    private var filterJob: Job? = null

    init {
        loadPersistentConfig()
    }

    private fun refreshFilters() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val currentLogs = logs.toList()
            val activeFilters = filters.toList()
            
            if (activeFilters.isEmpty()) {
                _filteredLogs.value = currentLogs
                statusMessage = "Loaded ${logs.size} lines. Flows: ${flowTraces.size}"
                return@launch
            }

            statusMessage = "Filtering ${currentLogs.size} lines..."

            // Group filters for performance
            val includeFiltersByField = FilterField.entries.associateWith { field ->
                activeFilters.filter { it.field == field && it.type == FilterType.INCLUDE }
            }
            val excludeFilters = activeFilters.filter { it.type == FilterType.EXCLUDE }

            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                currentLogs.filter { log ->
                    // 1. Check Include filters (AND between fields, OR within same field)
                    val includePass = includeFiltersByField.all { (field, fieldFilters) ->
                        if (fieldFilters.isEmpty()) true
                        else fieldFilters.any { filter -> matches(log, filter) }
                    }
                    if (!includePass) return@filter false
                    
                    // 2. Check Exclude filters (OR for any exclusion)
                    val excludeFail = excludeFilters.any { matches(log, it) }
                    !excludeFail
                }
            }
            _filteredLogs.value = result
            statusMessage = "Showing ${result.size} of ${logs.size} lines. Flows: ${flowTraces.size}"
        }
    }

    private fun loadPersistentConfig() {
        val savedSequences = storage.loadSequences()
        if (savedSequences.isNotEmpty()) {
            registeredSequences.addAll(savedSequences)
        } else {
            // Default sequences
            registerSequence(
                SequencePattern(
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
            )
            registerSequence(
                SequencePattern(
                    name = "Background Worker Flow",
                    steps = listOf(
                        FlowStep(LogPattern("Start", "Starting background task #{id}")),
                        FlowStep(LogPattern("Process", "Processing item ID: {id}")),
                        FlowStep(LogPattern("Cache", "Updating cache"))
                    ),
                    strategy = AnalysisStrategy.SEQUENTIAL,
                    isEnabled = true
                )
            )
        }

        val savedFilters = storage.loadFilters()
        if (savedFilters.isNotEmpty()) {
            filters.addAll(savedFilters)
        }
        
        val savedFormats = storage.loadFormats()
        if (savedFormats.isNotEmpty()) {
            customFormats.addAll(savedFormats)
        }
        
        // Ensure filters are applied initially
        refreshFilters()
    }

    fun saveCustomFormat(format: LogFormat) {
        val index = customFormats.indexOfFirst { it.id == format.id || it.name == format.name }
        if (index != -1) {
            customFormats[index] = format
        } else {
            val newFormat = if (format.id == "custom_or_preset" || format.id == "custom") {
                format.copy(id = "custom_${System.currentTimeMillis()}")
            } else format
            customFormats.add(newFormat)
        }
        storage.saveFormats(customFormats.toList())
    }

    fun onLogSelected(log: LogEvent) {
        selectedLog = log
        selectedLogIds.clear()
        selectedLogIds.add(log.id)
        lastSelectedIndex = filteredLogs.indexOfFirst { it.id == log.id }
    }

    fun onLogClicked(log: LogEvent, isCtrlPressed: Boolean, isShiftPressed: Boolean) {
        val currentIndex = filteredLogs.indexOfFirst { it.id == log.id }
        if (currentIndex == -1) return

        when {
            isShiftPressed && lastSelectedIndex != -1 -> {
                // Range selection
                val start = minOf(lastSelectedIndex, currentIndex)
                val end = maxOf(lastSelectedIndex, currentIndex)
                
                // If Ctrl is NOT pressed, clear previous selections
                if (!isCtrlPressed) {
                    selectedLogIds.clear()
                }
                
                for (i in start..end) {
                    val logAtIdx = filteredLogs[i]
                    if (!selectedLogIds.contains(logAtIdx.id)) {
                        selectedLogIds.add(logAtIdx.id)
                    }
                }
            }
            isCtrlPressed -> {
                // Toggle selection
                if (selectedLogIds.contains(log.id)) {
                    selectedLogIds.remove(log.id)
                } else {
                    selectedLogIds.add(log.id)
                }
                lastSelectedIndex = currentIndex
            }
            else -> {
                // Single selection
                selectedLog = log
                selectedLogIds.clear()
                selectedLogIds.add(log.id)
                lastSelectedIndex = currentIndex
            }
        }
    }

    fun getSelectedLogsText(): String {
        return filteredLogs
            .filter { selectedLogIds.contains(it.id) }
            .joinToString("\n") { log ->
                "${log.timestamp} ${log.pid}/${log.tid} ${log.level.toLabel()} ${log.tag}: ${log.message}"
            }
    }

    fun jumpToLog(log: LogEvent) {
        highlightedLog = log
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (highlightedLog == log) {
                highlightedLog = null
            }
        }
    }

    fun getLogIndexInFiltered(log: LogEvent): Int {
        return filteredLogs.indexOfFirst { it.id == log.id }
    }

    fun getClosestLogIndexByLineNumber(targetLineNumber: Int): Int {
        if (filteredLogs.isEmpty()) return -1
        val targetIndex = targetLineNumber - 1
        
        var closestDiff = Int.MAX_VALUE
        var closestIndex = -1
        
        for (i in filteredLogs.indices) {
            val log = filteredLogs[i]
            val diff = kotlin.math.abs(log.lineIndex - targetIndex)
            if (diff < closestDiff) {
                closestDiff = diff
                closestIndex = i
            }
            if (diff == 0) break // Exact match found
        }
        
        return closestIndex
    }

    private fun matches(log: LogEvent, filter: LogFilter): Boolean {
        val target = when (filter.field) {
            FilterField.PID -> log.pid
            FilterField.TID -> log.tid
            FilterField.TAG -> log.tag
            FilterField.MESSAGE -> log.message
        }
        return target.contains(filter.text, ignoreCase = true)
    }

    fun addFilter(text: String, type: FilterType, field: FilterField) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        if (filters.none { it.text == trimmedText && it.type == type && it.field == field }) {
            filters.add(LogFilter(text = trimmedText, type = type, field = field))
            saveFilters()
            refreshFilters()
        }
    }

    fun removeFilter(id: String) {
        filters.removeAll { it.id == id }
        saveFilters()
        refreshFilters()
    }

    fun updateFilter(id: String, updatedFilter: LogFilter) {
        val index = filters.indexOfFirst { it.id == id }
        if (index != -1) {
            filters[index] = updatedFilter
            saveFilters()
            refreshFilters()
        }
    }

    private fun saveFilters() {
        storage.saveFilters(filters.toList())
    }

    fun registerSequence(sequence: SequencePattern) {
        val index = registeredSequences.indexOfFirst { it.id == sequence.id }
        if (index != -1) {
            registeredSequences[index] = sequence
        } else {
            registeredSequences.add(sequence)
        }
        saveSequences()
        reanalyzeAllFlows()
    }

    fun unregisterSequence(id: String) {
        registeredSequences.removeAll { it.id == id }
        saveSequences()
        reanalyzeAllFlows()
    }

    fun toggleSequence(id: String) {
        val index = registeredSequences.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = registeredSequences[index]
            registeredSequences[index] = old.copy(isEnabled = !old.isEnabled)
            saveSequences()
            reanalyzeAllFlows()
        }
    }

    fun moveStep(sequenceId: String, fromIndex: Int, toIndex: Int) {
        val seqIndex = registeredSequences.indexOfFirst { it.id == sequenceId }
        if (seqIndex == -1) return
        
        val sequence = registeredSequences[seqIndex]
        if (fromIndex !in sequence.steps.indices || toIndex !in sequence.steps.indices) return
        
        val newSteps = sequence.steps.toMutableList()
        val step = newSteps.removeAt(fromIndex)
        newSteps.add(toIndex, step)
        
        registeredSequences[seqIndex] = sequence.copy(steps = newSteps)
        saveSequences()
        reanalyzeAllFlows()
    }

    fun addStep(sequenceId: String, pattern: LogPattern) {
        val seqIndex = registeredSequences.indexOfFirst { it.id == sequenceId }
        if (seqIndex == -1) return
        
        val sequence = registeredSequences[seqIndex]
        val newSteps = sequence.steps.toMutableList()
        newSteps.add(FlowStep(pattern))
        
        registeredSequences[seqIndex] = sequence.copy(steps = newSteps)
        saveSequences()
        reanalyzeAllFlows()
    }

    fun removeStep(sequenceId: String, stepIndex: Int) {
        val seqIndex = registeredSequences.indexOfFirst { it.id == sequenceId }
        if (seqIndex == -1) return
        
        val sequence = registeredSequences[seqIndex]
        if (stepIndex !in sequence.steps.indices) return
        
        val newSteps = sequence.steps.toMutableList()
        newSteps.removeAt(stepIndex)
        
        registeredSequences[seqIndex] = sequence.copy(steps = newSteps)
        saveSequences()
        reanalyzeAllFlows()
    }

    fun updateStep(sequenceId: String, stepIndex: Int, updatedStep: FlowStep) {
        val seqIndex = registeredSequences.indexOfFirst { it.id == sequenceId }
        if (seqIndex == -1) return
        
        val sequence = registeredSequences[seqIndex]
        if (stepIndex !in sequence.steps.indices) return
        
        val newSteps = sequence.steps.toMutableList()
        newSteps[stepIndex] = updatedStep
        
        registeredSequences[seqIndex] = sequence.copy(steps = newSteps)
        saveSequences()
        reanalyzeAllFlows()
    }

    private fun saveSequences() {
        storage.saveSequences(registeredSequences.toList())
    }

    private fun reanalyzeAllFlows() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            statusMessage = "Analyzing flows..."
            val traces = analyzeUseCase.analyzeIncremental(
                newLogs = logs.toList(),
                existingFlows = emptyList(),
                sequences = registeredSequences.toList(),
                onProgress = { progress ->
                    analysisProgress = progress
                }
            )
            flowTraces = traces
            refreshFlowIndex()
            analysisProgress = 1.0f
            statusMessage = "Analysis complete (${flowTraces.size} flows found)"
        }
    }

    private fun refreshFlowIndex() {
        logToFlowIndex.clear()
        flowTraces.forEach { trace ->
            trace.logs.forEach { matched ->
                logToFlowIndex[matched.log] = trace
            }
        }
    }

    fun loadLogFile(path: String, format: LogFormat = LogFormat.ANDROID_LOGCAT) {
        addLogFile(path, format)
    }

    fun addLogFile(path: String, format: LogFormat = LogFormat.ANDROID_LOGCAT) {
        val fileName = File(path).name
        if (loadedFiles.contains(fileName)) return

        viewModelScope.launch {
            parseUseCase(path, format).collect { result ->
                when (result) {
                    is LogParseResult.Loading -> {
                        statusMessage = "Loading $fileName..."
                        if (!sourceMap.containsKey(path)) {
                            sourceMap[path] = mutableListOf()
                        }
                    }
                    is LogParseResult.Success -> {
                        sourceMap[path]?.addAll(result.logs)
                        
                        if (result.isComplete) {
                            if (!loadedFiles.contains(fileName)) {
                                loadedFiles.add(fileName)
                            }
                            remergeLogs()
                        }
                    }
                    is LogParseResult.Error -> {
                        statusMessage = "Error loading $fileName: ${result.message}"
                    }
                }
            }
        }
    }

    fun removeLogFile(fileName: String) {
        val pathEntry = sourceMap.entries.find { File(it.key).name == fileName }
        if (pathEntry != null) {
            sourceMap.remove(pathEntry.key)
            loadedFiles.remove(fileName)
            remergeLogs()
        }
    }

    private fun remergeLogs() {
        logs.clear()
        val merged = mergeUseCase(sourceMap.mapValues { it.value.toList() })
        logs.addAll(merged)
        
        reanalyzeAllFlows()
        refreshFilters()
        
        statusMessage = "Loaded ${loadedFiles.size} files. Total ${logs.size} lines. Flows: ${flowTraces.size}"
    }
    fun exportConfig(path: String) {
        val config = LogPulseConfig(
            sequences = registeredSequences.toList(),
            filters = filters.toList(),
            customFormats = customFormats.toList()
        )
        storage.exportConfig(path, config)
        statusMessage = "Configuration exported to ${File(path).name}"
    }

    fun loadConfigFromFile(path: String): LogPulseConfig? {
        return storage.importConfig(path)
    }

    fun importConfig(config: LogPulseConfig, includeFlows: Boolean, includeFilters: Boolean) {
        if (includeFlows) {
            config.sequences.forEach { imported ->
                if (registeredSequences.none { it.id == imported.id }) {
                    registeredSequences.add(imported)
                }
            }
            saveSequences()
            reanalyzeAllFlows()
        }
        
        if (includeFilters) {
            config.filters.forEach { imported ->
                if (filters.none { it.id == imported.id }) {
                    filters.add(imported)
                }
            }
            saveFilters()
            refreshFilters()
        }
        
        if (config.customFormats.isNotEmpty()) {
            config.customFormats.forEach { imported ->
                if (customFormats.none { it.id == imported.id || it.name == imported.name }) {
                    customFormats.add(imported)
                }
            }
            storage.saveFormats(customFormats.toList())
        }
        statusMessage = "Imported configuration components."
    }
}
