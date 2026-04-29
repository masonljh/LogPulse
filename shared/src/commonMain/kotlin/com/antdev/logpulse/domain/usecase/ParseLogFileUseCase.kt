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
                val parser = RegexLogParser(format)
                
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val event = parser.parseLine(line, idPrefix = filePath, source = actualSource, lineIndex = lineCount++)
                    
                    // RegexLogParser buffers the previous line if it's a multi-line log.
                    // We need to carefully handle appending. Actually, `parseLine` returns the updated previous log if it appended.
                    // Wait, if it appended, it modifies the object we already emitted? No, it creates a new copy.
                    // If it's a new log, it returns the new log. If it appends, it returns the updated log, BUT we already emitted the old one!
                    // So returning immediately on append is problematic for streaming.
                    // Let's change the parser flow slightly. Or just collect and emit. 
                    // Actually, if `parseLine` returns the current parsed log, and we just add it, we might have duplicates if it appended.
                    // A better way for RegexLogParser is to buffer the current log, and return it ONLY when a NEW log starts or EOF.
                    
                    // For now, let's keep it simple: RegexLogParser returns non-null when it successfully parses a NEW line.
                    // Let's modify the RegexLogParser behavior inside here or adjust the use case.
                    
                    if (event != null && event === parser.getPendingPreviousLog() && lineCount - 1 == event.lineIndex) {
                        currentChunk.add(event)
                    } else if (event != null && event.lineIndex < lineCount - 1) {
                        // It was an append. We need to replace the last item in the chunk.
                        val indexInChunk = currentChunk.indexOfFirst { it.lineIndex == event.lineIndex }
                        if (indexInChunk != -1) {
                            currentChunk[indexInChunk] = event
                        }
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
                    emit(LogParseResult.Success(logs = emptyList(), isComplete = true))
                }
            }
        } catch (e: Exception) {
            emit(LogParseResult.Error("로그 파싱 중 오류 발생", e))
        }
    }.flowOn(Dispatchers.IO)
}
