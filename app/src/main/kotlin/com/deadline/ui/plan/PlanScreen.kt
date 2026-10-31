package com.deadline.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.deadline.ui.components.UniversalSwipeableItem
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import com.deadline.ui.components.UniversalAutoScrollCoordinator
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.detectFastDragGesturesAfterLongPress
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.triggerPickupHaptic
import kotlinx.coroutines.Job
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.MorphingLeadingActionIcon
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.TimeBlock
import com.deadline.domain.GlobalTimeManager
import com.deadline.domain.HeadlineScaler
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.calculateDynamicHeadlineSize
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.BrandAmberEnd
import com.deadline.ui.theme.DangerRed
import com.deadline.ui.theme.FocusOrange
import com.deadline.ui.theme.WorkIndigo
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val userSettings by repository.userSettings.collectAsState()
    val timeBlocks by repository.timelineTimeBlocks.collectAsState()
    val selectedPlanDate by repository.selectedPlanDate.collectAsState()
    val todayDateStr = remember { repository.getTodayString() }
    val isViewingToday = selectedPlanDate.isBlank() || selectedPlanDate == todayDateStr

    var showAddBlockSheet by remember { mutableStateOf(false) }

    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var dragBlockOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedBlockInitialStartMin by remember { mutableStateOf<Int?>(null) }
    var isBlockSettling by remember { mutableStateOf(false) }
    var blockSettleJob by remember { mutableStateOf<Job?>(null) }

    val timelineLazyListState = rememberLazyListState()
    var timelineViewportHeight by remember { mutableFloatStateOf(0f) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val hourRowHeightPx = with(density) { 68.dp.toPx() }

    val cal = remember(userSettings.timeFormat24Hour) {
        GlobalTimeManager.getCurrentCalendar(userSettings.timeFormat24Hour)
    }
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = cal.get(Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute

    UniversalAutoScrollCoordinator(
        lazyListState = timelineLazyListState,
        isDragging = draggedBlockId != null,
        pointerYInViewport = dragBlockOffsetY,
        viewportHeight = timelineViewportHeight
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isViewingToday) "Timeline" else "Timeline • $selectedPlanDate",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Time rail list (Hours 08:00 to 22:00)
            val hours = (8..22).toList()

            LazyColumn(
                state = timelineLazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        timelineViewportHeight = coords.size.height.toFloat()
                    }
            ) {
                items(hours) { hour ->
                    val hourMinute = hour * 60
                    val matchingBlocks = timeBlocks.filter {
                        it.startMinute >= hourMinute && it.startMinute < (hourMinute + 60)
                    }
                    val isCurrentHour = isViewingToday && (currentHour == hour)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = GlobalTimeManager.formatHour(hour, userSettings.timeFormat24Hour),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isCurrentHour) BrandAmberEnd else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.width(if (userSettings.timeFormat24Hour) 52.dp else 68.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Vertical Timeline Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Current time line with Apple-style accent yellow
                            if (isCurrentHour) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(BrandAmberEnd)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(BrandAmberEnd)
                                    )
                                }
                            }

                            if (matchingBlocks.isEmpty()) {
                                // Empty slot line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                matchingBlocks.forEach { block ->
                                    val isThisBlockDragged = draggedBlockId == block.id
                                    val blockDate = block.date.ifBlank { selectedPlanDate.ifBlank { todayDateStr } }
                                    val isBlockPassed = GlobalTimeManager.isEventElapsed(
                                        startMinute = block.startMinute,
                                        durationMinutes = block.durationMinutes,
                                        eventDate = blockDate,
                                        currentDate = todayDateStr,
                                        currentMinuteOfDay = currentTotalMinutes,
                                        is24Hour = userSettings.timeFormat24Hour
                                    )
                                    UniversalSwipeableItem(
                                        itemId = block.id,
                                        cornerRadius = 14.dp,
                                        onDelete = {
                                            HapticHelper.heavy(context)
                                            scope.launch {
                                                repository.deleteTimeBlock(block.id)
                                            }
                                        }
                                    ) {
                                        TimeBlockCard(
                                            block = block,
                                            is24Hour = userSettings.timeFormat24Hour,
                                            isPassed = isBlockPassed,
                                            isThisItemDragged = isThisBlockDragged,
                                            dragOffsetY = if (isThisBlockDragged) dragBlockOffsetY else 0f,
                                            onDragStart = {
                                                blockSettleJob?.cancel()
                                                isBlockSettling = false
                                                triggerPickupHaptic(context, hapticFeedback)
                                                draggedBlockId = block.id
                                                draggedBlockInitialStartMin = block.startMinute
                                                dragBlockOffsetY = 0f
                                            },
                                            onDragEnd = onDragEnd@ {
                                                if (isBlockSettling) return@onDragEnd
                                                val initialMin = draggedBlockInitialStartMin ?: block.startMinute
                                                val initialHour = initialMin / 60
                                                val hourShift = (dragBlockOffsetY / hourRowHeightPx).roundToInt()
                                                val targetHour = (initialHour + hourShift).coerceIn(8, 22)
                                                val targetStartMin = targetHour * 60 + (initialMin % 60)
                                                val targetSettleY = (targetHour - initialHour) * hourRowHeightPx

                                                // Real-time scheduling constraint: prevent dropping into a past hour on today
                                                if (isViewingToday && targetStartMin <= currentTotalMinutes) {
                                                    HapticHelper.warning(context)
                                                    isBlockSettling = true
                                                    blockSettleJob = scope.launch {
                                                        animateDragToSettle(
                                                            currentOffset = dragBlockOffsetY,
                                                            targetOffset = 0f,
                                                            springSpec = getReorderSpringSpec(context)
                                                        ) { animatedY ->
                                                            dragBlockOffsetY = animatedY
                                                        }
                                                        draggedBlockId = null
                                                        draggedBlockInitialStartMin = null
                                                        dragBlockOffsetY = 0f
                                                        isBlockSettling = false
                                                    }
                                                    return@onDragEnd
                                                }

                                                isBlockSettling = true
                                                blockSettleJob = scope.launch {
                                                    animateDragToSettle(
                                                        currentOffset = dragBlockOffsetY,
                                                        targetOffset = targetSettleY,
                                                        springSpec = getReorderSpringSpec(context)
                                                    ) { animatedY ->
                                                        dragBlockOffsetY = animatedY
                                                    }
                                                    if (targetStartMin != block.startMinute) {
                                                        HapticHelper.success(context)
                                                        repository.updateTimeBlock(block.copy(startMinute = targetStartMin))
                                                    }
                                                    draggedBlockId = null
                                                    draggedBlockInitialStartMin = null
                                                    dragBlockOffsetY = 0f
                                                    isBlockSettling = false
                                                }
                                            },
                                            onDragCancel = onDragCancel@ {
                                                if (isBlockSettling) return@onDragCancel
                                                isBlockSettling = true
                                                blockSettleJob = scope.launch {
                                                    animateDragToSettle(
                                                        currentOffset = dragBlockOffsetY,
                                                        targetOffset = 0f,
                                                        springSpec = getReorderSpringSpec(context)
                                                    ) { animatedY ->
                                                        dragBlockOffsetY = animatedY
                                                    }
                                                    draggedBlockId = null
                                                    draggedBlockInitialStartMin = null
                                                    dragBlockOffsetY = 0f
                                                    isBlockSettling = false
                                                }
                                            },
                                            onVerticalDragDelta = onVerticalDragDelta@ { dy ->
                                                if (isBlockSettling) return@onVerticalDragDelta
                                                dragBlockOffsetY += dy
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddBlockSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                sheetState.animateDismiss(scope) {
                    showAddBlockSheet = false
                }
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddTimeBlockSheet(
                targetDate = selectedPlanDate.ifBlank { todayDateStr },
                onDismiss = {
                    sheetState.animateDismiss(scope) {
                        showAddBlockSheet = false
                    }
                },
                onCreate = { title, startMin, durationMin, color ->
                    scope.launch {
                        repository.addTimeBlock(
                            title = title,
                            startMinute = startMin,
                            durationMinutes = durationMin,
                            color = color,
                            projectId = null,
                            date = selectedPlanDate.ifBlank { todayDateStr }
                        )
                        sheetState.smoothDismiss()
                        showAddBlockSheet = false
                    }
                }
            )
        }
    }
}

@Composable
fun TimeBlockCard(
    block: TimeBlock,
    is24Hour: Boolean = false,
    isPassed: Boolean = false,
    isThisItemDragged: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onVerticalDragDelta: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val springSpec = remember(context) { getReorderSpringSpec<Float>(context) }

    val dragScale by animateFloatAsState(
        targetValue = if (isThisItemDragged) 1.03f else 1f,
        animationSpec = springSpec,
        label = "timeBlockScale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isThisItemDragged) with(density) { 16.dp.toPx() } else 0f,
        animationSpec = springSpec,
        label = "timeBlockElevation"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isPassed) 0.45f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
        label = "timeBlockDimAlpha"
    )

    val blockColor = try {
        Color(android.graphics.Color.parseColor(block.color))
    } catch (_: Exception) {
        WorkIndigo
    }

    val proportionalHeight = ((block.durationMinutes.coerceIn(15, 360) / 60f) * 68).dp
    val cardHeight = maxOf(56.dp, proportionalHeight)

    SquircleCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .zIndex(if (isThisItemDragged) 100f else 1f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation
                alpha = cardAlpha
            }
            .then(
                if (onDragStart != null) {
                    Modifier.pointerInput(Unit) {
                        detectFastDragGesturesAfterLongPress(
                            longPressTimeoutMillis = DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS,
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragCancel?.invoke() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onVerticalDragDelta?.invoke(dragAmount.y)
                            }
                        )
                    }
                } else Modifier
            ),
        cornerRadius = 14.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MorphingLeadingActionIcon(
                    isDragging = isThisItemDragged,
                    dragHandleColor = blockColor
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(blockColor)
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    val blockHeadline = HeadlineScaler.clamp(block.title)
                    Text(
                        text = blockHeadline,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = calculateDynamicHeadlineSize(blockHeadline, baseSize = 16.sp, minSize = 12.sp)
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = GlobalTimeManager.formatTimeRange(block.startMinute, block.durationMinutes, is24Hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isPassed) "Past" else "Scheduled",
                    fontSize = 11.sp,
                    color = if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant else blockColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddTimeBlockSheet(
    onDismiss: () -> Unit,
    onCreate: (title: String, startMinute: Int, durationMinute: Int, color: String) -> Unit,
    targetDate: String? = null
) {
    val currentCal = remember { Calendar.getInstance() }
    val todayDateStr = remember {
        java.lang.String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH) + 1,
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
    }
    val effectiveDate = targetDate ?: todayDateStr
    val isToday = effectiveDate == todayDateStr
    val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
    val currentMin = currentCal.get(Calendar.MINUTE)
    val currentTotalMin = currentHour * 60 + currentMin
    val initialHour = if (isToday) (currentHour + 1).coerceIn(0, 23) else 10

    var title by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(initialHour.toString()) }
    var duration by remember { mutableStateOf("60") }
    var isSubmitting by remember { mutableStateOf(false) }

    val h = hour.toIntOrNull() ?: initialHour
    val d = duration.toIntOrNull() ?: 60
    val startMinute = h * 60
    val isPast = (effectiveDate < todayDateStr) || (isToday && startMinute <= currentTotalMin)
    val canSave = title.isNotBlank() && !isPast

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Schedule Time Block",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(20) },
            label = { Text("Block Title (max 20)") },
            supportingText = { Text("${title.length}/20", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            placeholder = { Text("e.g. Design review, Team sync") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it },
                label = { Text("Start Hour (0-23)") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration (mins)") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.animation.AnimatedVisibility(visible = isPast) {
            Text(
                text = "Cannot schedule in the past. Choose a future time.",
                color = DangerRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (canSave) WorkIndigo else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = {
                if (canSave && !isSubmitting) {
                    isSubmitting = true
                    onCreate(title.trim().take(20), startMinute, d, "#5E5CE6")
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Block",
                    color = if (canSave) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
