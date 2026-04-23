package com.antdev.logpulse.data.storage

import com.antdev.logpulse.domain.model.LogFilter
import com.antdev.logpulse.domain.model.LogPulseConfig
import com.antdev.logpulse.domain.model.SequencePattern
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

class SequenceStorage(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val sequencesFileName: String = "sequences.json",
    private val filtersFileName: String = "filters.json"
) {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveSequences(sequences: List<SequencePattern>) {
        saveToFile(sequencesFileName, sequences)
    }

    fun loadSequences(): List<SequencePattern> {
        return loadFromFile<List<SequencePattern>>(sequencesFileName) ?: emptyList()
    }

    fun saveFilters(filters: List<LogFilter>) {
        saveToFile(filtersFileName, filters)
    }

    fun loadFilters(): List<LogFilter> {
        return loadFromFile<List<LogFilter>>(filtersFileName) ?: emptyList()
    }

    fun exportConfig(path: String, config: LogPulseConfig) {
        saveToFile(path, config)
    }

    fun importConfig(path: String): LogPulseConfig? {
        return loadFromFile<LogPulseConfig>(path)
    }

    private inline fun <reified T> saveToFile(fileName: String, data: T) {
        try {
            val content = json.encodeToString(data)
            fileSystem.write(fileName.toPath()) {
                writeUtf8(content)
            }
        } catch (e: Exception) {
            println("Error saving to $fileName: ${e.message}")
        }
    }

    private inline fun <reified T> loadFromFile(fileName: String): T? {
        val path = fileName.toPath()
        if (!fileSystem.exists(path)) return null

        return try {
            val content = fileSystem.read(path) {
                readUtf8()
            }
            json.decodeFromString<T>(content)
        } catch (e: Exception) {
            println("Error loading from $fileName: ${e.message}")
            null
        }
    }
}
