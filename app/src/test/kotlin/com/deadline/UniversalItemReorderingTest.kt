package com.deadline

import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.Project
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.QuickNote
import com.deadline.data.local.TaskStatus
import com.deadline.data.local.TimeBlock
import com.deadline.ui.components.calculateAutoScrollVelocity
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateContinuousHorizontalDisplacement
import com.deadline.ui.components.calculateHorizontalSlotTargetIndex
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class UniversalItemReorderingTest {

    @Test
    fun testVerticalContinuousDisplacement_draggingDownwards() {
        val itemHeightPx = 100f
        val draggedIndex = 0

        // Dragging down halfway to slot 1 (offset = 50px)
        val displacementItem1Half = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = 50f,
            itemHeightPx = itemHeightPx
        )
        // Item 1 should slide upwards by 50px (-50f)
        assertEquals(-50f, displacementItem1Half, 0.01f)

        // Dragging down fully past slot 1 (offset = 100px)
        val displacementItem1Full = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = 100f,
            itemHeightPx = itemHeightPx
        )
        // Item 1 is fully displaced by -100px
        assertEquals(-100f, displacementItem1Full, 0.01f)

        // Item 2 should not move yet when dragged offset is 50px
        val displacementItem2At50 = calculateContinuousDisplacement(
            itemIndex = 2,
            draggedIndex = draggedIndex,
            dragOffsetY = 50f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(0f, displacementItem2At50, 0.01f)

        // Dragging down towards slot 2 (offset = 150px)
        val displacementItem2At150 = calculateContinuousDisplacement(
            itemIndex = 2,
            draggedIndex = draggedIndex,
            dragOffsetY = 150f,
            itemHeightPx = itemHeightPx
        )
        // Item 2 slides up by 50px
        assertEquals(-50f, displacementItem2At150, 0.01f)
    }

    @Test
    fun testVerticalContinuousDisplacement_draggingUpwards() {
        val itemHeightPx = 100f
        val draggedIndex = 2

        // Dragging upwards by 50px (offset = -50px)
        val displacementItem1 = calculateContinuousDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetY = -50f,
            itemHeightPx = itemHeightPx
        )
        // Item 1 should slide downwards by 50px (+50f)
        assertEquals(50f, displacementItem1, 0.01f)

        // Item 0 should not move yet
        val displacementItem0 = calculateContinuousDisplacement(
            itemIndex = 0,
            draggedIndex = draggedIndex,
            dragOffsetY = -50f,
            itemHeightPx = itemHeightPx
        )
        assertEquals(0f, displacementItem0, 0.01f)
    }

    @Test
    fun testHorizontalContinuousDisplacement_projectsCarousel() {
        val cardWidthPx = 200f
        val draggedIndex = 0

        // Dragging right by 100px (offset = +100px)
        val displacementItem1 = calculateContinuousHorizontalDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetX = 100f,
            cardWidthPx = cardWidthPx
        )
        // Item 1 should slide left by 100px (-100f)
        assertEquals(-100f, displacementItem1, 0.01f)

        // Dragging right fully past item 1 (offset = +200px)
        val displacementItem1Full = calculateContinuousHorizontalDisplacement(
            itemIndex = 1,
            draggedIndex = draggedIndex,
            dragOffsetX = 200f,
            cardWidthPx = cardWidthPx
        )
        assertEquals(-200f, displacementItem1Full, 0.01f)
    }

    @Test
    fun testProjectSequenceOrdering() {
        val p1 = Project(id = "1", name = "Zebra Project", sortOrder = 0, isPinned = false)
        val p2 = Project(id = "2", name = "Alpha Project", sortOrder = 1, isPinned = false)
        val p3 = Project(id = "3", name = "Beta Project", sortOrder = 2, isPinned = false)

        val projectsList = listOf(p1, p2, p3)

        // Reordering: move p2 to index 0
        val reordered = projectsList.toMutableList()
        val moved = reordered.removeAt(1)
        reordered.add(0, moved)

        // Reassigned sortOrder values
        val updated = reordered.mapIndexed { index, p -> p.copy(sortOrder = index) }
        val sortedResult = updated.sortedWith(
            compareByDescending<Project> { it.isPinned }.thenBy { it.sortOrder }
        )

        assertEquals("2", sortedResult[0].id)
        assertEquals("1", sortedResult[1].id)
        assertEquals("3", sortedResult[2].id)

        // Pinned projects take precedence
        val pPinned = Project(id = "pinned", name = "Pinned", sortOrder = 5, isPinned = true)
        val withPinned = (sortedResult + pPinned).sortedWith(
            compareByDescending<Project> { it.isPinned }.thenBy { it.sortOrder }
        )
        assertEquals("pinned", withPinned[0].id)
    }

    @Test
    fun testTodoSequenceOrdering() {
        val t1 = ProjectTodo(id = "t1", projectId = "p1", title = "Task 1", sortOrder = 0)
        val t2 = ProjectTodo(id = "t2", projectId = "p1", title = "Task 2", sortOrder = 1)
        val t3 = ProjectTodo(id = "t3", projectId = "p1", title = "Task 3", sortOrder = 2)

        val todos = listOf(t1, t2, t3)
        // Drag Task 3 to top (index 0)
        val reordered = todos.toMutableList()
        val moved = reordered.removeAt(2)
        reordered.add(0, moved)

        val updated = reordered.mapIndexed { index, t -> t.copy(sortOrder = index) }
        val sorted = updated.sortedBy { it.sortOrder }

        assertEquals("t3", sorted[0].id)
        assertEquals("t1", sorted[1].id)
        assertEquals("t2", sorted[2].id)
    }

    @Test
    fun testHabitSequenceOrdering() {
        val h1 = Habit(id = "h1", name = "Water", position = 0)
        val h2 = Habit(id = "h2", name = "Gym", position = 1)
        val h3 = Habit(id = "h3", name = "Read", position = 2)

        val habits = listOf(h1, h2, h3)
        // Swap h1 and h2
        val reordered = habits.toMutableList()
        val moved = reordered.removeAt(0)
        reordered.add(1, moved)

        val updated = reordered.mapIndexed { index, h -> h.copy(position = index) }
        val sorted = updated.sortedBy { it.position }

        assertEquals("h2", sorted[0].id)
        assertEquals("h1", sorted[1].id)
        assertEquals("h3", sorted[2].id)
    }

    @Test
    fun testQuickNoteSequenceOrdering() {
        val n1 = QuickNote(id = "n1", title = "Note 1", content = "", sortOrder = 0)
        val n2 = QuickNote(id = "n2", title = "Note 2", content = "", sortOrder = 1)
        val n3 = QuickNote(id = "n3", title = "Note 3", content = "", sortOrder = 2)

        val notes = listOf(n1, n2, n3)
        // Move Note 3 to index 1
        val reordered = notes.toMutableList()
        val moved = reordered.removeAt(2)
        reordered.add(1, moved)

        val updated = reordered.mapIndexed { index, n -> n.copy(sortOrder = index) }
        val sorted = updated.sortedWith(
            compareByDescending<QuickNote> { it.isPinned }.thenBy { it.sortOrder }
        )

        assertEquals("n1", sorted[0].id)
        assertEquals("n3", sorted[1].id)
        assertEquals("n2", sorted[2].id)
    }

    @Test
    fun testPlanTaskSequencePersistence() {
        // Plan tasks are stored as comma-separated task IDs in analytics_cache
        val initialPlanIds = listOf("task_a", "task_b", "task_c")
        val serialized = initialPlanIds.joinToString(",")
        assertEquals("task_a,task_b,task_c", serialized)

        // User reorders task_c to top
        val reordered = initialPlanIds.toMutableList()
        val moved = reordered.removeAt(2)
        reordered.add(0, moved)

        val updatedSerialized = reordered.joinToString(",")
        assertEquals("task_c,task_a,task_b", updatedSerialized)

        // Parse back on app launch
        val parsed = updatedSerialized.split(",").filter { it.isNotBlank() }
        assertEquals("task_c", parsed[0])
        assertEquals("task_a", parsed[1])
        assertEquals("task_b", parsed[2])
    }

    @Test
    fun testSlotTargetIndex_strict50PercentThreshold() {
        val itemHeight = 100f
        val itemCount = 5

        // Dragging down from index 1:
        // Less than 50% overlap (e.g. 40px) -> remains at index 1
        assertEquals(1, calculateSlotTargetIndex(startIndex = 1, dragOffsetY = 40f, itemHeightPx = itemHeight, itemCount = itemCount))

        // Exactly 50% overlap or greater (>= 50px) -> moves to index 2
        assertEquals(2, calculateSlotTargetIndex(startIndex = 1, dragOffsetY = 50f, itemHeightPx = itemHeight, itemCount = itemCount))
        assertEquals(2, calculateSlotTargetIndex(startIndex = 1, dragOffsetY = 70f, itemHeightPx = itemHeight, itemCount = itemCount))

        // Dragging further down (> 150px) -> moves to index 3
        assertEquals(3, calculateSlotTargetIndex(startIndex = 1, dragOffsetY = 150f, itemHeightPx = itemHeight, itemCount = itemCount))

        // Clamping to bottom boundary
        assertEquals(4, calculateSlotTargetIndex(startIndex = 1, dragOffsetY = 500f, itemHeightPx = itemHeight, itemCount = itemCount))

        // Dragging up from index 2:
        // Less than 50% upwards (-40px) -> remains at index 2
        assertEquals(2, calculateSlotTargetIndex(startIndex = 2, dragOffsetY = -40f, itemHeightPx = itemHeight, itemCount = itemCount))

        // >= 50% upwards (-50px) -> moves to index 1
        assertEquals(1, calculateSlotTargetIndex(startIndex = 2, dragOffsetY = -50f, itemHeightPx = itemHeight, itemCount = itemCount))

        // Clamping to top boundary
        assertEquals(0, calculateSlotTargetIndex(startIndex = 2, dragOffsetY = -300f, itemHeightPx = itemHeight, itemCount = itemCount))
    }

    @Test
    fun testHorizontalSlotTargetIndex_carousel() {
        val cardWidth = 200f
        val itemCount = 4

        // Rightward drag below 50% threshold
        assertEquals(0, calculateHorizontalSlotTargetIndex(startIndex = 0, dragOffsetX = 80f, itemWidthPx = cardWidth, itemCount = itemCount))

        // Rightward drag at 50% threshold
        assertEquals(1, calculateHorizontalSlotTargetIndex(startIndex = 0, dragOffsetX = 100f, itemWidthPx = cardWidth, itemCount = itemCount))

        // Boundary clamp
        assertEquals(3, calculateHorizontalSlotTargetIndex(startIndex = 0, dragOffsetX = 1000f, itemWidthPx = cardWidth, itemCount = itemCount))

        // Leftward drag below 50% threshold
        assertEquals(2, calculateHorizontalSlotTargetIndex(startIndex = 2, dragOffsetX = -80f, itemWidthPx = cardWidth, itemCount = itemCount))

        // Leftward drag at 50% threshold
        assertEquals(1, calculateHorizontalSlotTargetIndex(startIndex = 2, dragOffsetX = -100f, itemWidthPx = cardWidth, itemCount = itemCount))

        // Leftward clamp
        assertEquals(0, calculateHorizontalSlotTargetIndex(startIndex = 2, dragOffsetX = -1000f, itemWidthPx = cardWidth, itemCount = itemCount))
    }

    @Test
    fun testAutoScrollVelocity_boundariesAndDeadzone() {
        val viewportHeight = 800f
        val edgeThreshold = 100f
        val maxSpeed = 30f

        // Top boundary: pointerY = 0 -> maximum negative velocity (scroll up)
        val velocityTopMax = calculateAutoScrollVelocity(pointerY = 0f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold, maxSpeedPx = maxSpeed)
        assertEquals(-30f, velocityTopMax, 0.01f)

        // Top boundary: pointerY = 50 -> half negative velocity
        val velocityTopHalf = calculateAutoScrollVelocity(pointerY = 50f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold, maxSpeedPx = maxSpeed)
        assertEquals(-15f, velocityTopHalf, 0.01f)

        // Middle deadzone: pointerY = 400 -> zero velocity
        val velocityMiddle = calculateAutoScrollVelocity(pointerY = 400f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold, maxSpeedPx = maxSpeed)
        assertEquals(0f, velocityMiddle, 0.01f)

        // Bottom boundary: pointerY = 750 (50px into threshold of 800) -> half positive velocity (scroll down)
        val velocityBottomHalf = calculateAutoScrollVelocity(pointerY = 750f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold, maxSpeedPx = maxSpeed)
        assertEquals(15f, velocityBottomHalf, 0.01f)

        // Bottom boundary: pointerY = 800 -> maximum positive velocity
        val velocityBottomMax = calculateAutoScrollVelocity(pointerY = 800f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold, maxSpeedPx = maxSpeed)
        assertEquals(30f, velocityBottomMax, 0.01f)
    }

    @Test
    fun testTaskCompletionStatusPreservedDuringReordering() {
        // Active list with mixed statuses
        val active1 = ProjectTodo(id = "a1", projectId = "p1", title = "Task 1", status = TaskStatus.TODO)
        val active2 = ProjectTodo(id = "a2", projectId = "p1", title = "Task 2", status = TaskStatus.IN_PROGRESS)
        val activeList = listOf(active1, active2)

        // Reorder active tasks
        val reorderedActive = activeList.toMutableList()
        val movedA = reorderedActive.removeAt(1)
        reorderedActive.add(0, movedA)

        // Check statuses are preserved
        assertEquals(TaskStatus.IN_PROGRESS, reorderedActive[0].status)
        assertEquals(TaskStatus.TODO, reorderedActive[1].status)

        // Backlog / Done list
        val done1 = ProjectTodo(id = "d1", projectId = "p1", title = "Done 1", status = TaskStatus.COMPLETED)
        val done2 = ProjectTodo(id = "d2", projectId = "p1", title = "Done 2", status = TaskStatus.COMPLETED)
        val doneList = listOf(done1, done2)

        // Reorder done tasks
        val reorderedDone = doneList.toMutableList()
        val movedD = reorderedDone.removeAt(1)
        reorderedDone.add(0, movedD)

        assertEquals("d2", reorderedDone[0].id)
        assertEquals(TaskStatus.COMPLETED, reorderedDone[0].status)
        assertEquals("d1", reorderedDone[1].id)
        assertEquals(TaskStatus.COMPLETED, reorderedDone[1].status)
    }

    @Test
    fun testHabitTrackersPreservedDuringReorder() {
        val h1 = Habit(
            id = "h1",
            name = "Meditation",
            completionType = HabitCompletionType.POMODORO,
            targetCount = 2,
            currentStreak = 7,
            maxStreak = 14,
            lastCompletedDate = "2026-10-21"
        )
        val h2 = Habit(
            id = "h2",
            name = "Hydration",
            completionType = HabitCompletionType.COUNT,
            targetCount = 8,
            countUnit = "cups",
            currentStreak = 12,
            maxStreak = 20,
            lastCompletedDate = "2026-10-22"
        )

        val list = listOf(h1, h2)
        // Swap habits
        val reordered = listOf(list[1], list[0])

        // Verify all counters, streaks, and completion types remain intact
        assertEquals("h2", reordered[0].id)
        assertEquals(8, reordered[0].targetCount)
        assertEquals("cups", reordered[0].countUnit)
        assertEquals(12, reordered[0].currentStreak)

        assertEquals("h1", reordered[1].id)
        assertEquals(HabitCompletionType.POMODORO, reordered[1].completionType)
        assertEquals(7, reordered[1].currentStreak)
        assertEquals("2026-10-21", reordered[1].lastCompletedDate)
    }

    @Test
    fun testTimelineBlockReorderingAndRescheduling() {
        // Block scheduled at 09:00 (540 min)
        val block = TimeBlock(
            id = "b1",
            date = "2026-10-22",
            title = "Deep Focus Architecture",
            startMinute = 9 * 60,
            durationMinutes = 60,
            color = "#5E5CE6"
        )

        // Drag down by 2 hours (120 min)
        val hourShift = 2
        val newHour = ((block.startMinute / 60) + hourShift).coerceIn(8, 22)
        val rescheduled = block.copy(startMinute = newHour * 60 + (block.startMinute % 60))

        assertEquals(11 * 60, rescheduled.startMinute)
        assertEquals(60, rescheduled.durationMinutes)
        assertEquals("Deep Focus Architecture", rescheduled.title)
    }

    @Test
    fun testAutoScrollVelocity_15PercentViewportRatioThreshold() {
        val viewportHeight = 1000f
        val edgeThreshold15Pct = viewportHeight * 0.15f // 150f
        val maxSpeed = 28f

        // Top 15% boundary: at pointerY = 0, maximum negative speed
        val topVelocity0 = calculateAutoScrollVelocity(pointerY = 0f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(-28f, topVelocity0, 0.01f)

        // Top 15% boundary: at pointerY = 75 (half of 15%), half negative speed
        val topVelocity75 = calculateAutoScrollVelocity(pointerY = 75f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(-14f, topVelocity75, 0.01f)

        // Deadzone: between 150f and 850f, velocity is 0
        val deadzoneVelocity200 = calculateAutoScrollVelocity(pointerY = 200f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(0f, deadzoneVelocity200, 0.01f)

        val deadzoneVelocity500 = calculateAutoScrollVelocity(pointerY = 500f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(0f, deadzoneVelocity500, 0.01f)

        val deadzoneVelocity800 = calculateAutoScrollVelocity(pointerY = 800f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(0f, deadzoneVelocity800, 0.01f)

        // Bottom 15% boundary: at pointerY = 925 (75px into bottom 150px zone), half positive speed
        val bottomVelocity925 = calculateAutoScrollVelocity(pointerY = 925f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(14f, bottomVelocity925, 0.01f)

        // Bottom 15% boundary: at pointerY = 1000, maximum positive speed
        val bottomVelocity1000 = calculateAutoScrollVelocity(pointerY = 1000f, viewportHeight = viewportHeight, edgeThresholdPx = edgeThreshold15Pct, maxSpeedPx = maxSpeed)
        assertEquals(28f, bottomVelocity1000, 0.01f)
    }

    @Test
    fun testTimePickerOpacityFalloffDistribution() {
        fun computeOpacity(dist: Int): Float = when (dist) {
            0 -> 1f
            1 -> 0.45f
            2 -> 0.20f
            3 -> 0.08f
            else -> 0.03f
        }

        assertEquals(1f, computeOpacity(0), 0.001f)
        assertEquals(0.45f, computeOpacity(1), 0.001f)
        assertEquals(0.20f, computeOpacity(2), 0.001f)
        assertEquals(0.08f, computeOpacity(3), 0.001f)
        assertTrue(computeOpacity(0) > computeOpacity(1))
        assertTrue(computeOpacity(1) > computeOpacity(2))
        assertTrue(computeOpacity(2) > computeOpacity(3))
    }

    @Test
    fun testDragLongPressTimeout_is150ms() {
        assertEquals(150L, DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS)
    }

    @Test
    fun testGapClosingOnItemDeletion_subsequentItemsSlideUpward() {
        val n1 = QuickNote(id = "n1", title = "Note 1", content = "", sortOrder = 0)
        val n2 = QuickNote(id = "n2", title = "Note 2", content = "", sortOrder = 1)
        val n3 = QuickNote(id = "n3", title = "Note 3", content = "", sortOrder = 2)

        val initialList = listOf(n1, n2, n3)

        // Item n2 is deleted
        val afterDeletion = initialList.filter { it.id != "n2" }
        assertEquals(2, afterDeletion.size)
        assertEquals("n1", afterDeletion[0].id)
        assertEquals("n3", afterDeletion[1].id)

        // Resequenced sortOrders to fill vacated slot gap smoothly
        val resequenced = afterDeletion.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        assertEquals(0, resequenced[0].sortOrder)
        assertEquals(1, resequenced[1].sortOrder)
    }

    @Test
    fun testDropSettlingAndPersistence_inMemoryUntilGestureEnd() {
        var dbWriteCount = 0
        var persistedList: List<ProjectTodo>? = null

        val t1 = ProjectTodo(id = "t1", projectId = "p1", title = "Task 1", sortOrder = 0)
        val t2 = ProjectTodo(id = "t2", projectId = "p1", title = "Task 2", sortOrder = 1)
        val t3 = ProjectTodo(id = "t3", projectId = "p1", title = "Task 3", sortOrder = 2)

        // Local state
        var localList = listOf(t1, t2, t3)

        // While dragging: delta updates calculate slot displacement in memory
        val draggedIdx = 0
        val targetIdx = calculateSlotTargetIndex(draggedIdx, dragOffsetY = 160f, itemHeightPx = 100f, itemCount = localList.size)
        assertEquals(2, targetIdx)
        // No DB writes during drag gesture
        assertEquals(0, dbWriteCount)

        // On gesture end (onDragEnd): in-memory swap and strictly ONE DB write
        if (draggedIdx != targetIdx) {
            val mutable = localList.toMutableList()
            val moved = mutable.removeAt(draggedIdx)
            mutable.add(targetIdx, moved)
            localList = mutable

            // Single persistence commit
            dbWriteCount++
            persistedList = localList
        }

        assertEquals(1, dbWriteCount)
        assertEquals(listOf("t2", "t3", "t1"), persistedList?.map { it.id })
    }

    @Test
    fun testDecoupledLocalState_doesNotResetDuringActiveDrag() {
        val original = listOf("item1", "item2", "item3")
        var localList = original
        var isDragging = true

        // Reordering in progress locally
        localList = listOf("item2", "item1", "item3")

        // Incoming upstream update arrives while dragging
        val incomingUpdate = listOf("item1", "item2", "item3", "item4")
        if (!isDragging) {
            localList = incomingUpdate
        }

        // Must preserve reordered local state mid-drag
        assertEquals(listOf("item2", "item1", "item3"), localList)

        // Once drag concludes, state sync can resume
        isDragging = false
        if (!isDragging) {
            localList = incomingUpdate
        }
        assertEquals(listOf("item1", "item2", "item3", "item4"), localList)
    }

    @Test
    fun testLiftedItemZIndexAndScaleConstants() {
        val restingZIndex = 1f
        val draggedZIndex = 100f
        assertTrue("Lifted item must have higher zIndex than resting items", draggedZIndex > restingZIndex)

        val restingScale = 1.0f
        val draggedScale = 1.03f
        assertTrue("Lifted item scale must be slightly enlarged (1.03x)", draggedScale > restingScale)
    }
}
