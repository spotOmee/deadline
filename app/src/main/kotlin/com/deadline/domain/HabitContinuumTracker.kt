package com.deadline.domain

/**
 * Automated Status Logic for Habit Consistency Continuum.
 *
 * - COMPLETED: Displays a checkmark when habit is completed on that day (or logged in "Today" tab).
 * - MISSED: Automatically transitions to an 'X' after midnight for past uncompleted days.
 * - PENDING: Remains displayed as an 'O' for upcoming or uncompleted days (today or future).
 */
enum class HabitContinuumStatus {
    COMPLETED,
    MISSED,
    PENDING
}

object HabitContinuumTracker {

    /**
     * Resolves the dynamic status for a habit on a given timeline day.
     *
     * @param isCompleted True if the habit was logged/completed for this date.
     * @param isPast True if the date has passed midnight (i.e. date is before today).
     * @return [HabitContinuumStatus] representing COMPLETED, MISSED, or PENDING.
     */
    fun resolveStatus(
        isCompleted: Boolean,
        isPast: Boolean
    ): HabitContinuumStatus {
        return when {
            isCompleted -> HabitContinuumStatus.COMPLETED
            isPast -> HabitContinuumStatus.MISSED
            else -> HabitContinuumStatus.PENDING
        }
    }
}
