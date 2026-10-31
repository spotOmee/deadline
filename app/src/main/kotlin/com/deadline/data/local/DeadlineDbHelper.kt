package com.deadline.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeadlineDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "deadline_local.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: DeadlineDbHelper? = null

        fun getInstance(context: Context): DeadlineDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeadlineDbHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. users table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                email TEXT,
                dailyGoal INTEGER NOT NULL DEFAULT 4,
                hasExplicitDailyGoal INTEGER NOT NULL DEFAULT 0,
                timezone TEXT NOT NULL DEFAULT 'UTC',
                focusDuration INTEGER NOT NULL DEFAULT 25,
                shortBreakDuration INTEGER NOT NULL DEFAULT 5,
                longBreakDuration INTEGER NOT NULL DEFAULT 15,
                longBreakInterval INTEGER NOT NULL DEFAULT 4,
                currentStreak INTEGER NOT NULL DEFAULT 0,
                maxStreak INTEGER NOT NULL DEFAULT 0,
                lastStreakDate TEXT,
                morningReminderEnabled INTEGER NOT NULL DEFAULT 0,
                morningReminderTime TEXT NOT NULL DEFAULT '07:00',
                eveningReminderEnabled INTEGER NOT NULL DEFAULT 1,
                eveningReminderTime TEXT NOT NULL DEFAULT '20:00',
                hasCompletedOnboarding INTEGER NOT NULL DEFAULT 1,
                showReflection INTEGER NOT NULL DEFAULT 1,
                themeMode TEXT NOT NULL DEFAULT 'system',
                hasExplicitTheme INTEGER NOT NULL DEFAULT 0,
                defaultStartupScreen TEXT NOT NULL DEFAULT 'TIMER',
                hasExplicitStartupScreen INTEGER NOT NULL DEFAULT 0,
                timerCategoryOrder TEXT NOT NULL DEFAULT 'FOCUS,SHORT_BREAK,LONG_BREAK',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 2. pomodoroSessions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pomodoroSessions (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                projectId TEXT,
                tagId TEXT,
                todoId TEXT,
                title TEXT,
                sessionType TEXT NOT NULL,
                duration INTEGER NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                completed INTEGER NOT NULL DEFAULT 1,
                pausedDuration INTEGER NOT NULL DEFAULT 0,
                note TEXT,
                quality TEXT,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_user_started ON pomodoroSessions(userId, startedAt);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_user_project ON pomodoroSessions(userId, projectId, startedAt);")

        // 3. projects table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS projects (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                color TEXT NOT NULL DEFAULT '#5E5CE6',
                targetPomodoros INTEGER NOT NULL DEFAULT 10,
                archived INTEGER NOT NULL DEFAULT 0,
                isPaused INTEGER NOT NULL DEFAULT 0,
                linkedNoteIds TEXT DEFAULT '',
                isPinned INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 4. projectTodos table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS projectTodos (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                projectId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                estimatedMinutes INTEGER NOT NULL DEFAULT 60,
                loggedMinutes INTEGER NOT NULL DEFAULT 0,
                priority TEXT NOT NULL DEFAULT 'MEDIUM',
                tagId TEXT,
                status TEXT NOT NULL DEFAULT 'TODO',
                dueDate TEXT,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                focusDuration INTEGER NOT NULL DEFAULT 1500,
                breakDuration INTEGER NOT NULL DEFAULT 300,
                flowDuration INTEGER NOT NULL DEFAULT 900,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_todos_project_status ON projectTodos(userId, projectId, status);")

        // 5. tags table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tags (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL DEFAULT '#5E5CE6',
                usageCount INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 6. habits table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS habits (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL DEFAULT '⚡',
                color TEXT NOT NULL DEFAULT '#30D158',
                status TEXT NOT NULL DEFAULT 'active',
                position INTEGER NOT NULL DEFAULT 0,
                schedule TEXT NOT NULL DEFAULT 'daily',
                completionType TEXT NOT NULL DEFAULT 'MANUAL',
                targetCount INTEGER NOT NULL DEFAULT 1,
                countUnit TEXT,
                pomodoroThreshold INTEGER NOT NULL DEFAULT 1,
                currentStreak INTEGER NOT NULL DEFAULT 0,
                maxStreak INTEGER NOT NULL DEFAULT 0,
                lastCompletedDate TEXT,
                focusDuration INTEGER NOT NULL DEFAULT 1500,
                breakDuration INTEGER NOT NULL DEFAULT 300,
                flowDuration INTEGER NOT NULL DEFAULT 900,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 7. habitCompletions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS habitCompletions (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                habitId TEXT NOT NULL,
                date TEXT NOT NULL,
                completedAt INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                isSkip INTEGER NOT NULL DEFAULT 0
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_completions_user_date ON habitCompletions(userId, date);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_completions_habit_date ON habitCompletions(habitId, date);")

        // 8. timeBlocks table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS timeBlocks (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                date TEXT NOT NULL,
                startMinute INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                title TEXT NOT NULL,
                color TEXT NOT NULL DEFAULT '#5E5CE6',
                projectId TEXT,
                todoId TEXT,
                repeatRule TEXT NOT NULL DEFAULT 'none',
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_user_date ON timeBlocks(userId, date);")

        // 9. dailyReflections table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS dailyReflections (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                date TEXT NOT NULL,
                mood INTEGER NOT NULL DEFAULT 3,
                energyLevel INTEGER NOT NULL DEFAULT 3,
                win TEXT,
                challenge TEXT,
                gratitude TEXT,
                tomorrowIntent TEXT,
                totalFocusMinutes INTEGER NOT NULL DEFAULT 0,
                habitsCompleted INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reflections_user_date ON dailyReflections(userId, date);")

        // 13. quickNotes table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quickNotes (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 14. knowledgeArticles table (Custom User CMS)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS knowledgeArticles (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                headline TEXT NOT NULL,
                body TEXT NOT NULL,
                tags TEXT NOT NULL,
                imageUri TEXT,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 15. focusTasks table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS focusTasks (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL DEFAULT 25,
                sessionType TEXT NOT NULL DEFAULT 'FOCUS',
                isDefault INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())

        // 16. analytics_cache table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS analytics_cache (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            );
        """.trimIndent())

        // Seed initial data
        seedDefaults(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quickNotes (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                noteType TEXT NOT NULL DEFAULT 'NOTE',
                isPinned INTEGER NOT NULL DEFAULT 0,
                isChecklist INTEGER NOT NULL DEFAULT 0,
                fontSize INTEGER NOT NULL DEFAULT 16,
                textColor TEXT NOT NULL DEFAULT 'DEFAULT',
                alignment TEXT NOT NULL DEFAULT 'LEFT',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL DEFAULT 0
            );
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS knowledgeArticles (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                headline TEXT NOT NULL,
                body TEXT NOT NULL,
                tags TEXT NOT NULL,
                imageUri TEXT,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS focusTasks (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                title TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL DEFAULT 25,
                sessionType TEXT NOT NULL DEFAULT 'FOCUS',
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            );
        """.trimIndent())
        try { db.execSQL("ALTER TABLE users ADD COLUMN themeMode TEXT DEFAULT 'system';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN hasExplicitTheme INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET themeMode = 'system' WHERE hasExplicitTheme = 0 OR hasExplicitTheme IS NULL;") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET name = '' WHERE name = 'Name' OR name = 'Productivity Master' OR name = 'Focus Champion';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN avatarUri TEXT;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN timeFormat24Hour INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN targetDeadline TEXT DEFAULT '';") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET targetDeadline = '' WHERE targetDeadline = 'Q4 Launch • Dec 31';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN currentState TEXT DEFAULT '';") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET currentState = '' WHERE currentState = 'Disciplined engineer & craftsman';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN targetIdentity TEXT DEFAULT '';") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET targetIdentity = '' WHERE targetIdentity = 'World-class creator building generational tools';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN bioNarrative TEXT DEFAULT '';") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET bioNarrative = '' WHERE bioNarrative = 'Focused on building quiet, local-first software that honors attention and craft.';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projects ADD COLUMN isPaused INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projects ADD COLUMN linkedNoteIds TEXT DEFAULT '';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projects ADD COLUMN isPinned INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projects ADD COLUMN sortOrder INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projectTodos ADD COLUMN sortOrder INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE focusTasks ADD COLUMN sessionType TEXT DEFAULT 'FOCUS';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE focusTasks ADD COLUMN sortOrder INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN noteType TEXT DEFAULT 'NOTE';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN isPinned INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN isChecklist INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN fontSize INTEGER DEFAULT 16;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN textColor TEXT DEFAULT 'DEFAULT';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN alignment TEXT DEFAULT 'LEFT';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN updatedAt INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE quickNotes ADD COLUMN sortOrder INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("UPDATE quickNotes SET noteType = 'IMPORTANT' WHERE noteType = 'SCRIPT';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN heatmapPixelIcon TEXT DEFAULT 'TARGET_BULLSEYE';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN defaultStartupScreen TEXT DEFAULT 'TIMER';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN hasExplicitStartupScreen INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("UPDATE users SET defaultStartupScreen = 'TIMER' WHERE (hasExplicitStartupScreen = 0 OR hasExplicitStartupScreen IS NULL) AND (defaultStartupScreen IS NULL OR defaultStartupScreen = '');") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN hasExplicitDailyGoal INTEGER DEFAULT 0;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN timerCategoryOrder TEXT DEFAULT 'FOCUS,SHORT_BREAK,LONG_BREAK';") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE habits ADD COLUMN focusDuration INTEGER DEFAULT 1500;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE habits ADD COLUMN breakDuration INTEGER DEFAULT 300;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE habits ADD COLUMN flowDuration INTEGER DEFAULT 900;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projectTodos ADD COLUMN focusDuration INTEGER DEFAULT 1500;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projectTodos ADD COLUMN breakDuration INTEGER DEFAULT 300;") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE projectTodos ADD COLUMN flowDuration INTEGER DEFAULT 900;") } catch (_: Exception) {}
        try {
            // Guarantee a clean slate by purging any legacy pre-seeded demo records
            db.execSQL("DELETE FROM projects WHERE id = 'proj_deadline';")
            db.execSQL("DELETE FROM projectTodos WHERE id IN ('task_core', 'task_timer') OR projectId = 'proj_deadline';")
            db.execSQL("DELETE FROM habits WHERE id IN ('habit_deep_work', 'habit_water', 'habit_walk');")
            db.execSQL("DELETE FROM timeBlocks WHERE id IN ('block_morning', 'block_afternoon') OR projectId = 'proj_deadline';")
            db.execSQL("DELETE FROM quickNotes WHERE id IN ('note_welcome', 'note_active_checklist', 'note_important_manifesto');")
            db.execSQL("DELETE FROM knowledgeArticles WHERE id = 'article_craft';")
            db.execSQL("DELETE FROM focusTasks WHERE id IN ('task_focus_deepwork', 'task_focus_architecture', 'task_focus_review', 'task_break_hydrate', 'task_break_eyerest', 'task_break_walk', 'task_flow_reset', 'task_flow_walk', 'task_flow_meditate');")
        } catch (_: Exception) {}
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS analytics_cache (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            );
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive upgrades
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        // Default user settings profile
        val userValues = ContentValues().apply {
            put("id", "default_user")
            put("name", "")
            put("dailyGoal", 4)
            put("hasExplicitDailyGoal", 0)
            put("focusDuration", 25)
            put("shortBreakDuration", 5)
            put("longBreakDuration", 15)
            put("currentStreak", 0)
            put("maxStreak", 0)
            putNull("lastStreakDate")
            put("eveningReminderEnabled", 1)
            put("eveningReminderTime", "20:00")
            put("themeMode", "system")
            put("hasExplicitTheme", 0)
            put("defaultStartupScreen", "TIMER")
            put("hasExplicitStartupScreen", 0)
            put("targetDeadline", "")
            put("currentState", "")
            put("targetIdentity", "")
            put("bioNarrative", "")
            put("createdAt", now)
            put("updatedAt", now)
        }
        db.insert("users", null, userValues)

        // Production-Ready Specification:
        // Initializes with a completely empty state across all modules
        // (zero pre-existing habits, tasks, projects, notes, or focus/break/flow tasks).
    }
}
