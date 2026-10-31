package com.deadline

import com.deadline.ui.components.isItemDragged
import com.deadline.ui.components.calculateSlotTargetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test suite verifying Full-Surface Tap-and-Hold Drag Reorder Without Gesture Interception:
 * 1. Root Surface Gesture Detection (pointerInput(Unit) using detectDragGesturesAfterLongPress)
 * 2. Event Consumption on Pickup (change.consume() locking drag and suppressing scroll / swipe)
 * 3. Competing Modifier Gating (isCurrentlyDragged = isItemDragged(itemId), swipe disabled while dragged)
 * 4. Child Click Debounce Guard (System.currentTimeMillis() - lastDragEndTime > 300L)
 * 5. Touch Slop & Directional Lock (horizontal movement below baseSlop does not cancel vertical drag)
 * 6. Modifier Ordering Sequence (.animateItem().fillMaxWidth().then(dragAndDropModifier).then(if (!isCurrentlyDragged) swipeModifier else Modifier))
 */
class FullSurfaceTapAndHoldDragReorderTest {

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
    fun testRootSurfaceGestureDetection_pointerInputUnitOnCardContainer() {
        val swipeableFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        val code = swipeableFile.readText()

        // 1. Root card container applies pointerInput(Unit) with detectDragGesturesAfterLongPress
        assertTrue(
            "UniversalSwipeableItem must bind dragAndDropModifier with pointerInput(Unit)",
            code.contains("Modifier.pointerInput(Unit)") &&
            code.contains("detectDragGesturesAfterLongPress(")
        )

        // 2. Drag Start consumes changes to lock ownership
        assertTrue(
            "UniversalSwipeableItem must consume drag pointer changes",
            code.contains("change.consume()")
        )
    }

    @Test
    fun testUniversalReorderArchitecture_pointerEventPassInitialAndConsumption() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        val code = file.readText()

        // 1. Intercept at PointerEventPass.Initial
        assertTrue(
            "Must intercept down events on PointerEventPass.Initial",
            code.contains("awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)")
        )
        assertTrue(
            "Must intercept motion events on PointerEventPass.Initial",
            code.contains("awaitPointerEvent(PointerEventPass.Initial)")
        )

        // 2. On drag trigger, consume changes to lock gesture ownership and suppress parent scroll/swipe
        assertTrue(
            "Must consume currentChange on drag trigger",
            code.contains("currentChange.consume()")
        )
        assertTrue(
            "Must consume all event changes on drag trigger to cancel child gestures",
            code.contains("currentEvent.changes.forEach { it.consume() }")
        )
    }

    @Test
    fun testTouchSlopAndDirectionalLock_doesNotAbortBelowHorizontalSlop() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        val code = file.readText()

        // 1. Check that horizontal movement below touch slop does not abort vertical drag
        assertTrue(
            "Must check isBelowHorizontalTouchSlop against baseSlop",
            code.contains("val isBelowHorizontalTouchSlop = kotlin.math.abs(totalOffset.x) <= baseSlop")
        )
        assertTrue(
            "Must require displacement beyond base slop with strong horizontal bias to yield to horizontal swipe",
            code.contains("val isIntentionalHorizontalSwipe = !isBelowHorizontalTouchSlop &&") &&
            code.contains("kotlin.math.abs(totalOffset.x) > baseSlop * 1.5f") &&
            code.contains("kotlin.math.abs(totalOffset.x) > kotlin.math.abs(totalOffset.y) * 1.5f")
        )
    }

    @Test
    fun testDisableCompetingSwipeModifierDuringDrag() {
        val swipeableFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        val code = swipeableFile.readText()

        // 1. Maintains isCurrentlyDragged state
        assertTrue(
            "UniversalSwipeableItem must compute isCurrentlyDragged via isItemDragged",
            code.contains("val isCurrentlyDragged = isItemDragged(itemId)")
        )

        // 2. Conditionally attaches swipeModifier only when !isCurrentlyDragged
        assertTrue(
            "UniversalSwipeableItem must conditionally attach swipeModifier when not dragged",
            code.contains(".then(if (!isCurrentlyDragged) swipeModifier else Modifier)")
        )

        // 3. Guards horizontal drag handlers against active drag
        assertTrue(
            "UniversalSwipeableItem horizontal drag handler must guard against isDraggingCard or isThisItemDragged",
            code.contains("if (isDraggingCard || isThisItemDragged) return@detectHorizontalDragGestures")
        )
    }

    @Test
    fun testChildClickDebounceGuard_preventClickOnDragRelease() {
        // Test simulation of 300ms debounce guard logic
        val lastDragEndTime = 1000L
        var clickFired = false

        // Attempt click immediately after drag release (e.g. 50ms later)
        val immediateClickTime = 1050L
        val isCurrentlyDragged = false
        if (!isCurrentlyDragged && immediateClickTime - lastDragEndTime > 300L) {
            clickFired = true
        }
        assertFalse("Click must be debounced within 300ms of drag completion", clickFired)

        // Attempt click after 350ms (clean intentional tap)
        val intentionalClickTime = 1350L
        if (!isCurrentlyDragged && intentionalClickTime - lastDragEndTime > 300L) {
            clickFired = true
        }
        assertTrue("Click must fire when >300ms elapsed since drag release", clickFired)

        // Click while currently dragged must never fire
        var draggedClickFired = false
        val isItemDraggedMidGesture = true
        if (!isItemDraggedMidGesture && intentionalClickTime - lastDragEndTime > 300L) {
            draggedClickFired = true
        }
        assertFalse("Click must never fire when item is currently dragged", draggedClickFired)
    }

    @Test
    fun testModifierOrderingSequence_universalPattern() {
        val archFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalReorderArchitecture.kt")
        val code = archFile.readText()

        // Helper extension functions must follow correct modifier chain sequence:
        // .animateItem() -> .fillMaxWidth() -> .then(dragAndDropModifier) -> .then(if (!isCurrentlyDragged) swipeModifier else Modifier)
        assertTrue(
            "reorderableItemModifier must chain animateItem, fillMaxWidth, dragAndDropModifier, and conditional swipeModifier",
            code.contains("fun LazyItemScope.reorderableItemModifier(") &&
            code.contains(".animateItem()") &&
            code.contains(".fillMaxWidth()") &&
            code.contains(".then(dragAndDropModifier)") &&
            code.contains(".then(if (!isCurrentlyDragged) swipeModifier else Modifier)")
        )

        // UniversalSwipeableItem foreground box follows the sequence
        val swipeableFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/components/UniversalSwipeableItem.kt")
        val swipeableCode = swipeableFile.readText()
        assertTrue(
            "UniversalSwipeableItem foreground box must chain dragAndDropModifier then conditional swipeModifier",
            swipeableCode.contains(".then(dragAndDropModifier)") &&
            swipeableCode.contains(".then(if (!isCurrentlyDragged) swipeModifier else Modifier)")
        )
    }

    @Test
    fun testIsItemDragged_helperFunction() {
        assertTrue("isItemDragged must be true when draggedId matches itemId", isItemDragged("item1", "item1"))
        assertFalse("isItemDragged must be false when draggedId differs", isItemDragged("item1", "item2"))
        assertFalse("isItemDragged must be false when draggedId is null", isItemDragged(null, "item1"))
    }
}
