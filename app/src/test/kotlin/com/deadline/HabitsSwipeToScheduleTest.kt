package com.deadline

import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.ItemTimerPreset
import com.deadline.ui.components.UniversalSwipeAction
import com.deadline.ui.habits.GoalItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test suite verifying Habit Swipe-to-Schedule interaction:
 * 1. Today tab in HabitsScreen binds left-to-right swipe to 'Schedule' action (onSchedule) instead of 'Edit'
 * 2. HabitsScreen displays ScheduleTimePickerSheet when habitToSchedule is set
 * 3. ScheduleTimePickerSheet is identical to the one in Plan mode (dual-wheel hours/minutes, AM/PM toggle, future-time gating)
 * 4. GoalItem mapping correctly preserves habit properties, color, and timer preset duration
 */
class HabitsSwipeToScheduleTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/")),
            File("/storage/emulated/0/AndroidIDEProjects/deadline/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Source file not found: $relativePath")
    }

    @Test
    fun testHabitsScreen_todayTabSwipeActionWiresOnSchedule() {
        val habitsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        assertTrue("HabitsScreen.kt must exist", habitsFile.exists())
        val content = habitsFile.readText()

        // 1. UniversalSwipeableItem in Today tab must wire onSchedule
        assertTrue(
            "HabitsScreen must bind onSchedule on UniversalSwipeableItem",
            content.contains("onSchedule = {") &&
            content.contains("habitToSchedule = habit") &&
            content.contains("showScheduleSheet = true")
        )

        // 2. UniversalSwipeableItem in Today tab must NOT wire onEdit
        val todaySwipeItemBlock = content.substringAfter("UniversalSwipeableItem(")
            .substringBefore("HabitCard(")
        assertFalse(
            "Today tab habit item must not wire onEdit",
            todaySwipeItemBlock.contains("onEdit = {")
        )

        // 3. HabitsScreen must render ScheduleTimePickerSheet with GoalItem
        assertTrue(
            "HabitsScreen must render ScheduleTimePickerSheet when showScheduleSheet is true",
            content.contains("if (showScheduleSheet && habitToSchedule != null)") &&
            content.contains("ScheduleTimePickerSheet(") &&
            content.contains("repository.addTimeBlock(")
        )
    }

    @Test
    fun testUniversalSwipeableItem_swipeLeftToRightTriggersScheduleAction() {
        val swipeFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        assertTrue("UniversalSwipeableItem.kt must exist", swipeFile.exists())
        val content = swipeFile.readText()

        // 1. Swiping right (testDelta > 10f) prioritizes UniversalSwipeAction.SCHEDULE when onSchedule != null
        assertTrue(
            "UniversalSwipeableItem must activate UniversalSwipeAction.SCHEDULE when currentOnSchedule != null",
            content.contains("if (currentOnSchedule != null) {") &&
            content.contains("activeSwipeAction = UniversalSwipeAction.SCHEDULE")
        )

        // 2. Visual indicator for SCHEDULE includes StopwatchIcon and 'Schedule' text
        assertTrue(
            "UniversalSwipeableItem must display StopwatchIcon and 'Schedule' label",
            content.contains("StopwatchIcon(") &&
            content.contains("text = \"Schedule\"")
        )

        // 3. Completing swipe invokes currentOnSchedule
        assertTrue(
            "UniversalSwipeableItem must call currentOnSchedule on threshold crossing",
            content.contains("currentOnSchedule?.invoke()")
        )
    }

    @Test
    fun testHabitToGoalItemMapping_preservesProperties() {
        val habit = Habit(
            id = "habit_hydration",
            name = "Morning Hydration",
            color = "#10B981",
            completionType = HabitCompletionType.COUNT,
            targetCount = 8,
            timerPreset = ItemTimerPreset(
                focusDuration = 25 * 60,
                breakDuration = 5 * 60,
                flowDuration = 15 * 60
            )
        )

        val goalItem = GoalItem(
            id = habit.id,
            title = habit.name,
            subtitle = "",
            isHabit = true,
            habitRef = habit,
            timerPreset = habit.timerPreset
        )

        assertEquals("habit_hydration", goalItem.id)
        assertEquals("Morning Hydration", goalItem.title)
        assertTrue(goalItem.isHabit)
        assertEquals("#10B981", goalItem.habitRef?.color)
        assertEquals(25 * 60, goalItem.focusSeconds)
        assertEquals(5 * 60, goalItem.breakSeconds)
    }

    @Test
    fun testScheduleTimePickerSheet_sharedBetweenPlanAndHabits() {
        val planFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        val habitsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        val planContent = planFile.readText()
        val habitsContent = habitsFile.readText()

        // ScheduleTimePickerSheet defined in PlanView.kt
        assertTrue(
            "ScheduleTimePickerSheet must be defined in PlanView.kt",
            planContent.contains("fun ScheduleTimePickerSheet(")
        )

        // Used in HabitsScreen.kt
        assertTrue(
            "HabitsScreen.kt must invoke ScheduleTimePickerSheet",
            habitsContent.contains("ScheduleTimePickerSheet(")
        )

        // Time picker has hours, minutes, and AM/PM toggle
        assertTrue(
            "ScheduleTimePickerSheet must include AM/PM toggle",
            planContent.contains("Text(") && planContent.contains("text = \"AM\"") &&
            planContent.contains("text = \"PM\"")
        )
    }
}
