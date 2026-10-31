package com.deadline.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Global Theme Manager for Deadline.
 *
 * Responsibilities:
 * 1. Persistent Preference Architecture:
 *    - Reliably stores and retrieves chosen theme identifiers (e.g. THEME_MODE_KEY) across app relaunches.
 *    - Supports both string identifiers ("light", "dark", "system") and integer identifiers (MODE_NIGHT_NO, MODE_NIGHT_YES, MODE_NIGHT_FOLLOW_SYSTEM).
 * 2. State Synchronization:
 *    - Reads saved preference upon app startup (Application & MainActivity initialization).
 *    - Applies the corresponding theme globally using AppCompatDelegate.setDefaultNightMode().
 * 3. State Isolation:
 *    - Retains exact chosen theme ("light" / "dark") independently of the system OS dark mode until explicitly changed.
 */
object ThemeManager {
    const val THEME_MODE_KEY = "theme_mode"
    const val PREF_KEY_THEME_MODE = "theme_mode"
    const val PREF_KEY_THEME_MODE_CAMEL = "themeMode"
    const val PREF_KEY_THEME_INT = "theme_mode_int"

    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    const val THEME_LIGHT_LABEL = "Warm Cream Light"
    const val THEME_DARK_LABEL = "OLED Dark Mode"
    const val THEME_SYSTEM_LABEL = "System Auto"

    val THEME_OPTIONS = listOf(
        THEME_LIGHT to THEME_LIGHT_LABEL,
        THEME_DARK to THEME_DARK_LABEL,
        THEME_SYSTEM to THEME_SYSTEM_LABEL
    )

    fun normalizeThemeMode(mode: String?): String {
        if (mode.isNullOrBlank()) return THEME_SYSTEM
        return when (mode.trim().lowercase()) {
            "light", "warm", "warm cream", "warm cream light", "1" -> THEME_LIGHT
            "dark", "oled", "oled dark", "black", "oled dark mode", "2" -> THEME_DARK
            "system", "auto", "system auto", "-1", "0" -> THEME_SYSTEM
            else -> THEME_SYSTEM
        }
    }

    fun intToThemeMode(nightMode: Int): String {
        return when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_NO, 1 -> THEME_LIGHT
            AppCompatDelegate.MODE_NIGHT_YES, 2 -> THEME_DARK
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, -1, 0 -> THEME_SYSTEM
            else -> THEME_SYSTEM
        }
    }

    fun toNightMode(mode: String?): Int {
        return when (normalizeThemeMode(mode)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun applyThemeMode(mode: String?) {
        val nightMode = toNightMode(mode)
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    fun getThemeLabel(mode: String?): String {
        return when (normalizeThemeMode(mode)) {
            THEME_LIGHT -> THEME_LIGHT_LABEL
            THEME_DARK -> THEME_DARK_LABEL
            else -> THEME_SYSTEM_LABEL
        }
    }

    fun readSavedThemePreference(ctx: Context?): String? {
        if (ctx == null) return null
        val prefFiles = listOf(
            try { "${ctx.packageName}_preferences" } catch (_: Throwable) { "com.deadline_preferences" },
            "deadline_preferences",
            "deadline_prefs",
            "deadline_pomodoro_prefs",
            "user_settings",
            "settings",
            "default_preferences"
        )
        val candidateKeys = listOf(
            THEME_MODE_KEY,
            PREF_KEY_THEME_MODE,
            PREF_KEY_THEME_MODE_CAMEL,
            "THEME_MODE",
            "selected_theme",
            "app_theme",
            PREF_KEY_THEME_INT
        )
        for (fileName in prefFiles) {
            val sp = try {
                ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            } catch (_: Throwable) { null } ?: continue

            for (key in candidateKeys) {
                try {
                    if (!sp.contains(key)) continue
                    val rawStr = sp.getString(key, null)
                    if (!rawStr.isNullOrBlank()) {
                        return normalizeThemeMode(rawStr)
                    }
                } catch (_: ClassCastException) {
                    try {
                        val intVal = sp.getInt(key, Int.MIN_VALUE)
                        if (intVal != Int.MIN_VALUE) {
                            return intToThemeMode(intVal)
                        }
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    fun writeSavedThemePreference(mode: String, ctx: Context?) {
        if (ctx == null) return
        val normalized = normalizeThemeMode(mode)
        val nightMode = toNightMode(normalized)
        val prefFiles = listOf(
            try { "${ctx.packageName}_preferences" } catch (_: Throwable) { "com.deadline_preferences" },
            "deadline_preferences",
            "deadline_prefs",
            "deadline_pomodoro_prefs",
            "user_settings"
        )
        val stringKeys = listOf(
            THEME_MODE_KEY,
            PREF_KEY_THEME_MODE,
            PREF_KEY_THEME_MODE_CAMEL,
            "THEME_MODE"
        )
        val intKeys = listOf(
            PREF_KEY_THEME_INT,
            "night_mode"
        )

        for (fileName in prefFiles) {
            try {
                val sp = ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                for (k in stringKeys) {
                    editor.putString(k, normalized)
                }
                for (k in intKeys) {
                    editor.putInt(k, nightMode)
                }
                editor.apply()
            } catch (_: Throwable) {}
        }
    }
}
