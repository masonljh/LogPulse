package com.antdev.logpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    INCLUDE, EXCLUDE
}

@Serializable
enum class FilterField {
    PID, TID, TAG, MESSAGE
}

@Serializable
data class LogFilter(
    val id: String = "filter_${(0..999999).random()}",
    val text: String,
    val type: FilterType,
    val field: FilterField
)
