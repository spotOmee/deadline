package com.deadline.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.Habit
import com.deadline.data.local.HabitPlanItem
import com.deadline.data.local.ItemTimerPreset
import com.deadline.data.local.PomodoroType
import com.deadline.data.local.Project
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.TaskPriority
import com.deadline.data.local.TaskStatus
import com.deadline.domain.GlobalTimeManager
import com.deadline.service.PomodoroService
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.ItemTimerPresetModal
import com.deadline.ui.components.PlanCalendarIcon
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.StandardEmptyState
import com.deadline.ui.components.UniversalSwipeableItem
import com.deadline.ui.components.LocalIsItemCurrentlyDragged
import com.deadline.ui.components.LocalItemLastDragEndTime
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.UniversalAutoScrollCoordinator
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.MorphingLeadingActionIcon
import com.deadline.ui.components.triggerPickupHaptic
import androidx.compose.ui.platform.LocalHapticFeedback
import com.deadline.ui.theme.AmberGradient
import com.deadline.ui.theme.BrandAmberEnd
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.StatusHigh
import com.deadline.ui.theme.StatusLow
import com.deadline.ui.theme.StatusMedium
import com.deadline.ui.theme.StatusUrgent
import com.deadline.ui.theme.WorkIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalDeadlineRepository = staticCompositionLocalOf<DeadlineRepository?> { null }
val LocalTimerNavigator = staticCompositionLocalOf<() -> Unit> { {} }
val LocalGoalConfigHandler = staticCompositionLocalOf<(GoalItem) -> Unit> { {} }

data class PlanDateItem(
    val calendar: Calendar,
    val dateString: String,
    val dayOfWeekInitial: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isPast: Boolean = false
)

fun generatePlanDates(
    anchorCalendar: Calendar = Calendar.getInstance(),
    pastDays: Int = 0,
    futureDays: Int = 60
): List<PlanDateItem> {
    val list = ArrayList<PlanDateItem>(pastDays + futureDays + 1)
    val todayCal = anchorCalendar.clone() as Calendar
    val todayStr = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        todayCal.get(Calendar.YEAR),
        todayCal.get(Calendar.MONTH) + 1,
        todayCal.get(Calendar.DAY_OF_MONTH)
    )

    val startCal = (todayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -pastDays)
    }

    for (i in 0..(pastDays + futureDays)) {
        val dayCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, i)
        }
        val dateStr = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            dayCal.get(Calendar.YEAR),
            dayCal.get(Calendar.MONTH) + 1,
            dayCal.get(Calendar.DAY_OF_MONTH)
        )
        val initial = when (dayCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> "D"
        }
        val dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH)
        val isPast = dateStr < todayStr
        list.add(
            PlanDateItem(
                calendar = dayCal,
                dateString = dateStr,
                dayOfWeekInitial = initial,
                dayOfMonth = dayOfMonth,
                isToday = (dateStr == todayStr),
                isPast = isPast
            )
        )
    }
    return list
}

@Composable
fun DayPillCapsule(
    day: PlanDateItem,
    isSelected: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val universalYellow = BrandAmberEnd // #F59E0B primary amber

    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected && !day.isPast) 6.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillElevation"
    )

    val animatedBackgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && !day.isPast -> universalYellow
            day.isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillBgColor"
    )

    val animatedWeekdayColor by animateColorAsState(
        targetValue = when {
            isSelected && !day.isPast -> Color.White.copy(alpha = 0.9f)
            day.isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillWeekdayColor"
    )

    val animatedDateColor by animateColorAsState(
        targetValue = when {
            isSelected && !day.isPast -> Color.White
            day.isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillDateColor"
    )

    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .then(
                if (isSelected && !day.isPast) {
                    Modifier.shadow(
                        elevation = animatedElevation,
                        shape = pillShape,
                        spotColor = universalYellow.copy(alpha = 0.5f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(pillShape)
            .background(animatedBackgroundColor)
            .clickable(enabled = !day.isPast, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.dayOfWeekInitial,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = animatedWeekdayColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${day.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 17.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = animatedDateColor
            )
        }
    }
}

@Composable
fun HorizontalPlanDatePicker(
    selectedDate: PlanDateItem?,
    onDateSelected: (PlanDateItem) -> Unit,
    modifier: Modifier = Modifier,
    days: List<PlanDateItem> = remember { generatePlanDates() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialTodayIndex = remember(days) {
        val idx = days.indexOfFirst { it.isToday }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialTodayIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val itemWidthDp = 48.dp
    val itemSpacingDp = 10.dp
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
    val itemSpacingPx = with(density) { itemSpacingDp.toPx() }
    val totalItemWidthPx = itemWidthPx + itemSpacingPx

    val centerIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val calculated = if (offset > totalItemWidthPx / 2f) firstIdx + 1 else firstIdx
            calculated.coerceIn(0, days.size - 1)
        }
    }

    LaunchedEffect(centerIndex) {
        if (centerIndex in days.indices) {
            val centeredItem = days[centerIndex]
            if (!centeredItem.isPast && (selectedDate == null || selectedDate.dateString != centeredItem.dateString)) {
                HapticHelper.selection(context)
                onDateSelected(centeredItem)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Center indicator with down-arrow pointer directly above the selected date
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(width = 12.dp, height = 7.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = BrandAmberEnd)
            }
        }

        // Horizontal swipeable carousel with vertical rounded pill/capsule containers
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val horizontalPadding = ((maxWidth - itemWidthDp) / 2).coerceAtLeast(0.dp)

            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(itemSpacingDp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(days, key = { _, day -> day.dateString }) { index, day ->
                    val isSelected = index == centerIndex
                    DayPillCapsule(
                        day = day,
                        isSelected = isSelected,
                        width = itemWidthDp,
                        height = 72.dp,
                        onClick = {
                            if (!day.isPast) {
                                scope.launch {
                                    HapticHelper.selection(context)
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

data class GoalItem(
    override val id: String,
    override val title: String,
    val subtitle: String,
    val isPaused: Boolean = false,
    val isProject: Boolean = false,
    val isTask: Boolean = false,
    val isHabit: Boolean = false,
    val projectRef: Project? = null,
    val todoRef: ProjectTodo? = null,
    val habitRef: Habit? = null,
    override val timerPreset: ItemTimerPreset? = null
) : HabitPlanItem {
    val isCompleted: Boolean get() = todoRef?.status == TaskStatus.COMPLETED
    override val date: String? get() = subtitle
    override val focusSeconds: Int get() = timerPreset?.focusDuration ?: (25 * 60)
    override val breakSeconds: Int get() = timerPreset?.breakDuration ?: (5 * 60)
    override val flowSeconds: Int get() = timerPreset?.flowDuration ?: (15 * 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanView(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier,
    subTabBarBoundsInRoot: Rect? = null,
    onHoverTab0: (Boolean) -> Unit = {},
    onDropOnTab0: () -> Unit = {},
    showImportTaskSheet: Boolean = false,
    onDismissImportTaskSheet: () -> Unit = {},
    onRequestCreate: () -> Unit = {},
    onNavigateToTimer: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var internalShowImportSheet by remember { mutableStateOf(false) }
    val isImportSheetVisible = showImportTaskSheet || internalShowImportSheet

    val userSettings by repository.userSettings.collectAsState()
    val projects by repository.projects.collectAsState()
    val habits by repository.habits.collectAsState(initial = emptyList())
    val todos by repository.todos.collectAsState()
    val planImportedTaskIds by repository.planImportedTaskIds.collectAsState()

    var itemToSchedule by remember { mutableStateOf<GoalItem?>(null) }
    var goalForTimerConfig by remember { mutableStateOf<GoalItem?>(null) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    val planDates = remember { generatePlanDates(pastDays = 0) }
    var selectedDateItem by remember {
        mutableStateOf<PlanDateItem?>(planDates.find { it.isToday } ?: planDates.firstOrNull())
    }

    LaunchedEffect(selectedDateItem) {
        selectedDateItem?.dateString?.let { dateStr ->
            repository.setSelectedPlanDate(dateStr)
        }
    }

    val allActiveGoals = remember(
        planImportedTaskIds,
        todos,
        selectedDateItem?.dateString
    ) {
        val currentDateStr = selectedDateItem?.dateString
        val todayStr = planDates.find { it.isToday }?.dateString
        val list = mutableListOf<GoalItem>()
        planImportedTaskIds.forEach { taskId ->
            val cleanTaskId = taskId.removePrefix("task_")
            val todo = todos.find { it.id == taskId || it.id == cleanTaskId }
            if (todo != null && todo.status != TaskStatus.COMPLETED) {
                val effectiveDate = todo.dueDate ?: todayStr
                if (effectiveDate == currentDateStr) {
                    val currentProject = projects.find { it.id == todo.projectId }
                    list.add(
                        GoalItem(
                            id = todo.id,
                            title = todo.title,
                            subtitle = effectiveDate ?: "",
                            isPaused = false,
                            isProject = false,
                            isTask = true,
                            projectRef = currentProject,
                            todoRef = if (todo.dueDate == null && effectiveDate != null) todo.copy(dueDate = effectiveDate) else todo,
                            timerPreset = todo.timerPreset
                        )
                    )
                }
            }
        }
        list.toList()
    }

    var draggedGoalIndex by remember { mutableStateOf<Int?>(null) }
    var dragGoalOffsetX by remember { mutableFloatStateOf(0f) }
    var dragGoalOffsetY by remember { mutableFloatStateOf(0f) }
    var lastGoalDragEndTime by remember { mutableLongStateOf(0L) }
    var currentHoverGoalIndex by remember { mutableStateOf<Int?>(null) }
    var draggedInitialPointerInRoot by remember { mutableStateOf<Offset?>(null) }
    var isGoalSettling by remember { mutableStateOf(false) }
    var goalSettleJob by remember { mutableStateOf<Job?>(null) }
    var isHoveringTab0 by remember { mutableStateOf(false) }

    var localGoalItems by remember { mutableStateOf<List<GoalItem>>(allActiveGoals) }
    var exitingGoalIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(allActiveGoals) {
        if (draggedGoalIndex == null) {
            val newMap = allActiveGoals.associateBy { it.id }
            val currentIds = localGoalItems.map { it.id }
            if (currentIds.isNotEmpty() && currentIds.any { it in newMap }) {
                val remainingPreserved = currentIds.filter { it in newMap }.mapNotNull { newMap[it] }
                val addedItems = allActiveGoals.filter { it.id !in currentIds }
                localGoalItems = remainingPreserved + addedItems
            } else {
                localGoalItems = allActiveGoals
            }
        }
    }

    val goalItemHeightDp = 76.dp
    val goalItemHeightPx = with(density) { (goalItemHeightDp + 16.dp).toPx() }
    var measuredGoalItemHeightPx by remember { mutableFloatStateOf(goalItemHeightPx) }

    CompositionLocalProvider(
        LocalDeadlineRepository provides repository,
        LocalTimerNavigator provides onNavigateToTimer,
        LocalGoalConfigHandler provides { goal ->
            val cleanId = goal.id.removePrefix("task_")
            val latestTodo = repository.todos.value.find { it.id == goal.id || it.id == cleanId }
            val latestHabit = repository.habits.value.find { it.id == goal.id }
            val latestPreset = latestTodo?.timerPreset ?: latestHabit?.timerPreset ?: goal.timerPreset
            goalForTimerConfig = if (latestPreset != null) goal.copy(timerPreset = latestPreset) else goal
        }
    ) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Horizontal Date Picker Carousel with Center Down-Arrow Indicator
        HorizontalPlanDatePicker(
            selectedDate = selectedDateItem,
            onDateSelected = { date ->
                if (!date.isPast) {
                    selectedDateItem = date
                    repository.setSelectedPlanDate(date.dateString)
                }
            },
            days = planDates,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        if (localGoalItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                StandardEmptyState(
                    title = "No plans yet",
                    icon = {
                        PlanCalendarIcon(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            size = 28.dp
                        )
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                val planLazyListState = rememberLazyListState()
                var planViewportHeight by remember { mutableFloatStateOf(0f) }
                var planListBounds by remember { mutableStateOf(Rect.Zero) }

                val pointerYInViewport = if (draggedInitialPointerInRoot != null) {
                    draggedInitialPointerInRoot!!.y + dragGoalOffsetY - planListBounds.top
                } else 0f

                UniversalAutoScrollCoordinator(
                    lazyListState = planLazyListState,
                    isDragging = draggedGoalIndex != null,
                    pointerYInViewport = pointerYInViewport,
                    viewportHeight = planViewportHeight
                )

                LazyColumn(
                    state = planLazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            planViewportHeight = coords.size.height.toFloat()
                            planListBounds = coords.boundsInRoot()
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                itemsIndexed(localGoalItems, key = { _, it -> it.id }) { index, goalItem ->
                    val isThisItemDragged = draggedGoalIndex == index
                    val isExiting = goalItem.id in exitingGoalIds

                    AnimatedVisibility(
                        visible = !isExiting,
                        modifier = Modifier
                            .zIndex(if (isThisItemDragged) 100f else 1f)
                            .then(
                                if (!isThisItemDragged) {
                                    Modifier.animateItem(
                                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        placementSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                } else Modifier
                            ),
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ) + fadeOut(tween(160))
                    ) {
                        UniversalSwipeableItem(
                        modifier = Modifier.fillMaxWidth(),
                        itemId = goalItem.id,
                        cornerRadius = 20.dp,
                        isThisItemDragged = isThisItemDragged,
                        dragOffsetX = dragGoalOffsetX,
                        dragOffsetY = dragGoalOffsetY,
                        nonDraggedDisplacement = 0f,
                        constrainToVerticalAxis = (!isHoveringTab0),
                        onDelete = {
                            HapticHelper.heavy(context)
                            val item = goalItem
                            exitingGoalIds = exitingGoalIds + item.id
                            scope.launch {
                                delay(200)
                                localGoalItems = localGoalItems.filter { it.id != item.id }
                                exitingGoalIds = exitingGoalIds - item.id
                                val taskId = item.todoRef?.id ?: item.id.removePrefix("task_")
                                withContext(Dispatchers.IO) {
                                    repository.removeTaskFromPlan(taskId)
                                }
                            }
                        },
                        onSchedule = {
                            HapticHelper.click(context)
                            itemToSchedule = goalItem
                            showScheduleSheet = true
                        },
                        onDragStartWithOffset = { offset, boundsInRoot ->
                            goalSettleJob?.cancel()
                            isGoalSettling = false
                            triggerPickupHaptic(context, hapticFeedback)
                            measuredGoalItemHeightPx = boundsInRoot.height + with(density) { 16.dp.toPx() }
                            draggedGoalIndex = index
                            currentHoverGoalIndex = index
                            dragGoalOffsetX = 0f
                            dragGoalOffsetY = 0f
                            draggedInitialPointerInRoot = boundsInRoot.topLeft + offset
                            isHoveringTab0 = false
                            onHoverTab0(false)
                        },
                        onDragDelta = onDragDelta@ { dx, dy ->
                            if (isGoalSettling) return@onDragDelta
                            dragGoalOffsetX += dx
                            dragGoalOffsetY += dy
                            val currentPointer = draggedInitialPointerInRoot?.plus(Offset(dragGoalOffsetX, dragGoalOffsetY))

                            val subTabBounds = subTabBarBoundsInRoot
                            if (currentPointer != null && subTabBounds != null &&
                                currentPointer.y in (subTabBounds.top - 40f)..(subTabBounds.bottom + 40f) &&
                                currentPointer.x in (subTabBounds.left - 20f)..(subTabBounds.left + subTabBounds.width * 0.55f)
                            ) {
                                if (!isHoveringTab0) {
                                    HapticHelper.selection(context)
                                    isHoveringTab0 = true
                                    onHoverTab0(true)
                                }
                            } else {
                                if (isHoveringTab0) {
                                    isHoveringTab0 = false
                                    onHoverTab0(false)
                                }

                                val currentIdx = draggedGoalIndex ?: index
                                val effectiveHeight = if (measuredGoalItemHeightPx > 0f) measuredGoalItemHeightPx else goalItemHeightPx
                                val targetIdx = calculateSlotTargetIndex(currentIdx, dragGoalOffsetY, effectiveHeight, localGoalItems.size)
                                if (targetIdx != currentIdx && targetIdx in localGoalItems.indices) {
                                    val reordered = localGoalItems.toMutableList()
                                    val direction = if (targetIdx > currentIdx) 1 else -1
                                    var iterIdx = currentIdx
                                    while (iterIdx != targetIdx) {
                                        val nextIdx = iterIdx + direction
                                        java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                        iterIdx = nextIdx
                                    }
                                    val slotsMoved = targetIdx - currentIdx
                                    val goalItemHeightPx = effectiveHeight
                                    dragGoalOffsetY -= slotsMoved * goalItemHeightPx
                                    draggedGoalIndex = targetIdx
                                    currentHoverGoalIndex = targetIdx
                                    localGoalItems = reordered
                                    HapticHelper.tick(context)
                                }
                            }
                        },
                        onDragEnd = onDragEnd@ {
                            if (isGoalSettling) return@onDragEnd
                            if (isHoveringTab0) {
                                HapticHelper.success(context)
                                onDropOnTab0()
                                draggedGoalIndex = null
                                currentHoverGoalIndex = null
                                dragGoalOffsetX = 0f
                                dragGoalOffsetY = 0f
                                draggedInitialPointerInRoot = null
                                isHoveringTab0 = false
                                onHoverTab0(false)
                            } else {
                                val startIdx = draggedGoalIndex
                                if (startIdx != null) {
                                    val effectiveHeight = if (measuredGoalItemHeightPx > 0f) measuredGoalItemHeightPx else goalItemHeightPx
                                    val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                    isGoalSettling = true
                                    goalSettleJob = scope.launch {
                                        animateDragToSettle(
                                            currentOffset = dragGoalOffsetY,
                                            targetOffset = targetSettleY,
                                            springSpec = getReorderSpringSpec(context)
                                        ) { animatedY ->
                                            dragGoalOffsetY = animatedY
                                        }
                                        val reorderedTaskIds = localGoalItems.mapNotNull { it.todoRef?.id ?: it.id.removePrefix("task_") }
                                        if (reorderedTaskIds.isNotEmpty()) {
                                            withContext(Dispatchers.IO) {
                                                repository.reorderPlanTasks(reorderedTaskIds)
                                            }
                                        }
                                        HapticHelper.success(context)
                                        draggedGoalIndex = null
                                        currentHoverGoalIndex = null
                                        dragGoalOffsetX = 0f
                                        dragGoalOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                        isHoveringTab0 = false
                                        onHoverTab0(false)
                                        lastGoalDragEndTime = System.currentTimeMillis()
                                        isGoalSettling = false
                                    }
                                } else {
                                    draggedGoalIndex = null
                                    currentHoverGoalIndex = null
                                    dragGoalOffsetX = 0f
                                    dragGoalOffsetY = 0f
                                    draggedInitialPointerInRoot = null
                                    isHoveringTab0 = false
                                    onHoverTab0(false)
                                    lastGoalDragEndTime = System.currentTimeMillis()
                                }
                            }
                        },
                        onDragCancel = onDragCancel@ {
                            if (isGoalSettling) return@onDragCancel
                            val startIdx = draggedGoalIndex
                            if (startIdx != null) {
                                isGoalSettling = true
                                goalSettleJob = scope.launch {
                                    animateDragToSettle(
                                        currentOffset = dragGoalOffsetY,
                                        targetOffset = 0f,
                                        springSpec = getReorderSpringSpec(context)
                                    ) { animatedY ->
                                        dragGoalOffsetY = animatedY
                                    }
                                    val reorderedTaskIds = localGoalItems.mapNotNull { it.todoRef?.id ?: it.id.removePrefix("task_") }
                                    if (reorderedTaskIds.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            repository.reorderPlanTasks(reorderedTaskIds)
                                        }
                                    }
                                    draggedGoalIndex = null
                                    currentHoverGoalIndex = null
                                    dragGoalOffsetX = 0f
                                    dragGoalOffsetY = 0f
                                    draggedInitialPointerInRoot = null
                                    isHoveringTab0 = false
                                    onHoverTab0(false)
                                    lastGoalDragEndTime = System.currentTimeMillis()
                                    isGoalSettling = false
                                }
                            } else {
                                draggedGoalIndex = null
                                currentHoverGoalIndex = null
                                dragGoalOffsetX = 0f
                                dragGoalOffsetY = 0f
                                draggedInitialPointerInRoot = null
                                isHoveringTab0 = false
                                onHoverTab0(false)
                                lastGoalDragEndTime = System.currentTimeMillis()
                            }
                        }
                    ) {
                        InteractiveGoalCard(goal = goalItem) {
                            val todo = goalItem.todoRef
                            val isDone = todo?.status == TaskStatus.COMPLETED
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MorphingLeadingActionIcon(
                                        isDragging = isThisItemDragged,
                                        dragHandleColor = BrandAmberStart
                                    ) {
                                        Box(
                                            modifier = Modifier.size(20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ReorderGrabHandleIcon(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                size = 16.dp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = goalItem.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDone) BreakGreen
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            val isCurrentlyDragged = isThisItemDragged
                                            if (isThisItemDragged) return@clickable
                                            if (!isCurrentlyDragged && System.currentTimeMillis() - lastGoalDragEndTime > 300L) {
                                                if (todo != null) {
                                                HapticHelper.success(context)
                                                val targetId = todo.id
                                                if (!isDone) {
                                                    exitingGoalIds = exitingGoalIds + goalItem.id
                                                    scope.launch {
                                                        delay(180)
                                                        localGoalItems = localGoalItems.filter { it.id != goalItem.id && it.id != targetId }
                                                        exitingGoalIds = exitingGoalIds - goalItem.id
                                                        repository.updateTodoStatus(targetId, TaskStatus.COMPLETED)
                                                    }
                                                } else {
                                                    scope.launch {
                                                        repository.updateTodoStatus(targetId, TaskStatus.TODO)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Completed",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                item { Spacer(modifier = Modifier.height(90.dp)) }
            }
        }
    }
    }

    // Schedule Item directly onto Timeline Bottom Sheet
    if (showScheduleSheet && itemToSchedule != null) {
        ScheduleTimePickerSheet(
            item = itemToSchedule!!,
            is24Hour = userSettings.timeFormat24Hour,
            selectedDateString = selectedDateItem?.dateString,
            onDismiss = {
                showScheduleSheet = false
                itemToSchedule = null
            },
            onConfirmSchedule = { startMinute, durationMinutes, colorHex, projectId, todoId ->
                val scheduledItem = itemToSchedule
                if (scheduledItem != null) {
                    val dateStr = selectedDateItem?.dateString
                    scope.launch {
                        repository.addTimeBlock(
                            title = scheduledItem.title,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            color = colorHex,
                            projectId = projectId,
                            todoId = todoId,
                            date = dateStr
                        )
                        if (todoId != null && dateStr != null) {
                            repository.updateTodoDueDate(todoId, dateStr)
                        }
                    }
                }
                showScheduleSheet = false
                itemToSchedule = null
            }
        )
    }

    // Import Task to Plan Modal Bottom Sheet
    if (isImportSheetVisible) {
        ImportTaskBottomSheet(
            projects = projects,
            todos = todos,
            planImportedTaskIds = planImportedTaskIds,
            localGoalItemIds = localGoalItems.map { it.id }.toSet(),
            selectedDateString = selectedDateItem?.dateString,
            onDismiss = {
                internalShowImportSheet = false
                onDismissImportTaskSheet()
            },
            onImportTask = { todo ->
                val currentDateStr = selectedDateItem?.dateString
                val currentProject = projects.find { it.id == todo.projectId }
                val isDone = todo.status == TaskStatus.COMPLETED
                val updatedTodo = todo.copy(dueDate = currentDateStr)
                val newGoalItem = GoalItem(
                    id = todo.id,
                    title = todo.title,
                    subtitle = currentDateStr ?: "",
                    isPaused = false,
                    isProject = false,
                    isTask = true,
                    projectRef = currentProject,
                    todoRef = updatedTodo,
                    timerPreset = todo.timerPreset
                )
                if (localGoalItems.none { it.id == newGoalItem.id }) {
                    localGoalItems = localGoalItems + newGoalItem
                }
                scope.launch {
                    repository.importTaskToPlan(todo.id, currentDateStr)
                }
                internalShowImportSheet = false
                onDismissImportTaskSheet()
            }
        )
    }

    if (goalForTimerConfig != null) {
        val configGoal = goalForTimerConfig!!
        ItemTimerPresetModal(
            item = configGoal,
            onDismiss = { goalForTimerConfig = null },
            onSavePreset = { updatedPreset ->
                // Immediately commit to local state for instantaneous UI reactivity
                localGoalItems = localGoalItems.map {
                    if (it.id == configGoal.id) it.copy(timerPreset = updatedPreset) else it
                }
                scope.launch {
                    repository.updateItemTimerPreset(configGoal.id, updatedPreset)
                }
            }
        )
    }
    }
}

@Composable
private fun InteractiveGoalCard(
    goal: GoalItem,
    isThisItemDragged: Boolean = LocalIsItemCurrentlyDragged.current,
    lastDragEndTime: Long = LocalItemLastDragEndTime.current,
    onSingleTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isCurrentlyDragged = isThisItemDragged
    var localLastDragEndTime by remember(goal.id) { mutableLongStateOf(0L) }
    LaunchedEffect(isThisItemDragged) {
        if (!isThisItemDragged) {
            localLastDragEndTime = System.currentTimeMillis()
        }
    }
    val effectiveDragEndTime = if (lastDragEndTime > 0L) lastDragEndTime else localLastDragEndTime
    val context = LocalContext.current
    val timerNavigator = LocalTimerNavigator.current
    val goalConfigHandler = LocalGoalConfigHandler.current
    val repo = LocalDeadlineRepository.current

    val handleSingleTap = onSingleTap ?: {
        HapticHelper.selection(context)
        val cleanId = goal.id.removePrefix("task_")
        val repoPreset = repo?.todos?.value?.find { it.id == goal.id || it.id == cleanId }?.timerPreset
            ?: repo?.habits?.value?.find { it.id == goal.id }?.timerPreset
        val preset = repoPreset ?: goal.timerPreset ?: ItemTimerPreset(
            focusDuration = goal.focusSeconds,
            breakDuration = goal.breakSeconds,
            flowDuration = goal.flowSeconds
        )
        PomodoroService.commitPreset(
            context = context,
            preset = preset,
            taskTitle = goal.title,
            sessionType = PomodoroType.FOCUS
        )
        timerNavigator()
    }

    val handleDoubleTap = onDoubleTap ?: {
        HapticHelper.click(context)
        val cleanId = goal.id.removePrefix("task_")
        val repoPreset = repo?.todos?.value?.find { it.id == goal.id || it.id == cleanId }?.timerPreset
            ?: repo?.habits?.value?.find { it.id == goal.id }?.timerPreset
        val goalWithPreset = if (repoPreset != null) goal.copy(timerPreset = repoPreset) else goal
        goalConfigHandler(goalWithPreset)
    }

    SquircleCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(goal.id, isThisItemDragged) {
                if (isThisItemDragged) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        if (!isCurrentlyDragged && System.currentTimeMillis() - effectiveDragEndTime > 300L) {
                            if (!isThisItemDragged) handleDoubleTap()
                        }
                    },
                    onTap = {
                        if (!isCurrentlyDragged && System.currentTimeMillis() - effectiveDragEndTime > 300L) {
                            if (!isThisItemDragged) handleSingleTap()
                        }
                    }
                )
            },
        cornerRadius = 20.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

/**
 * Apple-Style Continuous Scrolling Wheel Column (Cupertino-style UIPickerView)
 * matching the Timer section architecture.
 */
@Composable
fun PlanAppleWheelColumn(
    items: List<Int>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    itemHeightDp: Dp = 38.dp,
    visibleCount: Int = 3
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val baseCount = items.size
    val loopMultiplier = 1000
    val midMultiplier = loopMultiplier / 2
    val totalCount = baseCount * loopMultiplier

    val initialIndex = remember(baseCount) {
        (midMultiplier * baseCount) + selectedIndex.coerceIn(0, baseCount - 1)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val rawCenterIndex by remember {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > itemHeightPx / 2) firstIndex + 1 else firstIndex
        }
    }

    val normalizedIndex by remember {
        derivedStateOf {
            ((rawCenterIndex % baseCount) + baseCount) % baseCount
        }
    }

    LaunchedEffect(normalizedIndex) {
        if (normalizedIndex != selectedIndex && normalizedIndex in items.indices) {
            HapticHelper.selection(context)
            onSelectedIndexChanged(normalizedIndex)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices && !listState.isScrollInProgress) {
            val currentMod = ((rawCenterIndex % baseCount) + baseCount) % baseCount
            if (currentMod != selectedIndex) {
                val delta = selectedIndex - currentMod
                val target = rawCenterIndex + delta
                listState.scrollToItem(target)
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val current = rawCenterIndex
            val currentCycle = current / baseCount
            if (abs(currentCycle - midMultiplier) > 100) {
                val offset = listState.firstVisibleItemScrollOffset
                val reCentered = (midMultiplier * baseCount) + (((current % baseCount) + baseCount) % baseCount)
                listState.scrollToItem(reCentered, offset)
            }
        }
    }

    val wheelHeightDp = itemHeightDp * visibleCount

    Box(
        modifier = modifier
            .height(wheelHeightDp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Center Selection Lens
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(itemHeightDp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeightDp * ((visibleCount - 1) / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            items(totalCount) { globalIndex ->
                val dist = abs(globalIndex - rawCenterIndex)
                val isSelected = dist == 0
                val alpha = when (dist) {
                    0 -> 1f
                    1 -> 0.45f
                    2 -> 0.20f
                    3 -> 0.08f
                    else -> 0.03f
                }
                val scale = when (dist) {
                    0 -> 1.15f
                    1 -> 0.90f
                    2 -> 0.78f
                    3 -> 0.68f
                    else -> 0.60f
                }
                val value = items[globalIndex % baseCount]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d", value),
                        fontSize = 24.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = accentColor.copy(alpha = alpha),
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimePickerSheet(
    item: GoalItem,
    is24Hour: Boolean,
    selectedDateString: String? = null,
    onDismiss: () -> Unit,
    onConfirmSchedule: (startMinute: Int, durationMinutes: Int, colorHex: String, projectId: String?, todoId: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSubmitting by remember { mutableStateOf(false) }

    val currentCal = remember { Calendar.getInstance() }
    val todayDateStr = remember {
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH) + 1,
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
    }
    val targetDate = selectedDateString ?: todayDateStr
    val isToday = targetDate == todayDateStr
    val isPastDate = targetDate < todayDateStr
    val currentMinuteOfDay = currentCal.get(Calendar.HOUR_OF_DAY) * 60 + currentCal.get(Calendar.MINUTE)

    // Future default selection: Default to next 15-minute slot so initial time isn't past
    val futureCal = remember {
        (currentCal.clone() as Calendar).apply {
            if (isToday) {
                add(Calendar.MINUTE, 15)
            }
        }
    }
    val defaultHour24 = futureCal.get(Calendar.HOUR_OF_DAY)
    val defaultMinute = futureCal.get(Calendar.MINUTE)
    val initialIsPm = defaultHour24 >= 12
    val initialHour12 = when {
        defaultHour24 == 0 -> 12
        defaultHour24 > 12 -> defaultHour24 - 12
        else -> defaultHour24
    }

    val hours = remember { (1..12).toList() }
    val minutes = remember { (0..59).toList() }

    var selectedHourIndex by remember { mutableIntStateOf(hours.indexOf(initialHour12).coerceAtLeast(0)) }
    var selectedMinuteIndex by remember { mutableIntStateOf(defaultMinute.coerceIn(0, 59)) }
    var isPm by remember { mutableStateOf(initialIsPm) }

    val itemColor = try {
        when {
            item.projectRef != null -> Color(android.graphics.Color.parseColor(item.projectRef.color))
            item.habitRef != null -> Color(android.graphics.Color.parseColor(item.habitRef.color))
            else -> WorkIndigo
        }
    } catch (_: Exception) {
        WorkIndigo
    }

    val itemColorHex = when {
        item.projectRef != null -> item.projectRef.color
        item.habitRef != null -> item.habitRef.color
        else -> "#6366F1"
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSubmitting) {
                sheetState.animateDismiss(scope, onDismiss)
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = itemColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = {
                        if (!isSubmitting) {
                            sheetState.animateDismiss(scope, onDismiss)
                        }
                    },
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

            // Timer-Styled Scheduling Interface (Dual-Wheel Picker with required AM/PM meridian toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hours Wheel
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlanAppleWheelColumn(
                        items = hours,
                        selectedIndex = selectedHourIndex,
                        onSelectedIndexChanged = { selectedHourIndex = it },
                        accentColor = BrandAmberStart,
                        itemHeightDp = 38.dp,
                        visibleCount = 3
                    )
                }

                // Colon Separator
                Text(
                    text = ":",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandAmberStart,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Minutes Wheel
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PlanAppleWheelColumn(
                        items = minutes,
                        selectedIndex = selectedMinuteIndex,
                        onSelectedIndexChanged = { selectedMinuteIndex = it },
                        accentColor = BrandAmberStart,
                        itemHeightDp = 38.dp,
                        visibleCount = 3
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Required AM / PM Meridian Toggle
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isPm) BrandAmberStart else Color.Transparent)
                            .clickable {
                                if (isPm) {
                                    HapticHelper.click(context)
                                    isPm = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isPm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPm) BrandAmberStart else Color.Transparent)
                            .clickable {
                                if (!isPm) {
                                    HapticHelper.click(context)
                                    isPm = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val selectedHour = hours[selectedHourIndex]
            val selectedMinute = minutes[selectedMinuteIndex]
            val hour24 = when {
                selectedHour == 12 -> if (isPm) 12 else 0
                isPm -> selectedHour + 12
                else -> selectedHour
            }
            val startMinute = hour24 * 60 + selectedMinute
            val isTimeInPast = isPastDate || (isToday && startMinute <= currentMinuteOfDay)
            val canSchedule = !isTimeInPast

            AnimatedVisibility(visible = isTimeInPast) {
                Text(
                    text = "Cannot schedule in the past. Select a future time.",
                    color = com.deadline.ui.theme.DangerRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }

            // Primary "Create" Action Button
            SquircleCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                containerColor = if (canSchedule) BrandAmberEnd else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = {
                    if (!isSubmitting && canSchedule) {
                        isSubmitting = true
                        HapticHelper.success(context)
                        val durationMinutes = item.habitRef?.timerPreset?.let { (it.focusDuration / 60).coerceIn(15, 240) }
                            ?: item.todoRef?.estimatedMinutes?.coerceIn(15, 240)
                            ?: 60
                        val projId = item.projectRef?.id ?: item.todoRef?.projectId
                        val todoId = item.todoRef?.id
                        scope.launch {
                            sheetState.smoothDismiss()
                            onConfirmSchedule(
                                startMinute,
                                durationMinutes,
                                itemColorHex,
                                projId,
                                todoId
                            )
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canSchedule) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTaskBottomSheet(
    projects: List<Project>,
    todos: List<ProjectTodo>,
    planImportedTaskIds: List<String>,
    localGoalItemIds: Set<String>,
    selectedDateString: String? = null,
    onDismiss: () -> Unit,
    onImportTask: (ProjectTodo) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isImporting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isImporting) {
                sheetState.animateDismiss(scope, onDismiss)
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import Task to Plan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (!isImporting) {
                            sheetState.animateDismiss(scope, onDismiss)
                        }
                    },
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

            if (todos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(todos, key = { it.id }) { todo ->
                            val isAlreadyImported = localGoalItemIds.contains(todo.id) ||
                                localGoalItemIds.contains("task_${todo.id}") ||
                                (planImportedTaskIds.contains(todo.id) && todo.dueDate == selectedDateString)

                            SquircleCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 14.dp,
                                containerColor = if (isAlreadyImported) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                onClick = {
                                    if (!isImporting) {
                                        isImporting = true
                                        HapticHelper.success(context)
                                        scope.launch {
                                            sheetState.smoothDismiss()
                                            onImportTask(todo)
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = todo.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isAlreadyImported) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(BreakGreen.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "In Plan",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BreakGreen
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(BrandAmberStart),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Import",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}
