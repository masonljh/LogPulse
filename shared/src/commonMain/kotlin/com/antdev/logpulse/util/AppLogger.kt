package com.antdev.logpulse.util

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

object AppLogger {
    fun init(isDebug: Boolean = true) {
        if (isDebug) {
            Napier.base(DebugAntilog())
        }
        // Always log to file for error tracking
        Napier.base(FileLogAntilog("app_error.log"))
    }

    fun d(message: String, tag: String? = null) = Napier.d(message, tag = tag)
    fun i(message: String, tag: String? = null) = Napier.i(message, tag = tag)
    fun w(message: String, tag: String? = null) = Napier.w(message, tag = tag)
    fun e(message: String, throwable: Throwable? = null, tag: String? = null) = Napier.e(message, throwable, tag)
}

class FileLogAntilog(private val fileName: String) : Antilog() {
    private val fileSystem = FileSystem.SYSTEM
    private val path = fileName.toPath()

    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        // Only log Warning, Error, and Assert to file to keep it manageable
        if (priority < LogLevel.WARNING) return
        
        val logEntry = buildString {
            append("[${priority.name}]")
            if (tag != null) append(" [$tag]")
            append(" ")
            if (message != null) append(message)
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
        }

        try {
            fileSystem.appendingSink(path).buffer().use { sink ->
                sink.writeUtf8("$logEntry\n")
            }
        } catch (e: Exception) {
            // Fallback to console if file logging fails
            println("Failed to write to log file: ${e.message}")
        }
    }
}
