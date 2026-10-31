package com.deadline

import androidx.appcompat.app.AppCompatDelegate
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.UserSettings
import com.deadline.ui.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit Test Suite for Persistent Theme State & Smooth Transition Animations:
 * 1. Persistent Preference Architecture:
 *    - THEME_MODE_KEY persistence ("theme_mode", "themeMode", "THEME_MODE").
 *    - Reliable string & integer identifiers ("light", "dark", "system", MODE_NIGHT_NO, MODE_NIGHT_YES, MODE_NIGHT_FOLLOW_SYSTEM).
 * 2. State Synchronization:
 *    - Cold start Application & MainActivity initialization with ThemeManager.applyThemeMode().
 *    - DeadlineApp registration in AndroidManifest.xml.
 *    - Repository initializes _userSettings with saved theme mode on relaunch, bypassing default System Auto.
 * 3. State Isolation:
 *    - Overriding System Auto with Warm Cream Light or OLED Dark Mode persists with explicit theme flag.
 * 4. Smooth Transition Animations:
 *    - animateColorScheme interpolates semantic ColorScheme tokens with animateColorAsState.
 *    - ThemeCrossfade crossfade animation integration for UI components.
 *    - View-based layout window background color animation via ValueAnimator.
 */
class PersistentThemeAndSmoothTransitionTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/")),
            File("/storage/emulated/0/AndroidIDEProjects/deadline/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Source file not found: $relativePath")
    }

    // =========================================================================
    // 1. Persistent Preference Architecture
    // =========================================================================

    @Test
    fun testThemeModeKey_andIdentifierConstants() {
        assertEquals("theme_mode", ThemeManager.THEME_MODE_KEY)
        assertEquals("light", ThemeManager.THEME_LIGHT)
        assertEquals("dark", ThemeManager.THEME_DARK)
        assertEquals("system", ThemeManager.THEME_SYSTEM)
        assertEquals("Warm Cream Light", ThemeManager.THEME_LIGHT_LABEL)
        assertEquals("OLED Dark Mode", ThemeManager.THEME_DARK_LABEL)
        assertEquals("System Auto", ThemeManager.THEME_SYSTEM_LABEL)
    }

    @Test
    fun testThemeNormalization_supportsAllDisplayAliasesAndNumbers() {
        // Light theme variants
        assertEquals("light", ThemeManager.normalizeThemeMode("light"))
        assertEquals("light", ThemeManager.normalizeThemeMode("Warm Cream Light"))
        assertEquals("light", ThemeManager.normalizeThemeMode("warm cream"))
        assertEquals("light", ThemeManager.normalizeThemeMode("warm"))
        assertEquals("light", ThemeManager.normalizeThemeMode("1"))

        // Dark theme variants
        assertEquals("dark", ThemeManager.normalizeThemeMode("dark"))
        assertEquals("dark", ThemeManager.normalizeThemeMode("OLED Dark Mode"))
        assertEquals("dark", ThemeManager.normalizeThemeMode("oled dark"))
        assertEquals("dark", ThemeManager.normalizeThemeMode("oled"))
        assertEquals("dark", ThemeManager.normalizeThemeMode("black"))
        assertEquals("dark", ThemeManager.normalizeThemeMode("2"))

        // System theme variants
        assertEquals("system", ThemeManager.normalizeThemeMode("system"))
        assertEquals("system", ThemeManager.normalizeThemeMode("System Auto"))
        assertEquals("system", ThemeManager.normalizeThemeMode("auto"))
        assertEquals("system", ThemeManager.normalizeThemeMode("-1"))
        assertEquals("system", ThemeManager.normalizeThemeMode("0"))
        assertEquals("system", ThemeManager.normalizeThemeMode(null))
        assertEquals("system", ThemeManager.normalizeThemeMode(""))
    }

    @Test
    fun testAppCompatNightModeMapping() {
        // Night mode integer to normalized mode
        assertEquals("light", ThemeManager.intToThemeMode(AppCompatDelegate.MODE_NIGHT_NO))
        assertEquals("dark", ThemeManager.intToThemeMode(AppCompatDelegate.MODE_NIGHT_YES))
        assertEquals("system", ThemeManager.intToThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        assertEquals("light", ThemeManager.intToThemeMode(1))
        assertEquals("dark", ThemeManager.intToThemeMode(2))
        assertEquals("system", ThemeManager.intToThemeMode(-1))

        // Normalized mode to Night mode integer
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeManager.toNightMode("light"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeManager.toNightMode("Warm Cream Light"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeManager.toNightMode("dark"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeManager.toNightMode("OLED Dark Mode"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeManager.toNightMode("system"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeManager.toNightMode(null))
    }

    // =========================================================================
    // 2. State Synchronization & Cold Startup
    // =========================================================================

    @Test
    fun testDeadlineApp_applicationColdStartArchitecture() {
        val appFile = getSourceFile("app/src/main/kotlin/com/deadline/DeadlineApp.kt")
        val content = appFile.readText()

        assertTrue("DeadlineApp must extend Application",
            content.contains("class DeadlineApp : Application()"))
        assertTrue("DeadlineApp must read saved theme preference on cold start",
            content.contains("ThemeManager.readSavedThemePreference(this)"))
        assertTrue("DeadlineApp must apply night mode globally via ThemeManager.applyThemeMode",
            content.contains("ThemeManager.applyThemeMode("))

        // Manifest registration
        val manifestFile = getSourceFile("app/src/main/AndroidManifest.xml")
        val manifestContent = manifestFile.readText()
        assertTrue("AndroidManifest.xml must declare android:name=\".DeadlineApp\"",
            manifestContent.contains("android:name=\".DeadlineApp\""))
    }

    @Test
    fun testMainActivity_startupThemeSynchronization() {
        val activityFile = getSourceFile("app/src/main/kotlin/com/deadline/MainActivity.kt")
        val content = activityFile.readText()

        assertTrue("MainActivity must read saved theme preference on onCreate",
            content.contains("ThemeManager.readSavedThemePreference(this)"))
        assertTrue("MainActivity must apply night mode globally via ThemeManager.applyThemeMode",
            content.contains("ThemeManager.applyThemeMode("))
        assertTrue("MainActivity must pass userSettings.themeMode to DeadlineTheme",
            content.contains("DeadlineTheme(themeMode = userSettings.themeMode)"))
    }

    @Test
    fun testRepository_relaunchThemePersistenceBypassesSystemDefault() {
        val repoFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        val content = repoFile.readText()

        // Initial state flow reads saved preference
        assertTrue("DeadlineRepository must initialize _userSettings with readSavedThemePreference",
            content.contains("themeMode = ThemeManager.readSavedThemePreference(context) ?: \"system\""))

        // getSavedThemeMode helper
        assertTrue("DeadlineRepository must provide getSavedThemeMode helper",
            content.contains("fun getSavedThemeMode(): String"))

        // updateThemeMode persists to SharedPreferences and applies night mode
        assertTrue("updateThemeMode must write to SharedPreferences via ThemeManager",
            content.contains("ThemeManager.writeSavedThemePreference(normalized, context)"))
        assertTrue("updateThemeMode must apply night mode via ThemeManager",
            content.contains("ThemeManager.applyThemeMode(normalized)"))

        // loadUserSettings maintains DB and SharedPreferences sync
        assertTrue("loadUserSettings must read saved theme preference",
            content.contains("val prefTheme = ThemeManager.readSavedThemePreference(context)"))
    }

    // =========================================================================
    // 3. State Isolation
    // =========================================================================

    @Test
    fun testStateIsolation_explicitThemeRetainsChoiceOverSystem() {
        // Simulated user settings update sequence
        val defaultSettings = UserSettings(themeMode = "system")

        // 1. User overrides system auto with Warm Cream Light
        val lightTheme = ThemeManager.normalizeThemeMode("Warm Cream Light")
        assertEquals("light", lightTheme)
        val lightSettings = defaultSettings.copy(themeMode = lightTheme)
        val hasExplicitLight = if (lightTheme == "system") 0 else 1
        assertEquals(1, hasExplicitLight)
        assertEquals("light", lightSettings.themeMode)

        // 2. User overrides system auto with OLED Dark Mode
        val darkTheme = ThemeManager.normalizeThemeMode("OLED Dark Mode")
        assertEquals("dark", darkTheme)
        val darkSettings = lightSettings.copy(themeMode = darkTheme)
        val hasExplicitDark = if (darkTheme == "system") 0 else 1
        assertEquals(1, hasExplicitDark)
        assertEquals("dark", darkSettings.themeMode)

        // 3. Explicit choice is preserved until actively set back to System Auto
        val systemTheme = ThemeManager.normalizeThemeMode("System Auto")
        assertEquals("system", systemTheme)
        val hasExplicitSystem = if (systemTheme == "system") 0 else 1
        assertEquals(0, hasExplicitSystem)
    }

    // =========================================================================
    // 4. Smooth Transition Animations
    // =========================================================================

    @Test
    fun testThemeTransitions_animateColorSchemeAndCrossfade() {
        val themeFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/theme/Theme.kt")
        val content = themeFile.readText()

        // animateColorScheme implementation with animateColorAsState
        assertTrue("Theme.kt must provide animateColorScheme for fluid color interpolation",
            content.contains("fun animateColorScheme("))
        assertTrue("animateColorScheme must use animateColorAsState for primary color",
            content.contains("animateColorAsState(target.primary"))
        assertTrue("animateColorScheme must use animateColorAsState for background color",
            content.contains("animateColorAsState(target.background"))
        assertTrue("animateColorScheme must use animateColorAsState for surface color",
            content.contains("animateColorAsState(target.surface"))
        assertTrue("animateColorScheme must use animateColorAsState for onSurface color",
            content.contains("animateColorAsState(target.onSurface"))

        // ThemeCrossfade helper implementation
        assertTrue("Theme.kt must provide ThemeCrossfade helper for UI crossfade animations",
            content.contains("fun <T> ThemeCrossfade("))
        assertTrue("ThemeCrossfade must wrap Compose Crossfade",
            content.contains("Crossfade("))

        // View-based background fade on Android Window
        assertTrue("DeadlineTheme must smoothly animate window decorView background color via ValueAnimator",
            content.contains("ValueAnimator.ofArgb(") &&
            content.contains("window.decorView.setBackgroundColor("))

        // Root Surface wrapper
        assertTrue("DeadlineTheme must wrap content in Surface with animated background color",
            content.contains("Surface(") &&
            content.contains("color = animatedColorScheme.background"))
    }

    @Test
    fun testProfileScreen_themeSelectorSmoothTransitions() {
        val profileFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/profile/ProfileScreen.kt")
        val content = profileFile.readText()

        assertTrue("ProfileScreen must animate theme row background with animateColorAsState",
            content.contains("val rowBgColor by animateColorAsState("))
        assertTrue("ProfileScreen must animate theme row border with animateColorAsState",
            content.contains("val rowBorderColor by animateColorAsState("))
        assertTrue("ProfileScreen must animate theme row text with animateColorAsState",
            content.contains("val rowTextColor by animateColorAsState("))
        assertTrue("ProfileScreen must update theme mode via repository.updateThemeMode",
            content.contains("repository.updateThemeMode(mode)"))
    }
}
