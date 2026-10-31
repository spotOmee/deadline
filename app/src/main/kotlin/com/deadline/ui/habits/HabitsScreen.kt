package com.deadline.ui.habits

import java.util.Calendar
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deadline.ui.components.UniversalSwipeableItem
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
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.Habit
import com.deadline.data.local.HabitCompletion
import com.deadline.data.local.HabitCompletionType
import com.deadline.data.local.ItemTimerPreset
import com.deadline.data.local.PomodoroType
import com.deadline.service.PomodoroService
import com.deadline.ui.components.ItemTimerPresetModal
import com.deadline.ui.components.AppleHeader
import com.deadline.ui.components.HabitsSlidersIcon
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.PlanStateToggle
import com.deadline.ui.components.SegmentedControl
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.StandardEmptyState
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import com.deadline.ui.navigation.NavigationZoningDefaults
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.StatusUrgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {},
    onNavigateToTimer: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val userSettings by repository.userSettings.collectAsState()
    val habits by repository.habits.collectAsState()
    val todayCompletions by repository.todayCompletions.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showImportTaskSheet by remember { mutableStateOf(false) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    var habitToSchedule by remember { mutableStateOf<Habit?>(null) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var habitForTimerConfig by remember { mutableStateOf<Habit?>(null) }
    var isAnyCardSwiping by remember { mutableStateOf(false) }

    var draggedHabitIndex by remember { mutableStateOf<Int?>(null) }
    var dragHabitOffsetX by remember { mutableFloatStateOf(0f) }
    var dragHabitOffsetY by remember { mutableFloatStateOf(0f) }
    var lastHabitDragEndTime by remember { mutableLongStateOf(0L) }
    var currentHoverHabitIndex by remember { mutableStateOf<Int?>(null) }
    var draggedInitialPointerInRoot by remember { mutableStateOf<Offset?>(null) }
    var isHabitSettling by remember { mutableStateOf(false) }
    var habitSettleJob by remember { mutableStateOf<Job?>(null) }
    var subTabBarBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var hoveredPlanTab by remember { mutableStateOf(false) }
    var hoveredTodayTab by remember { mutableStateOf(false) }
    var isTouchInSubTabZone by remember { mutableStateOf(true) }

    var localHabits by remember { mutableStateOf(habits) }
    var exitingHabitIds by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(habits) {
        if (draggedHabitIndex == null) {
            localHabits = habits
        }
    }

    val density = LocalDensity.current
    val habitItemHeightDp = 76.dp
    val habitItemHeightPx = with(density) { (habitItemHeightDp + 16.dp).toPx() }
    var measuredHabitItemHeightPx by remember { mutableFloatStateOf(habitItemHeightPx) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val downChange = event.changes.firstOrNull { it.changedToDown() }
                        if (downChange != null) {
                            val totalH = size.height.toFloat()
                            isTouchInSubTabZone = NavigationZoningDefaults.isTouchInLocalizedContentZone(
                                touchY = downChange.position.y,
                                totalHeight = totalH,
                                topHeaderRatio = 0.18f
                            )
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Top Header Bar
            AppleHeader(
                title = "Habits",
                avatarUri = userSettings.avatarUri,
                onProfileClick = onOpenProfile,
                modifier = Modifier.padding(horizontal = 20.dp),
                trailingContent = {
                    AnimatedVisibility(
                        visible = pagerState.currentPage == 1,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    HapticHelper.click(context)
                                    showImportTaskSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to Plan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // View Switcher (Today | Plan)
            SegmentedControl(
                items = listOf("Today", "Plan"),
                selectedIndex = pagerState.currentPage,
                pagerState = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .onGloballyPositioned { coords ->
                        subTabBarBoundsInRoot = coords.boundsInRoot()
                    }
                    .then(
                        if (hoveredPlanTab || hoveredTodayTab) Modifier.border(2.dp, BreakGreen, RoundedCornerShape(16.dp))
                        else Modifier
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = pagerState.currentPage == 0 && isTouchInSubTabZone && !isAnyCardSwiping && draggedHabitIndex == null && !isHabitSettling,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // Today Habits List with Universal Swipe Actions & Fluid Drag-and-Drop Reordering
                        if (localHabits.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                StandardEmptyState(
                                    title = "No habits yet",
                                    icon = {
                                        HabitsSlidersIcon(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            size = 28.dp
                                        )
                                    }
                                )
                            }
                        } else {
                            val habitsLazyListState = rememberLazyListState()
                            var habitsViewportHeight by remember { mutableFloatStateOf(0f) }
                            var habitsListBounds by remember { mutableStateOf(Rect.Zero) }

                            val pointerYInViewport = if (draggedInitialPointerInRoot != null) {
                                draggedInitialPointerInRoot!!.y + dragHabitOffsetY - habitsListBounds.top
                            } else 0f

                            UniversalAutoScrollCoordinator(
                                lazyListState = habitsLazyListState,
                                isDragging = draggedHabitIndex != null,
                                pointerYInViewport = pointerYInViewport,
                                viewportHeight = habitsViewportHeight
                            )

                            LazyColumn(
                                state = habitsLazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp)
                                    .onGloballyPositioned { coords ->
                                        habitsViewportHeight = coords.size.height.toFloat()
                                        habitsListBounds = coords.boundsInRoot()
                                    },
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                            itemsIndexed(localHabits, key = { _, it -> it.id }) { index, habit ->
                                val completion = todayCompletions[habit.id]
                                val isThisItemDragged = draggedHabitIndex == index
                                val isExiting = habit.id in exitingHabitIds

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
                                    itemId = habit.id,
                                    cornerRadius = 20.dp,
                                    isThisItemDragged = isThisItemDragged,
                                    dragOffsetX = dragHabitOffsetX,
                                    dragOffsetY = dragHabitOffsetY,
                                    nonDraggedDisplacement = 0f,
                                    constrainToVerticalAxis = (!hoveredPlanTab),
                                    onDelete = {
                                        HapticHelper.heavy(context)
                                        val habitId = habit.id
                                        exitingHabitIds = exitingHabitIds + habitId
                                        scope.launch {
                                            delay(200)
                                            localHabits = localHabits.filter { it.id != habitId }
                                            exitingHabitIds = exitingHabitIds - habitId
                                            withContext(Dispatchers.IO) {
                                                repository.deleteHabit(habitId)
                                            }
                                        }
                                    },
                                    onSchedule = {
                                        HapticHelper.click(context)
                                        habitToSchedule = habit
                                        showScheduleSheet = true
                                    },
                                    onSwipeStart = { isAnyCardSwiping = true },
                                    onSwipeEnd = { isAnyCardSwiping = false },
                                    onDragStartWithOffset = { offset, boundsInRoot ->
                                        habitSettleJob?.cancel()
                                        isHabitSettling = false
                                        triggerPickupHaptic(context, hapticFeedback)
                                        measuredHabitItemHeightPx = boundsInRoot.height + with(density) { 16.dp.toPx() }
                                        draggedHabitIndex = index
                                        currentHoverHabitIndex = index
                                        dragHabitOffsetX = 0f
                                        dragHabitOffsetY = 0f
                                        draggedInitialPointerInRoot = boundsInRoot.topLeft + offset
                                        hoveredPlanTab = false
                                    },
                                    onDragDelta = onDragDelta@ { dx, dy ->
                                        if (isHabitSettling) return@onDragDelta
                                        dragHabitOffsetX += dx
                                        dragHabitOffsetY += dy
                                        val currentPointer = draggedInitialPointerInRoot?.plus(Offset(dragHabitOffsetX, dragHabitOffsetY))

                                        val subTabBounds = subTabBarBoundsInRoot
                                        if (currentPointer != null && subTabBounds != null &&
                                            currentPointer.y in (subTabBounds.top - 40f)..(subTabBounds.bottom + 40f) &&
                                            currentPointer.x in (subTabBounds.left + subTabBounds.width * 0.45f)..(subTabBounds.right + 20f)
                                        ) {
                                            if (!hoveredPlanTab) {
                                                HapticHelper.selection(context)
                                                hoveredPlanTab = true
                                            }
                                        } else {
                                            if (hoveredPlanTab) hoveredPlanTab = false

                                            val currentIdx = draggedHabitIndex ?: index
                                            val effectiveHeight = if (measuredHabitItemHeightPx > 0f) measuredHabitItemHeightPx else habitItemHeightPx
                                            val targetIdx = calculateSlotTargetIndex(currentIdx, dragHabitOffsetY, effectiveHeight, localHabits.size)
                                            if (targetIdx != currentIdx && targetIdx in localHabits.indices) {
                                                val reordered = localHabits.toMutableList()
                                                val direction = if (targetIdx > currentIdx) 1 else -1
                                                var iterIdx = currentIdx
                                                while (iterIdx != targetIdx) {
                                                    val nextIdx = iterIdx + direction
                                                    java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                                    iterIdx = nextIdx
                                                }
                                                val slotsMoved = targetIdx - currentIdx
                                                val habitItemHeightPx = effectiveHeight
                                                dragHabitOffsetY -= slotsMoved * habitItemHeightPx
                                                draggedHabitIndex = targetIdx
                                                currentHoverHabitIndex = targetIdx
                                                localHabits = reordered
                                                HapticHelper.tick(context)
                                            }
                                        }
                                    },
                                    onDragEnd = onDragEnd@ {
                                        if (isHabitSettling) return@onDragEnd
                                        if (hoveredPlanTab) {
                                            HapticHelper.success(context)
                                            scope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                            draggedHabitIndex = null
                                            currentHoverHabitIndex = null
                                            dragHabitOffsetX = 0f
                                            dragHabitOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            hoveredPlanTab = false
                                        } else {
                                            val startIdx = draggedHabitIndex
                                            if (startIdx != null) {
                                                val effectiveHeight = if (measuredHabitItemHeightPx > 0f) measuredHabitItemHeightPx else habitItemHeightPx
                                                val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                                isHabitSettling = true
                                                habitSettleJob = scope.launch {
                                                    animateDragToSettle(
                                                        currentOffset = dragHabitOffsetY,
                                                        targetOffset = targetSettleY,
                                                        springSpec = getReorderSpringSpec(context)
                                                    ) { animatedY ->
                                                        dragHabitOffsetY = animatedY
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        repository.reorderHabits(localHabits)
                                                    }
                                                    HapticHelper.success(context)
                                                    draggedHabitIndex = null
                                                    currentHoverHabitIndex = null
                                                    dragHabitOffsetX = 0f
                                                    dragHabitOffsetY = 0f
                                                    draggedInitialPointerInRoot = null
                                                    hoveredPlanTab = false
                                                    lastHabitDragEndTime = System.currentTimeMillis()
                                                    isHabitSettling = false
                                                }
                                            } else {
                                                draggedHabitIndex = null
                                                currentHoverHabitIndex = null
                                                dragHabitOffsetX = 0f
                                                dragHabitOffsetY = 0f
                                                draggedInitialPointerInRoot = null
                                                hoveredPlanTab = false
                                                lastHabitDragEndTime = System.currentTimeMillis()
                                            }
                                        }
                                    },
                                    onDragCancel = onDragCancel@ {
                                        if (isHabitSettling) return@onDragCancel
                                        val startIdx = draggedHabitIndex
                                        if (startIdx != null) {
                                            isHabitSettling = true
                                            habitSettleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragHabitOffsetY,
                                                    targetOffset = 0f,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragHabitOffsetY = animatedY
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.reorderHabits(localHabits)
                                                }
                                                draggedHabitIndex = null
                                                currentHoverHabitIndex = null
                                                dragHabitOffsetX = 0f
                                                dragHabitOffsetY = 0f
                                                draggedInitialPointerInRoot = null
                                                hoveredPlanTab = false
                                                lastHabitDragEndTime = System.currentTimeMillis()
                                                isHabitSettling = false
                                            }
                                        } else {
                                            draggedHabitIndex = null
                                            currentHoverHabitIndex = null
                                            dragHabitOffsetX = 0f
                                            dragHabitOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            hoveredPlanTab = false
                                            lastHabitDragEndTime = System.currentTimeMillis()
                                        }
                                    }
                                ) {
                                    HabitCard(
                                        habit = habit,
                                        completion = completion,
                                        isThisItemDragged = isThisItemDragged,
                                        lastDragEndTime = lastHabitDragEndTime,
                                        onToggle = {
                                            val isCurrentlyDragged = isThisItemDragged
                                            if (!isCurrentlyDragged && System.currentTimeMillis() - lastHabitDragEndTime > 300L) {
                                                HapticHelper.success(context)
                                                scope.launch { repository.toggleHabitCompletion(habit.id) }
                                            }
                                        },
                                        onCountDelta = { delta ->
                                            HapticHelper.click(context)
                                            scope.launch { repository.updateHabitCount(habit.id, delta) }
                                        },
                                        onSingleTap = {
                                            HapticHelper.selection(context)
                                            val preset = habit.timerPreset ?: ItemTimerPreset(
                                                focusDuration = habit.focusSeconds,
                                                breakDuration = habit.breakSeconds,
                                                flowDuration = habit.flowSeconds
                                            )
                                            PomodoroService.commitPreset(
                                                context = context,
                                                preset = preset,
                                                taskTitle = habit.name,
                                                sessionType = PomodoroType.FOCUS
                                            )
                                            onNavigateToTimer()
                                        },
                                        onDoubleTap = {
                                            HapticHelper.click(context)
                                            habitForTimerConfig = habit
                                        }
                                    )
                                }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }

                    1 -> {
                        // Complete Plan Mode: Goals, Projects, Tasks with Drag-and-Drop & Scheduling
                        PlanView(
                            repository = repository,
                            subTabBarBoundsInRoot = subTabBarBoundsInRoot,
                            onHoverTab0 = { hovered -> hoveredTodayTab = hovered },
                            onDropOnTab0 = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            showImportTaskSheet = showImportTaskSheet,
                            onDismissImportTaskSheet = { showImportTaskSheet = false },
                            onRequestCreate = { showImportTaskSheet = true },
                            onNavigateToTimer = onNavigateToTimer
                        )
                    }
                }
            }
        }

        if (habitForTimerConfig != null) {
            val configHabit = habitForTimerConfig!!
            ItemTimerPresetModal(
                item = configHabit,
                onDismiss = { habitForTimerConfig = null },
                onSavePreset = { updatedPreset ->
                    scope.launch {
                        repository.updateHabitTimerPreset(configHabit.id, updatedPreset)
                    }
                }
            )
        }

        // Floating Action Button (+) anchored at bottom right (Only visible on Today tab)
        AnimatedVisibility(
            visible = pagerState.currentPage == 0,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 20.dp, end = 20.dp)
                    .size(56.dp)
                    .shadow(10.dp, CircleShape, spotColor = BrandAmberStart.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(BrandAmberStart)
                    .clickable {
                        HapticHelper.click(context)
                        habitToEdit = null
                        showCreateSheet = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Habit",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }



        // Create / Edit Habit Sheet
        if (showCreateSheet) {
            CreateHabitSheet(
                initialHabit = habitToEdit,
                onDismiss = {
                    showCreateSheet = false
                    habitToEdit = null
                },
                onSave = { name, emoji, colorHex, type, target, unit ->
                    scope.launch {
                        val currentEdit = habitToEdit
                        if (currentEdit != null) {
                            val updated = currentEdit.copy(
                                name = name,
                                emoji = emoji,
                                color = colorHex,
                                completionType = type,
                                targetCount = target,
                                countUnit = unit
                            )
                            localHabits = localHabits.map { if (it.id == updated.id) updated else it }
                            repository.updateHabit(updated)
                        } else {
                            repository.addHabit(name, emoji, colorHex, type, target, unit)
                        }
                        showCreateSheet = false
                        habitToEdit = null
                    }
                }
            )
        }

        // Schedule Habit directly onto Timeline Bottom Sheet (Identical to Plan mode)
        if (showScheduleSheet && habitToSchedule != null) {
            val habit = habitToSchedule!!
            val goalItem = remember(habit.id) {
                GoalItem(
                    id = habit.id,
                    title = habit.name,
                    subtitle = "",
                    isHabit = true,
                    habitRef = habit,
                    timerPreset = habit.timerPreset
                )
            }
            ScheduleTimePickerSheet(
                item = goalItem,
                is24Hour = userSettings.timeFormat24Hour,
                selectedDateString = null,
                onDismiss = {
                    showScheduleSheet = false
                    habitToSchedule = null
                },
                onConfirmSchedule = { startMinute, durationMinutes, colorHex, projectId, todoId ->
                    val scheduledHabit = habitToSchedule
                    if (scheduledHabit != null) {
                        val cal = Calendar.getInstance()
                        val todayStr = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        scope.launch {
                            repository.addTimeBlock(
                                title = scheduledHabit.name,
                                startMinute = startMinute,
                                durationMinutes = durationMinutes,
                                color = colorHex,
                                projectId = null,
                                todoId = null,
                                date = todayStr
                            )
                        }
                    }
                    showScheduleSheet = false
                    habitToSchedule = null
                }
            )
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    completion: HabitCompletion?,
    isThisItemDragged: Boolean = false,
    lastDragEndTime: Long = 0L,
    onToggle: () -> Unit,
    onCountDelta: (Int) -> Unit,
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    val isCurrentlyDragged = isThisItemDragged
    var localLastDragEndTime by remember(habit.id) { mutableLongStateOf(0L) }
    LaunchedEffect(isThisItemDragged) {
        if (!isThisItemDragged) {
            localLastDragEndTime = System.currentTimeMillis()
        }
    }
    val effectiveDragEndTime = if (lastDragEndTime > 0L) lastDragEndTime else localLastDragEndTime
    val isCompleted = completion != null && !completion.isSkip && completion.count >= habit.targetCount
    val isSkipped = completion?.isSkip == true
    val currentCount = completion?.count ?: 0

    val cardBg by animateColorAsState(
        targetValue = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        label = "cardBg"
    )

    SquircleCard(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(habit.id, isThisItemDragged) {
                if (isThisItemDragged) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        if (!isCurrentlyDragged && System.currentTimeMillis() - effectiveDragEndTime > 300L) {
                            if (!isThisItemDragged) onDoubleTap()
                        }
                    },
                    onTap = {
                        if (!isCurrentlyDragged && System.currentTimeMillis() - effectiveDragEndTime > 300L) {
                            if (!isThisItemDragged) onSingleTap()
                        }
                    }
                )
            },
        cornerRadius = 18.dp,
        containerColor = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Morphing status dot / Grab Handle & Habit Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val habitColor = try {
                    Color(android.graphics.Color.parseColor(habit.color))
                } catch (_: Exception) {
                    BrandAmberStart
                }
                MorphingLeadingActionIcon(
                    isDragging = isThisItemDragged,
                    dragHandleColor = if (isCompleted) BreakGreen else habitColor
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReorderGrabHandleIcon(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            size = 14.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) BreakGreen else habitColor)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSkipped) {
                        Text(
                            text = "Rest Day",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandAmberStart,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Right Action Zone (Thumb-friendly)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (habit.completionType) {
                    HabitCompletionType.COUNT -> {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { if (!isThisItemDragged) onCountDelta(-1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "$currentCount/${habit.targetCount}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) BreakGreen else MaterialTheme.colorScheme.onSurface
                        )

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) BreakGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { if (!isThisItemDragged) onCountDelta(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    else -> {
                        // Right-aligned Circular Check Button (32x32dp)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCompleted) BreakGreen
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = !isThisItemDragged, onClick = onToggle),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateHabitSheet(
    initialHabit: Habit? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, colorHex: String, type: HabitCompletionType, target: Int, unit: String?) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialHabit?.name ?: "") }
    val emoji = initialHabit?.emoji ?: "⚡"
    val selectedColor = initialHabit?.color ?: "#22C55E"

    // Optional Numerical / Time-based Targets
    var hasTarget by remember { mutableStateOf(initialHabit?.completionType == HabitCompletionType.COUNT || (initialHabit?.targetCount ?: 0) > 1) }
    var targetCount by remember { mutableStateOf(if ((initialHabit?.targetCount ?: 1) > 1) initialHabit!!.targetCount else 8) }
    var unit by remember { mutableStateOf(initialHabit?.countUnit ?: "glasses") }
    var isSubmitting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSubmitting) {
                sheetState.animateDismiss(scope, onDismiss)
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialHabit != null) "Edit Habit" else "Create Habit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Habit Name") },
                placeholder = { Text("e.g. Read 20 pages, Hydrate...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Optional Numerical / Time-based Targets (with + Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable {
                        HapticHelper.click(context)
                        hasTarget = !hasTarget
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Target Goal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasTarget) {
                        Text(
                            text = "$targetCount $unit per day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PlanStateToggle(
                    isActive = hasTarget,
                    onToggle = { hasTarget = it }
                )
            }

            if (hasTarget) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Quantity",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { if (targetCount > 1) targetCount-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(
                                text = "$targetCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { targetCount++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }

                    // Quick Unit Selectors - Redesigned Uniform Containers in a Horizontal Scrollable Row
                    Text(text = "Unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickUnits = listOf("Hour", "mins", "glasses", "pages", "reps", "km", "times")
                        items(quickUnits) { u ->
                            val isSel = unit.equals(u, true) || (u == "Hour" && unit.equals("hours", true))
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) BrandAmberStart else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { unit = u },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Create Habit Button: strictly a solid yellow surface with crisp white text, completely removed decorative circle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandAmberStart)
                    .clickable(enabled = name.isNotBlank() && !isSubmitting) {
                        if (name.isNotBlank() && !isSubmitting) {
                            HapticHelper.success(context)
                            isSubmitting = true
                            scope.launch {
                                val type = if (hasTarget) HabitCompletionType.COUNT else HabitCompletionType.MANUAL
                                sheetState.smoothDismiss()
                                onSave(name, emoji, selectedColor, type, if (hasTarget) targetCount else 1, if (hasTarget) unit else null)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSubmitting) "Saving..." else if (initialHabit != null) "Save Changes" else "Create Habit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
