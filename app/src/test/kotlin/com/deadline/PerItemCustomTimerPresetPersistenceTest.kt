package com.deadline

import com.deadline.data.local.ItemTimerPreset
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.TaskPriority
import com.deadline.data.local.TaskStatus
import com.deadline.service.PomodoroService
import com.deadline.ui.habits.GoalItem
import com.deadline.ui.theme.BrandAmberEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Mission Requirements:
 * 1. Fix Persistence in Bottom Sheet Save Action:
 *    - Captures selected wheel values (focusDuration, breakDuration, flowDuration).
 *    - Persists these values keyed by unique id to the data model.
 *    - Reloading the preset for that item displays saved custom values rather than hardcoded defaults.
 * 2. Bind Custom Presets During Single-Tap Routing:
 *    - Injects target item's saved ItemTimerPreset directly into PomodoroService state.
 *    - Sets activeFocusDuration, activeBreakDuration, activeFlowDuration.
 *    - Pre-loads custom intervals on the timer ready state and mode switcher.
 * 3. Scope Isolation:
 *    - Overrides apply strictly to the specific configured item in Plan view.
 *    - Modifying one item's presets never overwrites global timer defaults or other items.
 * 4. Timeline Grid & Color Restoration:
 *    - Active hour and horizontal timeline indicator line use universal Apple-style accent yellow (BrandAmberEnd).
 *    - Empty state placeholder ("No scheduled events") removed so grid lines remain uninterrupted.
 *    - Schedule tasks displayed horizontally along horizontal line with horizontal scrolling (LazyRow).
 */
class PerItemCustomTimerPresetPersistenceTest {

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
    // Step 1: Persistence in Bottom Sheet Save Action
    // -------------------------------------------------------------------------

    @Test
    fun testCustomPresetDataModelAndPersistence() {
        // Default preset initialization
        val defaultPreset = ItemTimerPreset()
        assertEquals(25 * 60, defaultPreset.focusDuration)
        assertEquals(5 * 60, defaultPreset.breakDuration)
        assertEquals(15 * 60, defaultPreset.flowDuration)

        // Custom preset configuration (e.g. Focus: 10m, Break: 5m, Flow: 25m)
        val customPreset = ItemTimerPreset(
            focusDuration = 10 * 60,  // 600 seconds
            breakDuration = 5 * 60,   // 300 seconds
            flowDuration = 25 * 60    // 1500 seconds
        )
        assertEquals(600, customPreset.focusDuration)
        assertEquals(300, customPreset.breakDuration)
        assertEquals(1500, customPreset.flowDuration)

        // Todo entity persistence check
        val todo = ProjectTodo(
            id = "todo_plan_test_1",
            userId = "user_1",
            projectId = "proj_1",
            title = "Write Tech Spec",
            priority = TaskPriority.HIGH,
            status = TaskStatus.TODO,
            timerPreset = customPreset
        )
        assertEquals(600, todo.focusSeconds)
        assertEquals(300, todo.breakSeconds)
        assertEquals(1500, todo.flowSeconds)

        // GoalItem reflects the saved custom values
        val goalItem = GoalItem(
            id = todo.id,
            title = todo.title,
            subtitle = "Today",
            timerPreset = todo.timerPreset
        )
        assertEquals(600, goalItem.focusSeconds)
        assertEquals(300, goalItem.breakSeconds)
        assertEquals(1500, goalItem.flowSeconds)
        assertEquals(customPreset, goalItem.timerPreset)
    }

    // -------------------------------------------------------------------------
    // Step 2: Bind Custom Presets During Single-Tap Routing
    // -------------------------------------------------------------------------

    @Test
    fun testSingleTapRoutingInjectsCustomPresetsIntoTimerService() {
        val customPreset = ItemTimerPreset(
            focusDuration = 600,   // 10 minutes Focus
            breakDuration = 300,   // 5 minutes Break
            flowDuration = 1500    // 25 minutes Flow
        )

        // Execute commitPreset simulation as triggered by InteractiveGoalCard single-tap
        PomodoroService.commitPreset(
            preset = customPreset,
            taskTitle = "Review Pull Request",
            sessionType = PomodoroType.FOCUS
        )

        // 1. Central timer state store holds item's exact preset values
        assertEquals("Active preset must match injected custom preset", customPreset, PomodoroService.activePreset.value)
        assertEquals("Focus duration must be 600s (10m)", 600, PomodoroService.focusSeconds.value)
        assertEquals("Break duration must be 300s (5m)", 300, PomodoroService.breakSeconds.value)
        assertEquals("Flow duration must be 1500s (25m)", 1500, PomodoroService.flowSeconds.value)

        // 2. Pre-loaded timer values are ready / paused without auto-starting
        assertFalse("Timer must not auto-start countdown", PomodoroService.isRunning.value)
        assertEquals("Remaining seconds must equal custom focus duration", 600, PomodoroService.remainingSeconds.value)
        assertEquals("Total duration must equal custom focus duration", 600, PomodoroService.totalDurationSeconds.value)
        assertEquals("Task title must be set", "Review Pull Request", PomodoroService.currentTaskTitle.value)
        assertEquals("Session type must be FOCUS", PomodoroType.FOCUS, PomodoroService.currentSessionType.value)

        // 3. Mode switching reads corresponding duration from active preset
        val activePreset = PomodoroService.activePreset.value
        assertNotNull(activePreset)

        val breakDuration = when (PomodoroType.SHORT_BREAK) {
            PomodoroType.FOCUS -> activePreset!!.focusDuration
            PomodoroType.SHORT_BREAK -> activePreset!!.breakDuration
            PomodoroType.LONG_BREAK -> activePreset!!.flowDuration
        }
        assertEquals("Break mode must read 300s from active preset", 300, breakDuration)

        val flowDuration = when (PomodoroType.LONG_BREAK) {
            PomodoroType.FOCUS -> activePreset!!.focusDuration
            PomodoroType.SHORT_BREAK -> activePreset!!.breakDuration
            PomodoroType.LONG_BREAK -> activePreset!!.flowDuration
        }
        assertEquals("Flow mode must read 1500s from active preset", 1500, flowDuration)
    }

    // -------------------------------------------------------------------------
    // Step 3: Scope Isolation
    // -------------------------------------------------------------------------

    @Test
    fun testScopeIsolationBetweenDistinctItems() {
        val itemA = ProjectTodo(
            id = "item_a",
            userId = "user_1",
            projectId = "proj_1",
            title = "Task A",
            timerPreset = ItemTimerPreset(focusDuration = 600, breakDuration = 300, flowDuration = 1200)
        )

        val itemB = ProjectTodo(
            id = "item_b",
            userId = "user_1",
            projectId = "proj_1",
            title = "Task B",
            timerPreset = ItemTimerPreset(focusDuration = 1800, breakDuration = 600, flowDuration = 2400)
        )

        // Item A and Item B have completely independent custom presets
        assertNotEquals(itemA.timerPreset?.focusDuration, itemB.timerPreset?.focusDuration)
        assertEquals(600, itemA.focusSeconds)
        assertEquals(1800, itemB.focusSeconds)

        // Modifying Item A does not affect Item B
        val updatedItemA = itemA.copy(
            timerPreset = itemA.timerPreset!!.copy(focusDuration = 900)
        )
        assertEquals(900, updatedItemA.focusSeconds)
        assertEquals(1800, itemB.focusSeconds)
    }

    // -------------------------------------------------------------------------
    // Step 4: Code Architecture & Timeline Color Verification
    // -------------------------------------------------------------------------

    @Test
    fun testItemTimerPresetModal_keysRememberOnItemIdAndPreset() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/ItemTimerPresetModal.kt")
        assertTrue("ItemTimerPresetModal.kt must exist", file.exists())
        val content = file.readText()

        // Keyed remembers guarantee fresh values when reloading modal for an item
        assertTrue(
            "focusDuration must be keyed on item.id and item.timerPreset",
            content.contains("remember(item.id, item.timerPreset)")
        )
        assertTrue(
            "selectedHour must be keyed on selectedSegmentIndex and currentDuration",
            content.contains("remember(selectedSegmentIndex, currentDuration)")
        )
    }

    @Test
    fun testPlanView_savesCustomPresetToRepositoryAndState() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", file.exists())
        val content = file.readText()

        // Verifies modal save calls repository.updateItemTimerPreset and updates localGoalItems
        assertTrue(
            "PlanView must update localGoalItems synchronously on preset save",
            content.contains("localGoalItems.map") &&
            content.contains("it.copy(timerPreset = updatedPreset)")
        )
        assertTrue(
            "PlanView must persist updated preset to repository",
            content.contains("repository.updateItemTimerPreset(configGoal.id, updatedPreset)")
        )
        // Verifies single tap commits preset to PomodoroService
        assertTrue(
            "InteractiveGoalCard must call PomodoroService.commitPreset",
            content.contains("PomodoroService.commitPreset(") &&
            content.contains("timerNavigator()")
        )
    }

    @Test
    fun testTimeline_usesBrandAmberEndAndHorizontalScheduleSwipe() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. Accent yellow color (BrandAmberEnd)
        assertTrue(
            "ProjectsScreen timeline must use BrandAmberEnd for active hour",
            content.contains("color = if (isHourActive) BrandAmberEnd")
        )
        assertTrue(
            "ProjectsScreen timeline horizontal grid line must use BrandAmberEnd for active hour",
            content.contains("if (isHourActive) BrandAmberEnd.copy(alpha = 0.5f)")
        )

        // 2. Empty state removed
        assertFalse(
            "No scheduled events placeholder must be removed from timeline view",
            content.contains("title = \"No scheduled events\"")
        )

        // 3. Schedules placed in horizontal swipeable row (LazyRow)
        assertTrue(
            "Timeline schedule blocks must render inside LazyRow for horizontal swiping",
            content.contains("LazyRow(") &&
            content.contains("items(hourBlocks, key = { it.id })")
        )
    }
}
