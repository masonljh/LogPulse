package com.antdev.logpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LogPulseConfig(
    val version: Int = 1,
    val sequences: List<SequencePattern> = emptyList(),
    val filters: List<LogFilter> = emptyList(),
    val customFormats: List<LogFormat> = emptyList()
)
