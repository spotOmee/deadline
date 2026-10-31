package com.deadline

import android.app.Application
import com.deadline.ui.theme.ThemeManager

class DeadlineApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Persistent Preference Architecture:
        // Read saved theme preference immediately upon application cold startup
        val savedTheme = ThemeManager.readSavedThemePreference(this) ?: "system"

        // 2. Global State Synchronization:
        // Apply corresponding night mode globally using AppCompatDelegate.setDefaultNightMode()
        ThemeManager.applyThemeMode(savedTheme)
    }
}
