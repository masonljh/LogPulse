package com.antdev.logpulse.domain.model

sealed interface LogParseResult {
    data class Success(val logs: List<LogEvent>, val isComplete: Boolean) : LogParseResult
    data class Error(val message: String, val throwable: Throwable? = null) : LogParseResult
    data class Loading(val progress: Float) : LogParseResult
}
