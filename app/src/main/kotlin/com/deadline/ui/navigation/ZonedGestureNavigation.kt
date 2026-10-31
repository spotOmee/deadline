package com.deadline.ui.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Constants and thresholds for Zoned Gesture Navigation:
 * - Global Navigation Zone: Dedicated to the bottom 28% of the viewport for tab switching.
 * - Localized Content Zone: Confined to the top 72% (below headers) for sub-tabs, lists, and items.
 * - Disambiguation: Strict 1.5x horizontal dominance ratio and velocity/slop thresholds.
 */
object NavigationZoningDefaults {
    const val GLOBAL_NAV_ZONE_RATIO = 0.28f
    const val LOCALIZED_ZONE_TOP_HEADER_RATIO = 0.18f
    const val LOCALIZED_ZONE_BOTTOM_RATIO = 0.72f
    const val HORIZONTAL_DOMINANCE_RATIO = 1.5f
    const val VELOCITY_THRESHOLD_PX_PER_SEC = 800f
    const val PAGE_SNAP_OFFSET_THRESHOLD = 0.25f

    val ButterSmoothNavSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun isTouchInGlobalNavZone(touchY: Float, totalHeight: Float, zoneRatio: Float = GLOBAL_NAV_ZONE_RATIO): Boolean {
        if (totalHeight <= 0f) return false
        val startY = totalHeight * (1f - zoneRatio)
        return touchY >= startY
    }

    fun isTouchInLocalizedContentZone(
        touchY: Float,
        totalHeight: Float,
        topHeaderRatio: Float = LOCALIZED_ZONE_TOP_HEADER_RATIO,
        bottomZoneRatio: Float = GLOBAL_NAV_ZONE_RATIO
    ): Boolean {
        if (totalHeight <= 0f) return false
        val minHeaderY = totalHeight * topHeaderRatio
        val maxContentY = totalHeight * (1f - bottomZoneRatio)
        return touchY in minHeaderY..maxContentY
    }

    fun isHorizontalSwipeDominant(deltaX: Float, deltaY: Float): Boolean {
        return abs(deltaX) > abs(deltaY) * HORIZONTAL_DOMINANCE_RATIO
    }

    fun calculateTargetPage(
        currentPage: Int,
        offsetFraction: Float,
        velocityX: Float,
        pageCount: Int
    ): Int {
        val targetPage = when {
            velocityX < -VELOCITY_THRESHOLD_PX_PER_SEC -> {
                (currentPage + 1).coerceAtMost(pageCount - 1)
            }
            velocityX > VELOCITY_THRESHOLD_PX_PER_SEC -> {
                (currentPage - 1).coerceAtLeast(0)
            }
            else -> {
                if (offsetFraction > PAGE_SNAP_OFFSET_THRESHOLD) {
                    (currentPage + 1).coerceAtMost(pageCount - 1)
                } else if (offsetFraction < -PAGE_SNAP_OFFSET_THRESHOLD) {
                    (currentPage - 1).coerceAtLeast(0)
                } else {
                    currentPage
                }
            }
        }
        return targetPage.coerceIn(0, pageCount - 1)
    }
}

/**
 * Modifier to intercept horizontal swipe gestures strictly within the Global Navigation Zone (bottom 28% of the viewport).
 *
 * Disambiguates horizontal swipes from vertical scrolls:
 * - If horizontal movement dominates (abs(dx) > abs(dy) * 1.5f), consumes the pointer event, cancels active animations,
 *   and smoothly translates the root pager 1:1 using dispatchRawDelta.
 * - If vertical movement dominates or touch is outside the bottom 28% zone, lets pointer events pass unconsumed to child
 *   vertical scrollables (LazyColumn, ScrollView, etc.).
 * - On release, evaluates velocity and offset to settle to the target tab with interruptible physics springs.
 */
fun Modifier.globalNavigationZoneSwipe(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    onTabSettled: ((DeadlineTab) -> Unit)? = null,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    activeAnimationJob: (() -> Job?)? = null,
    onActiveAnimationJobChanged: ((Job?) -> Unit)? = null,
    zoneRatio: Float = NavigationZoningDefaults.GLOBAL_NAV_ZONE_RATIO
): Modifier = pointerInput(pagerState) {
    val touchSlop = viewConfiguration.touchSlop
    val velocityTracker = VelocityTracker()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val totalH = size.height.toFloat()
        val startY = totalH * (1f - zoneRatio)

        // Check if touch down occurs in Global Navigation Zone
        if (down.position.y < startY) {
            return@awaitEachGesture
        }

        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var totalDeltaX = 0f
        var totalDeltaY = 0f
        var isDragging = false
        var lastPosition = down.position

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                break
            }

            val currentPosition = change.position
            val deltaX = currentPosition.x - lastPosition.x
            val deltaY = currentPosition.y - lastPosition.y
            totalDeltaX += deltaX
            totalDeltaY += deltaY
            lastPosition = currentPosition

            velocityTracker.addPosition(change.uptimeMillis, change.position)

            if (!isDragging) {
                val distSq = totalDeltaX * totalDeltaX + totalDeltaY * totalDeltaY
                if (distSq >= touchSlop * touchSlop) {
                    if (NavigationZoningDefaults.isHorizontalSwipeDominant(totalDeltaX, totalDeltaY)) {
                        isDragging = true
                        onDragStateChanged?.invoke(true)
                        activeAnimationJob?.invoke()?.cancel()
                        change.consume()
                        pagerState.dispatchRawDelta(-deltaX)
                    } else {
                        // Dominantly vertical: release interception and allow child scrollables to handle it
                        while (true) {
                            val releaseEvent = awaitPointerEvent(PointerEventPass.Initial)
                            if (releaseEvent.changes.all { !it.pressed }) break
                        }
                        return@awaitEachGesture
                    }
                }
            } else {
                change.consume()
                pagerState.dispatchRawDelta(-deltaX)
            }
        }

        if (isDragging) {
            onDragStateChanged?.invoke(false)
            val velocityX = velocityTracker.calculateVelocity().x
            val currentPage = pagerState.currentPage
            val offsetFraction = pagerState.currentPageOffsetFraction
            val pageCount = pagerState.pageCount

            val targetPage = NavigationZoningDefaults.calculateTargetPage(
                currentPage = currentPage,
                offsetFraction = offsetFraction,
                velocityX = velocityX,
                pageCount = pageCount
            )

            activeAnimationJob?.invoke()?.cancel()
            val job = coroutineScope.launch {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = NavigationZoningDefaults.ButterSmoothNavSpring
                )
                if (targetPage in DeadlineTab.entries.indices) {
                    onTabSettled?.invoke(DeadlineTab.entries[targetPage])
                }
            }
            onActiveAnimationJobChanged?.invoke(job)
        }
    }
}
