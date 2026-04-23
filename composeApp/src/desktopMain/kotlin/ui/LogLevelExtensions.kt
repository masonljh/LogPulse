package ui

import androidx.compose.ui.graphics.Color
import com.antdev.logpulse.domain.model.LogLevel

fun LogLevel.toColor(): Color {
    return when (this) {
        LogLevel.VERBOSE -> Color(0xFFBDBDBD) // Lighter Gray
        LogLevel.DEBUG -> Color(0xFF64B5F6)   // Lighter Blue
        LogLevel.INFO -> Color(0xFF81C784)    // Lighter Green
        LogLevel.WARN -> Color(0xFFFFD54F)    // Lighter Amber
        LogLevel.ERROR -> Color(0xFFE57373)   // Lighter Red
        LogLevel.ASSERT -> Color(0xFFCE93D8)  // Lighter Purple
        LogLevel.UNKNOWN -> Color(0xFFB0BEC5)
    }
}

