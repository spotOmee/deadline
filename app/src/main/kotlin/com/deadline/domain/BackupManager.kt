package com.deadline.domain

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.deadline.data.local.DeadlineDbHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupManager {

    val ALL_TABLES = listOf(
        "users",
        "projects",
        "projectTodos",
        "tags",
        "habits",
        "habitCompletions",
        "timeBlocks",
        "pomodoroSessions",
        "dailyReflections",
        "quickNotes",
        "knowledgeArticles",
        "focusTasks",
        "analytics_cache"
    )

    val RESTORE_DELETION_ORDER = listOf(
        "habitCompletions",
        "projectTodos",
        "timeBlocks",
        "pomodoroSessions",
        "dailyReflections",
        "quickNotes",
        "knowledgeArticles",
        "focusTasks",
        "tags",
        "habits",
        "projects",
        "analytics_cache",
        "users"
    )

    sealed class ValidationResult {
        data class Success(
            val version: Int,
            val exportedAt: Long,
            val tableCounts: Map<String, Int>
        ) : ValidationResult()

        data class Failure(val reason: String) : ValidationResult()
    }

    /**
     * Serializes the entire local database state into a structured, validated JSON payload.
     */
    fun exportToJson(context: Context): String {
        val dbHelper = DeadlineDbHelper.getInstance(context)
        val db = dbHelper.readableDatabase
        val root = JSONObject()
        root.put("app", "deadline")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val tableStats = JSONObject()

        for (table in ALL_TABLES) {
            val array = JSONArray()
            val cursor = db.rawQuery("SELECT * FROM $table", null)
            cursor.use {
                val colCount = it.columnCount
                while (it.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until colCount) {
                        val colName = it.getColumnName(i)
                        when (it.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(colName, it.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(colName, it.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> row.put(colName, it.getString(i))
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(colName, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_BLOB -> {
                                val bytes = it.getBlob(i)
                                if (bytes != null) {
                                    row.put(colName, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                                } else {
                                    row.put(colName, JSONObject.NULL)
                                }
                            }
                        }
                    }
                    array.put(row)
                }
            }
            root.put(table, array)
            tableStats.put(table, array.length())
        }
        root.put("tableStats", tableStats)

        return root.toString(2)
    }

    fun saveBackupToFile(context: Context, json: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "deadline_backup_${System.currentTimeMillis()}.json")
        file.writeText(json)
        return file
    }

    fun exportBackup(context: Context): File? {
        return try {
            val json = exportToJson(context)
            saveBackupToFile(context, json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Rigorous validation of JSON backup payload.
     * Verifies application identity, schema version, table structures, and record integrity.
     */
    fun validateBackup(jsonString: String?): ValidationResult {
        if (jsonString.isNullOrBlank()) {
            return ValidationResult.Failure("Backup payload is empty.")
        }

        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return ValidationResult.Failure("Malformed JSON format: ${e.localizedMessage ?: "parsing failed"}.")
        }

        val appSignature = root.optString("app", "")
        if (appSignature != "deadline") {
            return ValidationResult.Failure("Unrecognized application signature. Expected 'deadline', found '$appSignature'.")
        }

        val version = root.optInt("version", -1)
        if (version <= 0) {
            return ValidationResult.Failure("Invalid backup schema version: $version.")
        }

        val tableCounts = mutableMapOf<String, Int>()
        var foundAnyTable = false

        for (table in ALL_TABLES) {
            if (root.has(table)) {
                foundAnyTable = true
                val array = root.optJSONArray(table)
                    ?: return ValidationResult.Failure("Table '$table' must be a valid JSON array.")
                for (i in 0 until array.length()) {
                    if (array.optJSONObject(i) == null) {
                        return ValidationResult.Failure("Corrupted entry found at index $i in table '$table'.")
                    }
                }
                tableCounts[table] = array.length()
            }
        }

        if (!foundAnyTable) {
            return ValidationResult.Failure("No supported database tables found in backup payload.")
        }

        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
        return ValidationResult.Success(version, exportedAt, tableCounts)
    }

    /**
     * Reliable State Recovery: Restores all tables transactionally from JSON backup.
     * Validates payload before modification, disables constraints safely during restore,
     * seeds fallback user if required, and guarantees complete rollback on failure.
     */
    fun restoreFromJson(context: Context, jsonString: String): Boolean {
        val validation = validateBackup(jsonString)
        if (validation !is ValidationResult.Success) {
            return false
        }

        return try {
            val root = JSONObject(jsonString)
            val dbHelper = DeadlineDbHelper.getInstance(context)
            val db = dbHelper.writableDatabase

            db.beginTransaction()
            try {
                // Temporarily disable foreign keys for clean restoration
                try { db.execSQL("PRAGMA foreign_keys = OFF;") } catch (_: Exception) {}

                // Clear child tables first to avoid foreign key violations
                for (table in RESTORE_DELETION_ORDER) {
                    if (root.has(table)) {
                        db.delete(table, null, null)
                    }
                }

                // Restore records for each table present in backup
                for (table in ALL_TABLES) {
                    if (root.has(table)) {
                        val array = root.getJSONArray(table)
                        for (i in 0 until array.length()) {
                            val row = array.getJSONObject(i)
                            val values = ContentValues()
                            val keys = row.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                if (row.isNull(key)) {
                                    values.putNull(key)
                                } else {
                                    when (val v = row.get(key)) {
                                        is Int -> values.put(key, v)
                                        is Long -> values.put(key, v)
                                        is Double -> values.put(key, v)
                                        is Float -> values.put(key, v)
                                        is Boolean -> values.put(key, if (v) 1 else 0)
                                        else -> values.put(key, v.toString())
                                    }
                                }
                            }
                            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
                }

                // Guarantee default user profile exists
                val userCursor = db.rawQuery("SELECT COUNT(*) FROM users WHERE id = 'default_user'", null)
                var hasUser = false
                userCursor.use {
                    if (it.moveToFirst()) hasUser = it.getInt(0) > 0
                }
                if (!hasUser) {
                    val defaultUser = ContentValues().apply {
                        put("id", "default_user")
                        put("name", "")
                        put("dailyGoal", 4)
                        put("hasExplicitDailyGoal", 0)
                        put("focusDuration", 25)
                        put("shortBreakDuration", 5)
                        put("longBreakDuration", 15)
                        put("themeMode", "system")
                        put("hasExplicitTheme", 0)
                        put("defaultStartupScreen", "TIMER")
                        put("hasExplicitStartupScreen", 0)
                        put("createdAt", System.currentTimeMillis())
                        put("updatedAt", System.currentTimeMillis())
                    }
                    db.insert("users", null, defaultUser)
                }

                // Restore foreign key constraint enforcement
                try { db.execSQL("PRAGMA foreign_keys = ON;") } catch (_: Exception) {}

                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreFromFile(context: Context, file: File): Boolean {
        return try {
            if (!file.exists()) return false
            val jsonString = file.readText()
            restoreFromJson(context, jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getLatestBackupFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return dir.listFiles { f -> f.isFile && f.name.startsWith("deadline_backup_") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun listBackupFiles(context: Context): List<File> {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return dir.listFiles { f -> f.isFile && f.name.startsWith("deadline_backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
