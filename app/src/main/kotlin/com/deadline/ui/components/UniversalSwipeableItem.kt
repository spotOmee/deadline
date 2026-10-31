package com.deadline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import com.deadline.ui.theme.StatusUrgent
import com.deadline.ui.theme.WorkIndigo
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class UniversalSwipeAction {
    NONE,
    EDIT,
    SCHEDULE,
    DELETE
}

val LocalItemLastDragEndTime = compositionLocalOf { 0L }
val LocalIsItemCurrentlyDragged = compositionLocalOf { false }

/**
 * Universal Apple HIG Swipeable & Fluid Drag-and-Drop Item Container:
 * - Delete Action (Right-to-Left): Swiping from right to left (dx < 0) reveals Urgent Red background with Delete text and Trash icon.
 *   Crossing threshold triggers haptic feedback, executes designated shake animation, slides off, and executes onDelete.
 * - Edit Action (Left-to-Right): Swiping from left to right (dx > 0) reveals Action Blue background with Edit text and Pencil icon.
 *   Crossing threshold triggers haptic feedback, springs back, and executes onEdit.
 * - Fluid Multi-Axis Drag-and-Drop: Touch-and-hold (long-press) enables unconstrained multi-axis (X and Y) reordering
 *   and inter-tab/cross-list transfer. Movement follows gesture freely without rigid slot snapping, lifting with elevation shadow.
 */
@Composable
fun UniversalSwipeableItem(
    modifier: Modifier = Modifier,
    itemId: String,
    cornerRadius: Dp = 18.dp,
    enableSwipe: Boolean = true,
    isThisItemDragged: Boolean = false,
    dragOffsetX: Float = 0f,
    dragOffsetY: Float = 0f,
    nonDraggedDisplacement: Float = 0f,
    constrainToVerticalAxis: Boolean = false,
    longPressTimeoutMs: Long = DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onSchedule: (() -> Unit)? = null,
    onSwipeStart: () -> Unit = {},
    onSwipeEnd: () -> Unit = {},
    onDragStart: (() -> Unit)? = null,
    onDragStartWithOffset: ((Offset, Rect) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onVerticalDragDelta: ((Float) -> Unit)? = null,
    onDragDelta: ((Float, Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 130.dp.toPx() }

    var itemBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val swipeOffsetX = remember(itemId) { Animatable(0f) }
    val shakeRotation = remember(itemId) { Animatable(0f) }
    var activeSwipeAction by remember(itemId) { mutableStateOf(UniversalSwipeAction.NONE) }
    var hasTriggeredHaptic by remember(itemId) { mutableStateOf(false) }

    val springSpec = remember(context) { getReorderSpringSpec<Float>(context) }

    val dragScale by animateFloatAsState(
        targetValue = if (isThisItemDragged) 1.04f else 1f,
        animationSpec = springSpec,
        label = "universalDragScale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isThisItemDragged) with(density) { 16.dp.toPx() } else 0f,
        animationSpec = springSpec,
        label = "universalDragElevation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isThisItemDragged) 100f else 1f)
            .onGloballyPositioned { coords ->
                itemBoundsInRoot = coords.boundsInRoot()
            }
            .graphicsLayer {
                translationX = if (isThisItemDragged) (if (constrainToVerticalAxis) 0f else dragOffsetX) else 0f
                translationY = if (isThisItemDragged) dragOffsetY else nonDraggedDisplacement
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation
                shape = RoundedCornerShape(cornerRadius)
                clip = false
            }
    ) {
        // Swipe Background Action Indicators Layer
        if (activeSwipeAction != UniversalSwipeAction.NONE && abs(swipeOffsetX.value) > 1f) {
            val isSchedule = activeSwipeAction == UniversalSwipeAction.SCHEDULE
            val isEdit = activeSwipeAction == UniversalSwipeAction.EDIT
            val actionBgColor = when {
                isSchedule -> WorkIndigo
                isEdit -> Color(0xFF2563EB)
                else -> StatusUrgent
            }
            val actionProgress = (abs(swipeOffsetX.value) / thresholdPx).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(actionBgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isSchedule || isEdit) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.5f + 0.5f * actionProgress
                        scaleX = 0.85f + 0.15f * actionProgress
                        scaleY = 0.85f + 0.15f * actionProgress
                    }
                ) {
                    if (isSchedule) {
                        StopwatchIcon(
                            color = Color.White,
                            size = 18.dp
                        )
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else if (isEdit) {
                        PencilEditIcon(
                            color = Color.White,
                            size = 18.dp
                        )
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        fun isItemDragged(id: String): Boolean = isThisItemDragged && itemId == id
        val isCurrentlyDragged = isItemDragged(itemId)

        var isDraggingCard by remember(itemId) { mutableStateOf(false) }
        var isSwipingHorizontal by remember(itemId) { mutableStateOf(false) }
        var lastDragEndTime by remember(itemId) { mutableStateOf(0L) }

        val currentOnDragStart by rememberUpdatedState(onDragStart)
        val currentOnDragStartWithOffset by rememberUpdatedState(onDragStartWithOffset)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)
        val currentOnDragCancel by rememberUpdatedState(onDragCancel)
        val currentOnDragDelta by rememberUpdatedState(onDragDelta)
        val currentOnVerticalDragDelta by rememberUpdatedState(onVerticalDragDelta)

        val currentOnDelete by rememberUpdatedState(onDelete)
        val currentOnEdit by rememberUpdatedState(onEdit)
        val currentOnSchedule by rememberUpdatedState(onSchedule)
        val currentOnSwipeStart by rememberUpdatedState(onSwipeStart)
        val currentOnSwipeEnd by rememberUpdatedState(onSwipeEnd)

        val dragAndDropModifier = if (onDragStart != null || onDragStartWithOffset != null || onDragDelta != null || onVerticalDragDelta != null) {
            Modifier.pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    longPressTimeoutMillis = longPressTimeoutMs,
                    onDragStart = { offset ->
                        isDraggingCard = true
                        triggerPickupHaptic(context, hapticFeedback)
                        currentOnDragStartWithOffset?.invoke(offset, itemBoundsInRoot) ?: currentOnDragStart?.invoke()
                    },
                    onDragEnd = {
                        isDraggingCard = false
                        lastDragEndTime = System.currentTimeMillis()
                        currentOnDragEnd?.invoke()
                    },
                    onDragCancel = {
                        isDraggingCard = false
                        lastDragEndTime = System.currentTimeMillis()
                        currentOnDragCancel?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDragDelta?.invoke(dragAmount.x, dragAmount.y)
                        currentOnVerticalDragDelta?.invoke(dragAmount.y)
                    }
                )
            }
        } else Modifier

        val swipeModifier = if (enableSwipe && (onDelete != null || onEdit != null || onSchedule != null)) {
            Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!isDraggingCard && !isCurrentlyDragged) {
                            isSwipingHorizontal = true
                            hasTriggeredHaptic = false
                            activeSwipeAction = UniversalSwipeAction.NONE
                            currentOnSwipeStart()
                        }
                    },
                    onDragEnd = {
                        if (isDraggingCard || isCurrentlyDragged) return@detectHorizontalDragGestures
                        currentOnSwipeEnd()
                        coroutineScope.launch {
                            val currentAction = activeSwipeAction
                            val offset = swipeOffsetX.value
                            when (currentAction) {
                                UniversalSwipeAction.SCHEDULE -> {
                                    if (offset >= thresholdPx && currentOnSchedule != null) {
                                        HapticHelper.click(context)
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        currentOnSchedule?.invoke()
                                    } else {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                                UniversalSwipeAction.EDIT -> {
                                    if (offset >= thresholdPx && currentOnEdit != null) {
                                        HapticHelper.click(context)
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        currentOnEdit?.invoke()
                                    } else {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                                UniversalSwipeAction.DELETE -> {
                                    if (offset <= -thresholdPx && currentOnDelete != null) {
                                        HapticHelper.heavy(context)
                                        // Designated tactile shake feedback animation
                                        coroutineScope.launch {
                                            shakeRotation.animateTo(-2f, tween(35))
                                            shakeRotation.animateTo(1.8f, tween(35))
                                            shakeRotation.animateTo(-1.2f, tween(35))
                                            shakeRotation.animateTo(0f, tween(35))
                                        }
                                        val base = swipeOffsetX.value
                                        swipeOffsetX.animateTo(base - 18f, tween(35))
                                        swipeOffsetX.animateTo(base + 14f, tween(35))
                                        swipeOffsetX.animateTo(base - 10f, tween(35))
                                        swipeOffsetX.animateTo(base + 6f, tween(35))
                                        swipeOffsetX.animateTo(-1200f, tween(160))
                                        currentOnDelete?.invoke()
                                    } else {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                                UniversalSwipeAction.NONE -> {
                                    swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                }
                            }
                            activeSwipeAction = UniversalSwipeAction.NONE
                            isSwipingHorizontal = false
                            hasTriggeredHaptic = false
                        }
                    },
                    onDragCancel = {
                        if (isDraggingCard || isCurrentlyDragged) return@detectHorizontalDragGestures
                        currentOnSwipeEnd()
                        coroutineScope.launch {
                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                            activeSwipeAction = UniversalSwipeAction.NONE
                            isSwipingHorizontal = false
                            hasTriggeredHaptic = false
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isDraggingCard || isThisItemDragged) return@detectHorizontalDragGestures

                        // Lock direction once initial movement passes slop (10px threshold)
                        if (activeSwipeAction == UniversalSwipeAction.NONE) {
                            val testDelta = swipeOffsetX.value + dragAmount
                            if (testDelta > 10f) {
                                if (currentOnSchedule != null) {
                                    activeSwipeAction = UniversalSwipeAction.SCHEDULE
                                } else if (currentOnEdit != null) {
                                    activeSwipeAction = UniversalSwipeAction.EDIT
                                } else {
                                    return@detectHorizontalDragGestures
                                }
                            } else if (testDelta < -10f && currentOnDelete != null) {
                                activeSwipeAction = UniversalSwipeAction.DELETE
                            } else {
                                return@detectHorizontalDragGestures
                            }
                        }

                        change.consume()

                        val rawNext = swipeOffsetX.value + dragAmount
                        val clamped = when (activeSwipeAction) {
                            UniversalSwipeAction.SCHEDULE,
                            UniversalSwipeAction.EDIT -> {
                                if (rawNext < 0f) 0f
                                else if (rawNext <= maxDragPx) rawNext
                                else maxDragPx + (rawNext - maxDragPx) * 0.25f
                            }
                            UniversalSwipeAction.DELETE -> {
                                if (rawNext > 0f) 0f
                                else if (rawNext >= -maxDragPx) rawNext
                                else -maxDragPx + (rawNext - (-maxDragPx)) * 0.25f
                            }
                            UniversalSwipeAction.NONE -> 0f
                        }

                        coroutineScope.launch {
                            swipeOffsetX.snapTo(clamped)
                        }

                        val crossed = when (activeSwipeAction) {
                            UniversalSwipeAction.SCHEDULE,
                            UniversalSwipeAction.EDIT -> clamped >= thresholdPx
                            UniversalSwipeAction.DELETE -> clamped <= -thresholdPx
                            UniversalSwipeAction.NONE -> false
                        }
                        if (crossed && !hasTriggeredHaptic) {
                            hasTriggeredHaptic = true
                            HapticHelper.selection(context)
                        } else if (!crossed && hasTriggeredHaptic) {
                            hasTriggeredHaptic = false
                        }
                    }
                )
            }
        } else Modifier

        // Foreground Content Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = if (isThisItemDragged) 0f else swipeOffsetX.value
                    rotationZ = shakeRotation.value
                }
                .then(dragAndDropModifier)
                .then(if (!isCurrentlyDragged) swipeModifier else Modifier)
        ) {
            CompositionLocalProvider(
                LocalItemLastDragEndTime provides lastDragEndTime,
                LocalIsItemCurrentlyDragged provides isCurrentlyDragged
            ) {
                content()
            }
        }
    }
}

/**
 * Calculates continuous, fluid non-dragged item displacement without magnetic snapping or sticky intervals.
 * As the dragged item moves continuously in Y, neighboring items glide proportionally out of the way,
 * eliminating deadzones, artificial resistance, and abrupt jumping.
 *
 * @param itemIndex The index of the item being evaluated
 * @param draggedIndex The index of the item currently being dragged (or null if none)
 * @param dragOffsetY The continuous Y offset of the dragged item in pixels
 * @param itemHeightPx The height of a single slot (item height + spacing) in pixels
 * @return The continuous displacement in pixels for this non-dragged item
 */
fun calculateContinuousDisplacement(
    itemIndex: Int,
    draggedIndex: Int?,
    dragOffsetY: Float,
    itemHeightPx: Float
): Float {
    if (draggedIndex == null || itemIndex == draggedIndex || itemHeightPx <= 0f) return 0f

    val deltaSlots = dragOffsetY / itemHeightPx

    return if (deltaSlots > 0f) {
        // Dragging DOWNWARDS: items below the dragged item slide UP (-itemHeightPx)
        if (itemIndex > draggedIndex) {
            val slotDistance = itemIndex - draggedIndex
            val progress = (deltaSlots - (slotDistance - 1f)).coerceIn(0f, 1f)
            -progress * itemHeightPx
        } else {
            0f
        }
    } else if (deltaSlots < 0f) {
        // Dragging UPWARDS: items above the dragged item slide DOWN (+itemHeightPx)
        if (itemIndex < draggedIndex) {
            val slotDistance = draggedIndex - itemIndex
            val absDeltaSlots = -deltaSlots
            val progress = (absDeltaSlots - (slotDistance - 1f)).coerceIn(0f, 1f)
            progress * itemHeightPx
        } else {
            0f
        }
    } else {
        0f
    }
}

/**
 * Calculates continuous, fluid horizontal non-dragged item displacement for carousels.
 *
 * @param itemIndex The index of the item being evaluated
 * @param draggedIndex The index of the item currently being dragged (or null if none)
 * @param dragOffsetX The continuous X offset of the dragged item in pixels
 * @param cardWidthPx The width of a single slot (card width + spacing) in pixels
 * @return The continuous displacement in pixels for this non-dragged item
 */
fun calculateContinuousHorizontalDisplacement(
    itemIndex: Int,
    draggedIndex: Int?,
    dragOffsetX: Float,
    cardWidthPx: Float
): Float {
    if (draggedIndex == null || itemIndex == draggedIndex || cardWidthPx <= 0f) return 0f

    val deltaSlots = dragOffsetX / cardWidthPx

    return if (deltaSlots > 0f) {
        // Dragging RIGHTWARDS: items to the right slide LEFT (-cardWidthPx)
        if (itemIndex > draggedIndex) {
            val slotDistance = itemIndex - draggedIndex
            val progress = (deltaSlots - (slotDistance - 1f)).coerceIn(0f, 1f)
            -progress * cardWidthPx
        } else {
            0f
        }
    } else if (deltaSlots < 0f) {
        // Dragging LEFTWARDS: items to the left slide RIGHT (+cardWidthPx)
        if (itemIndex < draggedIndex) {
            val slotDistance = draggedIndex - itemIndex
            val absDeltaSlots = -deltaSlots
            val progress = (absDeltaSlots - (slotDistance - 1f)).coerceIn(0f, 1f)
            progress * cardWidthPx
        } else {
            0f
        }
    } else {
        0f
    }
}

