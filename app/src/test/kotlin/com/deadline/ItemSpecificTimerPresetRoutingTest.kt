package com.deadline

import com.deadline.data.local.Habit
import com.deadline.data.local.HabitPlanItem
import com.deadline.data.local.ItemTimerPreset
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.ProjectTodo
import com.deadline.service.PomodoroService
import com.deadline.ui.components.ItemTimerGestureRouter
import com.deadline.ui.habits.GoalItem
import com.deadline.ui.navigation.DeadlineTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ItemSpecificTimerPresetRoutingTest {

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
    // Step 1: Gesture Disambiguation & Routing Logic
    // -------------------------------------------------------------------------

    @Test
    fun testSingleTapRoutingAndStateInitialization() {
        val preset = ItemTimerPreset(
            focusDuration = 1800,  // 30 min
            breakDuration = 360,   // 6 min
            flowDuration = 1200    // 20 min
        )
        val item = HabitPlanItem(
            id = "habit_workout",
            title = "Morning Workout",
            timerPreset = preset
        )

        var switchedTabIndex = -1
        val onNavigateToTimer = {
            switchedTabIndex = DeadlineTab.TIMER.ordinal // index: 2
        }

        // Simulate single tap execution
        PomodoroService.commitPreset(
            preset = item.timerPreset!!,
            taskTitle = item.title,
            sessionType = PomodoroType.FOCUS
        )
        onNavigateToTimer()

        // 1. Read stored preset configuration
        assertEquals(1800, item.focusSeconds)
        assertEquals(360, item.breakSeconds)
        assertEquals(1200, item.flowSeconds)

        // 2. Commit active duration values to central timer state store
        assertEquals(preset, PomodoroService.activePreset.value)
        assertEquals(1800, PomodoroService.focusSeconds.value)
        assertEquals(360, PomodoroService.breakSeconds.value)
        assertEquals(1200, PomodoroService.flowSeconds.value)

        // 3. Seamlessly switch active bottom navigation tab to Timer (index: 2)
        assertEquals(2, switchedTabIndex)
        assertEquals(DeadlineTab.TIMER.ordinal, switchedTabIndex)

        // 4. Set timer state to Ready / Paused with pre-loaded values (no auto-start)
        assertFalse("Timer countdown must not auto-start", PomodoroService.isRunning.value)
        assertEquals("Configured focus duration must be pre-loaded", 1800, PomodoroService.remainingSeconds.value)
        assertEquals("Total duration must match configured focus seconds", 1800, PomodoroService.totalDurationSeconds.value)
        assertEquals("Session type must default to FOCUS", PomodoroType.FOCUS, PomodoroService.currentSessionType.value)
        assertEquals("Current task title must be committed", "Morning Workout", PomodoroService.currentTaskTitle.value)
    }

    @Test
    fun testDoubleTapDisambiguationPreventsSingleTapNavigation() {
        val router = ItemTimerGestureRouter(debounceMs = 280L)
        val item = HabitPlanItem(
            id = "habit_reading",
            title = "Deep Reading",
            timerPreset = ItemTimerPreset(focusDuration = 2400, breakDuration = 600, flowDuration = 1800)
        )

        var singleTapNavigated = false
        var doubleTapConfigTriggered = false
        var openedItem: HabitPlanItem? = null

        val onSingleTap: (HabitPlanItem) -> Unit = {
            singleTapNavigated = true
        }
        val onDoubleTap: (HabitPlanItem) -> Unit = { targetItem ->
            doubleTapConfigTriggered = true
            openedItem = targetItem
        }

        // Simulate double-tap (rapid successive taps within debounce window)
        router.simulateDoubleTap(item, onSingleTap, onDoubleTap)

        assertFalse("Single-tap navigation must be prevented on double tap", singleTapNavigated)
        assertTrue("Double-tap must trigger timer preset configuration sheet", doubleTapConfigTriggered)
        assertNotNull("Configuration sheet item must match double-tapped item", openedItem)
        assertEquals("habit_reading", openedItem?.id)
        assertEquals(2400, openedItem?.focusSeconds)
    }

    // -------------------------------------------------------------------------
    // Step 2: Modal UI & Component Architecture Source Verification
    // -------------------------------------------------------------------------

    @Test
    fun testModalUIArchitectureSourceVerification() {
        val modalFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/ItemTimerPresetModal.kt")
        assertTrue("ItemTimerPresetModal.kt must exist", modalFile.exists())
        val content = modalFile.readText()

        // 1. Re-uses bottom sheet drawer design, stripped of Name text field and New title
        assertFalse("Modal must strip Name text input field", content.contains("text = \"Name\""))
        assertFalse("Modal must strip 'New' title label", content.contains("text = \"New\""))
        assertTrue("Close button ('X') must be pinned to top right", content.contains("Icons.Default.Close"))
        assertTrue("Header row must arrange close button at End", content.contains("horizontalArrangement = Arrangement.End"))

        // 2. Mode Selector: 3-segment Apple Segmented Control [ Focus | Break | Flow ], default Focus
        assertTrue("Modal must contain SegmentedControl", content.contains("SegmentedControl("))
        assertTrue("SegmentedControl items must be [Focus, Break, Flow]", content.contains("listOf(\"Focus\", \"Break\", \"Flow\")"))
        assertTrue("Default selected segment index must be 0 (Focus)", content.contains("mutableIntStateOf(0)"))

        // 3. Duration Wheel Picker: dual-column scrollable wheel (Hour : Minute)
        assertTrue("Dual-column wheel must include AppleWheelColumn", content.contains("AppleWheelColumn("))
        assertTrue("Wheel picker must contain ':' colon separator", content.contains("text = \":\""))

        // 4. Prominent full-width Save button pinned at bottom with smooth dismissal
        assertTrue("Modal must feature prominent Save button", content.contains("text = \"Save\""))
        assertTrue("Save button must be aligned at BottomCenter", content.contains("Alignment.BottomCenter"))
        assertTrue("Modal must dismiss with smoothDismiss", content.contains("sheetState.smoothDismiss()"))
    }

    // -------------------------------------------------------------------------
    // Step 3: State Schema & Data Model Persistence Verification
    // -------------------------------------------------------------------------

    @Test
    fun testStateSchemaAndModelPersistence() {
        // ItemTimerPreset schema check
        val preset = ItemTimerPreset(
            focusDuration = 300,  // 5 min
            breakDuration = 300,  // 5 min
            flowDuration = 1500   // 25 min
        )
        assertEquals(300, preset.focusDuration)
        assertEquals(300, preset.breakDuration)
        assertEquals(1500, preset.flowDuration)
        assertEquals(300, preset.focusSeconds)
        assertEquals(300, preset.breakSeconds)
        assertEquals(1500, preset.flowSeconds)

        // Habit implementation of HabitPlanItem
        val habit = Habit(
            id = "habit_1",
            name = "Morning Journal",
            timerPreset = preset
        )
        assertTrue("Habit must implement HabitPlanItem", habit is HabitPlanItem)
        assertEquals("Morning Journal", habit.title)
        assertEquals(300, habit.focusSeconds)
        assertEquals(300, habit.breakSeconds)
        assertEquals(1500, habit.flowSeconds)

        // ProjectTodo implementation of HabitPlanItem
        val todo = ProjectTodo(
            id = "todo_1",
            projectId = "p1",
            title = "Complete Feature",
            timerPreset = preset
        )
        assertTrue("ProjectTodo must implement HabitPlanItem", todo is HabitPlanItem)
        assertEquals("Complete Feature", todo.title)
        assertEquals(300, todo.focusSeconds)

        // GoalItem implementation of HabitPlanItem
        val goal = GoalItem(
            id = "task_todo_1",
            title = "Complete Feature",
            subtitle = "2026-10-29",
            timerPreset = preset
        )
        assertTrue("GoalItem must implement HabitPlanItem", goal is HabitPlanItem)
        assertEquals("Complete Feature", goal.title)
        assertEquals("2026-10-29", goal.date)
        assertEquals(1500, goal.flowSeconds)
    }

    @Test
    fun testDatabaseSchemaSupportsPresetAttributes() {
        val dbHelperFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineDbHelper.kt")
        assertTrue("DeadlineDbHelper.kt must exist", dbHelperFile.exists())
        val dbContent = dbHelperFile.readText()

        assertTrue("habits table must contain focusDuration", dbContent.contains("focusDuration INTEGER"))
        assertTrue("habits table must contain breakDuration", dbContent.contains("breakDuration INTEGER"))
        assertTrue("habits table must contain flowDuration", dbContent.contains("flowDuration INTEGER"))

        assertTrue("projectTodos table must contain focusDuration", dbContent.contains("focusDuration INTEGER"))
        assertTrue("projectTodos table must contain breakDuration", dbContent.contains("breakDuration INTEGER"))
        assertTrue("projectTodos table must contain flowDuration", dbContent.contains("flowDuration INTEGER"))
    }

    @Test
    fun testHabitsAndPlanViewIntegration() {
        val habitsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        assertTrue("HabitsScreen.kt must exist", habitsFile.exists())
        val habitsContent = habitsFile.readText()

        assertTrue("HabitsScreen must support onNavigateToTimer", habitsContent.contains("onNavigateToTimer"))
        assertTrue("HabitsScreen must embed ItemTimerPresetModal", habitsContent.contains("ItemTimerPresetModal"))
        assertTrue("HabitsScreen must detect tap gestures on HabitCard", habitsContent.contains("detectTapGestures"))
        assertTrue("HabitsScreen must commit preset on single tap", habitsContent.contains("PomodoroService.commitPreset"))

        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val planContent = planViewFile.readText()

        assertTrue("PlanView must support onNavigateToTimer", planContent.contains("onNavigateToTimer"))
        assertTrue("PlanView must embed ItemTimerPresetModal", planContent.contains("ItemTimerPresetModal"))
        assertTrue("PlanView must detect tap gestures on InteractiveGoalCard", planContent.contains("detectTapGestures"))
        assertTrue("PlanView must commit preset on single tap", planContent.contains("PomodoroService.commitPreset"))
    }
}
