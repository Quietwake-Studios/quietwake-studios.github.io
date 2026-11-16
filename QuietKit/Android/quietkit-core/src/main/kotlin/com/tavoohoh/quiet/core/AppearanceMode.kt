package com.tavoohoh.quiet.core

/**
 * Settings › Appearance (family rule, 2026-11-08): "Match system" or a fixed light or dark look
 * for every theme. A setting, not a reward: Reset Progress keeps it.
 */
enum class AppearanceMode(val rawValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    /** Dark or not, given what the system says. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.rawValue == raw } ?: SYSTEM
    }
}
