package com.deadline.domain

import com.deadline.data.local.PomodoroSession
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.TimeBlock

object AdherenceScorer {

    /**
     * Calculates daily adherence score (0 - 100%)
     * Formula: ((Completed Habits + Completed Daily Tasks) / (Total Scheduled Habits + Total Scheduled Daily Tasks)) * 100
     */
    fun computeAdherencePercentage(
        completedHabits: Int,
        totalScheduledHabits: Int,
        completedDailyTasks: Int,
        totalScheduledDailyTasks: Int,
        completedPomodoros: Int = 0
    ): Int {
        val totalScheduled = totalScheduledHabits + totalScheduledDailyTasks
        val totalCompleted = completedHabits + completedDailyTasks
        if (totalScheduled == 0) {
            return if (completedPomodoros > 0) 100 else 0
        }
        val ratio = totalCompleted.toDouble() / totalScheduled.toDouble()
        return (ratio * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Calculates daily adherence score (0 - 100%)
     * Based on total focus minutes logged vs total planned time-block minutes.
     */
    fun computeScore(plannedBlocks: List<TimeBlock>, focusSessions: List<PomodoroSession>): Int {
        val totalPlannedMinutes = plannedBlocks.sumOf { it.durationMinutes }
        val totalFocusedMinutes = focusSessions
            .filter { it.sessionType == PomodoroType.FOCUS && it.completed }
            .sumOf { it.durationMinutes }

        if (totalPlannedMinutes == 0) {
            return if (totalFocusedMinutes > 0) 100 else 0
        }

        val ratio = totalFocusedMinutes.toDouble() / totalPlannedMinutes.toDouble()
        return (ratio * 100).toInt().coerceIn(0, 100)
    }
}
