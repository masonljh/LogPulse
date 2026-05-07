package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.data.parser.RegexLogParser
import com.antdev.logpulse.domain.model.LogEvent
import com.antdev.logpulse.domain.model.LogFormat
import com.antdev.logpulse.domain.model.LogParseResult
import com.antdev.logpulse.util.PathValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

class ParseLogFileUseCase(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val chunkSize: Int = 500
) {
    operator fun invoke(filePath: String, format: LogFormat, sourceName: String? = null): Flow<LogParseResult> = flow {
        emit(LogParseResult.Loading(0f))
        
        val actualSource = sourceName ?: filePath.toPath().name

        if (!PathValidator.isValid(filePath)) {
            emit(LogParseResult.Error("잘못된 파일 경로입니다: $filePath"))
            return@flow
        }

        try {
            val path = filePath.toPath()
            if (!fileSystem.exists(path)) {
                emit(LogParseResult.Success(logs = emptyList(), isComplete = true))
                return@flow
            }
            
            val metadata = fileSystem.metadata(path)
            val totalSize = metadata.size ?: 1L
            var bytesRead = 0L

            fileSystem.source(path).buffer().use { source ->
                val currentChunk = mutableListOf<LogEvent>()
                var lineCount = 0
                val parser = RegexLogParser(format)
                
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    bytesRead += line.length.toLong() + 1
                    
                    val completedEvent = parser.parseLine(line, idPrefix = filePath, source = actualSource, lineIndex = lineCount++)
                    
                    if (completedEvent != null) {
                        currentChunk.add(completedEvent)
                    }

                    if (currentChunk.size >= chunkSize) {
                        emit(LogParseResult.Success(logs = currentChunk.toList(), isComplete = false))
                        currentChunk.clear()
                        emit(LogParseResult.Loading(bytesRead.toFloat() / totalSize))
                    }
                }

                val lastEvent = parser.flush()
                if (lastEvent != null) {
                    currentChunk.add(lastEvent)
                }
                
                emit(LogParseResult.Success(logs = currentChunk.toList(), isComplete = true))
            }
        } catch (e: Exception) {
            emit(LogParseResult.Error("로그 파싱 중 오류 발생", e))
        }
    }.flowOn(Dispatchers.IO)
}
