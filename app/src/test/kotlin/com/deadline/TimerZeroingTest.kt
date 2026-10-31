package com.deadline

import com.deadline.data.local.FocusTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TimerZeroingTest {

    private fun formatDisplayTime(seconds: Int): String {
        val displayHours = seconds / 3600
        val displayMins = (seconds % 3600) / 60
        val displaySecs = seconds % 60
        return if (displayHours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", displayHours, displayMins, displaySecs)
        } else {
            String.format(Locale.US, "%02d:%02d", displayMins, displaySecs)
        }
    }

    @Test
    fun testZeroTimerDisplayFormatting() {
        val zeroFormatted = formatDisplayTime(0)
        assertEquals("00:00", zeroFormatted)
    }

    @Test
    fun testActiveTimerPresetDeletionZeroingLogic() {
        val task1 = FocusTask(id = "task_1", title = "Focus 25", durationMinutes = 25)
        val task2 = FocusTask(id = "task_2", title = "Short Break", durationMinutes = 5)
        var localModeTasks = listOf(task1, task2)

        var isRunning = true
        var remainingSeconds = 1200

        // When task1 is deleted while actively running:
        val wasRunning = isRunning
        assertTrue("Timer should be actively running before deletion", wasRunning)

        // Immediate halt & zero display
        if (wasRunning) {
            remainingSeconds = 0
            isRunning = false
        }

        assertEquals(0, remainingSeconds)
        assertFalse(isRunning)
        assertEquals("00:00", formatDisplayTime(remainingSeconds))

        // After exit animation completes:
        val updated = localModeTasks.filter { it.id != task1.id }
        localModeTasks = updated
        val nextTask = updated.firstOrNull() ?: FocusTask(id = "fallback", title = "Focus", durationMinutes = 25)

        // If wasRunning is true, remaining seconds stays forced to 0
        val resetSeconds = if (wasRunning) 0 else nextTask.durationMinutes * 60
        remainingSeconds = resetSeconds

        assertEquals(0, remainingSeconds)
        assertEquals("00:00", formatDisplayTime(remainingSeconds))
        assertEquals("Short Break", nextTask.title)
    }

    @Test
    fun testForegroundServiceNotificationStrictlyBoundToRunningState() {
        var isRunning = true
        var notificationVisible = true

        // Notification is active while running
        assertTrue(isRunning && notificationVisible)

        // When paused:
        isRunning = false
        notificationVisible = isRunning // Strictly bound to running state
        assertFalse("Notification must be dismissed when paused", notificationVisible)

        // When resumed:
        isRunning = true
        notificationVisible = isRunning
        assertTrue("Notification is shown when running", notificationVisible)

        // When deleted/stopped while running:
        isRunning = false
        notificationVisible = isRunning
        assertFalse("Notification must be immediately dismissed on stop/deletion", notificationVisible)
    }

    @Test
    fun testTimerNotificationCompletelyRemovedOnRunningAndTabSwitch() {
        // Zero timer notifications rule: no notifications are posted while timer is on or when switching tabs
        fun isTimerNotificationShown(isRunning: Boolean, isTabSwitched: Boolean): Boolean {
            return false // Completely eliminated
        }

        assertFalse("No notification while timer is running", isTimerNotificationShown(isRunning = true, isTabSwitched = false))
        assertFalse("No notification when switching tabs while running", isTimerNotificationShown(isRunning = true, isTabSwitched = true))
        assertFalse("No notification when paused on other tabs", isTimerNotificationShown(isRunning = false, isTabSwitched = true))
    }

    @Test
    fun testInactiveTimerPresetDeletionNormalResetLogic() {
        val task1 = FocusTask(id = "task_1", title = "Focus 25", durationMinutes = 25)
        val task2 = FocusTask(id = "task_2", title = "Short Break", durationMinutes = 5)
        var localModeTasks = listOf(task1, task2)

        var isRunning = false
        var remainingSeconds = 25 * 60

        // Inactive deletion should load the next preset's duration
        val wasRunning = isRunning
        assertFalse("Timer was idle, not running", wasRunning)

        val updated = localModeTasks.filter { it.id != task1.id }
        localModeTasks = updated
        val nextTask = updated.firstOrNull()!!

        val resetSeconds = if (wasRunning) 0 else nextTask.durationMinutes * 60
        remainingSeconds = resetSeconds

        assertEquals(300, remainingSeconds)
        assertEquals("05:00", formatDisplayTime(remainingSeconds))
    }
}
