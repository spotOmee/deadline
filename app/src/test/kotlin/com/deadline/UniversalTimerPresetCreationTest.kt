package com.deadline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class UniversalTimerPresetCreationTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/"))
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    // -------------------------------------------------------------------------
    // 1. Universal Creation Button Simplification
    // -------------------------------------------------------------------------

    @Test
    fun testTriggerButtonPurgedOfModeSpecificLabels() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", timerFile.exists())
        val content = timerFile.readText()

        // 1. Completely remove specific text labels across all sub-tabs ('Focus', 'Break', 'Flow')
        assertFalse(
            "TimerScreen must not contain 'Add Focus Task'",
            content.contains("Add Focus Task")
        )
        assertFalse(
            "TimerScreen must not contain 'Add Break Task'",
            content.contains("Add Break Task")
        )
        assertFalse(
            "TimerScreen must not contain 'Add Flow Task'",
            content.contains("Add Flow Task")
        )
        assertFalse(
            "TimerScreen must not dynamically interpolate mode in trigger button label",
            content.contains("Add \${if (selectedType ==")
        )
    }

    @Test
    fun testUnifiedTriggerButtonCenterAlignedWithAddIconAndWord() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", timerFile.exists())
        val content = timerFile.readText()

        // 2. Must contain single, unified, center-aligned button with '+' icon and 'Add'
        assertTrue(
            "TimerScreen must import Icons.Default.Add",
            content.contains("import androidx.compose.material.icons.filled.Add")
        )
        assertTrue(
            "TimerScreen trigger button must display '+' icon using Icons.Default.Add",
            content.contains("imageVector = Icons.Default.Add")
        )
        assertTrue(
            "TimerScreen trigger button must display text 'Add'",
            content.contains("text = \"Add\"")
        )
        assertTrue(
            "TimerScreen trigger button must be center-aligned",
            content.contains("horizontalArrangement = Arrangement.Center") &&
                content.contains("verticalAlignment = Alignment.CenterVertically")
        )
    }

    // -------------------------------------------------------------------------
    // 2. Debounced Submission & Prevention of Duplicates
    // -------------------------------------------------------------------------

    @Test
    fun testPresetCreationSubmissionDebounceInSource() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", timerFile.exists())
        val content = timerFile.readText()

        // Check for isSubmitting state in TimerScreen task dialog
        assertTrue(
            "TimerScreen must declare isSubmitting state for modal submission",
            content.contains("var isSubmitting by remember { mutableStateOf(false) }")
        )

        // Check that submission button is debounced immediately on first tap
        assertTrue(
            "Submission button must check and set isSubmitting immediately on click",
            content.contains("if (isSubmitting) return@bounceClick") &&
                content.contains("isSubmitting = true")
        )

        // Check that bounceClick or button is disabled when isSubmitting is true
        assertTrue(
            "Submission button must be disabled via enabled = !isSubmitting",
            content.contains("enabled = !isSubmitting")
        )
    }

    @Test
    fun testSubmissionDebounceLogicSimulatedExecution() {
        var isSubmitting = false
        val executionCounter = AtomicInteger(0)

        // Simulated click handler mirroring TimerScreen submit button
        val onClick: () -> Unit = {
            if (isSubmitting) {
                // Debounced: accidental multiple executions prevented immediately
            } else {
                isSubmitting = true
                executionCounter.incrementAndGet()
            }
        }

        // First tap: should execute
        onClick()
        assertEquals(1, executionCounter.get())
        assertTrue("isSubmitting must be true immediately after first tap", isSubmitting)

        // Rapid subsequent taps while submitting: should be blocked
        onClick()
        onClick()
        onClick()
        assertEquals("Subsequent taps must be debounced and discarded", 1, executionCounter.get())
    }

    // -------------------------------------------------------------------------
    // 3. Smooth Modal Auto-Dismissal
    // -------------------------------------------------------------------------

    @Test
    fun testSmoothModalAutoDismissalAfterDataPersistence() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", timerFile.exists())
        val content = timerFile.readText()

        // 1. Data persistence must occur before downward slide dismissal
        val saveBlock = content.substringAfter("// Sticky Bottom-Anchored Primary Action")
            .substringBefore("contentAlignment = Alignment.Center")

        val persistenceIndex = saveBlock.indexOf("repository.addFocusTask")
        val dismissIndex = saveBlock.indexOf("sheetState.smoothDismiss()")

        assertTrue("Persistence must be called in save action", persistenceIndex != -1)
        assertTrue("sheetState.smoothDismiss() must be called in save action", dismissIndex != -1)
        assertTrue(
            "Data persistence must occur prior to modal exit animation",
            persistenceIndex < dismissIndex
        )

        // 2. Interface cleanly returns to underlying view only after dismissal animation completes
        val finallyIndex = saveBlock.indexOf("finally {")
        val dismissInFinallyIndex = saveBlock.indexOf("showTaskDialog = false", finallyIndex)

        assertTrue("finally block must exist to ensure clean cleanup", finallyIndex != -1)
        assertTrue(
            "showTaskDialog = false must be inside finally block after smoothDismiss() completes",
            dismissInFinallyIndex > dismissIndex
        )
    }

    @Test
    fun testSmoothDismissalLifecycleSequence() {
        val eventLog = mutableListOf<String>()

        // Simulated persistence, animation, and completion flow
        var isSubmitting = false
        var showTaskDialog = true

        val submitPreset: () -> Unit = {
            if (!isSubmitting) {
                isSubmitting = true
                eventLog.add("button_disabled")
                try {
                    // Step 1: Persistence
                    eventLog.add("data_persisted")

                    // Step 2: Smooth dismissal animation begins
                    eventLog.add("dismiss_animation_start")
                    // Underlying view is still covered while animation is translating downward
                    assertTrue("Underlying view should still retain modal during animation", showTaskDialog)
                    eventLog.add("dismiss_animation_complete")
                } finally {
                    // Step 3: Clean return to underlying view only after dismissal completes
                    showTaskDialog = false
                    isSubmitting = false
                    eventLog.add("underlying_view_restored")
                }
            }
        }

        submitPreset()

        val expectedEvents = listOf(
            "button_disabled",
            "data_persisted",
            "dismiss_animation_start",
            "dismiss_animation_complete",
            "underlying_view_restored"
        )
        assertEquals(expectedEvents, eventLog)
        assertFalse("Modal must be dismissed after sequence completes", showTaskDialog)
        assertFalse("Submission lock must be reset", isSubmitting)
    }
}
