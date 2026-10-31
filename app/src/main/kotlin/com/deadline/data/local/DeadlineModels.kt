package com.deadline.data.local

import java.util.UUID

data class UserSettings(
    val id: String = "default_user",
    val name: String = "",
    val email: String? = null,
    val dailyGoalPomodoros: Int = 4,
    val focusDurationMinutes: Int = 25,
    val shortBreakDurationMinutes: Int = 5,
    val longBreakDurationMinutes: Int = 15,
    val longBreakInterval: Int = 4,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastStreakDate: String? = null,
    val morningReminderEnabled: Boolean = false,
    val morningReminderTime: String = "07:00",
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderTime: String = "20:00",
    val themeMode: String = "system", // "light", "dark", "system"
    val showReflection: Boolean = true,
    val avatarUri: String? = null,
    val timeFormat24Hour: Boolean = false,
    val targetDeadline: String = "",
    val currentState: String = "",
    val targetIdentity: String = "",
    val bioNarrative: String = "",
    val heatmapPixelIcon: String = "TARGET_BULLSEYE",
    val defaultStartupScreen: String = "TIMER",
    val hasExplicitDailyGoal: Boolean = false,
    val timerCategoryOrder: String = "FOCUS,SHORT_BREAK,LONG_BREAK"
)

data class ItemTimerPreset(
    val focusDuration: Int = 25 * 60, // in seconds (e.g., 300 for 5 min, 1500 for 25 min)
    val breakDuration: Int = 5 * 60,  // in seconds (e.g., 300 for 5 min)
    val flowDuration: Int = 15 * 60   // in seconds (e.g., 1500 for 25 min, 900 for 15 min)
) {
    val focusSeconds: Int get() = focusDuration
    val breakSeconds: Int get() = breakDuration
    val flowSeconds: Int get() = flowDuration
}

interface HabitPlanItem {
    val id: String
    val title: String
    val date: String?
    val timerPreset: ItemTimerPreset?
    val focusSeconds: Int get() = timerPreset?.focusDuration ?: (25 * 60)
    val breakSeconds: Int get() = timerPreset?.breakDuration ?: (5 * 60)
    val flowSeconds: Int get() = timerPreset?.flowDuration ?: (15 * 60)

    companion object {
        operator fun invoke(
            id: String,
            title: String,
            date: String? = null,
            timerPreset: ItemTimerPreset? = ItemTimerPreset()
        ): HabitPlanItem = HabitPlanItemData(id, title, date, timerPreset)
    }
}

data class HabitPlanItemData(
    override val id: String,
    override val title: String,
    override val date: String? = null,
    override val timerPreset: ItemTimerPreset? = ItemTimerPreset()
) : HabitPlanItem

enum class PomodoroType {
    FOCUS, SHORT_BREAK, LONG_BREAK
}

data class PomodoroSession(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val projectId: String? = null,
    val tagId: String? = null,
    val todoId: String? = null,
    val title: String? = null,
    val sessionType: PomodoroType = PomodoroType.FOCUS,
    val durationMinutes: Int = 25,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val completed: Boolean = true,
    val pausedDurationSeconds: Int = 0,
    val note: String? = null,
    val quality: String? = null
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val name: String,
    val description: String? = null,
    val color: String = "#5E5CE6", // WorkIndigo default
    val targetPomodoros: Int = 10,
    val archived: Boolean = false,
    val isPaused: Boolean = false,
    val linkedNoteIds: String = "", // Comma-separated list of linked QuickNote IDs
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskPriority {
    LOW, MEDIUM, HIGH, URGENT
}

enum class TaskStatus {
    TODO, IN_PROGRESS, COMPLETED
}

data class ProjectTodo(
    override val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val projectId: String,
    override val title: String,
    val description: String? = null,
    val estimatedMinutes: Int = 60,
    val loggedMinutes: Int = 0,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val tagId: String? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val dueDate: String? = null, // YYYY-MM-DD
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    override val timerPreset: ItemTimerPreset? = ItemTimerPreset()
) : HabitPlanItem {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    override val date: String? get() = dueDate
    override val focusSeconds: Int get() = timerPreset?.focusDuration ?: (25 * 60)
    override val breakSeconds: Int get() = timerPreset?.breakDuration ?: (5 * 60)
    override val flowSeconds: Int get() = timerPreset?.flowDuration ?: (15 * 60)
}

enum class HabitCompletionType {
    MANUAL, COUNT, POMODORO
}

data class Habit(
    override val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val name: String,
    val emoji: String = "⚡",
    val color: String = "#30D158",
    val schedule: String = "daily",
    val completionType: HabitCompletionType = HabitCompletionType.MANUAL,
    val targetCount: Int = 1,
    val countUnit: String? = null,
    val pomodoroThreshold: Int = 1,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastCompletedDate: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    override val timerPreset: ItemTimerPreset? = ItemTimerPreset()
) : HabitPlanItem {
    override val title: String get() = name
    override val date: String? get() = lastCompletedDate
    override val focusSeconds: Int get() = timerPreset?.focusDuration ?: (25 * 60)
    override val breakSeconds: Int get() = timerPreset?.breakDuration ?: (5 * 60)
    override val flowSeconds: Int get() = timerPreset?.flowDuration ?: (15 * 60)
}

data class HabitCompletion(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val habitId: String,
    val date: String, // YYYY-MM-DD
    val completedAt: Long = System.currentTimeMillis(),
    val count: Int = 1,
    val isSkip: Boolean = false
)

data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val date: String, // YYYY-MM-DD
    val startMinute: Int, // 0 - 1439 (e.g. 9*60 = 540 for 09:00)
    val durationMinutes: Int, // e.g. 60
    val title: String,
    val color: String = "#5E5CE6",
    val projectId: String? = null,
    val todoId: String? = null,
    val repeatRule: String = "none" // none, daily, weekdays, weekly
)

data class DailyReflection(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val date: String, // YYYY-MM-DD
    val mood: Int = 3, // 1 to 5
    val energyLevel: Int = 3, // 1 to 5
    val win: String? = null,
    val challenge: String? = null,
    val gratitude: String? = null,
    val tomorrowIntent: String? = null,
    val totalFocusMinutes: Int = 0,
    val habitsCompleted: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class FocusTask(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val title: String,
    val durationMinutes: Int = 25,
    val sessionType: String = "FOCUS", // "FOCUS", "SHORT_BREAK", "LONG_BREAK"
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class QuickNote(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val title: String,
    val content: String,
    val noteType: String = "NOTE", // "ACTIVE", "NOTE", "IMPORTANT"
    val isPinned: Boolean = false,
    val isChecklist: Boolean = false,
    val fontSize: Int = 16,
    val textColor: String = "DEFAULT",
    val alignment: String = "LEFT",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class KnowledgeArticle(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "default_user",
    val headline: String,
    val body: String,
    val tags: String = "DESIGN, PRODUCTIVITY",
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
