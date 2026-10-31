package com.deadline

import com.deadline.ui.components.CalendarPickerMath
import com.deadline.ui.components.ProjectDeadlineValidator
import com.deadline.ui.components.SelectedDeadlineDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class UniversalModalDismissalAndDeadlineOptimizationTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/")),
            File("/storage/emulated/0/AndroidIDEProjects/deadline", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    // -------------------------------------------------------------------------
    // 1. Consistent Modal Exit Transition Across All Creation Modals
    // -------------------------------------------------------------------------

    @Test
    fun testModalDismissAnimationHelper_enforcesSmoothDownwardsExit() {
        val helperFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/ModalDismissAnimation.kt")
        assertTrue("ModalDismissAnimation.kt must exist", helperFile.exists())
        val content = helperFile.readText()

        assertTrue("Must provide smoothDismiss suspend extension", content.contains("suspend fun SheetState.smoothDismiss()"))
        assertTrue("Must provide animateDismiss helper", content.contains("fun SheetState.animateDismiss("))
        assertTrue("smoothDismiss must call hide() when isVisible", content.contains("hide()"))
    }

    @Test
    fun testHabitCreationModal_usesSmoothDownwardDismissal() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        assertTrue("HabitsScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Habit modal must use animateDismiss on dismiss request", content.contains("sheetState.animateDismiss(scope, onDismiss)"))
        assertTrue("Habit modal must call smoothDismiss on submit", content.contains("sheetState.smoothDismiss()"))
    }

    @Test
    fun testTaskCreationModal_usesSmoothDownwardDismissal() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Task modal must use animateDismiss on dismiss", content.contains("sheetState.animateDismiss(scope)") && content.contains("showCreateTaskSheet = false"))
        assertTrue("Task modal must call smoothDismiss on submit", content.contains("sheetState.smoothDismiss()"))
    }

    @Test
    fun testProjectCreationModal_usesSmoothDownwardDismissal() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Project modal must use animateDismiss on dismiss", content.contains("sheetState.animateDismiss(scope)") && content.contains("showCreateProjectSheet = false"))
        assertTrue("Project modal must call smoothDismiss on submit", content.contains("sheetState.smoothDismiss()"))
    }

    @Test
    fun testTimerPresetCreationModal_usesSmoothDownwardDismissal() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Timer preset modal must use animateDismiss on dismiss", content.contains("sheetState.animateDismiss(scope)") && content.contains("showTaskDialog = false"))
        assertTrue("Timer preset modal must call smoothDismiss on submit", content.contains("sheetState.smoothDismiss()"))
    }

    @Test
    fun testNotesCreationModal_usesSmoothDownwardDismissal() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/notes/NotesScreen.kt")
        assertTrue("NotesScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Notes editor modal must call smoothDismiss on save and dismiss", content.contains("sheetState.smoothDismiss()"))
    }

    // -------------------------------------------------------------------------
    // 2. Milestone / Deadline Selection Drop-Down Animation (60-120 FPS Fluidity)
    // -------------------------------------------------------------------------

    @Test
    fun testDeadlineDropdownOptimization_hardwareLayerClippingAndSpringPhysics() {
        val pickerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/ProjectDeadlinePicker.kt")
        assertTrue("ProjectDeadlinePicker.kt must exist", pickerFile.exists())
        val content = pickerFile.readText()

        // Must use GPU layer clipping for fluid 60-120 FPS performance
        assertTrue("Must use hardware graphicsLayer clip", content.contains("graphicsLayer { clip = true }"))

        // Must use responsive medium spring stiffness (avoiding sluggish low stiffness that caused lag)
        assertTrue("Must use Spring.StiffnessMedium for responsive dropdown transition", content.contains("Spring.StiffnessMedium"))

        // Column inside UniversalDeadlinePicker must not duplicate outer size animation
        assertFalse("Inner column must avoid conflicting nested animateContentSize", content.contains("Column(\n        modifier = modifier\n            .fillMaxWidth()\n            .animateContentSize"))
    }

    @Test
    fun testDeadlineDropdown_projectIntegratedAndTaskDeadlineRemoved() {
        val projectsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", projectsFile.exists())
        val content = projectsFile.readText()

        // Projects modal integration
        assertTrue("Projects creation modal must integrate UniversalDeadlinePicker", content.contains("UniversalDeadlinePicker("))

        // Tasks modal: "Set Task Deadline" button must be completely removed
        assertFalse("Tasks creation modal must not contain 'Set Task Deadline' button", content.contains("buttonLabel = \"Set Task Deadline\""))
    }

    @Test
    fun testZeroAllocationCalendarMath_preserves100PercentAccuracy() {
        // Test accuracy across multiple leap and non-leap years
        assertEquals(31, CalendarPickerMath.getDaysInMonth(2026, Calendar.JANUARY))
        assertEquals(28, CalendarPickerMath.getDaysInMonth(2025, Calendar.FEBRUARY))
        assertEquals(29, CalendarPickerMath.getDaysInMonth(2024, Calendar.FEBRUARY)) // Leap
        assertEquals(28, CalendarPickerMath.getDaysInMonth(2100, Calendar.FEBRUARY)) // Century non-leap
        assertEquals(29, CalendarPickerMath.getDaysInMonth(2000, Calendar.FEBRUARY)) // 400-yr leap
        assertEquals(31, CalendarPickerMath.getDaysInMonth(2026, Calendar.OCTOBER))
        assertEquals(30, CalendarPickerMath.getDaysInMonth(2026, Calendar.NOVEMBER))

        // First day of week offset for October 2026 (Oct 1 = Thursday -> Monday-first index 3)
        assertEquals(3, CalendarPickerMath.getFirstDayOfWeekOffset(2026, Calendar.OCTOBER))
        // Nov 1, 2026 is Sunday -> Monday-first index 6
        assertEquals(6, CalendarPickerMath.getFirstDayOfWeekOffset(2026, Calendar.NOVEMBER))
        // Dec 1, 2026 is Tuesday -> Monday-first index 1
        assertEquals(1, CalendarPickerMath.getFirstDayOfWeekOffset(2026, Calendar.DECEMBER))

        // Total rows
        assertEquals(5, CalendarPickerMath.getTotalRows(2026, Calendar.OCTOBER))

        // Formatted header
        assertEquals("October 2026", CalendarPickerMath.formatMonthHeader(2026, Calendar.OCTOBER))
        assertEquals("November 2026", CalendarPickerMath.formatMonthHeader(2026, Calendar.NOVEMBER))
    }
}
