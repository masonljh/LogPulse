package com.antdev.logpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LogFormat(
    val id: String,
    val name: String,
    val pattern: String,
    val timestampGroup: Int?,
    val levelGroup: Int?,
    val tagGroup: Int?,
    val pidGroup: Int?,
    val tidGroup: Int?,
    val messageGroup: Int?
) {
    companion object {
        val ANDROID_LOGCAT = LogFormat(
            id = "android_logcat",
            name = "Android Logcat",
            pattern = """^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEA])\s+([^:]+):\s+(.*)$""",
            timestampGroup = 1,
            pidGroup = 2,
            tidGroup = 3,
            levelGroup = 4,
            tagGroup = 5,
            messageGroup = 6
        )

        val NGINX_ERROR = LogFormat(
            id = "nginx_error",
            name = "Nginx Error Log",
            pattern = """^(\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})\s+\[(\w+)\]\s+(\d+)#(\d+):\s+(.*)$""",
            timestampGroup = 1,
            levelGroup = 2,
            pidGroup = 3,
            tidGroup = 4,
            tagGroup = null,
            messageGroup = 5
        )

        val STANDARD_SYSLOG = LogFormat(
            id = "standard_syslog",
            name = "Standard Syslog",
            pattern = """^([A-Z][a-z]{2}\s+\d+\s\d{2}:\d{2}:\d{2})\s+([^\s]+)\s+([^\[:]+)(?:\[(\d+)\])?:\s+(.*)$""",
            // format: Oct 11 22:14:15 hostname appname[pid]: message
            timestampGroup = 1,
            levelGroup = null, // Syslog often doesn't have a clear level in the string without parsing priority
            tagGroup = 3,
            pidGroup = 4,
            tidGroup = null,
            messageGroup = 5
        )
        
        val PRESETS = listOf(ANDROID_LOGCAT, NGINX_ERROR, STANDARD_SYSLOG)
    }
}
