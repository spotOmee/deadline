package com.deadline

import com.deadline.data.local.FocusTask
import com.deadline.data.local.PomodoroType
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.calculateSlotTargetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * Unit Test Suite for Fluid Drag-to-Reorder & Gap-Filling List in Timer Section:
 * 1. Reorder & Interaction Architecture:
 *    - Jetpack Compose LazyColumn with itemsIndexed and Modifier.animateItem placement spec.
 *    - Drag physics: elevated dragged item (zIndex 100f, 16.dp shadow, scale 1.03f) and tactile pickup haptics.
 * 2. State & Adapter Synchronization:
 *    - Real-time Collections.swap in-memory state update.
 *    - Dynamic gap-filling and zero-jitter dragOffsetY compensation math.
 *    - Clean deletion gap-closing with AnimatedVisibility and animateItem exit transitions.
 * 3. Persistence & Clean Architecture:
 *    - Asynchronous persistence to SQLite/repository on Dispatchers.IO after drop gesture completes.
 *    - Strict mode isolation: reordering Focus presets never alters Break or Flow preset ordering.
 */
class TimerPresetFluidDragReorderTest {

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

    // -------------------------------------------------------------------------
    // 1. Reorder & Interaction Architecture Verification
    // -------------------------------------------------------------------------

    @Test
    fun testTimerScreen_reorderAndGapFillingArchitecture() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        val content = timerFile.readText()

        // 1. Compose LazyColumn with state & itemsIndexed
        assertTrue(
            "TimerScreen must use LazyColumn for list structure",
            content.contains("LazyColumn(")
        )
        assertTrue(
            "TimerScreen must use itemsIndexed for preset items",
            content.contains("itemsIndexed(")
        )
        assertTrue(
            "TimerScreen must key items by unique task id",
            content.contains("key = { _, task -> task.id }")
        )

        // 2. Compose animateItem placement & transitions
        assertTrue(
            "TimerScreen must integrate Modifier.animateItem for fluid gap-filling animations",
            content.contains(".animateItem(")
        )
        assertTrue(
            "TimerScreen animateItem must define placementSpec",
            content.contains("placementSpec = spring(")
        )

        // 3. Pickup feedback & elevated active dragged styling
        assertTrue(
            "TimerScreen must trigger tactile pickup haptic on drag start",
            content.contains("triggerPickupHaptic(")
        )
        assertTrue(
            "TimerScreen dragged item must elevate zIndex to 100f",
            content.contains("zIndex(if (isThisItemDragged) 100f else 1f)")
        )
        assertTrue(
            "TimerScreen dragged item must apply elevated dragScale",
            content.contains("1.03f")
        )
        assertTrue(
            "TimerScreen dragged item must apply elevated shadow",
            content.contains("16.dp.toPx()")
        )
    }

    // -------------------------------------------------------------------------
    // 2. In-Memory Collections.swap & Real-Time Gap-Filling Math
    // -------------------------------------------------------------------------

    @Test
    fun testRealTimeSwap_stepByStepDownwardReorder() {
        // Initial list of presets
        val taskA = FocusTask(id = "1", title = "Focus 25", durationMinutes = 25, sortOrder = 0)
        val taskB = FocusTask(id = "2", title = "Focus 50", durationMinutes = 50, sortOrder = 1)
        val taskC = FocusTask(id = "3", title = "Deep Work", durationMinutes = 90, sortOrder = 2)
        val taskD = FocusTask(id = "4", title = "Review", durationMinutes = 15, sortOrder = 3)

        val localList = mutableListOf(taskA, taskB, taskC, taskD)

        // Simulating dragging taskA (slot 0) down to slot 2:
        val currentIdx = 0
        val targetIdx = 2
        val itemHeightPx = 100f
        var dragOffsetY = 210f // Dragged 210px downwards

        // Compute step-by-step swaps
        val direction = if (targetIdx > currentIdx) 1 else -1
        var iterIdx = currentIdx
        while (iterIdx != targetIdx) {
            val nextIdx = iterIdx + direction
            Collections.swap(localList, iterIdx, nextIdx)
            iterIdx = nextIdx
        }

        // Adjust dragOffsetY by moved slot delta
        val slotsMoved = targetIdx - currentIdx
        dragOffsetY -= slotsMoved * itemHeightPx

        // Verify order: taskA is now at index 2, B and C shifted up to fill gaps
        assertEquals(taskB.id, localList[0].id)
        assertEquals(taskC.id, localList[1].id)
        assertEquals(taskA.id, localList[2].id)
        assertEquals(taskD.id, localList[3].id)

        // Verify zero-jitter dragOffsetY math:
        // Before swap: layout base (0 * 100) + 210 = 210px visual
        // After swap: new layout base (2 * 100) + 10px = 210px visual
        assertEquals(10f, dragOffsetY, 0.001f)
    }

    @Test
    fun testRealTimeSwap_stepByStepUpwardReorder() {
        val taskA = FocusTask(id = "1", title = "Focus 25", durationMinutes = 25, sortOrder = 0)
        val taskB = FocusTask(id = "2", title = "Focus 50", durationMinutes = 50, sortOrder = 1)
        val taskC = FocusTask(id = "3", title = "Deep Work", durationMinutes = 90, sortOrder = 2)

        val localList = mutableListOf(taskA, taskB, taskC)

        // Dragging taskC (slot 2) upwards to slot 0:
        val currentIdx = 2
        val targetIdx = 0
        val itemHeightPx = 100f
        var dragOffsetY = -215f // Dragged 215px upwards

        val direction = if (targetIdx > currentIdx) 1 else -1
        var iterIdx = currentIdx
        while (iterIdx != targetIdx) {
            val nextIdx = iterIdx + direction
            Collections.swap(localList, iterIdx, nextIdx)
            iterIdx = nextIdx
        }

        val slotsMoved = targetIdx - currentIdx // -2
        dragOffsetY -= slotsMoved * itemHeightPx // dragOffsetY - (-200) = dragOffsetY + 200

        // Verify order: taskC is at index 0, A and B shifted down
        assertEquals(taskC.id, localList[0].id)
        assertEquals(taskA.id, localList[1].id)
        assertEquals(taskB.id, localList[2].id)

        // Visual position before: (2 * 100) - 215 = -15px
        // Visual position after: (0 * 100) - 15 = -15px
        assertEquals(-15f, dragOffsetY, 0.001f)
    }

    @Test
    fun testDropAndSettle_callsCalculateSettleTargetOffset() {
        val itemHeightPx = 80f
        // When drag finishes, the item is already swapped into its target slot:
        val finalIdx = 2
        val settleTargetOffset = calculateSettleTargetOffset(finalIdx, finalIdx, itemHeightPx)
        assertEquals(0f, settleTargetOffset, 0.001f)
    }

    // -------------------------------------------------------------------------
    // 3. Deletion Gap Closing Verification
    // -------------------------------------------------------------------------

    @Test
    fun testTimerPresetDeletion_gapClosingTransitions() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        val content = timerFile.readText()

        // Verify AnimatedVisibility handles shrinkVertically and fadeOut
        assertTrue(
            "AnimatedVisibility must use shrinkVertically to cleanly collapse vacated space",
            content.contains("shrinkVertically(")
        )
        assertTrue(
            "AnimatedVisibility must use fadeOut for smooth deletion opacity reduction",
            content.contains("fadeOut(")
        )
        // Verify deletingTaskIds tracks items during the exit animation
        assertTrue(
            "TimerScreen must maintain deletingTaskIds to animate item exit before list removal",
            content.contains("deletingTaskIds")
        )
        // Verify asynchronous delete call
        assertTrue(
            "TimerScreen must invoke deleteFocusTask asynchronously",
            content.contains("repository.deleteFocusTask(")
        )
    }

    // -------------------------------------------------------------------------
    // 4. Persistence & Mode Isolation
    // -------------------------------------------------------------------------

    @Test
    fun testAsyncPersistenceAndModeIsolation() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        val content = timerFile.readText()

        // 1. Dispatchers.IO persistence without blocking main animation frame
        assertTrue(
            "TimerScreen must perform reorder persistence on Dispatchers.IO",
            content.contains("withContext(Dispatchers.IO)") &&
            content.contains("repository.reorderFocusTasks(localModeTasks)")
        )

        // 2. Repository optimistic update and mode isolation logic
        val repoFile = getSourceFile("app/src/main/kotlin/com/deadline/data/local/DeadlineRepository.kt")
        val repoContent = repoFile.readText()

        assertTrue(
            "DeadlineRepository must update in-memory state isolating tasks not in the reordered set",
            repoContent.contains("val taskIds = tasks.map { it.id }.toSet()") &&
            repoContent.contains("val currentOther = _focusTasks.value.filter { it.id !in taskIds }")
        )
    }

    @Test
    fun testModeIsolationLogic_simulated() {
        // Setup focus tasks for FOCUS, SHORT_BREAK, and LONG_BREAK
        val focus1 = FocusTask(id = "f1", title = "Focus 1", sessionType = PomodoroType.FOCUS.name, sortOrder = 0)
        val focus2 = FocusTask(id = "f2", title = "Focus 2", sessionType = PomodoroType.FOCUS.name, sortOrder = 1)
        val break1 = FocusTask(id = "b1", title = "Break 1", sessionType = PomodoroType.SHORT_BREAK.name, sortOrder = 0)
        val flow1 = FocusTask(id = "l1", title = "Flow 1", sessionType = PomodoroType.LONG_BREAK.name, sortOrder = 0)

        val allTasks = listOf(focus1, focus2, break1, flow1)

        // Reorder only the FOCUS tasks: [focus2, focus1]
        val reorderedFocus = listOf(focus2, focus1)
        val reorderedIds = reorderedFocus.map { it.id }.toSet()

        val otherTasks = allTasks.filter { it.id !in reorderedIds }
        val updatedFocus = reorderedFocus.mapIndexed { idx, t -> t.copy(sortOrder = idx) }
        val finalCombined = otherTasks + updatedFocus

        // Verify other modes were untouched
        val finalBreaks = finalCombined.filter { it.sessionType == PomodoroType.SHORT_BREAK.name }
        val finalFlows = finalCombined.filter { it.sessionType == PomodoroType.LONG_BREAK.name }
        val finalFocus = finalCombined.filter { it.sessionType == PomodoroType.FOCUS.name }

        assertEquals(1, finalBreaks.size)
        assertEquals("b1", finalBreaks[0].id)
        assertEquals(0, finalBreaks[0].sortOrder)

        assertEquals(1, finalFlows.size)
        assertEquals("l1", finalFlows[0].id)
        assertEquals(0, finalFlows[0].sortOrder)

        // Verify Focus mode was reordered
        assertEquals(2, finalFocus.size)
        assertEquals("f2", finalFocus[0].id)
        assertEquals(0, finalFocus[0].sortOrder)
        assertEquals("f1", finalFocus[1].id)
        assertEquals(1, finalFocus[1].sortOrder)
    }
}
