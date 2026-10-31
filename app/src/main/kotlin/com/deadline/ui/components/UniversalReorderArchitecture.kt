package com.deadline.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/**
 * Universal Drag-to-Reorder Architecture Specification:
 * Core gesture lifecycle engine, auto-scroll coordinator, hardware-accelerated render layers,
 * and low battery / 60-120 FPS performance optimizations.
 */

const val DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS: Long = 150L

/**
 * Checks whether the Android OS is in Low Power Mode (Battery Saver).
 */
fun isSystemInPowerSaveMode(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isPowerSaveMode == true
    } catch (_: Exception) {
        false
    }
}

/**
 * Returns the fluid zero-bounce spring spec for drag settling, or falls back to
 * a clean linear transition when the system is in Low Power Mode to prevent frame drops.
 */
fun <T> getReorderSpringSpec(context: Context): AnimationSpec<T> {
    return if (isSystemInPowerSaveMode(context)) {
        tween(durationMillis = 80, easing = LinearEasing)
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
}

/**
 * Dispatches instant tactile confirmation on drag pickup using EFFECT_TICK haptics.
 */
fun triggerPickupHaptic(context: Context, hapticFeedback: HapticFeedback? = null) {
    try {
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) {}
    HapticHelper.tick(context)
}

/**
 * Calculates target reorder slot when crossing adjacent items.
 * Uses a strict >50% overlap threshold (half item height).
 */
fun calculateSlotTargetIndex(
    startIndex: Int,
    dragOffsetY: Float,
    itemHeightPx: Float,
    itemCount: Int
): Int {
    if (itemHeightPx <= 0f || itemCount <= 0) return startIndex
    val ratio = dragOffsetY / itemHeightPx
    val slotShift = if (ratio >= 0f) {
        (ratio + 0.5f).toInt()
    } else {
        (ratio - 0.5f).toInt()
    }
    return (startIndex + slotShift).coerceIn(0, itemCount - 1)
}

/**
 * Calculates the resting pixel offset for the target slot relative to the dragged item's origin.
 */
fun calculateSettleTargetOffset(
    startIndex: Int,
    targetIndex: Int,
    slotSizePx: Float
): Float {
    return (targetIndex - startIndex) * slotSizePx
}

/**
 * Smoothly animates the drag offset directly into the target slot center using matched spring physics,
 * ensuring no abrupt popping into place on touch release.
 */
suspend fun animateDragToSettle(
    currentOffset: Float,
    targetOffset: Float,
    springSpec: AnimationSpec<Float>,
    onUpdateOffset: (Float) -> Unit
) {
    if (kotlin.math.abs(currentOffset - targetOffset) < 0.5f) {
        onUpdateOffset(targetOffset)
        return
    }
    val animatable = Animatable(currentOffset)
    animatable.animateTo(
        targetValue = targetOffset,
        animationSpec = springSpec
    ) {
        onUpdateOffset(value)
    }
}

/**
 * Calculates target reorder slot for horizontal carousels (e.g. project cards).
 * Uses a strict >50% overlap threshold (half card width).
 */
fun calculateHorizontalSlotTargetIndex(
    startIndex: Int,
    dragOffsetX: Float,
    itemWidthPx: Float,
    itemCount: Int
): Int {
    if (itemWidthPx <= 0f || itemCount <= 0) return startIndex
    val ratio = dragOffsetX / itemWidthPx
    val slotShift = if (ratio >= 0f) {
        (ratio + 0.5f).toInt()
    } else {
        (ratio - 0.5f).toInt()
    }
    return (startIndex + slotShift).coerceIn(0, itemCount - 1)
}

/**
 * Calculates auto-scroll velocity for lists when a dragged item enters the top or bottom view boundaries.
 * Proportional smoothly accelerating speed near edges, 0 in center deadzone.
 */
fun calculateAutoScrollVelocity(
    pointerY: Float,
    viewportHeight: Float,
    edgeThresholdPx: Float,
    maxSpeedPx: Float
): Float {
    if (viewportHeight <= 0f || edgeThresholdPx <= 0f) return 0f
    return when {
        pointerY in 0f..edgeThresholdPx -> {
            // Dragged near top: scroll UP (negative delta)
            val factor = 1f - (pointerY / edgeThresholdPx).coerceIn(0f, 1f)
            -maxSpeedPx * factor
        }
        pointerY in (viewportHeight - edgeThresholdPx)..viewportHeight -> {
            // Dragged near bottom: scroll DOWN (positive delta)
            val factor = (pointerY - (viewportHeight - edgeThresholdPx)) / edgeThresholdPx
            maxSpeedPx * factor.coerceIn(0f, 1f)
        }
        else -> 0f
    }
}

/**
 * Conflict-Free Long-Press Drag-to-Reorder Gesture Detector:
 * 1. Primary Consumer: Intercepts pointer events at PointerEventPass.Initial so that child
 *    modifiers (such as .clickable or swipe-to-delete) cannot consume or cancel the initial drag down/hold.
 *    Once pickup is triggered, consumes all pointer events to cancel competing gestures cleanly.
 * 2. Gesture Isolation: Distinguishes between intentional horizontal swipe actions (swipe-to-delete/edit)
 *    and vertical/multi-axis drag reordering. If horizontal displacement passes touch slop with strong
 *    horizontal bias (abs(dx) > abs(dy) * 1.5f), it yields to horizontal swipe. Otherwise, drag retains priority.
 * 3. Touch Slop Adjustment: Applies an expanded pickup slop (touchSlop * pickupSlopMultiplier, default 2.5x)
 *    so natural physiological finger micro-jitters during the long-press hold phase never cancel pickup.
 */
suspend fun PointerInputScope.detectDragGesturesAfterLongPress(
    longPressTimeoutMillis: Long = 250L,
    pickupSlopMultiplier: Float = 2.5f,
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        try {
            // 1. Intercept at PointerEventPass.Initial so we get the down event before LazyColumn or child components
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var currentChange = down
            val baseSlop = viewConfiguration.touchSlop
            val microJitterSlop = baseSlop * pickupSlopMultiplier
            var dragTriggered = false

            try {
                withTimeout(longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            // Finger lifted or cancelled before long-press threshold
                            return@withTimeout
                        }

                        val totalOffset = change.position - down.position
                        val totalDistance = totalOffset.getDistance()

                        // In custom pointer-input processing, do not abort vertical drag detection if horizontal movement is below standard touch slop
                        val isBelowHorizontalTouchSlop = kotlin.math.abs(totalOffset.x) <= baseSlop

                        // Isolate intentional horizontal swipe: strong horizontal bias beyond base slop
                        val isIntentionalHorizontalSwipe = !isBelowHorizontalTouchSlop &&
                            kotlin.math.abs(totalOffset.x) > baseSlop * 1.5f &&
                            kotlin.math.abs(totalOffset.x) > kotlin.math.abs(totalOffset.y) * 1.5f

                        if (isIntentionalHorizontalSwipe) {
                            // Yield to horizontal swipe (swipe-to-delete / swipe-to-edit)
                            return@withTimeout
                        }

                        // Allow micro-jitters during the long-press hold phase
                        if (totalDistance > microJitterSlop) {
                            // Exceeded expanded micro-jitter threshold -> treat as normal scroll
                            return@withTimeout
                        }

                        currentChange = change
                    }
                }
            } catch (_: TimeoutCancellationException) {
                // Held stationary or within micro-jitter slop for longPressTimeoutMillis
                dragTriggered = true
            }

            if (dragTriggered) {
                // 1. Primary Consumer: Consume all changes in the current event and down change so LazyColumn and children cancel
                currentChange.consume()
                try {
                    currentEvent.changes.forEach { it.consume() }
                } catch (_: Exception) {}
                onDragStart(currentChange.position)

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        break
                    }
                    val dragAmount = change.position - change.previousPosition
                    event.changes.forEach { it.consume() }
                    change.consume()
                    onDrag(change, dragAmount)
                }
                onDragEnd()
            }
        } catch (c: CancellationException) {
            onDragCancel()
            throw c
        }
    }
}

/**
 * Fast Long-Press Drag Gesture Detector (~150ms pickup timeout).
 * Resolves long press rapidly to minimize pickup latency while rejecting fast horizontal swipes.
 */
suspend fun PointerInputScope.detectFastDragGesturesAfterLongPress(
    longPressTimeoutMillis: Long = DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS,
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    detectDragGesturesAfterLongPress(
        longPressTimeoutMillis = longPressTimeoutMillis,
        pickupSlopMultiplier = 2.5f,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDrag = onDrag
    )
}

/**
 * Extension modifier that turns any trailing or leading icon into a direct grab handle (≡).
 * Initiates drag immediately without waiting for long-press.
 */
fun Modifier.directReorderHandle(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onVerticalDragDelta: (Float) -> Unit,
    onDragDelta: ((Float, Float) -> Unit)? = null
): Modifier = pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset -> onDragStart(offset) },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragCancel() },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragDelta?.invoke(dragAmount.x, dragAmount.y)
            onVerticalDragDelta(dragAmount.y)
        }
    )
}

/**
 * High-performance smooth auto-scroll coordinator for Jetpack Compose LazyListState during active reorder gestures.
 */
@Composable
fun UniversalAutoScrollCoordinator(
    lazyListState: LazyListState,
    isDragging: Boolean,
    pointerYInViewport: Float,
    viewportHeight: Float,
    edgeThresholdPx: Float = 160f,
    edgeRatio: Float = 0.15f,
    maxSpeedPx: Float = 28f
) {
    if (!isDragging) return
    LaunchedEffect(isDragging, pointerYInViewport, viewportHeight) {
        val effectiveThreshold = if (viewportHeight > 0f) viewportHeight * edgeRatio else edgeThresholdPx
        val velocity = calculateAutoScrollVelocity(pointerYInViewport, viewportHeight, effectiveThreshold, maxSpeedPx)
        if (velocity != 0f) {
            while (isActive) {
                lazyListState.scrollBy(velocity)
                delay(16) // Smooth 60 FPS tick
            }
        }
    }
}

/**
 * High-performance smooth auto-scroll coordinator for standard ScrollState during active reorder gestures (e.g. Timer preset rows).
 */
@Composable
fun UniversalAutoScrollCoordinator(
    scrollState: ScrollState,
    isDragging: Boolean,
    pointerYInViewport: Float,
    viewportHeight: Float,
    edgeThresholdPx: Float = 160f,
    edgeRatio: Float = 0.15f,
    maxSpeedPx: Float = 28f
) {
    if (!isDragging) return
    LaunchedEffect(isDragging, pointerYInViewport, viewportHeight) {
        val effectiveThreshold = if (viewportHeight > 0f) viewportHeight * edgeRatio else edgeThresholdPx
        val velocity = calculateAutoScrollVelocity(pointerYInViewport, viewportHeight, effectiveThreshold, maxSpeedPx)
        if (velocity != 0f) {
            while (isActive) {
                scrollState.scrollBy(velocity)
                delay(16) // Smooth 60 FPS tick
            }
        }
    }
}

/**
 * Determines whether an item with the given ID is currently being dragged.
 */
fun isItemDragged(draggedId: String?, itemId: String): Boolean = draggedId != null && draggedId == itemId

/**
 * Standard conflict-free item modifier sequence for reorderable items:
 * Modifier
 *     .animateItem()
 *     .fillMaxWidth()
 *     .then(dragAndDropModifier)
 *     .then(if (!isCurrentlyDragged) swipeModifier else Modifier)
 */
fun Modifier.reorderItemModifiers(
    isCurrentlyDragged: Boolean,
    dragAndDropModifier: Modifier = Modifier,
    swipeModifier: Modifier = Modifier
): Modifier = this
    .fillMaxWidth()
    .then(dragAndDropModifier)
    .then(if (!isCurrentlyDragged) swipeModifier else Modifier)

fun LazyItemScope.reorderableItemModifier(
    isCurrentlyDragged: Boolean,
    dragAndDropModifier: Modifier = Modifier,
    swipeModifier: Modifier = Modifier
): Modifier = Modifier
    .animateItem()
    .fillMaxWidth()
    .then(dragAndDropModifier)
    .then(if (!isCurrentlyDragged) swipeModifier else Modifier)
