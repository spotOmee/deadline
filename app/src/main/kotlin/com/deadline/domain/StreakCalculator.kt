package com.deadline.domain

import com.deadline.data.local.HabitCompletion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StreakCalculator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Calculates the active streak in days.
     * Consecutive days completed (or skipped with isSkip=true) increment the streak.
     * A day without completion or skip breaks the streak.
     */
    fun calculateStreak(completions: List<HabitCompletion>): Int {
        if (completions.isEmpty()) return 0

        val dateMap = completions.associateBy { it.date }
        val cal = Calendar.getInstance()
        var streak = 0

        // Check today first. If today isn't logged yet, start checking from yesterday
        val todayStr = dateFormat.format(cal.time)
        val todayComp = dateMap[todayStr]

        if (todayComp != null) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Check yesterday
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(cal.time)
            if (dateMap[yesterdayStr] == null) {
                return 0
            }
        }

        // Walk backwards day by day
        while (true) {
            val dateStr = dateFormat.format(cal.time)
            val comp = dateMap[dateStr]
            if (comp != null) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }
}
