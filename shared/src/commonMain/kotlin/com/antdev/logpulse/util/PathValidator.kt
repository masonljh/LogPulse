package com.antdev.logpulse.util

object PathValidator {
    /**
     * Basic path validation to prevent Path Traversal.
     * In a production desktop app, you might also want to check against white-listed directories.
     */
    fun isValid(path: String): Boolean {
        if (path.isEmpty()) return false
        
        // Block obvious traversal sequences
        if (path.contains("..") || path.contains("./") || path.contains(".\\")) {
            return false
        }
        
        // Add more platform-specific or app-specific checks if needed
        return true
    }
}
