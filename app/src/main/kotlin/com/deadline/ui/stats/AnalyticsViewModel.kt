package com.deadline.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletion
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.PomodoroSession
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.TaskStatus
import com.deadline.data.local.UserSettings
import com.deadline.domain.AdherenceScorer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val adherencePercent: Int = 0,
    val completedHabitsCount: Int = 0,
    val totalHabitsCount: Int = 0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    val totalFocusMinutes: Int = 0,
    val totalBreakMinutes: Int = 0,
    val totalFlowMinutes: Int = 0,
    val focusSessionsCount: Int = 0,
    val breakSessionsCount: Int = 0,
    val flowSessionsCount: Int = 0,
    val focusTargetMinutes: Int = 100,
    val breakTargetMinutes: Int = 20,
    val flowTargetMinutes: Int = 30,
    val completedSessionsCount: Int = 0,
    val dailyGoalSessions: Int = 4,
    val currentStreak: Int = 0,
    val successRate: Int = 0,
    val monthlyActivityMinutes: Map<String, Int> = emptyMap()
)

class AnalyticsViewModel(
    private val repository: DeadlineRepository
) : ViewModel() {

    val analyticsUiState: StateFlow<AnalyticsUiState> = combine(
        repository.observeTodayHabits(),
        repository.observeTodayHabitCompletions(),
        repository.observeTodayTasks(),
        repository.observeTodaySessions(),
        repository.userSettings
    ) { habits, completions, tasks, sessions, settings ->
        calculateAnalytics(habits, completions, tasks, sessions, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.loadCachedAnalyticsState()
    )

    private fun calculateAnalytics(
        habits: List<Habit>,
        completions: Map<String, HabitCompletion>,
        tasks: List<ProjectTodo>,
        sessions: List<PomodoroSession>,
        settings: UserSettings
    ): AnalyticsUiState {
        val todayStr = repository.getTodayString()
        val monthlyMap = repository.monthlyFocusMinutes.value

        // 1. Habits calculation (including counter goals like Hydration reaching target)
        val totalScheduledHabits = habits.size
        val completedHabits = habits.count { h ->
            val c = completions[h.id]
            c != null && !c.isSkip && when (h.completionType) {
                HabitCompletionType.COUNT -> c.count >= h.targetCount
                HabitCompletionType.POMODORO -> c.count >= h.targetCount || c.count >= h.pomodoroThreshold
                HabitCompletionType.MANUAL -> c.count >= 1
            }
        }

        // 2. Tasks calculation (Today's scheduled tasks)
        val todayTasks = tasks.filter { it.dueDate == todayStr || it.dueDate.isNullOrBlank() }
        val totalScheduledTasks = todayTasks.size
        val completedTasks = todayTasks.count { it.status == TaskStatus.COMPLETED }

        // 3. Pomodoro Sessions (Focus, Break, Flow)
        val focusSessions = sessions.filter { it.sessionType == PomodoroType.FOCUS && it.completed }
        val breakSessions = sessions.filter { it.sessionType == PomodoroType.SHORT_BREAK && it.completed }
        val flowSessions = sessions.filter { it.sessionType == PomodoroType.LONG_BREAK && it.completed }

        val totalFocusMinutes = focusSessions.sumOf { it.durationMinutes }
        val totalBreakMinutes = breakSessions.sumOf { it.durationMinutes }
        val totalFlowMinutes = flowSessions.sumOf { it.durationMinutes }

        val focusSessionsCount = focusSessions.size
        val breakSessionsCount = breakSessions.size
        val flowSessionsCount = flowSessions.size

        val dailyGoalSessions = settings.dailyGoalPomodoros
        val focusTargetMinutes = maxOf(dailyGoalSessions * 25, 25)
        val breakTargetMinutes = maxOf(dailyGoalSessions * 5, 15)
        val flowTargetMinutes = 30
        val completedSessionsCount = focusSessionsCount

        // 4. Adherence Percentage:
        // Formula: ((Completed Habits + Completed Daily Tasks) / (Total Scheduled Habits + Total Scheduled Daily Tasks)) * 100
        val adherencePercent = AdherenceScorer.computeAdherencePercentage(
            completedHabits = completedHabits,
            totalScheduledHabits = totalScheduledHabits,
            completedDailyTasks = completedTasks,
            totalScheduledDailyTasks = totalScheduledTasks,
            completedPomodoros = completedSessionsCount
        )

        // 5. Success rate
        val totalSessionsCount = sessions.size
        val successRate = if (totalSessionsCount > 0) {
            ((completedSessionsCount.toFloat() / totalSessionsCount.toFloat()) * 100).toInt()
        } else if ((totalScheduledHabits + totalScheduledTasks) > 0) {
            adherencePercent
        } else 0

        // 6. Dynamic Monthly Heatmap: update today's cell intensity as soon as adherence changes from 0%
        val updatedMonthlyMap = monthlyMap.toMutableMap()
        val todayEffectiveMinutes = maxOf(
            totalFocusMinutes,
            if (adherencePercent >= 80) 50
            else if (adherencePercent >= 40) 25
            else if (adherencePercent > 0) 15
            else 0
        )
        updatedMonthlyMap[todayStr] = todayEffectiveMinutes

        // 7. Streak
        val streak = if (completedHabits == 0 && completedTasks == 0 && completedSessionsCount == 0) {
            0
        } else {
            maxOf(settings.currentStreak, if (adherencePercent > 0 || completedSessionsCount > 0) 1 else 0)
        }

        val state = AnalyticsUiState(
            isLoading = false,
            adherencePercent = adherencePercent,
            completedHabitsCount = completedHabits,
            totalHabitsCount = totalScheduledHabits,
            completedTasksCount = completedTasks,
            totalTasksCount = totalScheduledTasks,
            totalFocusMinutes = totalFocusMinutes,
            totalBreakMinutes = totalBreakMinutes,
            totalFlowMinutes = totalFlowMinutes,
            focusSessionsCount = focusSessionsCount,
            breakSessionsCount = breakSessionsCount,
            flowSessionsCount = flowSessionsCount,
            focusTargetMinutes = focusTargetMinutes,
            breakTargetMinutes = breakTargetMinutes,
            flowTargetMinutes = flowTargetMinutes,
            completedSessionsCount = completedSessionsCount,
            dailyGoalSessions = dailyGoalSessions,
            currentStreak = streak,
            successRate = successRate,
            monthlyActivityMinutes = updatedMonthlyMap
        )
        repository.persistAnalyticsState(state)
        return state
    }

    class Factory(private val repository: DeadlineRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(repository) as T
        }
    }
}
