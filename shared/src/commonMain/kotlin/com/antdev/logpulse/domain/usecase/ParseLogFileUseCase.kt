package com.antdev.logpulse.domain.usecase

import com.antdev.logpulse.data.parser.AndroidLogParser
import com.antdev.logpulse.domain.model.LogEvent
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
    private val parser: AndroidLogParser = AndroidLogParser(),
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val chunkSize: Int = 500
) {
    operator fun invoke(filePath: String, sourceName: String? = null): Flow<LogParseResult> = flow {
        emit(LogParseResult.Loading)
        
        val actualSource = sourceName ?: filePath.toPath().name

        if (!PathValidator.isValid(filePath)) {
            emit(LogParseResult.Error("잘못된 파일 경로입니다: $filePath"))
            return@flow
        }

        try {
            val path = filePath.toPath()
            if (!fileSystem.exists(path)) {
                // If file doesn't exist, it's not a hard error, just return empty success
                emit(LogParseResult.Success(logs = emptyList(), isComplete = true))
                return@flow
            }

            fileSystem.source(path).buffer().use { source ->
                val currentChunk = mutableListOf<LogEvent>()
                var lineCount = 0
                
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val event = parser.parseLine(line, idPrefix = filePath, source = actualSource, lineIndex = lineCount++)
                    if (event != null) {
                        currentChunk.add(event)
                    }

                    if (currentChunk.size >= chunkSize) {
                        emit(LogParseResult.Success(logs = currentChunk.toList(), isComplete = false))
                        currentChunk.clear()
                    }
                }

                // Emit remaining logs
                if (currentChunk.isNotEmpty()) {
                    emit(LogParseResult.Success(logs = currentChunk.toList(), isComplete = true))
                } else {
                    // If file is empty or all lines are already emitted
                    emit(LogParseResult.Success(logs = emptyList(), isComplete = true))
                }
            }
        } catch (e: Exception) {
            emit(LogParseResult.Error("로그 파싱 중 오류 발생", e))
        }
    }.flowOn(Dispatchers.IO)
}
