package com.deadline

import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateContinuousHorizontalDisplacement
import com.deadline.ui.components.calculateHorizontalSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.calculateSlotTargetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for Intentional Drag-and-Reorder Interaction:
 * 1. Intentional Trigger Threshold (150ms hold, immediate tactile pickup haptics)
 * 2. Active Drag State Styling (elevated scale, elevation, z-index 100f, 1:1 motion)
 * 3. Fluid Dynamic Reordering & Continuous Gap Filling (spring physics, no snapping/magnetic sticking)
 * 4. Drop & Settle Physics (smooth release into target slot, matched spring, zero pop)
 * 5. Targeted Sections (Habits, Plan, Task/Projects, Notes, Timer)
 */
class IntentionalDragAndReorderTest {

    @Test
    fun testIntentionalTriggerThreshold_standardConstant() {
        // Must enforce a fast intentional hold timeout (150ms) to prevent accidental drags on fast swipes
        assertEquals(150L, DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS)
    }

    @Test
    fun testSettleTargetOffset_downwardReorder() {
        val slotHeight = 80f
        val startIdx = 0
        val targetIdx = 2

        val settleOffset = calculateSettleTargetOffset(startIdx, targetIdx, slotHeight)
        // Displaced by exactly 2 slots downward: 2 * 80f = 160f
        assertEquals(160f, settleOffset, 0.001f)
    }

    @Test
    fun testSettleTargetOffset_upwardReorder() {
        val slotHeight = 92f
        val startIdx = 3
        val targetIdx = 1

        val settleOffset = calculateSettleTargetOffset(startIdx, targetIdx, slotHeight)
        // Displaced by 2 slots upward: -2 * 92f = -184f
        assertEquals(-184f, settleOffset, 0.001f)
    }

    @Test
    fun testSettleTargetOffset_sameSlotDropOrCancel() {
        val slotHeight = 76f
        val startIdx = 2
        val targetIdx = 2

        val settleOffset = calculateSettleTargetOffset(startIdx, targetIdx, slotHeight)
        // Must settle back to 0f without jumping
        assertEquals(0f, settleOffset, 0.001f)
    }

    @Test
    fun testSettleTargetOffset_horizontalCarousel() {
        val cardWidth = 220f
        val startIdx = 1
        val targetIdx = 3

        val settleOffset = calculateSettleTargetOffset(startIdx, targetIdx, cardWidth)
        assertEquals(440f, settleOffset, 0.001f)
    }

    @Test
    fun testDropAndSettleContinuity_zeroVisualPopMath() {
        // Given an item at slot 0 (layout base 0px), dragged to 135px with slotHeight = 100px.
        // Target slot is slot 1 (layout base 100px).
        val itemHeightPx = 100f
        val startIdx = 0
        val targetIdx = calculateSlotTargetIndex(startIdx, 135f, itemHeightPx, 5)
        assertEquals(1, targetIdx)

        // Settle target offset relative to startIdx:
        val settleOffsetY = calculateSettleTargetOffset(startIdx, targetIdx, itemHeightPx)
        assertEquals(100f, settleOffsetY, 0.001f)

        // Before list reordering: layout origin (0px) + settleOffsetY (100px) = 100px on screen.
        val onScreenBeforeCommit = (startIdx * itemHeightPx) + settleOffsetY
        // After list reordering: newly committed layout position (targetIdx * itemHeightPx) + offset (0px) = 100px.
        val onScreenAfterCommit = (targetIdx * itemHeightPx) + 0f

        // Math must guarantee 100% position continuity (zero delta, zero abrupt popping)
        assertEquals(onScreenBeforeCommit, onScreenAfterCommit, 0.001f)
    }

    @Test
    fun testContinuousGapFilling_fluidDisplacementWithoutSnapping() {
        val itemHeight = 100f
        val draggedIdx = 0

        // As dragged item moves continuously: 10px, 30px, 50px, 75px, 100px
        val displacements = listOf(10f, 30f, 50f, 75f, 100f).map { offset ->
            calculateContinuousDisplacement(
                itemIndex = 1,
                draggedIndex = draggedIdx,
                dragOffsetY = offset,
                itemHeightPx = itemHeight
            )
        }

        // Each displacement must smoothly match the continuous motion without instant jumps
        assertEquals(-10f, displacements[0], 0.01f)
        assertEquals(-30f, displacements[1], 0.01f)
        assertEquals(-50f, displacements[2], 0.01f)
        assertEquals(-75f, displacements[3], 0.01f)
        assertEquals(-100f, displacements[4], 0.01f)
    }

    @Test
    fun testContinuousHorizontalGapFilling_projectsCarousel() {
        val cardWidth = 200f
        val draggedIdx = 0

        // Dragging right continuously
        val d25 = calculateContinuousHorizontalDisplacement(1, draggedIdx, 50f, cardWidth)
        val d50 = calculateContinuousHorizontalDisplacement(1, draggedIdx, 100f, cardWidth)
        val d100 = calculateContinuousHorizontalDisplacement(1, draggedIdx, 200f, cardWidth)

        assertEquals(-50f, d25, 0.01f)
        assertEquals(-100f, d50, 0.01f)
        assertEquals(-200f, d100, 0.01f)
    }

    // --- Targeted Application Screens Code Verification ---

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("/storage/emulated/0/AndroidIDEProjects/deadline/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Source file not found: $relativePath")
    }

    @Test
    fun testHabitsSection_reorderArchitectureWiring() {
        val habitsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        val content = habitsFile.readText()

        assertTrue("HabitsScreen must import calculateSettleTargetOffset", content.contains("calculateSettleTargetOffset"))
        assertTrue("HabitsScreen must import animateDragToSettle", content.contains("animateDragToSettle"))
        assertTrue("HabitsScreen must use calculateSlotTargetIndex", content.contains("calculateSlotTargetIndex"))
        assertTrue("HabitsScreen must display explicit ReorderGrabHandleIcon", content.contains("ReorderGrabHandleIcon"))
        assertTrue("HabitsScreen must use triggerPickupHaptic for tactile feedback", content.contains("triggerPickupHaptic"))
        assertTrue("HabitsScreen must implement isHabitSettling state", content.contains("isHabitSettling"))
    }

    @Test
    fun testPlanSection_reorderArchitectureWiring() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        val planViewContent = planViewFile.readText()

        assertTrue("PlanView must import calculateSettleTargetOffset", planViewContent.contains("calculateSettleTargetOffset"))
        assertTrue("PlanView must import animateDragToSettle", planViewContent.contains("animateDragToSettle"))
        assertTrue("PlanView must display explicit ReorderGrabHandleIcon", planViewContent.contains("ReorderGrabHandleIcon"))
        assertTrue("PlanView must use triggerPickupHaptic", planViewContent.contains("triggerPickupHaptic"))
        assertTrue("PlanView must implement isGoalSettling state", planViewContent.contains("isGoalSettling"))

        val planScreenFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        val planScreenContent = planScreenFile.readText()
        assertTrue("PlanScreen must use animateDragToSettle", planScreenContent.contains("animateDragToSettle"))
        assertTrue("PlanScreen must implement isBlockSettling state", planScreenContent.contains("isBlockSettling"))
    }

    @Test
    fun testTaskSection_reorderArchitectureWiring() {
        val projectsFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        val content = projectsFile.readText()

        assertTrue("ProjectsScreen must import calculateSettleTargetOffset", content.contains("calculateSettleTargetOffset"))
        assertTrue("ProjectsScreen must import animateDragToSettle", content.contains("animateDragToSettle"))
        assertTrue("ProjectsScreen must use calculateHorizontalSlotTargetIndex for active project cards", content.contains("calculateHorizontalSlotTargetIndex"))
        assertTrue("ProjectsScreen must use calculateSlotTargetIndex for task rows", content.contains("calculateSlotTargetIndex"))
        assertTrue("ProjectsScreen must display explicit ReorderGrabHandleIcon", content.contains("ReorderGrabHandleIcon"))
        assertTrue("ProjectsScreen must use triggerPickupHaptic", content.contains("triggerPickupHaptic"))
        assertTrue("ProjectsScreen must implement isProjectSettling and isTodoSettling", content.contains("isProjectSettling") && content.contains("isTodoSettling"))
    }

    @Test
    fun testNotesSection_reorderArchitectureWiring() {
        val notesFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/notes/NotesScreen.kt")
        val content = notesFile.readText()

        assertTrue("NotesScreen must import calculateSettleTargetOffset", content.contains("calculateSettleTargetOffset"))
        assertTrue("NotesScreen must import animateDragToSettle", content.contains("animateDragToSettle"))
        assertTrue("NotesScreen must use calculateSlotTargetIndex", content.contains("calculateSlotTargetIndex"))
        assertTrue("NotesScreen must display explicit ReorderGrabHandleIcon on note items", content.contains("ReorderGrabHandleIcon"))
        assertTrue("NotesScreen must use triggerPickupHaptic", content.contains("triggerPickupHaptic"))
        assertTrue("NotesScreen must implement isNoteSettling state", content.contains("isNoteSettling"))
    }

    @Test
    fun testTimerSection_reorderArchitectureWiring() {
        val timerFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        val content = timerFile.readText()

        assertTrue("TimerScreen must import calculateSettleTargetOffset", content.contains("calculateSettleTargetOffset"))
        assertTrue("TimerScreen must import animateDragToSettle", content.contains("animateDragToSettle"))
        assertTrue("TimerScreen must use calculateSlotTargetIndex", content.contains("calculateSlotTargetIndex"))
        assertTrue("TimerScreen must display explicit ReorderGrabHandleIcon on focus preset tasks", content.contains("ReorderGrabHandleIcon"))
        assertTrue("TimerScreen must use triggerPickupHaptic", content.contains("triggerPickupHaptic"))
        assertTrue("TimerScreen must implement isTimerTaskSettling state", content.contains("isTimerTaskSettling"))
    }
}
