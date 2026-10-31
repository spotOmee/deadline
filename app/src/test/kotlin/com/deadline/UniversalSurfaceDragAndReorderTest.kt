package com.deadline

import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.QuickNote
import com.deadline.data.local.TaskStatus
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.calculateSlotTargetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UniversalSurfaceDragAndReorderTest {

    private fun getSourceFile(relativePath: String): File {
        val root = File("").absoluteFile
        val file = File(root, relativePath)
        return if (file.exists()) file else File(root.parentFile, relativePath)
    }

    @Test
    fun testUniversalSwipeableItem_bindsDragToForegroundSurfaceWithSlopGuard() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        assertTrue("UniversalSwipeableItem.kt must exist", file.exists())
        val code = file.readText()

        // 1. Full-surface drag detection bound to Foreground Content Box
        assertTrue(
            "Must use detectDragGesturesAfterLongPress on Foreground Box",
            code.contains("detectDragGesturesAfterLongPress(")
        )

        // 2. Drag coordination flags
        assertTrue(
            "Must maintain isDraggingCard state flag",
            code.contains("var isDraggingCard by remember(itemId)")
        )
        assertTrue(
            "Must maintain isSwipingHorizontal state flag",
            code.contains("var isSwipingHorizontal by remember(itemId)")
        )

        // 3. Horizontal drag has 10px slop threshold to prevent swallowing vertical hold
        assertTrue(
            "Must ignore horizontal drag when isDraggingCard is active",
            code.contains("if (isDraggingCard || isThisItemDragged) return@detectHorizontalDragGestures")
        )
        assertTrue(
            "Must enforce 10px slop threshold before locking swipe direction",
            code.contains("testDelta > 10f") && code.contains("testDelta < -10f")
        )
    }

    @Test
    fun testHabitsScreen_todayTabReorderConfiguration() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/HabitsScreen.kt")
        assertTrue("HabitsScreen.kt must exist", file.exists())
        val code = file.readText()

        // Keyed items with unique id
        assertTrue(
            "Today tab list must have itemsIndexed with unique key",
            code.contains("itemsIndexed(localHabits, key = { _, it -> it.id })")
        )

        // Fluid layout animation with Modifier.animateItem()
        assertTrue(
            "Items must apply Modifier.animateItem for gap filling",
            code.contains("Modifier.animateItem(")
        )

        // Full card surface drag via UniversalSwipeableItem
        assertTrue(
            "Habit cards must be wrapped with UniversalSwipeableItem",
            code.contains("UniversalSwipeableItem(")
        )

        // Clean separation of single tap/checkbox click from drag
        assertTrue(
            "HabitCard must guard tap gestures with isThisItemDragged",
            code.contains("if (!isThisItemDragged) onSingleTap()")
        )
        assertTrue(
            "HabitCard counter/check buttons must be guarded when dragged",
            code.contains("clickable(enabled = !isThisItemDragged, onClick = onToggle)")
        )

        // Persistence to repository
        assertTrue(
            "Reordered habits must persist via repository.reorderHabits",
            code.contains("repository.reorderHabits(localHabits)")
        )
    }

    @Test
    fun testPlanView_planTabReorderConfiguration() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", file.exists())
        val code = file.readText()

        // Keyed items
        assertTrue(
            "Plan tab list must have itemsIndexed with unique key",
            code.contains("itemsIndexed(localGoalItems, key = { _, it -> it.id })")
        )

        // Fluid layout animation
        assertTrue(
            "Items must apply Modifier.animateItem",
            code.contains("Modifier.animateItem(")
        )

        // Reordering persistence
        assertTrue(
            "Plan tasks must persist via repository.reorderPlanTasks",
            code.contains("repository.reorderPlanTasks(reorderedTaskIds)")
        )

        // Completion checkbox guarded during drag
        assertTrue(
            "Checkbox click must guard against active drag",
            code.contains("if (isThisItemDragged) return@clickable")
        )
    }

    @Test
    fun testProjectsScreen_activeTasksReorderConfiguration() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val code = file.readText()

        // Keyed items
        assertTrue(
            "Active tasks list must have itemsIndexed with unique key",
            code.contains("itemsIndexed(localTodos, key = { _, it -> it.id })")
        )

        // Fluid layout animation
        assertTrue(
            "Task items must apply Modifier.animateItem",
            code.contains("Modifier.animateItem(")
        )

        // Full card container drag via UniversalSwipeableItem
        assertTrue(
            "Task cards must be wrapped with UniversalSwipeableItem",
            code.contains("UniversalSwipeableItem(")
        )

        // Circular checkbox guarded against drag pickup
        assertTrue(
            "Circular checkbox click must be guarded with isThisItemDragged",
            code.contains("if (isThisItemDragged) return@clickable")
        )

        // Persistence to repository
        assertTrue(
            "Reordered tasks must persist via repository.reorderTodos",
            code.contains("repository.reorderTodos(localTodos)")
        )
    }

    @Test
    fun testNotesScreen_notesListReorderConfiguration() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/notes/NotesScreen.kt")
        assertTrue("NotesScreen.kt must exist", file.exists())
        val code = file.readText()

        // Keyed items
        assertTrue(
            "Notes list must have items with unique key",
            code.contains("items(otherNotes, key = { it.id })")
        )

        // Fluid layout animation
        assertTrue(
            "Note cards must apply Modifier.animateItem",
            code.contains("Modifier.animateItem(")
        )

        // Surface-level drag gesture
        assertTrue(
            "Note card surface must bind detectDragGesturesAfterLongPress directly to Surface",
            code.contains("detectDragGesturesAfterLongPress(")
        )

        // Clean separation of card tap from drag hold
        assertTrue(
            "Note card click must be guarded so drag does not open note",
            code.contains("!isDraggingCard && !isSwipingHorizontal && !isThisItemDragged")
        )

        // Persistence to repository
        assertTrue(
            "Reordered notes must persist via repository.reorderQuickNotes",
            code.contains("repository.reorderQuickNotes(localQuickNotes)")
        )
    }

    @Test
    fun testRealtimeReorderSimulation_habits() {
        val initialHabits = listOf(
            Habit(id = "h1", name = "Morning Meditation", position = 0),
            Habit(id = "h2", name = "Daily Exercise", position = 1),
            Habit(id = "h3", name = "Read 20 Pages", position = 2)
        )
        var localHabits = initialHabits
        val itemHeightPx = 120f

        // User drags h1 (index 0) down by 130px (crossing into slot 1)
        val draggedIdx = 0
        val dragOffsetY = 130f
        val targetIdx = calculateSlotTargetIndex(draggedIdx, dragOffsetY, itemHeightPx, localHabits.size)
        assertEquals(1, targetIdx)

        // Swap in-memory state for instant gap filling animation
        val reordered = localHabits.toMutableList()
        java.util.Collections.swap(reordered, 0, 1)
        localHabits = reordered
        assertEquals(listOf("h2", "h1", "h3"), localHabits.map { it.id })

        // Settle offset calculation
        val settleY = calculateSettleTargetOffset(1, 1, itemHeightPx)
        assertEquals(0f, settleY, 0.01f)

        // On release, persist updated sequence
        val persisted = localHabits.mapIndexed { idx, h -> h.copy(position = idx) }
        assertEquals(0, persisted[0].position)
        assertEquals("h2", persisted[0].id)
        assertEquals(1, persisted[1].position)
        assertEquals("h1", persisted[1].id)
        assertEquals(2, persisted[2].position)
        assertEquals("h3", persisted[2].id)
    }

    @Test
    fun testRealtimeReorderSimulation_tasks() {
        val initialTodos = listOf(
            ProjectTodo(id = "t1", title = "Setup architecture", projectId = "p1", sortOrder = 0),
            ProjectTodo(id = "t2", title = "Write unit tests", projectId = "p1", sortOrder = 1),
            ProjectTodo(id = "t3", title = "Deploy to prod", projectId = "p1", sortOrder = 2)
        )
        var localTodos = initialTodos
        val itemHeightPx = 80f

        // User drags t3 (index 2) upwards by -170px (crossing into slot 0)
        val draggedIdx = 2
        val dragOffsetY = -170f
        val targetIdx = calculateSlotTargetIndex(draggedIdx, dragOffsetY, itemHeightPx, localTodos.size)
        assertEquals(0, targetIdx)

        // Swap iteratively
        val reordered = localTodos.toMutableList()
        var iter = draggedIdx
        while (iter != targetIdx) {
            val next = iter - 1
            java.util.Collections.swap(reordered, iter, next)
            iter = next
        }
        localTodos = reordered
        assertEquals(listOf("t3", "t1", "t2"), localTodos.map { it.id })

        // Persist with reindexed positions
        val persisted = localTodos.mapIndexed { idx, t -> t.copy(sortOrder = idx) }
        assertEquals("t3", persisted[0].id)
        assertEquals(0, persisted[0].sortOrder)
        assertEquals("t1", persisted[1].id)
        assertEquals(1, persisted[1].sortOrder)
        assertEquals("t2", persisted[2].id)
        assertEquals(2, persisted[2].sortOrder)
    }

    @Test
    fun testRealtimeReorderSimulation_notes() {
        val initialNotes = listOf(
            QuickNote(id = "n1", title = "Meeting Notes", content = "Discuss roadmap", sortOrder = 0),
            QuickNote(id = "n2", title = "Architecture Ideas", content = "Compose drag and drop", sortOrder = 1),
            QuickNote(id = "n3", title = "Shopping List", content = "[ ] Milk\n[ ] Coffee", sortOrder = 2)
        )
        var localNotes = initialNotes
        val itemHeightPx = 100f

        // User drags n2 (index 1) down by 110px (crossing into slot 2)
        val draggedIdx = 1
        val dragOffsetY = 110f
        val targetIdx = calculateSlotTargetIndex(draggedIdx, dragOffsetY, itemHeightPx, localNotes.size)
        assertEquals(2, targetIdx)

        val reordered = localNotes.toMutableList()
        java.util.Collections.swap(reordered, 1, 2)
        localNotes = reordered.mapIndexed { idx, item -> item.copy(sortOrder = idx) }

        assertEquals(listOf("n1", "n3", "n2"), localNotes.map { it.id })
        assertEquals(0, localNotes[0].sortOrder)
        assertEquals(1, localNotes[1].sortOrder)
        assertEquals(2, localNotes[2].sortOrder)
    }

    @Test
    fun testConflictFreeDragGestureEngine_implementsPointerInitialConsumption() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        assertTrue("UniversalReorderArchitecture.kt must exist", file.exists())
        val code = file.readText()

        // 1. Must intercept at PointerEventPass.Initial so child modifiers (.clickable, swipe) cannot swallow the down event
        assertTrue(
            "Must await down on PointerEventPass.Initial",
            code.contains("awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)")
        )

        // 2. Must monitor events on PointerEventPass.Initial
        assertTrue(
            "Must await pointer events on PointerEventPass.Initial",
            code.contains("awaitPointerEvent(PointerEventPass.Initial)")
        )

        // 3. Must consume pointer changes upon pickup confirmation to cleanly cancel child gestures
        assertTrue(
            "Must consume all event changes upon pickup",
            code.contains("currentEvent.changes.forEach { it.consume() }")
        )
    }

    @Test
    fun testConflictFreeDragGestureEngine_isolatesVerticalDragFromHorizontalSwipe() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        assertTrue("UniversalReorderArchitecture.kt must exist", file.exists())
        val code = file.readText()

        // Intentional horizontal swipe detection
        assertTrue(
            "Must detect intentional horizontal swipe using strong horizontal bias",
            code.contains("isIntentionalHorizontalSwipe")
        )
        assertTrue(
            "Horizontal bias must require abs(dx) > abs(dy) * 1.5f",
            code.contains("kotlin.math.abs(totalOffset.x) > kotlin.math.abs(totalOffset.y) * 1.5f")
        )
    }

    @Test
    fun testConflictFreeDragGestureEngine_adjustsTouchSlopForMicroJitters() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        assertTrue("UniversalReorderArchitecture.kt must exist", file.exists())
        val code = file.readText()

        // Touch slop adjustment with multiplier
        assertTrue(
            "Must have pickupSlopMultiplier parameter",
            code.contains("pickupSlopMultiplier: Float = 2.5f")
        )
        assertTrue(
            "Must compute microJitterSlop = baseSlop * pickupSlopMultiplier",
            code.contains("val microJitterSlop = baseSlop * pickupSlopMultiplier")
        )
        assertTrue(
            "Must check totalDistance > microJitterSlop before canceling",
            code.contains("if (totalDistance > microJitterSlop)")
        )
    }

    @Test
    fun testTimerScreen_resolvesGestureConflictWithInitialPassAndReleaseProtection() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/timer/TimerScreen.kt")
        assertTrue("TimerScreen.kt must exist", file.exists())
        val code = file.readText()

        // Import the conflict-free detectDragGesturesAfterLongPress
        assertTrue(
            "TimerScreen must import com.deadline.ui.components.detectDragGesturesAfterLongPress",
            code.contains("import com.deadline.ui.components.detectDragGesturesAfterLongPress")
        )

        // Release protection with lastDragEndTime
        assertTrue(
            "TimerScreen must track lastDragEndTime",
            code.contains("var lastDragEndTime by remember(task.id)")
        )
        assertTrue(
            "TimerScreen clickable must enforce timeSinceDrag > 300L release guard",
            code.contains("timeSinceDrag > 300L")
        )
    }
}
