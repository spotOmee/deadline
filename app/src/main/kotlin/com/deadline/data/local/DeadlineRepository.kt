package com.deadline.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.deadline.ui.stats.AnalyticsUiState
import com.deadline.ui.theme.ThemeManager
import com.deadline.domain.GlobalTimeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class DeadlineRepository(private val context: Context) {

    companion object {
        const val PREF_KEY_DEFAULT_STARTUP_SCREEN = "default_startup_screen"
        const val PREF_KEY_DEFAULT_STARTUP_SCREEN_CAMEL = "defaultStartupScreen"
        const val PREF_KEY_DEFAULT_STARTUP_SCREEN_TITLE = "Default Startup Screen"
        const val PREF_KEY_STARTUP_SCREEN = "startup_screen"
        const val PREFS_NAME = "deadline_preferences"
        const val PREF_KEY_TIMER_CATEGORY_ORDER = "timer_category_order"
        val DEFAULT_TIMER_CATEGORY_ORDER: List<Pair<String, PomodoroType>> = listOf(
            "Focus" to PomodoroType.FOCUS,
            "Break" to PomodoroType.SHORT_BREAK,
            "Flow" to PomodoroType.LONG_BREAK
        )

        fun normalizeStartupScreen(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return when (raw.trim().uppercase(Locale.ROOT)) {
                "TIMER" -> "TIMER"
                "STATS" -> "STATS"
                "HABITS" -> "HABITS"
                "PROJECTS", "TASKS" -> "PROJECTS"
                "NOTES" -> "NOTES"
                "0" -> "STATS"
                "1" -> "HABITS"
                "2" -> "TIMER"
                "3" -> "PROJECTS"
                "4" -> "NOTES"
                else -> null
            }
        }

        fun normalizeStartupScreenIndex(index: Int): String? {
            return when (index) {
                0 -> "STATS"
                1 -> "HABITS"
                2 -> "TIMER"
                3 -> "PROJECTS"
                4 -> "NOTES"
                else -> null
            }
        }
    }

    private val dbHelper = DeadlineDbHelper.getInstance(context)

    fun readSavedStartupScreenPreference(ctx: Context? = context): String? {
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
            PREF_KEY_DEFAULT_STARTUP_SCREEN_TITLE,
            PREF_KEY_DEFAULT_STARTUP_SCREEN,
            PREF_KEY_DEFAULT_STARTUP_SCREEN_CAMEL,
            "DEFAULT_STARTUP_SCREEN",
            PREF_KEY_STARTUP_SCREEN,
            "startupScreen",
            "STARTUP_SCREEN",
            "default_screen",
            "defaultScreen",
            "startup_tab",
            "startupTab"
        )
        for (fileName in prefFiles) {
            val sp = try {
                ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            } catch (_: Throwable) {
                null
            } ?: continue

            for (key in candidateKeys) {
                try {
                    if (!sp.contains(key)) continue
                    val rawStr = sp.getString(key, null)
                    if (!rawStr.isNullOrBlank()) {
                        val mapped = normalizeStartupScreen(rawStr)
                        if (mapped != null) return mapped
                    }
                } catch (_: ClassCastException) {
                    try {
                        val intVal = sp.getInt(key, -1)
                        if (intVal >= 0) {
                            val mapped = normalizeStartupScreenIndex(intVal)
                            if (mapped != null) return mapped
                        }
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    fun writeSavedStartupScreenPreference(screen: String, ctx: Context? = context) {
        if (ctx == null) return
        val prefFiles = listOf(
            try { "${ctx.packageName}_preferences" } catch (_: Throwable) { "com.deadline_preferences" },
            "deadline_preferences",
            "deadline_prefs",
            "deadline_pomodoro_prefs"
        )
        val keys = listOf(
            PREF_KEY_DEFAULT_STARTUP_SCREEN_TITLE,
            PREF_KEY_DEFAULT_STARTUP_SCREEN,
            PREF_KEY_DEFAULT_STARTUP_SCREEN_CAMEL
        )
        for (fileName in prefFiles) {
            try {
                val sp = ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                for (k in keys) {
                    editor.putString(k, screen)
                }
                editor.apply()
            } catch (_: Throwable) {}
        }
    }

    fun getSavedStartupScreen(): String {
        val current = _userSettings.value.defaultStartupScreen
        if (current.isNotBlank() && !current.equals("TIMER", ignoreCase = true)) {
            return current
        }
        val fromPrefs = readSavedStartupScreenPreference(context)
        if (!fromPrefs.isNullOrBlank()) {
            return fromPrefs
        }
        return current.ifBlank { "TIMER" }
    }

    fun getSavedThemeMode(): String {
        val current = _userSettings.value.themeMode
        if (current.isNotBlank() && !current.equals("system", ignoreCase = true)) {
            return current
        }
        val fromPrefs = ThemeManager.readSavedThemePreference(context)
        if (!fromPrefs.isNullOrBlank()) {
            return fromPrefs
        }
        return current.ifBlank { "system" }
    }

    // Reactive StateFlows for Compose UI
    private val _userSettings = MutableStateFlow(
        UserSettings(
            themeMode = ThemeManager.readSavedThemePreference(context) ?: "system",
            defaultStartupScreen = readSavedStartupScreenPreference(context) ?: "TIMER"
        )
    )
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    init {
        loadUserSettings()
    }

    private val _planImportedTaskIds = MutableStateFlow<List<String>>(emptyList())
    val planImportedTaskIds: StateFlow<List<String>> = _planImportedTaskIds.asStateFlow()

    private val _planImportedProjectIds = MutableStateFlow<List<String>>(emptyList())
    val planImportedProjectIds: StateFlow<List<String>> = _planImportedProjectIds.asStateFlow()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _todos = MutableStateFlow<List<ProjectTodo>>(emptyList())
    val todos: StateFlow<List<ProjectTodo>> = _todos.asStateFlow()

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    private val _todayCompletions = MutableStateFlow<Map<String, HabitCompletion>>(emptyMap())
    val todayCompletions: StateFlow<Map<String, HabitCompletion>> = _todayCompletions.asStateFlow()

    private val _monthHabitCompletions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val monthHabitCompletions: StateFlow<Map<String, Set<String>>> = _monthHabitCompletions.asStateFlow()

    private val _todayTimeBlocks = MutableStateFlow<List<TimeBlock>>(emptyList())
    val todayTimeBlocks: StateFlow<List<TimeBlock>> = _todayTimeBlocks.asStateFlow()

    private val _selectedPlanDate = MutableStateFlow<String>("")
    val selectedPlanDate: StateFlow<String> = _selectedPlanDate.asStateFlow()

    private val _timelineTimeBlocks = MutableStateFlow<List<TimeBlock>>(emptyList())
    val timelineTimeBlocks: StateFlow<List<TimeBlock>> = _timelineTimeBlocks.asStateFlow()

    private val _todaySessions = MutableStateFlow<List<PomodoroSession>>(emptyList())
    val todaySessions: StateFlow<List<PomodoroSession>> = _todaySessions.asStateFlow()

    private val _todayReflection = MutableStateFlow<DailyReflection?>(null)
    val todayReflection: StateFlow<DailyReflection?> = _todayReflection.asStateFlow()

    private val _quickNotes = MutableStateFlow<List<QuickNote>>(emptyList())
    val quickNotes: StateFlow<List<QuickNote>> = _quickNotes.asStateFlow()

    private val _knowledgeArticles = MutableStateFlow<List<KnowledgeArticle>>(emptyList())
    val knowledgeArticles: StateFlow<List<KnowledgeArticle>> = _knowledgeArticles.asStateFlow()

    private val _focusTasks = MutableStateFlow<List<FocusTask>>(emptyList())
    val focusTasks: StateFlow<List<FocusTask>> = _focusTasks.asStateFlow()

    private val _timerCategoryOrder = MutableStateFlow<List<Pair<String, PomodoroType>>>(readSavedTimerCategoryOrder(context))
    val timerCategoryOrder: StateFlow<List<Pair<String, PomodoroType>>> = _timerCategoryOrder.asStateFlow()

    private val _monthlyFocusMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val monthlyFocusMinutes: StateFlow<Map<String, Int>> = _monthlyFocusMinutes.asStateFlow()

    // Global Undo Event Stream
    private val _undoEvent = MutableStateFlow<UndoPayload?>(null)
    val undoEvent: StateFlow<UndoPayload?> = _undoEvent.asStateFlow()

    private val undoStack = mutableListOf<UndoPayload>()

    fun dismissUndo() {
        _undoEvent.value = null
    }

    private fun pushUndoPayload(payload: UndoPayload) {
        undoStack.add(payload)
        _undoEvent.value = payload
    }

    suspend fun undoLastDeletion() = withContext(Dispatchers.IO) {
        val payload = if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex) else _undoEvent.value ?: return@withContext
        _undoEvent.value = null
        val db = dbHelper.writableDatabase

        when (payload) {
            is UndoPayload.TodoPayload -> {
                val values = ContentValues().apply {
                    put("id", payload.todo.id)
                    put("userId", payload.todo.userId)
                    put("projectId", payload.todo.projectId)
                    put("title", com.deadline.domain.HeadlineScaler.clamp(payload.todo.title))
                    put("description", payload.todo.description)
                    put("estimatedMinutes", payload.todo.estimatedMinutes)
                    put("loggedMinutes", payload.todo.loggedMinutes)
                    put("priority", payload.todo.priority.name)
                    put("status", payload.todo.status.name)
                    put("dueDate", payload.todo.dueDate)
                    put("createdAt", payload.todo.createdAt)
                }
                db.insertWithOnConflict("projectTodos", null, values, SQLiteDatabase.CONFLICT_REPLACE)

                payload.associatedTimeBlocks.forEach { block ->
                    val blockValues = ContentValues().apply {
                        put("id", block.id)
                        put("userId", block.userId)
                        put("title", block.title)
                        put("startMinute", block.startMinute)
                        put("durationMinutes", block.durationMinutes)
                        put("color", block.color)
                        put("projectId", block.projectId)
                        put("todoId", block.todoId)
                        put("date", block.date)
                        put("repeatRule", block.repeatRule)
                        put("createdAt", System.currentTimeMillis())
                    }
                    db.insertWithOnConflict("timeBlocks", null, blockValues, SQLiteDatabase.CONFLICT_REPLACE)
                }

                if (payload.wasInPlan) {
                    val currentPlan = _planImportedTaskIds.value.toMutableList()
                    if (!currentPlan.contains(payload.todo.id)) {
                        currentPlan.add(payload.todo.id)
                        _planImportedTaskIds.value = currentPlan
                        setCachedAnalytics("plan_imported_task_ids", currentPlan.joinToString(","))
                    }
                }

                val currentTodos = _todos.value.filter { it.id != payload.todo.id }.toMutableList()
                val insertIndex = payload.originalIndex.coerceIn(0, currentTodos.size)
                currentTodos.add(insertIndex, payload.todo)
                _todos.value = currentTodos
                reorderTodos(currentTodos)
                loadTodayTimeBlocks()
            val todayStr = getTodayString()
            _selectedPlanDate.value = todayStr
            loadTimelineTimeBlocks(todayStr)
                syncTodayProgressHelper()
            }

            is UndoPayload.HabitPayload -> {
                val values = ContentValues().apply {
                    put("id", payload.habit.id)
                    put("userId", payload.habit.userId)
                    put("name", payload.habit.name)
                    put("emoji", payload.habit.emoji)
                    put("color", payload.habit.color)
                    put("schedule", payload.habit.schedule)
                    put("completionType", payload.habit.completionType.name)
                    put("targetCount", payload.habit.targetCount)
                    put("countUnit", payload.habit.countUnit)
                    put("pomodoroThreshold", payload.habit.pomodoroThreshold)
                    put("currentStreak", payload.habit.currentStreak)
                    put("maxStreak", payload.habit.maxStreak)
                    put("lastCompletedDate", payload.habit.lastCompletedDate)
                    put("position", payload.originalIndex)
                    put("status", "active")
                    put("createdAt", payload.habit.createdAt)
                }
                db.insertWithOnConflict("habits", null, values, SQLiteDatabase.CONFLICT_REPLACE)

                payload.completions.forEach { comp ->
                    val compValues = ContentValues().apply {
                        put("id", comp.id)
                        put("userId", comp.userId)
                        put("habitId", comp.habitId)
                        put("date", comp.date)
                        put("count", comp.count)
                        put("isSkip", if (comp.isSkip) 1 else 0)
                        put("completedAt", comp.completedAt)
                    }
                    db.insertWithOnConflict("habitCompletions", null, compValues, SQLiteDatabase.CONFLICT_REPLACE)
                }

                val currentHabits = _habits.value.filter { it.id != payload.habit.id }.toMutableList()
                val insertIndex = payload.originalIndex.coerceIn(0, currentHabits.size)
                currentHabits.add(insertIndex, payload.habit)
                _habits.value = currentHabits
                reorderHabits(currentHabits)
                loadTodayCompletions()
            }

            is UndoPayload.ProjectPayload -> {
                val values = ContentValues().apply {
                    put("id", payload.project.id)
                    put("userId", payload.project.userId)
                    put("name", com.deadline.domain.HeadlineScaler.clamp(payload.project.name))
                    put("description", payload.project.description)
                    put("color", payload.project.color)
                    put("targetPomodoros", payload.project.targetPomodoros)
                    put("createdAt", payload.project.createdAt)
                    put("isPaused", if (payload.project.isPaused) 1 else 0)
                    put("archived", if (payload.project.archived) 1 else 0)
                    put("isPinned", if (payload.project.isPinned) 1 else 0)
                    put("linkedNoteIds", payload.project.linkedNoteIds)
                }
                db.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_REPLACE)

                payload.associatedTodos.forEach { todo ->
                    val todoValues = ContentValues().apply {
                        put("id", todo.id)
                        put("userId", todo.userId)
                        put("projectId", todo.projectId)
                        put("title", com.deadline.domain.HeadlineScaler.clamp(todo.title))
                        put("description", todo.description)
                        put("estimatedMinutes", todo.estimatedMinutes)
                        put("loggedMinutes", todo.loggedMinutes)
                        put("priority", todo.priority.name)
                        put("status", todo.status.name)
                        put("dueDate", todo.dueDate)
                        put("createdAt", todo.createdAt)
                    }
                    db.insertWithOnConflict("projectTodos", null, todoValues, SQLiteDatabase.CONFLICT_REPLACE)
                }

                payload.associatedTimeBlocks.forEach { block ->
                    val blockValues = ContentValues().apply {
                        put("id", block.id)
                        put("userId", block.userId)
                        put("title", block.title)
                        put("startMinute", block.startMinute)
                        put("durationMinutes", block.durationMinutes)
                        put("color", block.color)
                        put("projectId", block.projectId)
                        put("todoId", block.todoId)
                        put("date", block.date)
                        put("repeatRule", block.repeatRule)
                        put("createdAt", System.currentTimeMillis())
                    }
                    db.insertWithOnConflict("timeBlocks", null, blockValues, SQLiteDatabase.CONFLICT_REPLACE)
                }

                if (payload.planImportedIds.isNotEmpty()) {
                    val currentPlan = _planImportedTaskIds.value.toMutableList()
                    payload.planImportedIds.forEach { id ->
                        if (!currentPlan.contains(id)) currentPlan.add(id)
                    }
                    _planImportedTaskIds.value = currentPlan
                    setCachedAnalytics("plan_imported_task_ids", currentPlan.joinToString(","))
                }

                if (payload.wasInPlan) {
                    val currentProjectPlan = _planImportedProjectIds.value.toMutableList()
                    if (!currentProjectPlan.contains(payload.project.id)) {
                        currentProjectPlan.add(payload.project.id)
                        _planImportedProjectIds.value = currentProjectPlan
                        setCachedAnalytics("plan_imported_project_ids", currentProjectPlan.joinToString(","))
                    }
                }

                val currentProjects = _projects.value.filter { it.id != payload.project.id }.toMutableList()
                val insertIndex = payload.originalIndex.coerceIn(0, currentProjects.size)
                currentProjects.add(insertIndex, payload.project)
                _projects.value = currentProjects
                reorderProjects(currentProjects)
                loadTodos()
                loadTodayTimeBlocks()
            val todayStr = getTodayString()
            _selectedPlanDate.value = todayStr
            loadTimelineTimeBlocks(todayStr)
            }

            is UndoPayload.NotePayload -> {
                val values = ContentValues().apply {
                    put("id", payload.note.id)
                    put("userId", payload.note.userId)
                    put("title", payload.note.title)
                    put("content", payload.note.content)
                    put("noteType", payload.note.noteType)
                    put("isPinned", if (payload.note.isPinned) 1 else 0)
                    put("isChecklist", if (payload.note.isChecklist) 1 else 0)
                    put("fontSize", payload.note.fontSize)
                    put("textColor", payload.note.textColor)
                    put("alignment", payload.note.alignment)
                    put("sortOrder", payload.note.sortOrder)
                    put("createdAt", payload.note.createdAt)
                    put("updatedAt", payload.note.updatedAt)
                }
                db.insertWithOnConflict("quickNotes", null, values, SQLiteDatabase.CONFLICT_REPLACE)

                val currentNotes = _quickNotes.value.filter { it.id != payload.note.id }.toMutableList()
                val insertIndex = payload.originalIndex.coerceIn(0, currentNotes.size)
                currentNotes.add(insertIndex, payload.note)
                _quickNotes.value = currentNotes
                reorderQuickNotes(currentNotes)
            }
        }
    }

    init {
        try {
            loadUserSettings()
            loadProjects()
            loadTodos()
            loadHabits()
            loadTodayCompletions()
            loadMonthHabitCompletions()
            loadTodayTimeBlocks()
            val todayStr = getTodayString()
            _selectedPlanDate.value = todayStr
            loadTimelineTimeBlocks(todayStr)
            loadTodaySessions()
            loadTodayReflection()
            loadQuickNotes()
            loadKnowledgeArticles()
            loadFocusTasks()
            loadMonthlyFocusMinutes()
            loadPlanImportedTaskIds()
        loadPlanImportedProjectIds()
            loadPlanImportedProjectIds()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Reactive Flow stream observers for ViewModel/UI consumption
    fun observeTodayHabits(): kotlinx.coroutines.flow.Flow<List<Habit>> = habits
    fun observeTodayHabitCompletions(): kotlinx.coroutines.flow.Flow<Map<String, HabitCompletion>> = todayCompletions
    fun observeTodayTasks(): kotlinx.coroutines.flow.Flow<List<ProjectTodo>> = todos
    fun observeTodaySessions(): kotlinx.coroutines.flow.Flow<List<PomodoroSession>> = todaySessions
    fun observeMonthlyFocusMinutes(): kotlinx.coroutines.flow.Flow<Map<String, Int>> = monthlyFocusMinutes

    fun getTodayString(): String {
        return GlobalTimeManager.getTodayDateString(_userSettings.value.timeFormat24Hour)
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        loadUserSettings()
        loadProjects()
        loadTodos()
        loadHabits()
        loadTodayCompletions()
        loadMonthHabitCompletions()
        loadTodayTimeBlocks()
        loadTodaySessions()
        loadTodayReflection()
        loadQuickNotes()
        loadKnowledgeArticles()
        loadFocusTasks()
        loadMonthlyFocusMinutes()
        loadPlanImportedTaskIds()
        loadPlanImportedProjectIds()
        _timerCategoryOrder.value = readSavedTimerCategoryOrder(context)
    }

    // --- Users ---
    fun loadUserSettings() {
        val prefScreen = readSavedStartupScreenPreference(context)
        val prefTheme = ThemeManager.readSavedThemePreference(context)
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE id = 'default_user' LIMIT 1", null)
        cursor.use {
            if (it.moveToFirst()) {
                val dbStartupScreen = if (it.getColumnIndex("defaultStartupScreen") >= 0) {
                    it.getString(it.getColumnIndexOrThrow("defaultStartupScreen")) ?: "TIMER"
                } else "TIMER"
                val hasExplicitStartup = if (it.getColumnIndex("hasExplicitStartupScreen") >= 0) {
                    it.getInt(it.getColumnIndexOrThrow("hasExplicitStartupScreen")) == 1
                } else false

                val resolvedStartupScreen = when {
                    prefScreen != null -> prefScreen
                    hasExplicitStartup && dbStartupScreen.isNotBlank() -> dbStartupScreen
                    dbStartupScreen.isNotBlank() && !dbStartupScreen.equals("TIMER", ignoreCase = true) -> dbStartupScreen
                    else -> "TIMER"
                }

                // Keep DB and SharedPreferences synchronized
                if (prefScreen != null && prefScreen != dbStartupScreen) {
                    try {
                        val wDb = dbHelper.writableDatabase
                        val values = ContentValues().apply {
                            put("defaultStartupScreen", prefScreen)
                            put("hasExplicitStartupScreen", 1)
                        }
                        wDb.update("users", values, "id = 'default_user'", null)
                    } catch (_: Throwable) {}
                } else if (hasExplicitStartup && prefScreen == null && dbStartupScreen.isNotBlank()) {
                    writeSavedStartupScreenPreference(dbStartupScreen, context)
                }

                val dbTheme = if (it.getColumnIndex("themeMode") >= 0) {
                    it.getString(it.getColumnIndexOrThrow("themeMode")) ?: "system"
                } else "system"
                val hasExplicitTheme = if (it.getColumnIndex("hasExplicitTheme") >= 0) {
                    it.getInt(it.getColumnIndexOrThrow("hasExplicitTheme")) == 1
                } else false

                val resolvedTheme = when {
                    prefTheme != null -> prefTheme
                    hasExplicitTheme && dbTheme.isNotBlank() -> dbTheme
                    else -> dbTheme.ifBlank { "system" }
                }

                // Keep DB, SharedPreferences, and AppCompatDelegate in sync for theme
                if (prefTheme != null && prefTheme != dbTheme) {
                    try {
                        val wDb = dbHelper.writableDatabase
                        val values = ContentValues().apply {
                            put("themeMode", prefTheme)
                            put("hasExplicitTheme", if (prefTheme.equals("system", ignoreCase = true)) 0 else 1)
                        }
                        wDb.update("users", values, "id = 'default_user'", null)
                    } catch (_: Throwable) {}
                } else if (hasExplicitTheme && prefTheme == null && dbTheme.isNotBlank()) {
                    ThemeManager.writeSavedThemePreference(dbTheme, context)
                }
                ThemeManager.applyThemeMode(resolvedTheme)

                val settings = UserSettings(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    email = it.getString(it.getColumnIndexOrThrow("email")),
                    dailyGoalPomodoros = it.getInt(it.getColumnIndexOrThrow("dailyGoal")),
                    focusDurationMinutes = it.getInt(it.getColumnIndexOrThrow("focusDuration")),
                    shortBreakDurationMinutes = it.getInt(it.getColumnIndexOrThrow("shortBreakDuration")),
                    longBreakDurationMinutes = it.getInt(it.getColumnIndexOrThrow("longBreakDuration")),
                    longBreakInterval = it.getInt(it.getColumnIndexOrThrow("longBreakInterval")),
                    currentStreak = it.getInt(it.getColumnIndexOrThrow("currentStreak")),
                    maxStreak = it.getInt(it.getColumnIndexOrThrow("maxStreak")),
                    lastStreakDate = it.getString(it.getColumnIndexOrThrow("lastStreakDate")),
                    eveningReminderEnabled = it.getInt(it.getColumnIndexOrThrow("eveningReminderEnabled")) == 1,
                    eveningReminderTime = it.getString(it.getColumnIndexOrThrow("eveningReminderTime")),
                    themeMode = resolvedTheme,
                    avatarUri = if (it.getColumnIndex("avatarUri") >= 0) it.getString(it.getColumnIndexOrThrow("avatarUri")) else null,
                    timeFormat24Hour = if (it.getColumnIndex("timeFormat24Hour") >= 0) it.getInt(it.getColumnIndexOrThrow("timeFormat24Hour")) == 1 else false,
                    targetDeadline = if (it.getColumnIndex("targetDeadline") >= 0) it.getString(it.getColumnIndexOrThrow("targetDeadline")) ?: "" else "",
                    currentState = if (it.getColumnIndex("currentState") >= 0) it.getString(it.getColumnIndexOrThrow("currentState")) ?: "" else "",
                    targetIdentity = if (it.getColumnIndex("targetIdentity") >= 0) it.getString(it.getColumnIndexOrThrow("targetIdentity")) ?: "" else "",
                    bioNarrative = if (it.getColumnIndex("bioNarrative") >= 0) it.getString(it.getColumnIndexOrThrow("bioNarrative")) ?: "" else "",
                    heatmapPixelIcon = if (it.getColumnIndex("heatmapPixelIcon") >= 0) it.getString(it.getColumnIndexOrThrow("heatmapPixelIcon")) ?: "TARGET_BULLSEYE" else "TARGET_BULLSEYE",
                    defaultStartupScreen = resolvedStartupScreen,
                    hasExplicitDailyGoal = if (it.getColumnIndex("hasExplicitDailyGoal") >= 0) it.getInt(it.getColumnIndexOrThrow("hasExplicitDailyGoal")) == 1 else false,
                    timerCategoryOrder = serializeTimerCategoryOrder(_timerCategoryOrder.value)
                )
                _userSettings.value = settings
            } else if (prefScreen != null || prefTheme != null) {
                var current = _userSettings.value
                if (prefScreen != null) current = current.copy(defaultStartupScreen = prefScreen)
                if (prefTheme != null) {
                    current = current.copy(themeMode = prefTheme)
                    ThemeManager.applyThemeMode(prefTheme)
                }
                _userSettings.value = current
            }
        }
    }

    suspend fun updateDefaultStartupScreen(screen: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeStartupScreen(screen) ?: screen
        _userSettings.value = _userSettings.value.copy(defaultStartupScreen = normalized)
        writeSavedStartupScreenPreference(normalized, context)
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("defaultStartupScreen", normalized)
            put("hasExplicitStartupScreen", 1)
        }
        db.update("users", values, "id = 'default_user'", null)
        loadUserSettings()
    }

    suspend fun updateThemeMode(mode: String) = withContext(Dispatchers.IO) {
        val normalized = ThemeManager.normalizeThemeMode(mode)
        _userSettings.value = _userSettings.value.copy(themeMode = normalized)
        ThemeManager.writeSavedThemePreference(normalized, context)
        withContext(Dispatchers.Main) {
            ThemeManager.applyThemeMode(normalized)
        }
        val db = dbHelper.writableDatabase
        val hasExplicit = if (normalized.equals("system", ignoreCase = true)) 0 else 1
        db.execSQL("UPDATE users SET themeMode = ?, hasExplicitTheme = ? WHERE id = 'default_user'", arrayOf<Any>(normalized, hasExplicit))
        loadUserSettings()
    }

    suspend fun updateAvatar(uri: String?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            if (uri != null) put("avatarUri", uri) else putNull("avatarUri")
        }
        db.update("users", values, "id = 'default_user'", null)
        loadUserSettings()
    }

    suspend fun updateTimeFormat(is24Hour: Boolean) = withContext(Dispatchers.IO) {
        _userSettings.value = _userSettings.value.copy(timeFormat24Hour = is24Hour)
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("timeFormat24Hour", if (is24Hour) 1 else 0)
        }
        db.update("users", values, "id = 'default_user'", null)
        loadUserSettings()
        loadTodayCompletions()
        loadTodaySessions()
        loadTodayTimeBlocks()
        loadTodayReflection()
    }

    suspend fun updateProfile(name: String, targetDeadline: String, currentState: String, targetIdentity: String, bioNarrative: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", name)
            put("targetDeadline", targetDeadline)
            put("currentState", currentState)
            put("targetIdentity", targetIdentity)
            put("bioNarrative", bioNarrative)
        }
        db.update("users", values, "id = 'default_user'", null)
        loadUserSettings()
    }

    suspend fun updateDailyGoal(goal: Int, setExplicit: Boolean = false) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (setExplicit) {
            db.execSQL("UPDATE users SET dailyGoal = ?, hasExplicitDailyGoal = 1 WHERE id = 'default_user'", arrayOf(goal))
        } else {
            db.execSQL("UPDATE users SET dailyGoal = ? WHERE id = 'default_user'", arrayOf(goal))
        }
        loadUserSettings()
    }

    suspend fun setHasExplicitDailyGoal(enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE users SET hasExplicitDailyGoal = ? WHERE id = 'default_user'", arrayOf(if (enabled) 1 else 0))
        loadUserSettings()
    }

    suspend fun updateUserName(name: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE users SET name = ? WHERE id = 'default_user'", arrayOf(name))
        loadUserSettings()
    }

    suspend fun updateUserSettings(settings: UserSettings) = withContext(Dispatchers.IO) {
        val normalizedTheme = ThemeManager.normalizeThemeMode(settings.themeMode)
        ThemeManager.writeSavedThemePreference(normalizedTheme, context)
        withContext(Dispatchers.Main) {
            ThemeManager.applyThemeMode(normalizedTheme)
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", settings.name)
            put("dailyGoal", settings.dailyGoalPomodoros)
            put("focusDuration", settings.focusDurationMinutes)
            put("shortBreakDuration", settings.shortBreakDurationMinutes)
            put("longBreakDuration", settings.longBreakDurationMinutes)
            put("morningReminderEnabled", if (settings.morningReminderEnabled) 1 else 0)
            put("morningReminderTime", settings.morningReminderTime)
            put("eveningReminderEnabled", if (settings.eveningReminderEnabled) 1 else 0)
            put("eveningReminderTime", settings.eveningReminderTime)
            put("themeMode", normalizedTheme)
            put("hasExplicitTheme", if (normalizedTheme.equals("system", ignoreCase = true)) 0 else 1)
            put("timeFormat24Hour", if (settings.timeFormat24Hour) 1 else 0)
            put("targetDeadline", settings.targetDeadline)
            put("currentState", settings.currentState)
            put("targetIdentity", settings.targetIdentity)
            put("bioNarrative", settings.bioNarrative)
            put("heatmapPixelIcon", settings.heatmapPixelIcon)
            put("hasExplicitDailyGoal", if (settings.hasExplicitDailyGoal) 1 else 0)
        }
        db.update("users", values, "id = 'default_user'", null)
        loadUserSettings()
    }

    suspend fun updateHeatmapPixelIcon(pixelIcon: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE users SET heatmapPixelIcon = ? WHERE id = 'default_user'", arrayOf(pixelIcon))
        loadUserSettings()
    }

    // --- Analytics Persistent Cache ---
    fun getCachedAnalytics(key: String, defaultValue: String): String {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT value FROM analytics_cache WHERE key = ? LIMIT 1", arrayOf(key))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: defaultValue
            }
        }
        return defaultValue
    }

    fun setCachedAnalytics(key: String, value: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
            put("updatedAt", System.currentTimeMillis())
        }
        db.insertWithOnConflict("analytics_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadPlanImportedTaskIds() {
        val raw = getCachedAnalytics("plan_imported_task_ids", "")
        val ids = if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _planImportedTaskIds.value = ids
    }

    suspend fun importTaskToPlan(taskId: String, date: String? = null) = withContext(Dispatchers.IO) {
        val current = _planImportedTaskIds.value.toMutableList()
        if (!current.contains(taskId)) {
            current.add(taskId)
            _planImportedTaskIds.value = current
            setCachedAnalytics("plan_imported_task_ids", current.joinToString(","))
        }
        val targetDate = date ?: getTodayString()
        val existing = _todos.value.find { it.id == taskId }
        if (existing != null && (date != null || existing.dueDate.isNullOrBlank())) {
            updateTodo(existing.copy(dueDate = targetDate))
        }
    }

    suspend fun updateTodoDueDate(todoId: String, dueDate: String?) = withContext(Dispatchers.IO) {
        val existing = _todos.value.find { it.id == todoId }
        if (existing != null && existing.dueDate != dueDate) {
            updateTodo(existing.copy(dueDate = dueDate))
        }
    }

    suspend fun removeTaskFromPlan(taskId: String) = withContext(Dispatchers.IO) {
        val current = _planImportedTaskIds.value.toMutableList()
        if (current.remove(taskId)) {
            _planImportedTaskIds.value = current
            setCachedAnalytics("plan_imported_task_ids", current.joinToString(","))
        }

        val db = dbHelper.writableDatabase
        val associatedBlockIds = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT id FROM timeBlocks WHERE todoId = ?", arrayOf(taskId))
        cursor.use {
            while (it.moveToNext()) {
                associatedBlockIds.add(it.getString(0))
            }
        }

        // Immediately delete associated schedule blocks
        db.delete("timeBlocks", "todoId = ?", arrayOf(taskId))
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)

        // Immediately and permanently cancel any linked alarms/reminders
        associatedBlockIds.forEach { blockId ->
            com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)
        }
        com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, taskId)
    }

    suspend fun reorderPlanTasks(taskIds: List<String>) = withContext(Dispatchers.IO) {
        _planImportedTaskIds.value = taskIds
        setCachedAnalytics("plan_imported_task_ids", taskIds.joinToString(","))
    }

    fun loadPlanImportedProjectIds() {
        val raw = getCachedAnalytics("plan_imported_project_ids", "__UNSET__")
        if (raw == "__UNSET__") {
            val existingIds = _projects.value.map { it.id }
            _planImportedProjectIds.value = existingIds
            if (existingIds.isNotEmpty()) {
                setCachedAnalytics("plan_imported_project_ids", existingIds.joinToString(","))
            }
        } else {
            val ids = if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            _planImportedProjectIds.value = ids
        }
    }

    suspend fun importProjectToPlan(projectId: String, date: String? = null) = withContext(Dispatchers.IO) {
        val current = _planImportedProjectIds.value.toMutableList()
        if (!current.contains(projectId)) {
            current.add(projectId)
            _planImportedProjectIds.value = current
            setCachedAnalytics("plan_imported_project_ids", current.joinToString(","))
        }
        val projectTodos = _todos.value.filter { it.projectId == projectId }
        projectTodos.forEach { todo ->
            importTaskToPlan(todo.id, date)
        }
    }

    suspend fun removeProjectFromPlan(projectId: String) = withContext(Dispatchers.IO) {
        val current = _planImportedProjectIds.value.toMutableList()
        if (current.remove(projectId)) {
            _planImportedProjectIds.value = current
            setCachedAnalytics("plan_imported_project_ids", current.joinToString(","))
        }
        val projectTodos = _todos.value.filter { it.projectId == projectId }
        projectTodos.forEach { todo ->
            removeTaskFromPlan(todo.id)
        }
    }

    fun loadCachedAnalyticsState(): AnalyticsUiState {
        val adherencePercent = getCachedAnalytics("adherencePercent", "0").toIntOrNull() ?: 0
        val completedHabitsCount = getCachedAnalytics("completedHabitsCount", "0").toIntOrNull() ?: 0
        val totalHabitsCount = getCachedAnalytics("totalHabitsCount", "0").toIntOrNull() ?: 0
        val completedTasksCount = getCachedAnalytics("completedTasksCount", "0").toIntOrNull() ?: 0
        val totalTasksCount = getCachedAnalytics("totalTasksCount", "0").toIntOrNull() ?: 0
        val totalFocusMinutes = getCachedAnalytics("totalFocusMinutes", "0").toIntOrNull() ?: 0
        val totalBreakMinutes = getCachedAnalytics("totalBreakMinutes", "0").toIntOrNull() ?: 0
        val totalFlowMinutes = getCachedAnalytics("totalFlowMinutes", "0").toIntOrNull() ?: 0
        val focusSessionsCount = getCachedAnalytics("focusSessionsCount", "0").toIntOrNull() ?: 0
        val breakSessionsCount = getCachedAnalytics("breakSessionsCount", "0").toIntOrNull() ?: 0
        val flowSessionsCount = getCachedAnalytics("flowSessionsCount", "0").toIntOrNull() ?: 0
        val completedSessionsCount = getCachedAnalytics("completedSessionsCount", "0").toIntOrNull() ?: focusSessionsCount
        val dailyGoalSessions = getCachedAnalytics("dailyGoalSessions", "4").toIntOrNull() ?: 4
        val currentStreak = getCachedAnalytics("currentStreak", "0").toIntOrNull() ?: 0
        val successRate = getCachedAnalytics("successRate", "0").toIntOrNull() ?: 0
        return AnalyticsUiState(
            isLoading = false,
            adherencePercent = adherencePercent,
            completedHabitsCount = completedHabitsCount,
            totalHabitsCount = totalHabitsCount,
            completedTasksCount = completedTasksCount,
            totalTasksCount = totalTasksCount,
            totalFocusMinutes = totalFocusMinutes,
            totalBreakMinutes = totalBreakMinutes,
            totalFlowMinutes = totalFlowMinutes,
            focusSessionsCount = focusSessionsCount,
            breakSessionsCount = breakSessionsCount,
            flowSessionsCount = flowSessionsCount,
            completedSessionsCount = completedSessionsCount,
            dailyGoalSessions = dailyGoalSessions,
            currentStreak = currentStreak,
            successRate = successRate,
            monthlyActivityMinutes = _monthlyFocusMinutes.value
        )
    }

    fun persistAnalyticsState(state: AnalyticsUiState) {
        setCachedAnalytics("adherencePercent", state.adherencePercent.toString())
        setCachedAnalytics("completedHabitsCount", state.completedHabitsCount.toString())
        setCachedAnalytics("totalHabitsCount", state.totalHabitsCount.toString())
        setCachedAnalytics("completedTasksCount", state.completedTasksCount.toString())
        setCachedAnalytics("totalTasksCount", state.totalTasksCount.toString())
        setCachedAnalytics("totalFocusMinutes", state.totalFocusMinutes.toString())
        setCachedAnalytics("totalBreakMinutes", state.totalBreakMinutes.toString())
        setCachedAnalytics("totalFlowMinutes", state.totalFlowMinutes.toString())
        setCachedAnalytics("focusSessionsCount", state.focusSessionsCount.toString())
        setCachedAnalytics("breakSessionsCount", state.breakSessionsCount.toString())
        setCachedAnalytics("flowSessionsCount", state.flowSessionsCount.toString())
        setCachedAnalytics("completedSessionsCount", state.completedSessionsCount.toString())
        setCachedAnalytics("dailyGoalSessions", state.dailyGoalSessions.toString())
        setCachedAnalytics("currentStreak", state.currentStreak.toString())
        setCachedAnalytics("successRate", state.successRate.toString())
    }

    suspend fun updateProjectLinkedNotes(projectId: String, linkedNoteIds: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE projects SET linkedNoteIds = ? WHERE id = ?", arrayOf(linkedNoteIds, projectId))
        loadProjects()
    }

    suspend fun attachNoteToProject(projectId: String, noteId: String) = withContext(Dispatchers.IO) {
        val proj = _projects.value.find { it.id == projectId } ?: return@withContext
        val currentList = if (proj.linkedNoteIds.isBlank()) mutableListOf() else proj.linkedNoteIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (!currentList.contains(noteId)) {
            currentList.add(noteId)
            updateProjectLinkedNotes(projectId, currentList.joinToString(","))
        }
    }

    suspend fun detachNoteFromProject(projectId: String, noteId: String) = withContext(Dispatchers.IO) {
        val proj = _projects.value.find { it.id == projectId } ?: return@withContext
        val currentList = if (proj.linkedNoteIds.isBlank()) mutableListOf() else proj.linkedNoteIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (currentList.contains(noteId)) {
            currentList.remove(noteId)
            updateProjectLinkedNotes(projectId, currentList.joinToString(","))
        }
    }

    suspend fun reorderProjectNotes(projectId: String, newNoteIdsList: List<String>) = withContext(Dispatchers.IO) {
        updateProjectLinkedNotes(projectId, newNoteIdsList.joinToString(","))
    }

    // --- Projects ---
    fun loadProjects() {
        val list = mutableListOf<Project>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM projects WHERE archived = 0 ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Project(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        description = it.getString(it.getColumnIndexOrThrow("description")),
                        color = it.getString(it.getColumnIndexOrThrow("color")),
                        targetPomodoros = it.getInt(it.getColumnIndexOrThrow("targetPomodoros")),
                        archived = it.getInt(it.getColumnIndexOrThrow("archived")) == 1,
                        isPaused = if (it.getColumnIndex("isPaused") >= 0) it.getInt(it.getColumnIndexOrThrow("isPaused")) == 1 else false,
                        linkedNoteIds = if (it.getColumnIndex("linkedNoteIds") >= 0) it.getString(it.getColumnIndexOrThrow("linkedNoteIds")) ?: "" else "",
                        isPinned = if (it.getColumnIndex("isPinned") >= 0) it.getInt(it.getColumnIndexOrThrow("isPinned")) == 1 else false,
                        sortOrder = if (it.getColumnIndex("sortOrder") >= 0) it.getInt(it.getColumnIndexOrThrow("sortOrder")) else 0,
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        _projects.value = list.sortedWith(
            compareByDescending<Project> { it.isPinned }
                .thenBy { it.sortOrder }
        )
        loadPlanImportedProjectIds()
    }

    suspend fun addProject(name: String, description: String?, color: String, targetPomodoros: Int, linkedNoteIds: String = "", isPinned: Boolean = false): String = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("userId", "default_user")
            put("name", com.deadline.domain.HeadlineScaler.clamp(name))
            put("description", description)
            put("color", color)
            put("targetPomodoros", targetPomodoros)
            put("archived", 0)
            put("isPaused", 0)
            put("linkedNoteIds", linkedNoteIds)
            put("isPinned", if (isPinned) 1 else 0)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("projects", null, values)
        loadProjects()
        id
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", com.deadline.domain.HeadlineScaler.clamp(project.name))
            put("description", project.description)
            put("color", project.color)
            put("targetPomodoros", project.targetPomodoros)
            put("archived", if (project.archived) 1 else 0)
            put("isPaused", if (project.isPaused) 1 else 0)
            put("linkedNoteIds", project.linkedNoteIds)
            put("isPinned", if (project.isPinned) 1 else 0)
        }
        db.update("projects", values, "id = ?", arrayOf(project.id))
        loadProjects()
    }

    suspend fun togglePinProject(projectId: String) = withContext(Dispatchers.IO) {
        val project = _projects.value.find { it.id == projectId } ?: return@withContext
        val newPinned = !project.isPinned
        val updatedProject = project.copy(isPinned = newPinned)

        // Reactive sorting:
        // Pinned projects are placed at the front,
        // and unpinning instantly re-sorts the project back to its default alphabetical position!
        val reordered = _projects.value.map {
            if (it.id == projectId) updatedProject else it
        }.sortedWith(
            compareByDescending<Project> { it.isPinned }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
        _projects.value = reordered

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("isPinned", if (newPinned) 1 else 0)
            }
            db.update("projects", values, "id = ?", arrayOf(projectId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadProjects()
    }

    suspend fun togglePauseProject(projectId: String) = withContext(Dispatchers.IO) {
        val project = _projects.value.find { it.id == projectId } ?: return@withContext
        val newPaused = !project.isPaused
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE projects SET isPaused = ? WHERE id = ?", arrayOf(if (newPaused) "1" else "0", projectId))
        loadProjects()
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val project = _projects.value.find { it.id == projectId }
        val originalIndex = _projects.value.indexOfFirst { it.id == projectId }
        val associatedTodos = _todos.value.filter { it.projectId == projectId }
        val associatedTimeBlocks = _todayTimeBlocks.value.filter { it.projectId == projectId }
        val associatedPlanIds = _planImportedTaskIds.value.filter { id -> associatedTodos.any { it.id == id } }
        val wasInPlan = _planImportedProjectIds.value.contains(projectId)

        val db = dbHelper.writableDatabase
        val allAssociatedBlockIds = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT id FROM timeBlocks WHERE projectId = ?", arrayOf(projectId))
        cursor.use {
            while (it.moveToNext()) {
                allAssociatedBlockIds.add(it.getString(0))
            }
        }
        db.delete("projects", "id = ?", arrayOf(projectId))
        db.delete("projectTodos", "projectId = ?", arrayOf(projectId))
        db.delete("timeBlocks", "projectId = ?", arrayOf(projectId))
        allAssociatedBlockIds.forEach { blockId ->
            com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)
        }
        associatedTodos.forEach { todo ->
            com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, todo.id)
        }
        loadProjects()
        loadTodos()
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)
        val remainingTodoIds = _todos.value.map { it.id }.toSet()
        val updatedPlanTaskIds = _planImportedTaskIds.value.filter { it in remainingTodoIds }
        if (updatedPlanTaskIds.size != _planImportedTaskIds.value.size) {
            _planImportedTaskIds.value = updatedPlanTaskIds
            setCachedAnalytics("plan_imported_task_ids", updatedPlanTaskIds.joinToString(","))
        }

        val remainingProjectIds = _projects.value.map { it.id }.toSet()
        val updatedPlanProjectIds = _planImportedProjectIds.value.filter { it in remainingProjectIds }
        if (updatedPlanProjectIds.size != _planImportedProjectIds.value.size) {
            _planImportedProjectIds.value = updatedPlanProjectIds
            setCachedAnalytics("plan_imported_project_ids", updatedPlanProjectIds.joinToString(","))
        }

        if (project != null) {
            pushUndoPayload(
                UndoPayload.ProjectPayload(
                    project = project,
                    originalIndex = originalIndex,
                    associatedTodos = associatedTodos,
                    associatedTimeBlocks = associatedTimeBlocks,
                    planImportedIds = associatedPlanIds,
                    wasInPlan = wasInPlan
                )
            )
        }
    }

    suspend fun reorderProjects(projects: List<Project>) = withContext(Dispatchers.IO) {
        _projects.value = projects
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            projects.forEachIndexed { index, project ->
                val values = ContentValues().apply {
                    put("sortOrder", index)
                }
                db.update("projects", values, "id = ?", arrayOf(project.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadProjects()
    }

    // --- Todos ---
    fun loadTodos() {
        val list = mutableListOf<ProjectTodo>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM projectTodos ORDER BY sortOrder ASC, createdAt DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                val focusDur = if (it.getColumnIndex("focusDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("focusDuration")) else 1500
                val breakDur = if (it.getColumnIndex("breakDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("breakDuration")) else 300
                val flowDur = if (it.getColumnIndex("flowDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("flowDuration")) else 900
                val preset = ItemTimerPreset(
                    focusDuration = if (focusDur > 0) focusDur else 1500,
                    breakDuration = if (breakDur > 0) breakDur else 300,
                    flowDuration = if (flowDur > 0) flowDur else 900
                )
                list.add(
                    ProjectTodo(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        projectId = it.getString(it.getColumnIndexOrThrow("projectId")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        description = it.getString(it.getColumnIndexOrThrow("description")),
                        estimatedMinutes = it.getInt(it.getColumnIndexOrThrow("estimatedMinutes")),
                        loggedMinutes = it.getInt(it.getColumnIndexOrThrow("loggedMinutes")),
                        priority = try { TaskPriority.valueOf(it.getString(it.getColumnIndexOrThrow("priority"))) } catch (_: Exception) { TaskPriority.MEDIUM },
                        status = try { TaskStatus.valueOf(it.getString(it.getColumnIndexOrThrow("status"))) } catch (_: Exception) { TaskStatus.TODO },
                        dueDate = it.getString(it.getColumnIndexOrThrow("dueDate")),
                        sortOrder = if (it.getColumnIndex("sortOrder") >= 0) it.getInt(it.getColumnIndexOrThrow("sortOrder")) else 0,
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt")),
                        timerPreset = preset
                    )
                )
            }
        }
        _todos.value = list
    }

    suspend fun addTodo(title: String, projectId: String, priority: TaskPriority, estimatedMinutes: Int, dueDate: String?): String = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val id = UUID.randomUUID().toString()
        val nextOrder = (_todos.value.filter { it.projectId == projectId }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val values = ContentValues().apply {
            put("id", id)
            put("userId", "default_user")
            put("projectId", projectId)
            put("title", com.deadline.domain.HeadlineScaler.clamp(title))
            put("estimatedMinutes", estimatedMinutes)
            put("loggedMinutes", 0)
            put("priority", priority.name)
            put("status", TaskStatus.TODO.name)
            put("dueDate", dueDate)
            put("sortOrder", nextOrder)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("projectTodos", null, values)
        loadTodos()
        id
    }

    suspend fun toggleTodoStatus(todoId: String) = withContext(Dispatchers.IO) {
        val targetId = if (_todos.value.any { it.id == todoId }) todoId else todoId.removePrefix("task_")
        val current = _todos.value.find { it.id == targetId } ?: return@withContext
        val newStatus = if (current.status == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED
        _todos.value = _todos.value.map {
            if (it.id == targetId) it.copy(status = newStatus) else it
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", newStatus.name)
        }
        db.update("projectTodos", values, "id = ?", arrayOf(targetId))
        loadTodos()
        syncTodayProgressHelper()
    }

    suspend fun updateTodoStatus(todoId: String, status: TaskStatus) = withContext(Dispatchers.IO) {
        val targetId = if (_todos.value.any { it.id == todoId }) todoId else todoId.removePrefix("task_")
        _todos.value = _todos.value.map {
            if (it.id == targetId) it.copy(status = status) else it
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status.name)
        }
        db.update("projectTodos", values, "id = ?", arrayOf(targetId))
        loadTodos()
        syncTodayProgressHelper()
    }

    suspend fun completeTodo(todoId: String) = updateTodoStatus(todoId, TaskStatus.COMPLETED)

    suspend fun deleteTodo(todoId: String) = withContext(Dispatchers.IO) {
        val todo = _todos.value.find { it.id == todoId }
        val originalIndex = _todos.value.indexOfFirst { it.id == todoId }
        val wasInPlan = _planImportedTaskIds.value.contains(todoId)
        val associatedTimeBlocks = _todayTimeBlocks.value.filter { it.todoId == todoId }

        val db = dbHelper.writableDatabase
        val allAssociatedBlockIds = mutableListOf<String>()
        val cursor = db.rawQuery("SELECT id FROM timeBlocks WHERE todoId = ?", arrayOf(todoId))
        cursor.use {
            while (it.moveToNext()) {
                allAssociatedBlockIds.add(it.getString(0))
            }
        }
        db.delete("projectTodos", "id = ?", arrayOf(todoId))
        db.delete("timeBlocks", "todoId = ?", arrayOf(todoId))
        allAssociatedBlockIds.forEach { blockId ->
            com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)
        }
        com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, todoId)
        loadTodos()
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)
        syncTodayProgressHelper()
        val updatedPlanTaskIds = _planImportedTaskIds.value.filter { it != todoId }
        if (updatedPlanTaskIds.size != _planImportedTaskIds.value.size) {
            _planImportedTaskIds.value = updatedPlanTaskIds
            setCachedAnalytics("plan_imported_task_ids", updatedPlanTaskIds.joinToString(","))
        }

        if (todo != null) {
            pushUndoPayload(
                UndoPayload.TodoPayload(
                    todo = todo,
                    originalIndex = originalIndex,
                    wasInPlan = wasInPlan,
                    associatedTimeBlocks = associatedTimeBlocks
                )
            )
        }
    }

    suspend fun updateTodo(todo: ProjectTodo) = withContext(Dispatchers.IO) {
        _todos.value = _todos.value.map { if (it.id == todo.id) todo else it }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", com.deadline.domain.HeadlineScaler.clamp(todo.title))
            put("description", todo.description)
            put("priority", todo.priority.name)
            put("status", todo.status.name)
            put("estimatedMinutes", todo.estimatedMinutes)
            put("dueDate", todo.dueDate)
            put("sortOrder", todo.sortOrder)
            todo.timerPreset?.let { preset ->
                put("focusDuration", preset.focusDuration)
                put("breakDuration", preset.breakDuration)
                put("flowDuration", preset.flowDuration)
            }
        }
        db.update("projectTodos", values, "id = ?", arrayOf(todo.id))
        loadTodos()
        syncTodayProgressHelper()
    }

    suspend fun updateTodoTimerPreset(todoId: String, preset: ItemTimerPreset) = withContext(Dispatchers.IO) {
        val cleanId = todoId.removePrefix("task_")
        _todos.value = _todos.value.map {
            if (it.id == cleanId || it.id == todoId) it.copy(timerPreset = preset) else it
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("focusDuration", preset.focusDuration)
            put("breakDuration", preset.breakDuration)
            put("flowDuration", preset.flowDuration)
        }
        db.update("projectTodos", values, "id = ?", arrayOf(cleanId))
        loadTodos()
    }

    suspend fun reorderTodos(todos: List<ProjectTodo>) = withContext(Dispatchers.IO) {
        val taskIds = todos.map { it.id }.toSet()
        val currentOther = _todos.value.filter { it.id !in taskIds }
        val updatedTodos = todos.mapIndexed { idx, t -> t.copy(sortOrder = idx) }
        _todos.value = currentOther + updatedTodos
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            todos.forEachIndexed { index, todo ->
                val values = ContentValues().apply {
                    put("sortOrder", index)
                }
                db.update("projectTodos", values, "id = ?", arrayOf(todo.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadTodos()
    }

    // --- Habits ---
    fun loadHabits() {
        val list = mutableListOf<Habit>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM habits WHERE status = 'active' ORDER BY position ASC, createdAt ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                val focusDur = if (it.getColumnIndex("focusDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("focusDuration")) else 1500
                val breakDur = if (it.getColumnIndex("breakDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("breakDuration")) else 300
                val flowDur = if (it.getColumnIndex("flowDuration") >= 0) it.getInt(it.getColumnIndexOrThrow("flowDuration")) else 900
                val preset = ItemTimerPreset(
                    focusDuration = if (focusDur > 0) focusDur else 1500,
                    breakDuration = if (breakDur > 0) breakDur else 300,
                    flowDuration = if (flowDur > 0) flowDur else 900
                )
                list.add(
                    Habit(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        emoji = it.getString(it.getColumnIndexOrThrow("emoji")),
                        color = it.getString(it.getColumnIndexOrThrow("color")),
                        schedule = it.getString(it.getColumnIndexOrThrow("schedule")),
                        completionType = try { HabitCompletionType.valueOf(it.getString(it.getColumnIndexOrThrow("completionType"))) } catch (_: Exception) { HabitCompletionType.MANUAL },
                        targetCount = it.getInt(it.getColumnIndexOrThrow("targetCount")),
                        countUnit = it.getString(it.getColumnIndexOrThrow("countUnit")),
                        pomodoroThreshold = it.getInt(it.getColumnIndexOrThrow("pomodoroThreshold")),
                        currentStreak = it.getInt(it.getColumnIndexOrThrow("currentStreak")),
                        maxStreak = it.getInt(it.getColumnIndexOrThrow("maxStreak")),
                        lastCompletedDate = it.getString(it.getColumnIndexOrThrow("lastCompletedDate")),
                        position = it.getInt(it.getColumnIndexOrThrow("position")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt")),
                        timerPreset = preset
                    )
                )
            }
        }
        _habits.value = list
    }

    fun loadTodayCompletions() {
        val today = getTodayString()
        val map = mutableMapOf<String, HabitCompletion>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM habitCompletions WHERE date = ?", arrayOf(today))
        cursor.use {
            while (it.moveToNext()) {
                val completion = HabitCompletion(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    userId = it.getString(it.getColumnIndexOrThrow("userId")),
                    habitId = it.getString(it.getColumnIndexOrThrow("habitId")),
                    date = it.getString(it.getColumnIndexOrThrow("date")),
                    completedAt = it.getLong(it.getColumnIndexOrThrow("completedAt")),
                    count = it.getInt(it.getColumnIndexOrThrow("count")),
                    isSkip = it.getInt(it.getColumnIndexOrThrow("isSkip")) == 1
                )
                map[completion.habitId] = completion
            }
        }
        _todayCompletions.value = map
        loadMonthHabitCompletions()
    }

    fun loadMonthHabitCompletions() {
        val map = mutableMapOf<String, MutableSet<String>>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT habitId, date FROM habitCompletions WHERE isSkip = 0 AND count > 0",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val hId = it.getString(0)
                val dStr = it.getString(1)
                map.getOrPut(hId) { mutableSetOf() }.add(dStr)
            }
        }
        _monthHabitCompletions.value = map
    }

    suspend fun toggleHabitCompletion(habitId: String) = withContext(Dispatchers.IO) {
        val today = getTodayString()
        val current = _todayCompletions.value[habitId]
        val db = dbHelper.writableDatabase

        if (current != null && !current.isSkip) {
            // Delete completion
            db.delete("habitCompletions", "id = ?", arrayOf(current.id))
        } else {
            // Insert or update
            val values = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("userId", "default_user")
                put("habitId", habitId)
                put("date", today)
                put("completedAt", System.currentTimeMillis())
                put("count", 1)
                put("isSkip", 0)
            }
            db.insertWithOnConflict("habitCompletions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            // Increment streak
            db.execSQL("UPDATE habits SET currentStreak = currentStreak + 1, lastCompletedDate = ? WHERE id = ?", arrayOf(today, habitId))
        }
        loadHabits()
        loadTodayCompletions()
        syncTodayProgressHelper()
    }

    suspend fun toggleHabitSkip(habitId: String) = withContext(Dispatchers.IO) {
        val today = getTodayString()
        val current = _todayCompletions.value[habitId]
        val db = dbHelper.writableDatabase

        if (current != null && current.isSkip) {
            db.delete("habitCompletions", "id = ?", arrayOf(current.id))
        } else {
            val values = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("userId", "default_user")
                put("habitId", habitId)
                put("date", today)
                put("completedAt", System.currentTimeMillis())
                put("count", 0)
                put("isSkip", 1)
            }
            db.insertWithOnConflict("habitCompletions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        loadHabits()
        loadTodayCompletions()
        syncTodayProgressHelper()
    }

    suspend fun updateHabitCount(habitId: String, delta: Int) = withContext(Dispatchers.IO) {
        val today = getTodayString()
        val current = _todayCompletions.value[habitId]
        val currentCount = current?.count ?: 0
        val newCount = (currentCount + delta).coerceAtLeast(0)
        val db = dbHelper.writableDatabase

        if (newCount == 0 && current != null) {
            db.delete("habitCompletions", "id = ?", arrayOf(current.id))
        } else {
            val values = ContentValues().apply {
                put("id", current?.id ?: UUID.randomUUID().toString())
                put("userId", "default_user")
                put("habitId", habitId)
                put("date", today)
                put("completedAt", System.currentTimeMillis())
                put("count", newCount)
                put("isSkip", 0)
            }
            db.insertWithOnConflict("habitCompletions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        loadHabits()
        loadTodayCompletions()
        syncTodayProgressHelper()
    }

    suspend fun addHabit(
        name: String,
        emoji: String,
        color: String,
        completionType: HabitCompletionType,
        targetCount: Int,
        countUnit: String?
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("userId", "default_user")
            put("name", name)
            put("emoji", emoji)
            put("color", color)
            put("completionType", completionType.name)
            put("targetCount", targetCount)
            put("countUnit", countUnit)
            put("currentStreak", 0)
            put("maxStreak", 0)
            put("position", _habits.value.size)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("habits", null, values)
        loadHabits()
    }

    suspend fun deleteHabit(habitId: String) = withContext(Dispatchers.IO) {
        val habit = _habits.value.find { it.id == habitId }
        val originalIndex = _habits.value.indexOfFirst { it.id == habitId }

        val db = dbHelper.writableDatabase
        val completions = mutableListOf<HabitCompletion>()
        val compCursor = db.rawQuery("SELECT * FROM habitCompletions WHERE habitId = ?", arrayOf(habitId))
        compCursor.use {
            while (it.moveToNext()) {
                completions.add(
                    HabitCompletion(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        habitId = it.getString(it.getColumnIndexOrThrow("habitId")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        count = it.getInt(it.getColumnIndexOrThrow("count")),
                        isSkip = it.getInt(it.getColumnIndexOrThrow("isSkip")) == 1,
                        completedAt = it.getLong(it.getColumnIndexOrThrow("completedAt"))
                    )
                )
            }
        }

        db.delete("habits", "id = ?", arrayOf(habitId))
        db.delete("habitCompletions", "habitId = ?", arrayOf(habitId))
        loadHabits()
        loadTodayCompletions()

        if (habit != null) {
            pushUndoPayload(
                UndoPayload.HabitPayload(
                    habit = habit,
                    originalIndex = originalIndex,
                    completions = completions
                )
            )
        }
    }

    suspend fun updateHabit(habit: Habit) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", habit.name)
            put("emoji", habit.emoji)
            put("color", habit.color)
            put("targetCount", habit.targetCount)
            put("countUnit", habit.countUnit)
            habit.timerPreset?.let { preset ->
                put("focusDuration", preset.focusDuration)
                put("breakDuration", preset.breakDuration)
                put("flowDuration", preset.flowDuration)
            }
        }
        db.update("habits", values, "id = ?", arrayOf(habit.id))
        loadHabits()
    }

    suspend fun updateHabitTimerPreset(habitId: String, preset: ItemTimerPreset) = withContext(Dispatchers.IO) {
        _habits.value = _habits.value.map {
            if (it.id == habitId) it.copy(timerPreset = preset) else it
        }
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("focusDuration", preset.focusDuration)
            put("breakDuration", preset.breakDuration)
            put("flowDuration", preset.flowDuration)
        }
        db.update("habits", values, "id = ?", arrayOf(habitId))
        loadHabits()
    }

    suspend fun updateItemTimerPreset(itemId: String, preset: ItemTimerPreset) = withContext(Dispatchers.IO) {
        val cleanId = itemId.removePrefix("task_")
        _todos.value = _todos.value.map {
            if (it.id == itemId || it.id == cleanId) it.copy(timerPreset = preset) else it
        }
        _habits.value = _habits.value.map {
            if (it.id == itemId) it.copy(timerPreset = preset) else it
        }
        if (itemId.startsWith("task_")) {
            updateTodoTimerPreset(cleanId, preset)
        } else if (_habits.value.any { it.id == itemId }) {
            updateHabitTimerPreset(itemId, preset)
        } else if (_todos.value.any { it.id == itemId || it.id == cleanId }) {
            updateTodoTimerPreset(cleanId, preset)
        } else {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("focusDuration", preset.focusDuration)
                put("breakDuration", preset.breakDuration)
                put("flowDuration", preset.flowDuration)
            }
            db.update("projectTodos", values, "id = ?", arrayOf(cleanId))
            db.update("habits", values, "id = ?", arrayOf(itemId))
            loadTodos()
            loadHabits()
        }
    }

    suspend fun reorderHabits(habits: List<Habit>) = withContext(Dispatchers.IO) {
        val habitIds = habits.map { it.id }.toSet()
        val currentOther = _habits.value.filter { it.id !in habitIds }
        val updatedHabits = habits.mapIndexed { idx, h -> h.copy(position = idx) }
        _habits.value = currentOther + updatedHabits
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            habits.forEachIndexed { index, habit ->
                val values = ContentValues().apply {
                    put("position", index)
                }
                db.update("habits", values, "id = ?", arrayOf(habit.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadHabits()
    }

    // --- Pomodoro Sessions ---
    fun loadTodaySessions() {
        val list = mutableListOf<PomodoroSession>()
        val db = dbHelper.readableDatabase
        // Today range: start of today ms
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        val startOfToday = cal.timeInMillis

        val cursor = db.rawQuery("SELECT * FROM pomodoroSessions WHERE startedAt >= ? ORDER BY startedAt DESC", arrayOf(startOfToday.toString()))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PomodoroSession(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        projectId = it.getString(it.getColumnIndexOrThrow("projectId")),
                        todoId = it.getString(it.getColumnIndexOrThrow("todoId")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        sessionType = try { PomodoroType.valueOf(it.getString(it.getColumnIndexOrThrow("sessionType"))) } catch (_: Exception) { PomodoroType.FOCUS },
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow("duration")),
                        startedAt = it.getLong(it.getColumnIndexOrThrow("startedAt")),
                        endedAt = it.getLong(it.getColumnIndexOrThrow("endedAt")),
                        completed = it.getInt(it.getColumnIndexOrThrow("completed")) == 1,
                        note = it.getString(it.getColumnIndexOrThrow("note")),
                        quality = it.getString(it.getColumnIndexOrThrow("quality"))
                    )
                )
            }
        }
        _todaySessions.value = list
    }

    suspend fun recordPomodoroSession(session: PomodoroSession) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", session.id)
            put("userId", session.userId)
            put("projectId", session.projectId)
            put("tagId", session.tagId)
            put("todoId", session.todoId)
            put("title", session.title)
            put("sessionType", session.sessionType.name)
            put("duration", session.durationMinutes)
            put("startedAt", session.startedAt)
            put("endedAt", session.endedAt ?: System.currentTimeMillis())
            put("completed", if (session.completed) 1 else 0)
            put("pausedDuration", session.pausedDurationSeconds)
            put("note", session.note)
            put("quality", session.quality)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("pomodoroSessions", null, values)

        // If linked to a todo, update its loggedMinutes
        if (session.todoId != null && session.completed) {
            db.execSQL("UPDATE projectTodos SET loggedMinutes = loggedMinutes + ? WHERE id = ?", arrayOf<Any>(session.durationMinutes, session.todoId))
            loadTodos()
        }

        loadTodaySessions()
        syncTodayProgressHelper()
    }

    suspend fun savePomodoroSession(session: PomodoroSession) = recordPomodoroSession(session)

    // --- TimeBlocks & Timeline Architecture ---
    fun setSelectedPlanDate(dateStr: String) {
        if (_selectedPlanDate.value == dateStr && _timelineTimeBlocks.value.isNotEmpty()) return
        _selectedPlanDate.value = dateStr
        loadTimelineTimeBlocks(dateStr)
    }

    fun loadTimelineTimeBlocks(dateStr: String = _selectedPlanDate.value.ifBlank { getTodayString() }) {
        val targetDate = dateStr.ifBlank { getTodayString() }
        val list = mutableListOf<TimeBlock>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM timeBlocks WHERE date = ? ORDER BY startMinute ASC", arrayOf(targetDate))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TimeBlock(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        startMinute = it.getInt(it.getColumnIndexOrThrow("startMinute")),
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow("durationMinutes")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        color = it.getString(it.getColumnIndexOrThrow("color")),
                        projectId = it.getString(it.getColumnIndexOrThrow("projectId")),
                        todoId = it.getString(it.getColumnIndexOrThrow("todoId")),
                        repeatRule = it.getString(it.getColumnIndexOrThrow("repeatRule"))
                    )
                )
            }
        }
        _timelineTimeBlocks.value = list
        if (targetDate == getTodayString()) {
            _todayTimeBlocks.value = list
        }
    }

    fun isTimeInPast(dateStr: String, startMinute: Int): Boolean {
        val scheduledTimeMillis = com.deadline.service.TaskReminderScheduler.calculateScheduledTimestamp(dateStr, startMinute)
        return scheduledTimeMillis <= System.currentTimeMillis()
    }

    fun loadTodayTimeBlocks() {
        val today = getTodayString()
        val list = mutableListOf<TimeBlock>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM timeBlocks WHERE date = ? ORDER BY startMinute ASC", arrayOf(today))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TimeBlock(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        date = it.getString(it.getColumnIndexOrThrow("date")),
                        startMinute = it.getInt(it.getColumnIndexOrThrow("startMinute")),
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow("durationMinutes")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        color = it.getString(it.getColumnIndexOrThrow("color")),
                        projectId = it.getString(it.getColumnIndexOrThrow("projectId")),
                        todoId = it.getString(it.getColumnIndexOrThrow("todoId")),
                        repeatRule = it.getString(it.getColumnIndexOrThrow("repeatRule"))
                    )
                )
            }
        }
        _todayTimeBlocks.value = list
        val currentPlanDate = _selectedPlanDate.value.ifBlank { today }
        if (currentPlanDate == today) {
            _timelineTimeBlocks.value = list
        } else {
            loadTimelineTimeBlocks(currentPlanDate)
        }
        list.forEach { block ->
            com.deadline.service.TaskReminderScheduler.scheduleTaskReminders(
                context = context,
                taskId = block.id,
                taskTitle = block.title,
                dateStr = block.date,
                startMinute = block.startMinute
            )
        }
    }

    suspend fun addTimeBlock(
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        color: String,
        projectId: String?,
        todoId: String? = null,
        date: String? = null,
        allowPast: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val targetDateStr = date ?: _selectedPlanDate.value.ifBlank { getTodayString() }

        // Real-Time Scheduling Constraints: Only future dates and times can be scheduled
        if (!allowPast && isTimeInPast(targetDateStr, startMinute)) {
            return@withContext
        }

        val id = UUID.randomUUID().toString()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("userId", "default_user")
            put("date", targetDateStr)
            put("startMinute", startMinute)
            put("durationMinutes", durationMinutes)
            put("title", com.deadline.domain.HeadlineScaler.clamp(title))
            put("color", color)
            put("projectId", projectId)
            put("todoId", todoId)
            put("repeatRule", "none")
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("timeBlocks", null, values)
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)

        // Two-Stage Scheduled Task Reminder Protocol (Pre-Scheduled Alert & At-Time Mandatory Alarm)
        com.deadline.service.TaskReminderScheduler.scheduleTaskReminders(
            context = context,
            taskId = id,
            taskTitle = title,
            dateStr = targetDateStr,
            startMinute = startMinute
        )
    }

    suspend fun deleteTimeBlock(blockId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var associatedTodoId: String? = null
        val cursor = db.rawQuery("SELECT todoId FROM timeBlocks WHERE id = ?", arrayOf(blockId))
        cursor.use {
            if (it.moveToFirst()) {
                associatedTodoId = it.getString(0)
            }
        }
        db.delete("timeBlocks", "id = ?", arrayOf(blockId))
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)

        // Immediately and permanently cancel any linked alarms/reminders
        com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, blockId)
        associatedTodoId?.let { todoId ->
            com.deadline.service.TaskReminderScheduler.cancelTaskReminders(context, todoId)
        }
    }

    suspend fun updateTimeBlock(block: TimeBlock) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", block.title)
            put("startMinute", block.startMinute)
            put("durationMinutes", block.durationMinutes)
            put("color", block.color)
            put("projectId", block.projectId)
            put("todoId", block.todoId)
            put("repeatRule", block.repeatRule)
        }
        db.update("timeBlocks", values, "id = ?", arrayOf(block.id))
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)

        com.deadline.service.TaskReminderScheduler.scheduleTaskReminders(
            context = context,
            taskId = block.id,
            taskTitle = block.title,
            dateStr = block.date,
            startMinute = block.startMinute
        )
    }

    suspend fun reorderTimeBlocks(blocks: List<TimeBlock>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            blocks.forEach { block ->
                val values = ContentValues().apply {
                    put("startMinute", block.startMinute)
                }
                db.update("timeBlocks", values, "id = ?", arrayOf(block.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadTodayTimeBlocks()
        loadTimelineTimeBlocks(_selectedPlanDate.value)
    }

    // --- Daily Reflections ---
    fun loadTodayReflection() {
        val today = getTodayString()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM dailyReflections WHERE date = ? LIMIT 1", arrayOf(today))
        cursor.use {
            if (it.moveToFirst()) {
                _todayReflection.value = DailyReflection(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    userId = it.getString(it.getColumnIndexOrThrow("userId")),
                    date = it.getString(it.getColumnIndexOrThrow("date")),
                    mood = it.getInt(it.getColumnIndexOrThrow("mood")),
                    energyLevel = it.getInt(it.getColumnIndexOrThrow("energyLevel")),
                    win = it.getString(it.getColumnIndexOrThrow("win")),
                    challenge = it.getString(it.getColumnIndexOrThrow("challenge")),
                    gratitude = it.getString(it.getColumnIndexOrThrow("gratitude")),
                    tomorrowIntent = it.getString(it.getColumnIndexOrThrow("tomorrowIntent")),
                    totalFocusMinutes = it.getInt(it.getColumnIndexOrThrow("totalFocusMinutes")),
                    habitsCompleted = it.getInt(it.getColumnIndexOrThrow("habitsCompleted")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                )
            } else {
                _todayReflection.value = null
            }
        }
    }

    suspend fun saveDailyReflection(reflection: DailyReflection) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", reflection.id)
            put("userId", reflection.userId)
            put("date", reflection.date)
            put("mood", reflection.mood)
            put("energyLevel", reflection.energyLevel)
            put("win", reflection.win)
            put("challenge", reflection.challenge)
            put("gratitude", reflection.gratitude)
            put("tomorrowIntent", reflection.tomorrowIntent)
            put("totalFocusMinutes", reflection.totalFocusMinutes)
            put("habitsCompleted", reflection.habitsCompleted)
            put("createdAt", reflection.createdAt)
        }
        db.insertWithOnConflict("dailyReflections", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        loadTodayReflection()
    }

    // --- Habit History (for 365-day Heatmap) ---
    suspend fun getHabitHistory(habitId: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Int>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT date, count, isSkip FROM habitCompletions WHERE habitId = ? ORDER BY date ASC",
            arrayOf(habitId)
        )
        cursor.use {
            while (it.moveToNext()) {
                val date = it.getString(0)
                val count = it.getInt(1)
                val isSkip = it.getInt(2) == 1
                map[date] = if (isSkip) -1 else count
            }
        }
        map
    }

    // --- Quick Notes (Apple Notes System) ---
    fun loadQuickNotes() {
        val list = mutableListOf<QuickNote>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM quickNotes ORDER BY isPinned DESC, sortOrder ASC, updatedAt DESC, createdAt DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    QuickNote(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        content = it.getString(it.getColumnIndexOrThrow("content")),
                        noteType = if (it.getColumnIndex("noteType") >= 0) it.getString(it.getColumnIndexOrThrow("noteType")) ?: "NOTE" else "NOTE",
                        isPinned = if (it.getColumnIndex("isPinned") >= 0) it.getInt(it.getColumnIndexOrThrow("isPinned")) == 1 else false,
                        isChecklist = if (it.getColumnIndex("isChecklist") >= 0) it.getInt(it.getColumnIndexOrThrow("isChecklist")) == 1 else false,
                        fontSize = if (it.getColumnIndex("fontSize") >= 0) it.getInt(it.getColumnIndexOrThrow("fontSize")) else 16,
                        textColor = if (it.getColumnIndex("textColor") >= 0) it.getString(it.getColumnIndexOrThrow("textColor")) ?: "DEFAULT" else "DEFAULT",
                        alignment = if (it.getColumnIndex("alignment") >= 0) it.getString(it.getColumnIndexOrThrow("alignment")) ?: "LEFT" else "LEFT",
                        sortOrder = if (it.getColumnIndex("sortOrder") >= 0) it.getInt(it.getColumnIndexOrThrow("sortOrder")) else 0,
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt")),
                        updatedAt = if (it.getColumnIndex("updatedAt") >= 0) it.getLong(it.getColumnIndexOrThrow("updatedAt")) else it.getLong(it.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        _quickNotes.value = list
    }

    suspend fun addQuickNote(
        title: String,
        content: String,
        noteType: String = "NOTE",
        isPinned: Boolean = false,
        isChecklist: Boolean = false,
        fontSize: Int = 16,
        textColor: String = "DEFAULT",
        alignment: String = "LEFT",
        sortOrder: Int = 0
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("userId", "default_user")
            put("title", com.deadline.domain.HeadlineScaler.clamp(title))
            put("content", content)
            put("noteType", noteType)
            put("isPinned", if (isPinned) 1 else 0)
            put("isChecklist", if (isChecklist) 1 else 0)
            put("fontSize", fontSize)
            put("textColor", textColor)
            put("alignment", alignment)
            put("sortOrder", sortOrder)
            put("createdAt", now)
            put("updatedAt", now)
        }
        db.insert("quickNotes", null, values)
        loadQuickNotes()
    }

    suspend fun updateQuickNote(note: QuickNote) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", com.deadline.domain.HeadlineScaler.clamp(note.title))
            put("content", note.content)
            put("noteType", note.noteType)
            put("isPinned", if (note.isPinned) 1 else 0)
            put("isChecklist", if (note.isChecklist) 1 else 0)
            put("fontSize", note.fontSize)
            put("textColor", note.textColor)
            put("alignment", note.alignment)
            put("sortOrder", note.sortOrder)
            put("updatedAt", System.currentTimeMillis())
        }
        db.update("quickNotes", values, "id = ?", arrayOf(note.id))
        loadQuickNotes()
    }

    suspend fun reorderQuickNotes(notes: List<QuickNote>) = withContext(Dispatchers.IO) {
        val noteIds = notes.map { it.id }.toSet()
        val currentOther = _quickNotes.value.filter { it.id !in noteIds }
        val updatedNotes = notes.mapIndexed { idx, n -> n.copy(sortOrder = idx) }
        _quickNotes.value = (currentOther + updatedNotes).sortedWith(
            compareByDescending<QuickNote> { it.isPinned }.thenBy { it.sortOrder }
        )
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            notes.forEachIndexed { index, note ->
                val values = ContentValues().apply {
                    put("sortOrder", index)
                }
                db.update("quickNotes", values, "id = ?", arrayOf(note.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadQuickNotes()
    }

    suspend fun transferQuickNoteCategory(noteId: String, newNoteType: String) = withContext(Dispatchers.IO) {
        val current = _quickNotes.value.find { it.id == noteId } ?: return@withContext
        val now = System.currentTimeMillis()
        val updatedNote = current.copy(
            noteType = newNoteType,
            isPinned = if (newNoteType.equals("IMPORTANT", true)) true else current.isPinned,
            isChecklist = if (newNoteType.equals("ACTIVE", true)) true else current.isChecklist,
            updatedAt = now
        )
        // Optimistic update for zero-latency reactive transitions
        _quickNotes.value = _quickNotes.value.map { if (it.id == noteId) updatedNote else it }

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("noteType", newNoteType)
            if (newNoteType.equals("IMPORTANT", true)) {
                put("isPinned", 1)
            }
            if (newNoteType.equals("ACTIVE", true)) {
                put("isChecklist", 1)
            }
            put("updatedAt", now)
        }
        db.update("quickNotes", values, "id = ?", arrayOf(noteId))
        loadQuickNotes()
    }

    suspend fun togglePinNote(noteId: String) = withContext(Dispatchers.IO) {
        val current = _quickNotes.value.find { it.id == noteId } ?: return@withContext
        val newPinned = !current.isPinned
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("isPinned", if (newPinned) 1 else 0)
            put("updatedAt", System.currentTimeMillis())
        }
        db.update("quickNotes", values, "id = ?", arrayOf(noteId))
        loadQuickNotes()
    }

    suspend fun deleteQuickNote(id: String) = withContext(Dispatchers.IO) {
        val note = _quickNotes.value.find { it.id == id }
        val originalIndex = _quickNotes.value.indexOfFirst { it.id == id }

        val db = dbHelper.writableDatabase
        db.delete("quickNotes", "id = ?", arrayOf(id))
        loadQuickNotes()

        if (note != null) {
            pushUndoPayload(
                UndoPayload.NotePayload(
                    note = note,
                    originalIndex = originalIndex
                )
            )
        }
    }

    // --- Knowledge Articles (Custom User CMS) ---
    fun loadKnowledgeArticles() {
        val list = mutableListOf<KnowledgeArticle>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM knowledgeArticles ORDER BY createdAt DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    KnowledgeArticle(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        headline = it.getString(it.getColumnIndexOrThrow("headline")),
                        body = it.getString(it.getColumnIndexOrThrow("body")),
                        tags = it.getString(it.getColumnIndexOrThrow("tags")),
                        imageUri = it.getString(it.getColumnIndexOrThrow("imageUri")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        _knowledgeArticles.value = list
    }

    suspend fun addKnowledgeArticle(headline: String, body: String, tags: String, imageUri: String?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("userId", "default_user")
            put("headline", headline)
            put("body", body)
            put("tags", tags)
            put("imageUri", imageUri)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("knowledgeArticles", null, values)
        loadKnowledgeArticles()
    }

    suspend fun deleteKnowledgeArticle(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("knowledgeArticles", "id = ?", arrayOf(id))
        loadKnowledgeArticles()
    }

    // --- Focus Tasks (Task List & Duration Indicators) ---
    fun loadFocusTasks() {
        val list = mutableListOf<FocusTask>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM focusTasks ORDER BY sortOrder ASC, createdAt ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    FocusTask(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow("durationMinutes")),
                        sessionType = if (it.getColumnIndex("sessionType") >= 0) it.getString(it.getColumnIndexOrThrow("sessionType")) ?: "FOCUS" else "FOCUS",
                        isDefault = it.getInt(it.getColumnIndexOrThrow("isDefault")) == 1,
                        sortOrder = if (it.getColumnIndex("sortOrder") >= 0) it.getInt(it.getColumnIndexOrThrow("sortOrder")) else 0,
                        createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt"))
                    )
                )
            }
        }
        _focusTasks.value = list
    }

    suspend fun addFocusTask(title: String, durationMinutes: Int, sessionType: String = "FOCUS") = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val currentMaxOrder = _focusTasks.value.filter { it.sessionType.equals(sessionType, true) }.maxOfOrNull { it.sortOrder } ?: 0
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("userId", "default_user")
            put("title", title)
            put("durationMinutes", durationMinutes)
            put("sessionType", sessionType)
            put("isDefault", 0)
            put("sortOrder", currentMaxOrder + 1)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("focusTasks", null, values)
        loadFocusTasks()
    }

    suspend fun updateFocusTask(task: FocusTask) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", task.title)
            put("durationMinutes", task.durationMinutes)
            put("sessionType", task.sessionType)
        }
        db.update("focusTasks", values, "id = ?", arrayOf(task.id))
        loadFocusTasks()
    }

    suspend fun reorderFocusTasks(tasks: List<FocusTask>) = withContext(Dispatchers.IO) {
        val taskIds = tasks.map { it.id }.toSet()
        val currentOther = _focusTasks.value.filter { it.id !in taskIds }
        val updatedTasks = tasks.mapIndexed { idx, t -> t.copy(sortOrder = idx) }
        _focusTasks.value = currentOther + updatedTasks
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            tasks.forEachIndexed { index, task ->
                val values = ContentValues().apply {
                    put("sortOrder", index)
                }
                db.update("focusTasks", values, "id = ?", arrayOf(task.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadFocusTasks()
    }

    suspend fun deleteFocusTask(taskId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("focusTasks", "id = ?", arrayOf(taskId))
        loadFocusTasks()
    }

    // --- Timer Category Reordering & Persistence ---

    fun parseTimerCategoryOrder(raw: String?): List<Pair<String, PomodoroType>> {
        if (raw.isNullOrBlank()) return DEFAULT_TIMER_CATEGORY_ORDER
        val tokens = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = mutableListOf<Pair<String, PomodoroType>>()
        val addedTypes = mutableSetOf<PomodoroType>()

        for (token in tokens) {
            val pair = when (token.uppercase(Locale.ROOT)) {
                "FOCUS" -> "Focus" to PomodoroType.FOCUS
                "SHORT_BREAK", "BREAK" -> "Break" to PomodoroType.SHORT_BREAK
                "LONG_BREAK", "FLOW" -> "Flow" to PomodoroType.LONG_BREAK
                else -> null
            } ?: continue

            if (addedTypes.add(pair.second)) {
                parsed.add(pair)
            }
        }

        for (defaultPair in DEFAULT_TIMER_CATEGORY_ORDER) {
            if (addedTypes.add(defaultPair.second)) {
                parsed.add(defaultPair)
            }
        }
        return parsed
    }

    fun serializeTimerCategoryOrder(order: List<Pair<String, PomodoroType>>): String {
        return order.joinToString(",") { it.second.name }
    }

    fun readSavedTimerCategoryOrder(ctx: Context? = context): List<Pair<String, PomodoroType>> {
        if (ctx == null) return DEFAULT_TIMER_CATEGORY_ORDER
        val prefFiles = listOf(
            try { "${ctx.packageName}_preferences" } catch (_: Throwable) { "com.deadline_preferences" },
            "deadline_preferences",
            "deadline_prefs",
            "deadline_pomodoro_prefs"
        )
        for (fileName in prefFiles) {
            try {
                val sp = ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                val raw = sp.getString(PREF_KEY_TIMER_CATEGORY_ORDER, null)
                if (!raw.isNullOrBlank()) {
                    return parseTimerCategoryOrder(raw)
                }
            } catch (_: Throwable) {}
        }
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT timerCategoryOrder FROM users WHERE id = 'default_user' LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val raw = cursor.getString(0)
                    if (!raw.isNullOrBlank()) {
                        return parseTimerCategoryOrder(raw)
                    }
                }
            }
        } catch (_: Throwable) {}
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT value FROM analytics_cache WHERE key = ?", arrayOf(PREF_KEY_TIMER_CATEGORY_ORDER)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val raw = cursor.getString(0)
                    if (!raw.isNullOrBlank()) {
                        return parseTimerCategoryOrder(raw)
                    }
                }
            }
        } catch (_: Throwable) {}

        return DEFAULT_TIMER_CATEGORY_ORDER
    }

    fun writeSavedTimerCategoryOrder(order: List<Pair<String, PomodoroType>>, ctx: Context? = context) {
        if (ctx == null) return
        val serialized = serializeTimerCategoryOrder(order)
        val prefFiles = listOf(
            try { "${ctx.packageName}_preferences" } catch (_: Throwable) { "com.deadline_preferences" },
            "deadline_preferences",
            "deadline_prefs",
            "deadline_pomodoro_prefs"
        )
        for (fileName in prefFiles) {
            try {
                val sp = ctx.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                sp.edit().putString(PREF_KEY_TIMER_CATEGORY_ORDER, serialized).apply()
            } catch (_: Throwable) {}
        }
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("key", PREF_KEY_TIMER_CATEGORY_ORDER)
                put("value", serialized)
                put("updatedAt", System.currentTimeMillis())
            }
            db.insertWithOnConflict("analytics_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            val userValues = ContentValues().apply {
                put("timerCategoryOrder", serialized)
            }
            db.update("users", userValues, "id = 'default_user'", null)
        } catch (_: Throwable) {}
    }

    fun getTimerCategoryOrder(): List<Pair<String, PomodoroType>> {
        return _timerCategoryOrder.value
    }

    suspend fun reorderTimerCategories(order: List<Pair<String, PomodoroType>>) = withContext(Dispatchers.IO) {
        _timerCategoryOrder.value = order
        _userSettings.value = _userSettings.value.copy(timerCategoryOrder = serializeTimerCategoryOrder(order))
        writeSavedTimerCategoryOrder(order, context)
    }

    suspend fun updateTimerCategoryOrder(order: List<Pair<String, PomodoroType>>) = reorderTimerCategories(order)

    suspend fun reorderTimerCategoriesByType(types: List<PomodoroType>) = withContext(Dispatchers.IO) {
        val mapped = types.map { type ->
            val label = when (type) {
                PomodoroType.FOCUS -> "Focus"
                PomodoroType.SHORT_BREAK -> "Break"
                PomodoroType.LONG_BREAK -> "Flow"
            }
            label to type
        }
        reorderTimerCategories(mapped)
    }

    suspend fun reorderTimerCategoriesByNames(names: List<String>) = withContext(Dispatchers.IO) {
        val mapped = parseTimerCategoryOrder(names.joinToString(","))
        reorderTimerCategories(mapped)
    }

    // --- Monthly Focus Heatmap Activity ---
    fun loadMonthlyFocusMinutes() {
        val map = mutableMapOf<String, Int>()
        val db = dbHelper.readableDatabase
        // 1. Group completed focus sessions by day (YYYY-MM-DD)
        val cursor = db.rawQuery("""
            SELECT date(startedAt / 1000, 'unixepoch', 'localtime') as dayString, SUM(duration) as totalMin
            FROM pomodoroSessions
            WHERE completed = 1
            GROUP BY dayString
        """.trimIndent(), null)
        cursor.use {
            while (it.moveToNext()) {
                val dayStr = it.getString(0)
                val totalMin = it.getInt(1)
                if (dayStr != null) {
                    map[dayStr] = totalMin
                }
            }
        }

        // 2. Aggregate completed habits per day (each completed habit contributes 15m activity equivalent)
        try {
            val habitCursor = db.rawQuery("""
                SELECT date, COUNT(*) as cnt
                FROM habitCompletions
                WHERE isSkip = 0
                GROUP BY date
            """.trimIndent(), null)
            habitCursor.use {
                while (it.moveToNext()) {
                    val dayStr = it.getString(0)
                    val count = it.getInt(1)
                    if (dayStr != null && count > 0) {
                        val habitEquivalentMinutes = count * 15
                        map[dayStr] = (map[dayStr] ?: 0) + habitEquivalentMinutes
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Aggregate dailyReflections if recorded
        try {
            val refCursor = db.rawQuery("""
                SELECT date, totalFocusMinutes
                FROM dailyReflections
                WHERE totalFocusMinutes > 0
            """.trimIndent(), null)
            refCursor.use {
                while (it.moveToNext()) {
                    val dayStr = it.getString(0)
                    val mins = it.getInt(1)
                    if (dayStr != null && mins > 0) {
                        map[dayStr] = maxOf(map[dayStr] ?: 0, mins)
                    }
                }
            }
        } catch (_: Exception) {}

        _monthlyFocusMinutes.value = map
    }

    suspend fun getMonthlyFocusMinutes(): Map<String, Int> = withContext(Dispatchers.IO) {
        loadMonthlyFocusMinutes()
        _monthlyFocusMinutes.value
    }

    suspend fun syncTodayProgress(todayFocusMinutes: Int = 0, habitsCompleted: Int = 0) = withContext(Dispatchers.IO) {
        val today = getTodayString()
        val db = dbHelper.writableDatabase

        // Guard against multiple record duplications: update single existing record for today's timestamp
        val cursor = db.rawQuery("SELECT id FROM dailyReflections WHERE date = ? LIMIT 1", arrayOf(today))
        val existingId = cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

        val values = ContentValues().apply {
            put("totalFocusMinutes", todayFocusMinutes)
            put("habitsCompleted", habitsCompleted)
        }

        if (existingId != null) {
            db.update("dailyReflections", values, "id = ?", arrayOf(existingId))
        } else {
            values.put("id", UUID.randomUUID().toString())
            values.put("userId", "default_user")
            values.put("date", today)
            values.put("mood", 3)
            values.put("energyLevel", 3)
            values.put("createdAt", System.currentTimeMillis())
            db.insert("dailyReflections", null, values)
        }

        // Calculate and sync consecutive streaks dynamically
        val compCursor = db.rawQuery("SELECT date, count, isSkip FROM habitCompletions WHERE count > 0 OR isSkip = 1", null)
        val compList = mutableListOf<HabitCompletion>()
        compCursor.use {
            while (it.moveToNext()) {
                compList.add(
                    HabitCompletion(
                        id = "",
                        userId = "default_user",
                        habitId = "",
                        date = it.getString(0),
                        count = it.getInt(1),
                        isSkip = it.getInt(2) == 1
                    )
                )
            }
        }
        val calculatedStreak = com.deadline.domain.StreakCalculator.calculateStreak(compList)
        val finalStreak = if (calculatedStreak == 0 && (habitsCompleted > 0 || todayFocusMinutes > 0)) 1 else calculatedStreak
        db.execSQL("UPDATE users SET currentStreak = ?, lastStreakDate = ? WHERE id = 'default_user'", arrayOf<Any>(finalStreak, today))

        loadUserSettings()
        loadTodayReflection()
        loadMonthlyFocusMinutes()
    }

    suspend fun syncTodayProgressHelper() = withContext(Dispatchers.IO) {
        val focusMins = _todaySessions.value.filter { it.sessionType == PomodoroType.FOCUS && it.completed }.sumOf { it.durationMinutes }
        val completedHabits = _habits.value.count { h ->
            val c = _todayCompletions.value[h.id]
            c != null && !c.isSkip && when (h.completionType) {
                HabitCompletionType.COUNT -> c.count >= h.targetCount
                HabitCompletionType.POMODORO -> c.count >= h.targetCount || c.count >= h.pomodoroThreshold
                HabitCompletionType.MANUAL -> c.count >= 1
            }
        }
        syncTodayProgress(todayFocusMinutes = focusMins, habitsCompleted = completedHabits)
    }
}

