package com.deadline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)

    val pillShape = RoundedCornerShape(16.dp)
    val indicatorShape = RoundedCornerShape(12.dp)

    val indicatorProgress = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }

    // Synchronize indicator continuously with PagerState (if provided) or selectedIndex
    if (pagerState != null) {
        val continuousPagerOffset = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (items.size - 1).coerceAtLeast(0).toFloat())
        LaunchedEffect(continuousPagerOffset) {
            if (!isDragging && !isSettling) {
                indicatorProgress.snapTo(continuousPagerOffset)
            }
        }
    } else {
        LaunchedEffect(selectedIndex) {
            if (!isDragging && !isSettling && indicatorProgress.targetValue != selectedIndex.toFloat()) {
                indicatorProgress.animateTo(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
            .pointerInput(items.size) {
                var dragStartProgress = 0f
                var accumulatedDragPx = 0f
                var lastNotifiedIndex = selectedIndex

                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragStartProgress = indicatorProgress.value
                        accumulatedDragPx = 0f
                        lastNotifiedIndex = dragStartProgress.roundToInt().coerceIn(0, items.size - 1)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount
                        val currentWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        val tabCount = items.size.coerceAtLeast(1)
                        val tabWidthPx = currentWidthPx / tabCount
                        val newProgress = (dragStartProgress + accumulatedDragPx / tabWidthPx)
                            .coerceIn(0f, (items.size - 1).toFloat())
                        coroutineScope.launch {
                            indicatorProgress.snapTo(newProgress)
                        }
                        val nearestIndex = newProgress.roundToInt().coerceIn(0, items.size - 1)
                        if (nearestIndex != lastNotifiedIndex) {
                            lastNotifiedIndex = nearestIndex
                            HapticHelper.selection(context)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        isSettling = true
                        val targetIndex = indicatorProgress.value.roundToInt().coerceIn(0, items.size - 1)
                        coroutineScope.launch {
                            indicatorProgress.animateTo(
                                targetValue = targetIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            isSettling = false
                        }
                        if (pagerState != null) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = targetIndex,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                        if (targetIndex != selectedIndex) {
                            HapticHelper.selection(context)
                            currentOnItemSelected(targetIndex)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        isSettling = true
                        val targetIndex = indicatorProgress.value.roundToInt().coerceIn(0, items.size - 1)
                        coroutineScope.launch {
                            indicatorProgress.animateTo(
                                targetValue = targetIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            isSettling = false
                        }
                    }
                )
            }
    ) {
        val tabCount = items.size.coerceAtLeast(1)
        val tabWidth = maxWidth / tabCount
        val indicatorOffsetDp = tabWidth * indicatorProgress.value

        // Sliding Active Indicator Pill
        Box(
            modifier = Modifier
                .offset(x = indicatorOffsetDp)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(elevation = 2.dp, shape = indicatorShape)
                .clip(indicatorShape)
                .background(MaterialTheme.colorScheme.surface)
        )

        // Tab Text Labels on top
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val distance = abs(indicatorProgress.value - index).coerceIn(0f, 1f)
                val isSelected = distance < 0.5f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (selectedIndex != index) {
                                HapticHelper.selection(context)
                                currentOnItemSelected(index)
                                isSettling = true
                                coroutineScope.launch {
                                    indicatorProgress.animateTo(
                                        targetValue = index.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    isSettling = false
                                }
                                if (pagerState != null) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = index,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
