package com.antdev.logpulse.domain.model

data class LogEvent(
    val id: String,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val pid: String,
    val tid: String,
    val message: String,
    val rawData: String,
    val source: String = ""
)

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT, UNKNOWN;

    fun toLabel(): String = when (this) {
        VERBOSE -> "V"
        DEBUG -> "D"
        INFO -> "I"
        WARN -> "W"
        ERROR -> "E"
        ASSERT -> "A"
        UNKNOWN -> "?"
    }

    companion object {
        fun fromChar(c: String): LogLevel {
            return when (c.uppercase()) {
                "V" -> VERBOSE
                "D" -> DEBUG
                "I" -> INFO
                "W" -> WARN
                "E" -> ERROR
                "A" -> ASSERT
                else -> UNKNOWN
            }
        }
    }
}
