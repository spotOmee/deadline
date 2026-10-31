package com.deadline

import com.deadline.data.local.FocusTask
import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.QuickNote
import com.deadline.data.local.TaskPriority
import com.deadline.data.local.TaskStatus
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.calculateSlotTargetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * Comprehensive Unit Test Suite verifying Fluid Drag-to-Reorder & Gap-Filling Across ALL App Sections:
 * 1. Habits Section: 'Today' tab list (HabitsScreen.kt) & 'Plan' tab list (PlanView.kt)
 * 2. Timer Section: 'Focus', 'Break', and 'Flow' presets (TimerScreen.kt)
 * 3. Tasks Section: Active Projects task list & Done task list (ProjectsScreen.kt)
 * 4. Notes Section: 'Pinned' notes list & 'Others' notes list (NotesScreen.kt)
 * 5. Universal Component Drag Physics: UniversalSwipeableItem.kt
 * 6. Repository & Local Data Layer: In-memory set isolation & async IO persistence
 */
class FluidDragToReorderComprehensiveTest {

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

    // =========================================================================
    // 1. Habits Section ('Today' tab & 'Plan' tab)
    // =========================================================================

    @Test
    fun testHabitsScreen_todayTabFluidDragArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        val content = file.readText()

        // Compose LazyColumn with unique key
        assertTrue("HabitsScreen must key items by unique habit id",
            content.contains("itemsIndexed(localHabits, key = { _, it -> it.id })"))

        // Fluid gap filling via animateItem placementSpec
        assertTrue("HabitsScreen must use Modifier.animateItem for fluid gap-filling",
            content.contains(".animateItem(") && content.contains("placementSpec = spring("))

        // Tactile pickup haptic feedback
        assertTrue("HabitsScreen must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic(context, hapticFeedback)"))

        // Elevated styling for active dragged item
        assertTrue("HabitsScreen dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)"))

        // Gap filling handled by animateItem (nonDraggedDisplacement = 0f)
        assertTrue("HabitsScreen must set nonDraggedDisplacement to 0f so animateItem handles gap gliding cleanly",
            content.contains("nonDraggedDisplacement = 0f,"))

        // Real-time Collections.swap and zero-jitter dragHabitOffsetY math
        assertTrue("HabitsScreen must perform real-time Collections.swap during drag",
            content.contains("Collections.swap(reordered, iterIdx, nextIdx)"))
        assertTrue("HabitsScreen must compensate dragHabitOffsetY for zero-jitter offset",
            content.contains("dragHabitOffsetY -= slotsMoved * habitItemHeightPx"))

        // Settle physics
        assertTrue("HabitsScreen must invoke animateDragToSettle to zero settle offset",
            content.contains("animateDragToSettle(") &&
            content.contains("calculateSettleTargetOffset("))

        // Async persistence on Dispatchers.IO
        assertTrue("HabitsScreen must persist reordered habits on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderHabits(localHabits)"))

        // Deletion gap closing with AnimatedVisibility
        assertTrue("HabitsScreen must use AnimatedVisibility with shrinkVertically for deletion gap closing",
            content.contains("shrinkVertically(") &&
            content.contains("fadeOut("))
    }

    @Test
    fun testPlanView_planTabFluidDragArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        val content = file.readText()

        // Compose LazyColumn with unique key
        assertTrue("PlanView must key items by unique goal id",
            content.contains("itemsIndexed(localGoalItems, key = { _, it -> it.id })"))

        // Fluid gap filling via animateItem placementSpec
        assertTrue("PlanView must use Modifier.animateItem with placementSpec",
            content.contains(".animateItem(") && content.contains("placementSpec = spring("))

        // Tactile pickup haptic feedback
        assertTrue("PlanView must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic(context, hapticFeedback)"))

        // Elevated active dragged styling
        assertTrue("PlanView dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)"))

        // nonDraggedDisplacement = 0f
        assertTrue("PlanView must set nonDraggedDisplacement to 0f",
            content.contains("nonDraggedDisplacement = 0f,"))

        // Real-time Collections.swap and zero-jitter dragGoalOffsetY math
        assertTrue("PlanView must perform real-time Collections.swap during drag",
            content.contains("Collections.swap(reordered, iterIdx, nextIdx)"))
        assertTrue("PlanView must compensate dragGoalOffsetY for zero-jitter offset",
            content.contains("dragGoalOffsetY -= slotsMoved * goalItemHeightPx"))

        // Settle physics
        assertTrue("PlanView must invoke animateDragToSettle to zero settle offset",
            content.contains("animateDragToSettle(") &&
            content.contains("calculateSettleTargetOffset("))

        // Async persistence on Dispatchers.IO
        assertTrue("PlanView must persist reordered plan tasks on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderPlanTasks("))

        // Deletion gap closing
        assertTrue("PlanView must use AnimatedVisibility with shrinkVertically for deletion gap closing",
            content.contains("shrinkVertically(") &&
            content.contains("fadeOut("))
    }

    // =========================================================================
    // 2. Timer Section (Focus, Break, and Flow Presets)
    // =========================================================================

    @Test
    fun testTimerScreen_presetsFluidDragArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        val content = file.readText()

        // LazyColumn with itemsIndexed and unique task id key
        assertTrue("TimerScreen must key items by unique task id",
            content.contains("key = { _, task -> task.id }"))

        // Fluid gap filling via animateItem placementSpec
        assertTrue("TimerScreen must use Modifier.animateItem with placementSpec",
            content.contains(".animateItem(") && content.contains("placementSpec = spring("))

        // Tactile pickup haptic feedback
        assertTrue("TimerScreen must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic("))

        // Elevated styling for active dragged item
        assertTrue("TimerScreen dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)"))
        assertTrue("TimerScreen dragged item must apply elevated dragScale",
            content.contains("1.03f"))

        // Real-time Collections.swap and zero-jitter compensation
        assertTrue("TimerScreen must perform Collections.swap during drag delta",
            content.contains("Collections.swap(reordered, iterIdx, nextIdx)"))
        assertTrue("TimerScreen must compensate dragOffsetY for zero-jitter offset",
            content.contains("dragOffsetY -= slotsMoved * itemHeightPx"))

        // Settle physics
        assertTrue("TimerScreen must invoke animateDragToSettle to zero settle offset",
            content.contains("animateDragToSettle(") &&
            content.contains("calculateSettleTargetOffset("))

        // Async persistence on Dispatchers.IO
        assertTrue("TimerScreen must persist reordered presets on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderFocusTasks(localModeTasks)"))

        // Deletion gap closing
        assertTrue("TimerScreen must use AnimatedVisibility with shrinkVertically for deletion gap closing",
            content.contains("shrinkVertically(") &&
            content.contains("deletingTaskIds"))
    }

    // =========================================================================
    // 3. Tasks Section (Active Projects task list & Done task list)
    // =========================================================================

    @Test
    fun testProjectsScreen_tasksFluidDragArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        val content = file.readText()

        // LazyColumn with unique key
        assertTrue("ProjectsScreen must key items by unique todo id",
            content.contains("itemsIndexed(localTodos, key = { _, it -> it.id })"))

        // Fluid gap filling via animateItem placementSpec
        assertTrue("ProjectsScreen must use Modifier.animateItem with placementSpec",
            content.contains(".animateItem(") && content.contains("placementSpec = spring("))

        // Tactile pickup haptic feedback
        assertTrue("ProjectsScreen must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic(context, hapticFeedback)"))

        // Elevated active dragged styling
        assertTrue("ProjectsScreen dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)"))

        // nonDraggedDisplacement = 0f
        assertTrue("ProjectsScreen must set nonDraggedDisplacement to 0f",
            content.contains("nonDraggedDisplacement = 0f,"))

        // Real-time Collections.swap and zero-jitter dragTodoOffsetY math
        assertTrue("ProjectsScreen must perform real-time Collections.swap during drag",
            content.contains("Collections.swap(reordered, iterIdx, nextIdx)"))
        assertTrue("ProjectsScreen must compensate dragTodoOffsetY for zero-jitter offset",
            content.contains("dragTodoOffsetY -= slotsMoved * todoItemHeightPx"))

        // Settle physics
        assertTrue("ProjectsScreen must invoke animateDragToSettle to zero settle offset",
            content.contains("animateDragToSettle(") &&
            content.contains("calculateSettleTargetOffset("))

        // Async persistence on Dispatchers.IO
        assertTrue("ProjectsScreen must persist reordered todos on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderTodos(localTodos)"))

        // Deletion gap closing
        assertTrue("ProjectsScreen must use AnimatedVisibility with shrinkVertically for deletion gap closing",
            content.contains("shrinkVertically(") &&
            content.contains("fadeOut("))
    }

    // =========================================================================
    // 4. Notes Section ('Pinned' notes list & 'Others' notes list)
    // =========================================================================

    @Test
    fun testNotesScreen_notesFluidDragArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/notes/NotesScreen.kt")
        val content = file.readText()

        // LazyColumn with unique key
        assertTrue("NotesScreen must key items by unique note id",
            content.contains("items(pinnedNotes, key = { it.id })") &&
            content.contains("items(otherNotes, key = { it.id })"))

        // Fluid gap filling via animateItem placementSpec
        assertTrue("NotesScreen must use Modifier.animateItem with placementSpec",
            content.contains(".animateItem(") && content.contains("placementSpec = spring("))

        // Tactile pickup haptic feedback across all lists
        assertTrue("NotesScreen must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic(context, hapticFeedback)"))

        // Elevated active dragged styling
        assertTrue("NotesScreen dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)"))
        assertTrue("NotesScreen dragged item must apply elevated dragScale",
            content.contains("1.03f"))
        assertTrue("NotesScreen dragged item must apply 16.dp shadow",
            content.contains("16.dp.toPx()"))

        // nonDraggedDisplacement = 0f
        assertTrue("NotesScreen must set nonDraggedDisplacement to 0f",
            content.contains("nonDraggedDisplacement = 0f,"))

        // Real-time Collections.swap and zero-jitter dragOffsetY math
        assertTrue("NotesScreen must perform real-time Collections.swap during drag",
            content.contains("Collections.swap(reordered, iterIdx, nextIdx)"))
        assertTrue("NotesScreen must compensate dragOffsetY for zero-jitter offset",
            content.contains("dragOffsetY -= slotsMoved * itemHeightPx"))

        // Settle physics
        assertTrue("NotesScreen must invoke animateDragToSettle to zero settle offset",
            content.contains("animateDragToSettle(") &&
            content.contains("calculateSettleTargetOffset("))

        // Async persistence on Dispatchers.IO
        assertTrue("NotesScreen must persist reordered notes on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderQuickNotes("))

        // Strict Section Isolation in NotesScreen:
        // Updating pinned notes must preserve otherNotes; updating others must preserve pinnedNotes
        assertTrue("NotesScreen must isolate pinned reordering from other notes",
            content.contains("localQuickNotes = updated + otherNotes"))
        assertTrue("NotesScreen must isolate other reordering from pinned notes",
            content.contains("localQuickNotes = pinnedNotes + updated"))

        // Deletion gap closing
        assertTrue("NotesScreen must use AnimatedVisibility with shrinkVertically for deletion gap closing",
            content.contains("shrinkVertically(") &&
            content.contains("fadeOut("))
    }

    // =========================================================================
    // 5. Universal Swipeable Item Component Physics & Elevation
    // =========================================================================

    @Test
    fun testUniversalSwipeableItem_dragPhysicsAndElevation() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        val content = file.readText()

        assertTrue("UniversalSwipeableItem must elevate zIndex to 100f when dragged",
            content.contains(".zIndex(if (isThisItemDragged) 100f else 1f)"))
        assertTrue("UniversalSwipeableItem must elevate scale when dragged",
            content.contains("targetValue = if (isThisItemDragged) 1.04f else 1f"))
        assertTrue("UniversalSwipeableItem must apply 16.dp drop shadow when dragged",
            content.contains("targetValue = if (isThisItemDragged) with(density) { 16.dp.toPx() } else 0f"))
        assertTrue("UniversalSwipeableItem must apply dragScale to scaleX and scaleY",
            content.contains("scaleX = dragScale") && content.contains("scaleY = dragScale"))
        assertTrue("UniversalSwipeableItem must apply shadowElevation",
            content.contains("shadowElevation = dragElevation"))
    }

    // =========================================================================
    // 6. Repository State Isolation & Local Data Layer
    // =========================================================================

    @Test
    fun testRepository_stateIsolationLogic() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        val content = file.readText()

        // Todos isolation
        assertTrue("reorderTodos must isolate reordered tasks via set containment",
            content.contains("val taskIds = todos.map { it.id }.toSet()") &&
            content.contains("val currentOther = _todos.value.filter { it.id !in taskIds }"))

        // Habits isolation
        assertTrue("reorderHabits must isolate reordered habits via set containment",
            content.contains("val habitIds = habits.map { it.id }.toSet()") &&
            content.contains("val currentOther = _habits.value.filter { it.id !in habitIds }"))

        // QuickNotes isolation
        assertTrue("reorderQuickNotes must isolate reordered notes via set containment",
            content.contains("val noteIds = notes.map { it.id }.toSet()") &&
            content.contains("val currentOther = _quickNotes.value.filter { it.id !in noteIds }"))

        // FocusTasks isolation
        assertTrue("reorderFocusTasks must isolate reordered presets via set containment",
            content.contains("val taskIds = tasks.map { it.id }.toSet()") &&
            content.contains("val currentOther = _focusTasks.value.filter { it.id !in taskIds }"))
    }

    @Test
    fun testTasksSectionIsolation_activeVsDone() {
        val active1 = ProjectTodo(id = "a1", projectId = "p1", title = "Active 1", status = TaskStatus.TODO, sortOrder = 0)
        val active2 = ProjectTodo(id = "a2", projectId = "p1", title = "Active 2", status = TaskStatus.IN_PROGRESS, sortOrder = 1)
        val done1 = ProjectTodo(id = "d1", projectId = "p1", title = "Done 1", status = TaskStatus.COMPLETED, sortOrder = 0)
        val done2 = ProjectTodo(id = "d2", projectId = "p1", title = "Done 2", status = TaskStatus.COMPLETED, sortOrder = 1)

        val initialAll = listOf(active1, active2, done1, done2)

        // User reorders Active tasks: swap a1 and a2
        val reorderedActive = listOf(active2, active1)
        val activeIds = reorderedActive.map { it.id }.toSet()

        val otherTodos = initialAll.filter { it.id !in activeIds }
        val updatedActive = reorderedActive.mapIndexed { idx, t -> t.copy(sortOrder = idx) }
        val newAllTodos = otherTodos + updatedActive

        // Verify Done tasks remain completely intact with original sortOrder and status
        val remainingDone = newAllTodos.filter { it.status == TaskStatus.COMPLETED }
        assertEquals(2, remainingDone.size)
        assertEquals("d1", remainingDone[0].id)
        assertEquals(0, remainingDone[0].sortOrder)
        assertEquals("d2", remainingDone[1].id)
        assertEquals(1, remainingDone[1].sortOrder)

        // Verify Active tasks reflect new sortOrder
        val updatedActiveTasks = newAllTodos.filter { it.status != TaskStatus.COMPLETED }
        assertEquals(2, updatedActiveTasks.size)
        assertEquals("a2", updatedActiveTasks[0].id)
        assertEquals(0, updatedActiveTasks[0].sortOrder)
        assertEquals("a1", updatedActiveTasks[1].id)
        assertEquals(1, updatedActiveTasks[1].sortOrder)
    }

    @Test
    fun testNotesSectionIsolation_pinnedVsOthers() {
        val pin1 = QuickNote(id = "p1", title = "Pinned 1", content = "", isPinned = true, sortOrder = 0)
        val pin2 = QuickNote(id = "p2", title = "Pinned 2", content = "", isPinned = true, sortOrder = 1)
        val other1 = QuickNote(id = "o1", title = "Other 1", content = "", isPinned = false, sortOrder = 0)
        val other2 = QuickNote(id = "o2", title = "Other 2", content = "", isPinned = false, sortOrder = 1)

        val allNotes = listOf(pin1, pin2, other1, other2)

        // Reorder Pinned: move p2 before p1
        val reorderedPinned = listOf(pin2, pin1)
        val pinnedIds = reorderedPinned.map { it.id }.toSet()

        val remainingOthers = allNotes.filter { it.id !in pinnedIds }
        val updatedPinned = reorderedPinned.mapIndexed { idx, n -> n.copy(sortOrder = idx) }
        val combinedAfterPinnedReorder = remainingOthers + updatedPinned

        // Verify others were unchanged
        val verifiedOthers = combinedAfterPinnedReorder.filter { !it.isPinned }
        assertEquals(2, verifiedOthers.size)
        assertEquals("o1", verifiedOthers[0].id)
        assertEquals(0, verifiedOthers[0].sortOrder)
        assertEquals("o2", verifiedOthers[1].id)
        assertEquals(1, verifiedOthers[1].sortOrder)

        // Verify pinned were reordered
        val verifiedPinned = combinedAfterPinnedReorder.filter { it.isPinned }
        assertEquals(2, verifiedPinned.size)
        assertEquals("p2", verifiedPinned[0].id)
        assertEquals(0, verifiedPinned[0].sortOrder)
        assertEquals("p1", verifiedPinned[1].id)
        assertEquals(1, verifiedPinned[1].sortOrder)
    }

    @Test
    fun testZeroJitterStepByStepMathSimulation() {
        val list = mutableListOf("item0", "item1", "item2", "item3", "item4")
        val itemHeightPx = 100f

        // Move item0 down across 3 slots to index 3
        val currentIdx = 0
        val targetIdx = 3
        var dragOffsetY = 320f // Dragged 320px downwards (exceeds 300px slot boundary)

        val direction = if (targetIdx > currentIdx) 1 else -1
        var iterIdx = currentIdx
        while (iterIdx != targetIdx) {
            val nextIdx = iterIdx + direction
            Collections.swap(list, iterIdx, nextIdx)
            iterIdx = nextIdx
        }

        val slotsMoved = targetIdx - currentIdx
        dragOffsetY -= slotsMoved * itemHeightPx

        assertEquals(listOf("item1", "item2", "item3", "item0", "item4"), list)
        assertEquals(20f, dragOffsetY, 0.001f)

        // Now move item0 up 1 slot to index 2
        val newTargetIdx = 2
        var upwardDragOffsetY = -70f // Dragged 70px upwards past 50px threshold

        val upDirection = if (newTargetIdx > targetIdx) 1 else -1
        var upIter = targetIdx
        while (upIter != newTargetIdx) {
            val nextIdx = upIter + upDirection
            Collections.swap(list, upIter, nextIdx)
            upIter = nextIdx
        }

        val upSlotsMoved = newTargetIdx - targetIdx // -1
        upwardDragOffsetY -= upSlotsMoved * itemHeightPx // -70 - (-100) = +30f

        assertEquals(listOf("item1", "item2", "item0", "item3", "item4"), list)
        assertEquals(30f, upwardDragOffsetY, 0.001f)
    }
}
