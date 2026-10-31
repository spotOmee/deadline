package com.deadline

import com.deadline.domain.BackupManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun testValidateBackup_validPayload() {
        val root = JSONObject().apply {
            put("app", "deadline")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            val userArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "default_user")
                    put("name", "Craftsman")
                    put("themeMode", "dark")
                    put("defaultStartupScreen", "HABITS")
                    put("timeFormat24Hour", 1)
                })
            }
            put("users", userArray)
            put("projects", JSONArray())
            put("habits", JSONArray())
        }

        val result = BackupManager.validateBackup(root.toString())
        assertTrue("Expected valid result, got: $result", result is BackupManager.ValidationResult.Success)

        val success = result as BackupManager.ValidationResult.Success
        assertEquals(1, success.version)
        assertEquals(1, success.tableCounts["users"])
        assertEquals(0, success.tableCounts["projects"])
        assertEquals(0, success.tableCounts["habits"])
    }

    @Test
    fun testValidateBackup_rejectsEmptyOrBlank() {
        val resultNull = BackupManager.validateBackup(null)
        assertTrue(resultNull is BackupManager.ValidationResult.Failure)

        val resultBlank = BackupManager.validateBackup("   ")
        assertTrue(resultBlank is BackupManager.ValidationResult.Failure)
    }

    @Test
    fun testValidateBackup_rejectsMalformedJson() {
        val result = BackupManager.validateBackup("{ this is not valid json }")
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("Malformed JSON", ignoreCase = true))
    }

    @Test
    fun testValidateBackup_rejectsWrongAppSignature() {
        val root = JSONObject().apply {
            put("app", "other_app")
            put("version", 1)
            put("users", JSONArray())
        }
        val result = BackupManager.validateBackup(root.toString())
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("signature", ignoreCase = true))
    }

    @Test
    fun testValidateBackup_rejectsInvalidVersion() {
        val root = JSONObject().apply {
            put("app", "deadline")
            put("version", 0)
            put("users", JSONArray())
        }
        val result = BackupManager.validateBackup(root.toString())
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("version", ignoreCase = true))
    }

    @Test
    fun testValidateBackup_rejectsNonArrayTable() {
        val root = JSONObject().apply {
            put("app", "deadline")
            put("version", 1)
            put("users", "not an array")
        }
        val result = BackupManager.validateBackup(root.toString())
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("array", ignoreCase = true))
    }

    @Test
    fun testValidateBackup_rejectsCorruptedEntryInTable() {
        val root = JSONObject().apply {
            put("app", "deadline")
            put("version", 1)
            val corruptedArray = JSONArray().apply {
                put("just a string, not an object")
            }
            put("users", corruptedArray)
        }
        val result = BackupManager.validateBackup(root.toString())
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("Corrupted entry", ignoreCase = true))
    }

    @Test
    fun testValidateBackup_rejectsPayloadWithNoKnownTables() {
        val root = JSONObject().apply {
            put("app", "deadline")
            put("version", 1)
            put("unknownTable", JSONArray())
        }
        val result = BackupManager.validateBackup(root.toString())
        assertTrue(result is BackupManager.ValidationResult.Failure)
        val failure = result as BackupManager.ValidationResult.Failure
        assertTrue(failure.reason.contains("No supported database tables", ignoreCase = true))
    }

    @Test
    fun testThemeModeNormalization() {
        fun normalizeTheme(mode: String): String = when (mode.lowercase().trim()) {
            "dark", "oled", "oled dark", "black" -> "dark"
            "light" -> "light"
            else -> "system"
        }

        assertEquals("dark", normalizeTheme("dark"))
        assertEquals("dark", normalizeTheme("OLED"))
        assertEquals("dark", normalizeTheme("oled dark"))
        assertEquals("dark", normalizeTheme("black"))
        assertEquals("light", normalizeTheme("light"))
        assertEquals("light", normalizeTheme("LIGHT"))
        assertEquals("system", normalizeTheme("system"))
        assertEquals("system", normalizeTheme("unknown"))
        assertEquals("system", normalizeTheme(""))
    }

    @Test
    fun testDefaultStartupScreenResolution() {
        val validScreens = listOf("STATS", "HABITS", "TIMER", "PROJECTS", "NOTES")
        fun resolveScreen(raw: String): String {
            return validScreens.find { it.equals(raw.trim(), ignoreCase = true) } ?: "TIMER"
        }

        assertEquals("STATS", resolveScreen("STATS"))
        assertEquals("HABITS", resolveScreen("habits"))
        assertEquals("TIMER", resolveScreen("TIMER"))
        assertEquals("PROJECTS", resolveScreen("projects"))
        assertEquals("NOTES", resolveScreen("notes"))
        assertEquals("TIMER", resolveScreen("invalid"))
        assertEquals("TIMER", resolveScreen(""))
    }
}
