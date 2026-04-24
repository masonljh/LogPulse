package com.antdev.logpulse.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a user-defined pattern to match in log messages.
 * Optimized with pre-compiled Regex.
 */
@Serializable
data class LogPattern(
    val name: String,
    val patternString: String,
    val idPlaceholder: String = "{id}"
) {
    // Pre-compiled regex for performance. 
    @Transient
    val compiledRegex: Regex by lazy {
        // Advanced Regex Generation:
        // 1. Split the pattern by the ID placeholder
        val parts = if (patternString.contains(idPlaceholder)) {
            patternString.split(idPlaceholder)
        } else {
            listOf(patternString)
        }

        // 2. For each literal part, handle spaces flexibly.
        val escapedParts = parts.map { part ->
            val words = part.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                if (part.contains(" ")) "\\s*" else "" 
            } else {
                val escaped = words.joinToString("\\s+") { Regex.escape(it) }
                val leading = if (part.startsWith(" ")) "\\s+" else ""
                val trailing = if (part.endsWith(" ")) "\\s+" else ""
                leading + escaped + trailing
            }
        }

        // 3. Reconstruct the full regex with the named capture group
        // We use \S+ for ID to ensure it matches at least one non-whitespace character 
        // and doesn't capture emptiness or surrounding spaces.
        val regexString = if (parts.size > 1) {
            escapedParts.joinToString("(?<id>\\S+)")
        } else {
            escapedParts.first()
        }

        // 4. Use IGNORE_CASE to handle variations
        Regex(regexString, RegexOption.IGNORE_CASE)
    }
}

/**
 * Represents a specific step in a sequence, allowing for counts and repetitions.
 */
@Serializable
data class FlowStep(
    val pattern: LogPattern,
    val minCount: Int = 1,
    val maxCount: Int = 1 // -1 for infinite
) {
    val isOptional: Boolean get() = minCount == 0
    val isRepeatable: Boolean get() = maxCount == -1 || maxCount > 1
}

/**
 * Result of matching a log entry against a [LogPattern].
 */
data class MatchedLog(
    val log: LogEvent,
    val pattern: LogPattern,
    val extractedId: String?
)

@Serializable
enum class AnalysisStrategy {
    SEQUENTIAL // Group purely by appearance order in the log file
}

/**
 * A sequence of steps that define a logical flow.
 */
@Serializable
data class SequencePattern(
    val id: String = "seq_${(0..9999).random()}", 
    val name: String,
    val steps: List<FlowStep>,
    val strategy: AnalysisStrategy = AnalysisStrategy.SEQUENTIAL,
    val isEnabled: Boolean = false
)

/**
 * The status of a specific flow (grouped by ID).
 */
sealed interface FlowStatus {
    object Complete : FlowStatus
    object InProgress : FlowStatus
    data class Failed(val reason: String) : FlowStatus
}

/**
 * Detailed trace of a specific flow.
 */
data class FlowTrace(
    val id: String,
    val sequence: SequencePattern,
    val logs: List<MatchedLog>,
    val status: FlowStatus
)
