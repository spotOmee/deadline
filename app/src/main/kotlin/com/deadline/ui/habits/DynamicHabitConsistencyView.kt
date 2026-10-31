package com.deadline.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.detectFastDragGesturesAfterLongPress
import com.deadline.ui.components.triggerPickupHaptic
import com.deadline.ui.components.MorphingLeadingActionIcon
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.HapticHelper
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletion
import com.deadline.domain.GlobalTimeManager
import com.deadline.domain.HabitContinuumStatus
import com.deadline.domain.HabitContinuumTracker
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.StatusUrgent
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class MonthTimelineDay(
    val dayOfMonth: Int,
    val dayOfWeek: String,
    val dateKey: String,
    val isToday: Boolean,
    val isPast: Boolean,
    val isFuture: Boolean
)

@Composable
fun DynamicHabitConsistencyView(
    habits: List<Habit>,
    todayCompletions: Map<String, HabitCompletion>,
    monthCompletions: Map<String, Set<String>>,
    modifier: Modifier = Modifier,
    timeFormat24Hour: Boolean = false,
    onToggleToday: (Habit) -> Unit = {},
    onReorderHabits: ((List<Habit>) -> Unit)? = null
) {
    if (habits.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No habits yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isSettling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var draggedHabitIndex by remember { mutableStateOf<Int?>(null) }
    var dragHabitOffsetY by remember { mutableFloatStateOf(0f) }
    var localHabits by remember { mutableStateOf(habits) }
    LaunchedEffect(habits) {
        if (draggedHabitIndex == null) {
            localHabits = habits
        }
    }

    val currentCal = remember(timeFormat24Hour) {
        GlobalTimeManager.getCurrentCalendar(timeFormat24Hour)
    }
    val currentYear = currentCal.get(Calendar.YEAR)
    val currentMonth = currentCal.get(Calendar.MONTH) // 0-based
    val currentDay = currentCal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val timelineDays = remember(currentYear, currentMonth, currentDay, daysInMonth) {
        (1..daysInMonth).map { dayNum ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, dayNum)
            }
            val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                Calendar.SUNDAY -> "Sun"
                else -> ""
            }
            val isToday = (dayNum == currentDay)
            val isPast = (dayNum < currentDay)
            val isFuture = (dayNum > currentDay)
            val dateKey = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, dayNum)

            MonthTimelineDay(
                dayOfMonth = dayNum,
                dayOfWeek = dayOfWeek,
                dateKey = dateKey,
                isToday = isToday,
                isPast = isPast,
                isFuture = isFuture
            )
        }
    }

    val density = LocalDensity.current
    val dayCellWidthDp = 40.dp
    val habitColumnWidthDp = 115.dp
    val habitRowHeightDp = 48.dp
    val habitRowHeightPx = with(density) { habitRowHeightDp.toPx() }
    val calendarHeaderHeightDp = 52.dp

    val initialScrollPx = remember(currentDay) {
        with(density) {
            val cellWidthPx = dayCellWidthDp.toPx()
            val targetIndex = (currentDay - 3).coerceAtLeast(0)
            (targetIndex * cellWidthPx).roundToInt()
        }
    }

    val horizontalScrollState = rememberScrollState(initial = initialScrollPx)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Sticky Header: Habit column title on left & Horizontally scrolling full month date strip on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Habit Column Header
            Box(
                modifier = Modifier
                    .width(habitColumnWidthDp)
                    .height(calendarHeaderHeightDp)
                    .padding(start = 16.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "HABIT",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Calendar Strip Header (Full month continuum 1..daysInMonth)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .height(calendarHeaderHeightDp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                timelineDays.forEach { day ->
                    CalendarDayHeaderCell(day = day, cellWidth = dayCellWidthDp)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
            thickness = 0.5.dp
        )

        // Fluid Habit List & Dynamic State Tracking Grid
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left Column: Habit Names (Vertically listed)
            Column(
                modifier = Modifier.width(habitColumnWidthDp)
            ) {
                localHabits.forEachIndexed { index, habit ->
                    key(habit.id) {
                    val isTodayCompleted = todayCompletions[habit.id]?.let {
                        !it.isSkip && it.count >= habit.targetCount
                    } == true || monthCompletions[habit.id]?.contains(
                        String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, currentDay)
                    ) == true
                    val isThisRowDragged = draggedHabitIndex == index
                    val displacement = calculateContinuousDisplacement(
                        itemIndex = index,
                        draggedIndex = draggedHabitIndex,
                        dragOffsetY = dragHabitOffsetY,
                        itemHeightPx = habitRowHeightPx
                    )

                    HabitNameRow(
                        habit = habit,
                        isTodayCompleted = isTodayCompleted,
                        rowHeight = habitRowHeightDp,
                        isDragging = isThisRowDragged,
                        modifier = Modifier
                            .zIndex(if (isThisRowDragged) 100f else 1f)
                            .graphicsLayer {
                                translationY = if (isThisRowDragged) dragHabitOffsetY else displacement
                                if (isThisRowDragged) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 12f
                                }
                            }
                            .pointerInput(habit.id) {
                                detectFastDragGesturesAfterLongPress(
                                    longPressTimeoutMillis = DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS,
                                    onDragStart = {
                                        settleJob?.cancel()
                                        isSettling = false
                                        triggerPickupHaptic(context, hapticFeedback)
                                        draggedHabitIndex = index
                                        dragHabitOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        if (isSettling) return@detectFastDragGesturesAfterLongPress
                                        val startIdx = draggedHabitIndex
                                        if (startIdx != null && habitRowHeightPx > 0f) {
                                            val targetIdx = calculateSlotTargetIndex(startIdx, dragHabitOffsetY, habitRowHeightPx, localHabits.size)
                                            val targetSettleY = calculateSettleTargetOffset(startIdx, targetIdx, habitRowHeightPx)
                                            isSettling = true
                                            settleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragHabitOffsetY,
                                                    targetOffset = targetSettleY,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragHabitOffsetY = animatedY
                                                }
                                                if (startIdx != targetIdx && targetIdx in localHabits.indices) {
                                                    HapticHelper.success(context)
                                                    val reordered = localHabits.toMutableList()
                                                    val moved = reordered.removeAt(startIdx)
                                                    reordered.add(targetIdx, moved)
                                                    localHabits = reordered
                                                    onReorderHabits?.invoke(reordered)
                                                }
                                                draggedHabitIndex = null
                                                dragHabitOffsetY = 0f
                                                isSettling = false
                                            }
                                        } else {
                                            draggedHabitIndex = null
                                            dragHabitOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        if (isSettling) return@detectFastDragGesturesAfterLongPress
                                        val startIdx = draggedHabitIndex
                                        if (startIdx != null) {
                                            isSettling = true
                                            settleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragHabitOffsetY,
                                                    targetOffset = 0f,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragHabitOffsetY = animatedY
                                                }
                                                draggedHabitIndex = null
                                                dragHabitOffsetY = 0f
                                                isSettling = false
                                            }
                                        } else {
                                            draggedHabitIndex = null
                                            dragHabitOffsetY = 0f
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (isSettling) return@detectFastDragGesturesAfterLongPress
                                        change.consume()
                                        dragHabitOffsetY += dragAmount.y
                                    }
                                )
                            }
                    )

                    if (index < localHabits.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
                            thickness = 0.5.dp
                        )
                    }
                    }
                }
            }

            // Right Column: Habit Status Matrix (Horizontally scrolling continuum)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                localHabits.forEachIndexed { index, habit ->
                    key(habit.id) {
                    val isThisRowDragged = draggedHabitIndex == index
                    val displacement = calculateContinuousDisplacement(
                        itemIndex = index,
                        draggedIndex = draggedHabitIndex,
                        dragOffsetY = dragHabitOffsetY,
                        itemHeightPx = habitRowHeightPx
                    )

                    Row(
                        modifier = Modifier
                            .height(habitRowHeightDp)
                            .zIndex(if (isThisRowDragged) 100f else 1f)
                            .graphicsLayer {
                                translationY = if (isThisRowDragged) dragHabitOffsetY else displacement
                                if (isThisRowDragged) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        timelineDays.forEach { day ->
                            HabitStatusIndicatorCell(
                                habit = habit,
                                day = day,
                                cellWidth = dayCellWidthDp,
                                todayCompletions = todayCompletions,
                                monthCompletions = monthCompletions,
                                onToggleToday = onToggleToday
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    if (index < localHabits.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
                            thickness = 0.5.dp
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayHeaderCell(
    day: MonthTimelineDay,
    cellWidth: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(cellWidth)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .then(
                if (day.isToday) Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandAmberStart.copy(alpha = 0.15f))
                    .border(1.dp, BrandAmberStart.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(vertical = 3.dp)
                else Modifier.padding(vertical = 3.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = day.dayOfWeek,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) BrandAmberStart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (day.isToday) BrandAmberStart else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HabitNameRow(
    habit: Habit,
    isTodayCompleted: Boolean,
    rowHeight: Dp,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.color))
    } catch (_: Exception) {
        BrandAmberStart
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MorphingLeadingActionIcon(
            isDragging = isDragging,
            dragHandleColor = if (isTodayCompleted) BreakGreen else habitColor
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isTodayCompleted) BreakGreen else habitColor)
            )
        }
        Text(
            text = habit.name,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HabitStatusIndicatorCell(
    habit: Habit,
    day: MonthTimelineDay,
    cellWidth: Dp,
    todayCompletions: Map<String, HabitCompletion>,
    monthCompletions: Map<String, Set<String>>,
    onToggleToday: (Habit) -> Unit
) {
    val isCompleted = if (day.isToday) {
        (todayCompletions[habit.id]?.let { !it.isSkip && it.count >= habit.targetCount } == true) ||
        (monthCompletions[habit.id]?.contains(day.dateKey) == true)
    } else {
        monthCompletions[habit.id]?.contains(day.dateKey) == true
    }

    val status = HabitContinuumTracker.resolveStatus(
        isCompleted = isCompleted,
        isPast = day.isPast
    )

    Box(
        modifier = Modifier
            .width(cellWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            HabitContinuumStatus.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(BreakGreen)
                        .then(
                            if (day.isToday) Modifier.clickable { onToggleToday(habit) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            HabitContinuumStatus.MISSED -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(StatusUrgent.copy(alpha = 0.12f))
                        .border(1.dp, StatusUrgent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Missed",
                        tint = StatusUrgent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            HabitContinuumStatus.PENDING -> {
                if (day.isToday) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(BrandAmberStart.copy(alpha = 0.10f))
                            .border(1.2.dp, BrandAmberStart.copy(alpha = 0.75f), CircleShape)
                            .clickable { onToggleToday(habit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "O",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAmberStart,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "O",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
