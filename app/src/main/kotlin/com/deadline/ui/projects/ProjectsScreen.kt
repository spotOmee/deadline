package com.deadline.ui.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.deadline.ui.components.UniversalSwipeableItem
import com.deadline.ui.navigation.NavigationZoningDefaults
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateContinuousHorizontalDisplacement
import com.deadline.ui.components.calculateHorizontalSlotTargetIndex
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.UniversalAutoScrollCoordinator
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.MorphingLeadingActionIcon
import com.deadline.ui.components.directReorderHandle
import com.deadline.ui.components.detectFastDragGesturesAfterLongPress
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.triggerPickupHaptic
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.deadline.ui.components.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.Project
import com.deadline.data.local.ProjectTodo
import com.deadline.data.local.QuickNote
import com.deadline.data.local.TaskPriority
import com.deadline.data.local.TaskStatus
import com.deadline.data.local.TimeBlock
import com.deadline.ui.components.AppleHeader
import com.deadline.ui.components.CheckmarkCheckIcon
import com.deadline.ui.components.FolderLayersIcon
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.PinIcon
import com.deadline.ui.components.PlanStateToggle
import com.deadline.ui.components.SegmentedControl
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.StandardEmptyState
import com.deadline.ui.components.StopwatchIcon
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import com.deadline.ui.components.UniversalDeadlinePicker
import com.deadline.ui.components.SelectedDeadlineDate
import com.deadline.ui.components.ProjectDeadlineValidator
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BrandAmberEnd
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.DangerRed
import com.deadline.ui.theme.FocusOrange
import com.deadline.ui.theme.StatusHigh
import com.deadline.ui.theme.StatusLow
import com.deadline.ui.theme.StatusMedium
import com.deadline.ui.theme.StatusUrgent
import com.deadline.ui.theme.WorkIndigo
import com.deadline.domain.GlobalTimeManager
import com.deadline.domain.HeadlineScaler
import com.deadline.ui.components.calculateDynamicHeadlineSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    repository: DeadlineRepository,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val userSettings by repository.userSettings.collectAsState()
    val projects by repository.projects.collectAsState()
    val todos by repository.todos.collectAsState()
    val planImportedTaskIds by repository.planImportedTaskIds.collectAsState()
    val timeBlocks by repository.timelineTimeBlocks.collectAsState()
    val selectedPlanDate by repository.selectedPlanDate.collectAsState()
    val todayDateStr = remember { repository.getTodayString() }
    val isViewingToday = selectedPlanDate.isBlank() || selectedPlanDate == todayDateStr
    val quickNotes by repository.quickNotes.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    var selectedProjectIdFilter by remember(projects) { mutableStateOf(projects.firstOrNull()?.id) }
    var selectedStatusFilter by remember { mutableStateOf("ACTIVE") } // "ACTIVE" = main active task list, "DONE" = dedicated Done section

    var showCreateTaskSheet by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<ProjectTodo?>(null) }
    var showCreateProjectSheet by remember { mutableStateOf(false) }
    var showAddTimeBlockSheet by remember { mutableStateOf(false) }
    var managingNotesProject by remember { mutableStateOf<Project?>(null) }
    var viewingLinkedNote by remember { mutableStateOf<QuickNote?>(null) }

    val activeProject = projects.find { it.id == selectedProjectIdFilter } ?: projects.firstOrNull()
    val filteredTodos = todos.filter { todo ->
        val projectMatch = activeProject == null || todo.projectId == activeProject.id
        val statusMatch = when (selectedStatusFilter) {
            "DONE" -> todo.status == TaskStatus.COMPLETED
            else -> todo.status != TaskStatus.COMPLETED
        }
        projectMatch && statusMatch
    }

    var draggedTodoIndex by remember { mutableStateOf<Int?>(null) }
    var dragTodoOffsetX by remember { mutableFloatStateOf(0f) }
    var dragTodoOffsetY by remember { mutableFloatStateOf(0f) }
    var lastTodoDragEndTime by remember { mutableLongStateOf(0L) }
    var currentHoverTodoIndex by remember { mutableStateOf<Int?>(null) }
    var draggedInitialPointerInRoot by remember { mutableStateOf<Offset?>(null) }
    var hoveredFilterKey by remember { mutableStateOf<String?>(null) }
    var hoveredProjectId by remember { mutableStateOf<String?>(null) }
    var hoveredTimelineTab by remember { mutableStateOf(false) }
    var isTodoSettling by remember { mutableStateOf(false) }
    var todoSettleJob by remember { mutableStateOf<Job?>(null) }

    var localTodos by remember { mutableStateOf(filteredTodos) }
    var exitingTodoIds by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(filteredTodos) {
        if (draggedTodoIndex == null) {
            val newMap = filteredTodos.associateBy { it.id }
            val currentIds = localTodos.map { it.id }
            if (currentIds.isNotEmpty() && currentIds.any { it in newMap }) {
                val remainingPreserved = currentIds.filter { it in newMap }.mapNotNull { newMap[it] }
                val addedItems = filteredTodos.filter { it.id !in currentIds }
                localTodos = remainingPreserved + addedItems
            } else {
                localTodos = filteredTodos
            }
        }
    }

    var subTabBarBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var filterPillBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    var projectCardBounds by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    var draggedProjectIndex by remember { mutableStateOf<Int?>(null) }
    var dragProjectOffsetX by remember { mutableFloatStateOf(0f) }
    var dragProjectOffsetY by remember { mutableFloatStateOf(0f) }
    var lastProjectDragEndTime by remember { mutableLongStateOf(0L) }
    var currentHoverProjectIndex by remember { mutableStateOf<Int?>(null) }
    var isProjectSettling by remember { mutableStateOf(false) }
    var projectSettleJob by remember { mutableStateOf<Job?>(null) }

    val sortedProjects = remember(projects) {
        projects.sortedWith(
            compareByDescending<Project> { it.isPinned }
                .thenBy { it.sortOrder }
        )
    }
    var localProjects by remember { mutableStateOf(sortedProjects) }
    LaunchedEffect(sortedProjects) {
        if (draggedProjectIndex == null) {
            localProjects = sortedProjects
        }
    }
    var isAnyCardSwiping by remember { mutableStateOf(false) }
    var isTouchInSubTabZone by remember { mutableStateOf(true) }

    val density = LocalDensity.current
    val projectCardWidthDp = 224.dp
    val projectCardSpacingDp = 12.dp
    val projectCardTotalWidthPx = with(density) { (projectCardWidthDp + projectCardSpacingDp).toPx() }
    val todoItemHeightDp = 58.dp
    val todoItemHeightPx = with(density) { (todoItemHeightDp + 16.dp).toPx() }
    var measuredTodoItemHeightPx by remember { mutableFloatStateOf(todoItemHeightPx) }

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

            // 1. Top Header Bar (SF Pro / Apple HIG)
            AppleHeader(
                title = if (pagerState.currentPage == 0) "Projects" else if (isViewingToday) "Timeline" else "Timeline • $selectedPlanDate",
                avatarUri = userSettings.avatarUri,
                onProfileClick = onOpenProfile,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Sub-Navigation Switcher: Tasks | Timeline
            SegmentedControl(
                items = listOf("Tasks", "Timeline"),
                selectedIndex = pagerState.currentPage,
                pagerState = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .onGloballyPositioned { coords ->
                        subTabBarBoundsInRoot = coords.boundsInRoot()
                    }
                    .then(
                        if (hoveredTimelineTab) Modifier.border(2.dp, WorkIndigo, RoundedCornerShape(16.dp))
                        else Modifier
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = isTouchInSubTabZone && !isAnyCardSwiping && draggedTodoIndex == null && draggedProjectIndex == null && !isTodoSettling && !isProjectSettling,
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
                        // --- VIEW 1: TASKS ---
                        if (projects.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                StandardEmptyState(
                                    title = "No projects yet",
                                    icon = {
                                        FolderLayersIcon(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            size = 28.dp
                                        )
                                    }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Active Projects Carousel with Sleek Aesthetic & Universal Drag-and-Drop Reordering
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE PROJECTS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(BrandAmberStart.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${localProjects.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandAmberStart,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                // Top "New Project" button is permanently removed as required
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(projectCardSpacingDp),
                                modifier = Modifier.fillMaxWidth(),
                                userScrollEnabled = draggedProjectIndex == null
                            ) {
                                itemsIndexed(localProjects, key = { _, project -> project.id }) { index, project ->
                                    val isSelected = activeProject?.id == project.id
                                    val isThisProjectDragged = draggedProjectIndex == index
                                    val isHoveredDrop = draggedTodoIndex != null && hoveredProjectId == project.id

                                    val targetDisplacement = calculateContinuousHorizontalDisplacement(
                                        itemIndex = index,
                                        draggedIndex = draggedProjectIndex,
                                        dragOffsetX = dragProjectOffsetX,
                                        cardWidthPx = projectCardTotalWidthPx
                                    )
                                    val nonDraggedProjectDisplacement by animateFloatAsState(
                                        targetValue = targetDisplacement,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        label = "nonDraggedProjectDisplacement_$index"
                                    )

                                    val projectDragScale by animateFloatAsState(
                                        targetValue = if (isThisProjectDragged) 1.03f else if (isHoveredDrop) 1.05f else 1.0f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        label = "projectDragScale_$index"
                                    )

                                    val projectDragElevation by animateFloatAsState(
                                        targetValue = if (isThisProjectDragged) 24f else 2f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioNoBouncy
                                        ),
                                        label = "projectDragElevation_$index"
                                    )

                                    val projectTasks = todos.filter { it.projectId == project.id }
                                    val doneTasks = projectTasks.count { it.status == TaskStatus.COMPLETED }
                                    val projectColor = try {
                                        Color(android.graphics.Color.parseColor(project.color))
                                    } catch (_: Exception) {
                                        WorkIndigo
                                    }
                                    val linkedNotes = remember(project.linkedNoteIds, quickNotes) {
                                        if (project.linkedNoteIds.isBlank()) emptyList()
                                        else project.linkedNoteIds.split(",").mapNotNull { id -> quickNotes.find { it.id.trim() == id.trim() } }
                                    }

                                    val pinScale by animateFloatAsState(
                                        targetValue = if (project.isPinned) 1.15f else 1.0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "projectPinScale_${project.id}"
                                    )
                                    val pinRotation by animateFloatAsState(
                                        targetValue = if (project.isPinned) -25f else -10f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "projectPinRotation_${project.id}"
                                    )
                                    val pinColor by animateColorAsState(
                                        targetValue = if (project.isPinned) BrandAmberStart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "projectPinColor_${project.id}"
                                    )

                                    SquircleCard(
                                        cornerRadius = 16.dp,
                                        containerColor = when {
                                            isHoveredDrop -> projectColor.copy(alpha = 0.32f)
                                            isThisProjectDragged -> projectColor.copy(alpha = 0.22f)
                                            isSelected -> projectColor.copy(alpha = 0.16f)
                                            project.isPinned -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        borderColor = when {
                                            isThisProjectDragged -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            isHoveredDrop -> projectColor
                                            isSelected -> projectColor.copy(alpha = 0.5f)
                                            project.isPinned -> BrandAmberStart.copy(alpha = 0.38f)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        },
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioNoBouncy
                                                ),
                                                fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                            .zIndex(if (isThisProjectDragged) 100f else 1f)
                                            .graphicsLayer {
                                                translationX = if (isThisProjectDragged) dragProjectOffsetX else nonDraggedProjectDisplacement
                                                translationY = if (isThisProjectDragged) dragProjectOffsetY else 0f
                                                scaleX = projectDragScale
                                                scaleY = projectDragScale
                                                shadowElevation = projectDragElevation
                                                if (isThisProjectDragged) alpha = 0.95f
                                            }
                                            .onGloballyPositioned { coords ->
                                                projectCardBounds = projectCardBounds + (project.id to coords.boundsInRoot())
                                            }
                                            .pointerInput(Unit) {
                                                detectFastDragGesturesAfterLongPress(
                                                    longPressTimeoutMillis = DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS,
                                                    onDragStart = {
                                                        projectSettleJob?.cancel()
                                                        isProjectSettling = false
                                                        triggerPickupHaptic(context, hapticFeedback)
                                                        draggedProjectIndex = index
                                                        currentHoverProjectIndex = index
                                                        dragProjectOffsetX = 0f
                                                        dragProjectOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        if (isProjectSettling) return@detectFastDragGesturesAfterLongPress
                                                        change.consume()
                                                        dragProjectOffsetX += dragAmount.x
                                                        dragProjectOffsetY += dragAmount.y
                                                        val startIdx = draggedProjectIndex ?: index
                                                        val computedTarget = calculateHorizontalSlotTargetIndex(startIdx, dragProjectOffsetX, projectCardTotalWidthPx, localProjects.size)
                                                        if (computedTarget != currentHoverProjectIndex) {
                                                            HapticHelper.selection(context)
                                                            currentHoverProjectIndex = computedTarget
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (isProjectSettling) return@detectFastDragGesturesAfterLongPress
                                                        val startIdx = draggedProjectIndex
                                                        val targetIdx = if (startIdx != null && projectCardTotalWidthPx > 0f) {
                                                            calculateHorizontalSlotTargetIndex(startIdx, dragProjectOffsetX, projectCardTotalWidthPx, localProjects.size)
                                                        } else {
                                                            currentHoverProjectIndex
                                                        }
                                                        if (startIdx != null && targetIdx != null) {
                                                            val targetSettleX = calculateSettleTargetOffset(startIdx, targetIdx, projectCardTotalWidthPx)
                                                            isProjectSettling = true
                                                            projectSettleJob = scope.launch {
                                                                animateDragToSettle(
                                                                    currentOffset = dragProjectOffsetX,
                                                                    targetOffset = targetSettleX,
                                                                    springSpec = getReorderSpringSpec(context)
                                                                ) { animatedX ->
                                                                    dragProjectOffsetX = animatedX
                                                                }
                                                                if (startIdx != targetIdx && targetIdx in localProjects.indices) {
                                                                    HapticHelper.success(context)
                                                                    val reordered = localProjects.toMutableList()
                                                                    val moved = reordered.removeAt(startIdx)
                                                                    reordered.add(targetIdx, moved)
                                                                    localProjects = reordered
                                                                    repository.reorderProjects(reordered)
                                                                }
                                                                draggedProjectIndex = null
                                                                currentHoverProjectIndex = null
                                                                dragProjectOffsetX = 0f
                                                                dragProjectOffsetY = 0f
                                                                lastProjectDragEndTime = System.currentTimeMillis()
                                                                isProjectSettling = false
                                                            }
                                                        } else {
                                                            draggedProjectIndex = null
                                                            currentHoverProjectIndex = null
                                                            dragProjectOffsetX = 0f
                                                            dragProjectOffsetY = 0f
                                                            lastProjectDragEndTime = System.currentTimeMillis()
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        if (isProjectSettling) return@detectFastDragGesturesAfterLongPress
                                                        val startIdx = draggedProjectIndex
                                                        if (startIdx != null) {
                                                            isProjectSettling = true
                                                            projectSettleJob = scope.launch {
                                                                animateDragToSettle(
                                                                    currentOffset = dragProjectOffsetX,
                                                                    targetOffset = 0f,
                                                                    springSpec = getReorderSpringSpec(context)
                                                                ) { animatedX ->
                                                                    dragProjectOffsetX = animatedX
                                                                }
                                                                draggedProjectIndex = null
                                                                currentHoverProjectIndex = null
                                                                dragProjectOffsetX = 0f
                                                                dragProjectOffsetY = 0f
                                                                lastProjectDragEndTime = System.currentTimeMillis()
                                                                isProjectSettling = false
                                                            }
                                                        } else {
                                                            draggedProjectIndex = null
                                                            currentHoverProjectIndex = null
                                                            dragProjectOffsetX = 0f
                                                            dragProjectOffsetY = 0f
                                                            lastProjectDragEndTime = System.currentTimeMillis()
                                                        }
                                                    }
                                                )
                                            },
                                        onClick = {
                                            val isCurrentlyDragged = draggedProjectIndex != null
                                            if (!isCurrentlyDragged && System.currentTimeMillis() - lastProjectDragEndTime > 300L) {
                                                if (draggedProjectIndex == null) {
                                                    HapticHelper.click(context)
                                                    selectedProjectIdFilter = if (selectedProjectIdFilter == project.id) null else project.id
                                                }
                                            }
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .width(projectCardWidthDp)
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Top Row: Status Dot (Clickable Delete Action) + Title + Extreme Top-Right Minimalist Pin
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    ReorderGrabHandleIcon(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                        size = 14.dp
                                                    )
                                                    // Colored circular status indicator dot transformed into direct clickable delete action
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                HapticHelper.heavy(context)
                                                                if (selectedProjectIdFilter == project.id) {
                                                                    selectedProjectIdFilter = localProjects.firstOrNull { it.id != project.id }?.id
                                                                }
                                                                localProjects = localProjects.filter { it.id != project.id }
                                                                scope.launch {
                                                                    repository.deleteProject(project.id)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(9.dp)
                                                                .clip(CircleShape)
                                                                .background(projectColor)
                                                        )
                                                    }

                                                    val projHeadline = HeadlineScaler.clamp(project.name)
                                                    Text(
                                                        text = projHeadline,
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontSize = calculateDynamicHeadlineSize(projHeadline, baseSize = 14.sp, minSize = 11.sp)
                                                        ),
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                // Extreme top-right corner: Minimalist tilted Pin Icon with theme-adaptive styling
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (project.isPinned) BrandAmberStart.copy(alpha = 0.14f)
                                                            else Color.Transparent
                                                        )
                                                        .clickable {
                                                            HapticHelper.click(context)
                                                            val newPinned = !project.isPinned
                                                            val updatedProject = project.copy(isPinned = newPinned)
                                                            // Reactive sorting: Unpinning instantly triggers re-sort back to alphabetical position
                                                            localProjects = localProjects.map {
                                                                if (it.id == project.id) updatedProject else it
                                                            }.sortedWith(
                                                                compareByDescending<Project> { it.isPinned }
                                                                    .thenBy { it.name.lowercase(java.util.Locale.ROOT) }
                                                            )
                                                            scope.launch {
                                                                repository.togglePinProject(project.id)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    PinIcon(
                                                        color = pinColor,
                                                        isPinned = project.isPinned,
                                                        size = 14.dp,
                                                        modifier = Modifier.graphicsLayer {
                                                            rotationZ = pinRotation
                                                            scaleX = pinScale
                                                            scaleY = pinScale
                                                        }
                                                    )
                                                }
                                            }

                                            // Subtitle Row: Dynamic Task Counter Subtitle + Linked Note Chip (if notes linked)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val taskCount = projectTasks.size
                                                Text(
                                                    text = "$taskCount ${if (taskCount == 1) "Task" else "Tasks"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                if (linkedNotes.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(BrandAmberStart.copy(alpha = 0.12f))
                                                            .clickable {
                                                                HapticHelper.click(context)
                                                                managingNotesProject = project
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            Text(text = "📌", fontSize = 9.sp)
                                                            Text(
                                                                text = "${linkedNotes.size}",
                                                                color = BrandAmberStart,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
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

                        // Dual-State Filter Buttons (Active | Done) + Distinct Purple Plus Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeCount = todos.count { (activeProject == null || it.projectId == activeProject.id) && it.status != TaskStatus.COMPLETED }
                            val doneCount = todos.count { (activeProject == null || it.projectId == activeProject.id) && it.status == TaskStatus.COMPLETED }

                            val filterOptions = listOf(
                                "ACTIVE" to "Active ($activeCount)",
                                "DONE" to "Done ($doneCount)"
                            )

                            // Dual-state filter buttons with identical outer container size
                            filterOptions.forEach { (key, label) ->
                                val isSel = selectedStatusFilter == key
                                val isHoveredDrop = draggedTodoIndex != null && hoveredFilterKey == key
                                val pillBg by animateColorAsState(
                                    targetValue = when {
                                        isHoveredDrop -> BrandAmberStart.copy(alpha = 0.35f)
                                        isSel -> BrandAmberStart.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "pillBg_$key"
                                )
                                val pillScale by animateFloatAsState(
                                    targetValue = if (isHoveredDrop) 1.06f else 1.0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    ),
                                    label = "pillScale_$key"
                                )
                                val pillBorder = when {
                                    isHoveredDrop -> Modifier.border(1.5.dp, BrandAmberStart, RoundedCornerShape(16.dp))
                                    isSel -> Modifier.border(1.dp, BrandAmberStart.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    else -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .graphicsLayer {
                                            scaleX = pillScale
                                            scaleY = pillScale
                                        }
                                        .onGloballyPositioned { coords ->
                                            filterPillBounds = filterPillBounds + (key to coords.boundsInRoot())
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(pillBg)
                                        .then(pillBorder)
                                        .clickable {
                                            HapticHelper.click(context)
                                            selectedStatusFilter = key
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val dropText = if (key == "DONE") "Drop to Done" else "Drop to Active"
                                    Text(
                                        text = if (isHoveredDrop) dropText else label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSel || isHoveredDrop) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel || isHoveredDrop) BrandAmberStart else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Distinct purple plus button directly to the right of the standardized filter buttons for ongoing project creation
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(WorkIndigo)
                                    .clickable {
                                        HapticHelper.click(context)
                                        showCreateProjectSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Project",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Tasks Content Area: perfectly centered empty state if empty, or scrollable list
                        if (localTodos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                StandardEmptyState(
                                    title = "No tasks yet",
                                    icon = {
                                        CheckmarkCheckIcon(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            size = 28.dp
                                        )
                                    }
                                )
                            }
                        } else {
                            val tasksLazyListState = rememberLazyListState()
                            var tasksViewportHeight by remember { mutableFloatStateOf(0f) }
                            var tasksListBounds by remember { mutableStateOf(Rect.Zero) }

                            val pointerYInViewport = if (draggedInitialPointerInRoot != null) {
                                draggedInitialPointerInRoot!!.y + dragTodoOffsetY - tasksListBounds.top
                            } else 0f

                            UniversalAutoScrollCoordinator(
                                lazyListState = tasksLazyListState,
                                isDragging = draggedTodoIndex != null,
                                pointerYInViewport = pointerYInViewport,
                                viewportHeight = tasksViewportHeight
                            )

                            LazyColumn(
                                state = tasksLazyListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        tasksViewportHeight = coords.size.height.toFloat()
                                        tasksListBounds = coords.boundsInRoot()
                                    },
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(localTodos, key = { _, it -> it.id }) { index, todo ->
                        val isThisItemDragged = draggedTodoIndex == index
                        val isDone = todo.status == TaskStatus.COMPLETED
                        val isExiting = todo.id in exitingTodoIds

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
                        itemId = todo.id,
                        cornerRadius = 16.dp,
                        isThisItemDragged = isThisItemDragged,
                        dragOffsetX = dragTodoOffsetX,
                        dragOffsetY = dragTodoOffsetY,
                        nonDraggedDisplacement = 0f,
                        constrainToVerticalAxis = (hoveredFilterKey == null && hoveredProjectId == null && !hoveredTimelineTab),
                        onDelete = {
                            HapticHelper.heavy(context)
                            exitingTodoIds = exitingTodoIds + todo.id
                            scope.launch {
                                delay(200)
                                localTodos = localTodos.filter { it.id != todo.id }
                                exitingTodoIds = exitingTodoIds - todo.id
                                withContext(Dispatchers.IO) {
                                    repository.deleteTodo(todo.id)
                                }
                            }
                        },
                        onEdit = {
                            HapticHelper.click(context)
                            taskToEdit = todo
                            showCreateTaskSheet = true
                        },
                        onSwipeStart = { isAnyCardSwiping = true },
                        onSwipeEnd = { isAnyCardSwiping = false },
                        onDragStartWithOffset = { offset, boundsInRoot ->
                            todoSettleJob?.cancel()
                            isTodoSettling = false
                            triggerPickupHaptic(context, hapticFeedback)
                            measuredTodoItemHeightPx = boundsInRoot.height + with(density) { 16.dp.toPx() }
                            draggedTodoIndex = index
                            currentHoverTodoIndex = index
                            dragTodoOffsetX = 0f
                            dragTodoOffsetY = 0f
                            draggedInitialPointerInRoot = boundsInRoot.topLeft + offset
                            hoveredFilterKey = null
                            hoveredProjectId = null
                            hoveredTimelineTab = false
                        },
                        onDragDelta = onDragDelta@ { dx, dy ->
                            if (isTodoSettling) return@onDragDelta
                            dragTodoOffsetX += dx
                            dragTodoOffsetY += dy
                            val currentPointer = draggedInitialPointerInRoot?.plus(Offset(dragTodoOffsetX, dragTodoOffsetY))

                            // 1. Check Sub-tab bar drop (Timeline tab)
                            val subTabBounds = subTabBarBoundsInRoot
                            if (currentPointer != null && subTabBounds != null &&
                                currentPointer.y in (subTabBounds.top - 40f)..(subTabBounds.bottom + 40f) &&
                                currentPointer.x in (subTabBounds.left + subTabBounds.width * 0.45f)..(subTabBounds.right + 20f)
                            ) {
                                if (!hoveredTimelineTab) {
                                    HapticHelper.selection(context)
                                    hoveredTimelineTab = true
                                }
                                hoveredFilterKey = null
                                hoveredProjectId = null
                            } else {
                                if (hoveredTimelineTab) hoveredTimelineTab = false

                                // 2. Check Filter pills drop
                                val targetFilter = filterPillBounds.entries.find { (_, rect) ->
                                    currentPointer != null &&
                                    currentPointer.y in (rect.top - 20f)..(rect.bottom + 20f) &&
                                    currentPointer.x in (rect.left - 10f)..(rect.right + 10f)
                                }?.key

                                if (targetFilter != null) {
                                    if (hoveredFilterKey != targetFilter) {
                                        HapticHelper.selection(context)
                                        hoveredFilterKey = targetFilter
                                    }
                                    hoveredProjectId = null
                                } else {
                                    if (hoveredFilterKey != null) hoveredFilterKey = null

                                    // 3. Check Project cards drop
                                    val targetProject = projectCardBounds.entries.find { (pId, rect) ->
                                        pId != todo.projectId && currentPointer != null &&
                                        currentPointer.y in (rect.top - 20f)..(rect.bottom + 20f) &&
                                        currentPointer.x in (rect.left - 10f)..(rect.right + 10f)
                                    }?.key

                                    if (targetProject != null) {
                                        if (hoveredProjectId != targetProject) {
                                            HapticHelper.selection(context)
                                            hoveredProjectId = targetProject
                                        }
                                    } else {
                                        if (hoveredProjectId != null) hoveredProjectId = null

                                        // 4. Default: within-list reordering
                                        val currentIdx = draggedTodoIndex ?: index
                                        val effectiveHeight = if (measuredTodoItemHeightPx > 0f) measuredTodoItemHeightPx else todoItemHeightPx
                                        val targetIdx = calculateSlotTargetIndex(currentIdx, dragTodoOffsetY, effectiveHeight, localTodos.size)
                                        if (targetIdx != currentIdx && targetIdx in localTodos.indices) {
                                            val reordered = localTodos.toMutableList()
                                            val direction = if (targetIdx > currentIdx) 1 else -1
                                            var iterIdx = currentIdx
                                            while (iterIdx != targetIdx) {
                                                val nextIdx = iterIdx + direction
                                                java.util.Collections.swap(reordered, iterIdx, nextIdx)
                                                iterIdx = nextIdx
                                            }
                                            val slotsMoved = targetIdx - currentIdx
                                            val todoItemHeightPx = effectiveHeight
                                            dragTodoOffsetY -= slotsMoved * todoItemHeightPx
                                            draggedTodoIndex = targetIdx
                                            currentHoverTodoIndex = targetIdx
                                            localTodos = reordered
                                            HapticHelper.tick(context)
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = onDragEnd@ {
                            if (isTodoSettling) return@onDragEnd
                            val draggedItem = localTodos.getOrNull(draggedTodoIndex ?: -1)
                            if (draggedItem != null) {
                                when {
                                    hoveredTimelineTab -> {
                                        // Dropped onto Timeline tab -> schedule time block & switch tab
                                        HapticHelper.success(context)
                                        val targetDate = selectedPlanDate.ifBlank { todayDateStr }
                                        val isTargetToday = targetDate == todayDateStr
                                        val calNow = Calendar.getInstance()
                                        val curTotalMin = calNow.get(Calendar.HOUR_OF_DAY) * 60 + calNow.get(Calendar.MINUTE)
                                        val curHour = calNow.get(Calendar.HOUR_OF_DAY)
                                        val nextHour = if (isTargetToday) {
                                            val h = (curHour + 1).coerceIn(8, 22)
                                            if (h * 60 <= curTotalMin) (h + 1).coerceIn(8, 22) else h
                                        } else 9
                                        scope.launch {
                                            repository.addTimeBlock(
                                                title = draggedItem.title,
                                                startMinute = nextHour * 60,
                                                durationMinutes = draggedItem.estimatedMinutes.coerceIn(15, 240),
                                                color = "#5E5CE6",
                                                projectId = draggedItem.projectId,
                                                todoId = draggedItem.id,
                                                date = targetDate
                                            )
                                            pagerState.animateScrollToPage(1)
                                        }
                                        draggedTodoIndex = null
                                        currentHoverTodoIndex = null
                                        dragTodoOffsetX = 0f
                                        dragTodoOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                        hoveredFilterKey = null
                                        hoveredProjectId = null
                                        hoveredTimelineTab = false
                                    }
                                    hoveredFilterKey != null -> {
                                        // Dropped onto Status Filter Pill -> reclassify task
                                        HapticHelper.success(context)
                                        val key = hoveredFilterKey!!
                                        val updated = when (key) {
                                            "DONE" -> draggedItem.copy(status = TaskStatus.COMPLETED)
                                            "ACTIVE" -> draggedItem.copy(status = TaskStatus.TODO)
                                            else -> draggedItem
                                        }
                                        scope.launch {
                                            repository.updateTodo(updated)
                                        }
                                        draggedTodoIndex = null
                                        currentHoverTodoIndex = null
                                        dragTodoOffsetX = 0f
                                        dragTodoOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                        hoveredFilterKey = null
                                        hoveredProjectId = null
                                        hoveredTimelineTab = false
                                    }
                                    hoveredProjectId != null -> {
                                        // Dropped onto Project card -> transfer task to project
                                        HapticHelper.success(context)
                                        val newProjId = hoveredProjectId!!
                                        val updated = draggedItem.copy(projectId = newProjId)
                                        scope.launch {
                                            repository.updateTodo(updated)
                                        }
                                        draggedTodoIndex = null
                                        currentHoverTodoIndex = null
                                        dragTodoOffsetX = 0f
                                        dragTodoOffsetY = 0f
                                        draggedInitialPointerInRoot = null
                                        hoveredFilterKey = null
                                        hoveredProjectId = null
                                        hoveredTimelineTab = false
                                    }
                                    else -> {
                                        // Within-list reorder with matched spring settle
                                        val startIdx = draggedTodoIndex
                                        if (startIdx != null) {
                                            val effectiveHeight = if (measuredTodoItemHeightPx > 0f) measuredTodoItemHeightPx else todoItemHeightPx
                                            val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                            isTodoSettling = true
                                            todoSettleJob = scope.launch {
                                                animateDragToSettle(
                                                    currentOffset = dragTodoOffsetY,
                                                    targetOffset = targetSettleY,
                                                    springSpec = getReorderSpringSpec(context)
                                                ) { animatedY ->
                                                    dragTodoOffsetY = animatedY
                                                }
                                                withContext(Dispatchers.IO) {
                                                    repository.reorderTodos(localTodos)
                                                }
                                                HapticHelper.success(context)
                                                draggedTodoIndex = null
                                                currentHoverTodoIndex = null
                                                dragTodoOffsetX = 0f
                                                dragTodoOffsetY = 0f
                                                draggedInitialPointerInRoot = null
                                                hoveredFilterKey = null
                                                hoveredProjectId = null
                                                hoveredTimelineTab = false
                                                lastTodoDragEndTime = System.currentTimeMillis()
                                                isTodoSettling = false
                                            }
                                        } else {
                                            draggedTodoIndex = null
                                            currentHoverTodoIndex = null
                                            dragTodoOffsetX = 0f
                                            dragTodoOffsetY = 0f
                                            draggedInitialPointerInRoot = null
                                            hoveredFilterKey = null
                                            hoveredProjectId = null
                                            hoveredTimelineTab = false
                                            lastTodoDragEndTime = System.currentTimeMillis()
                                        }
                                    }
                                }
                            } else {
                                draggedTodoIndex = null
                                currentHoverTodoIndex = null
                                dragTodoOffsetX = 0f
                                dragTodoOffsetY = 0f
                                draggedInitialPointerInRoot = null
                                hoveredFilterKey = null
                                hoveredProjectId = null
                                hoveredTimelineTab = false
                                lastTodoDragEndTime = System.currentTimeMillis()
                            }
                        },
                        onDragCancel = onDragCancel@ {
                            if (isTodoSettling) return@onDragCancel
                            val startIdx = draggedTodoIndex
                            if (startIdx != null) {
                                isTodoSettling = true
                                todoSettleJob = scope.launch {
                                    animateDragToSettle(
                                        currentOffset = dragTodoOffsetY,
                                        targetOffset = 0f,
                                        springSpec = getReorderSpringSpec(context)
                                    ) { animatedY ->
                                        dragTodoOffsetY = animatedY
                                    }
                                    withContext(Dispatchers.IO) {
                                        repository.reorderTodos(localTodos)
                                    }
                                    draggedTodoIndex = null
                                    currentHoverTodoIndex = null
                                    dragTodoOffsetX = 0f
                                    dragTodoOffsetY = 0f
                                    draggedInitialPointerInRoot = null
                                    hoveredFilterKey = null
                                    hoveredProjectId = null
                                    hoveredTimelineTab = false
                                    lastTodoDragEndTime = System.currentTimeMillis()
                                    isTodoSettling = false
                                }
                            } else {
                                draggedTodoIndex = null
                                currentHoverTodoIndex = null
                                dragTodoOffsetX = 0f
                                dragTodoOffsetY = 0f
                                draggedInitialPointerInRoot = null
                                hoveredFilterKey = null
                                hoveredProjectId = null
                                hoveredTimelineTab = false
                                lastTodoDragEndTime = System.currentTimeMillis()
                            }
                        }
                    ) {
                        SquircleCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
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
                                            modifier = Modifier
                                                .size(20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ReorderGrabHandleIcon(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                size = 16.dp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val taskHeadline = HeadlineScaler.clamp(todo.title)
                                    Text(
                                        text = taskHeadline,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = calculateDynamicHeadlineSize(taskHeadline, baseSize = 16.sp, minSize = 12.sp)
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right Side: Circular Checkbox (28x28dp) for natural right-thumb ergonomics!
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
                                            if (!isCurrentlyDragged && System.currentTimeMillis() - lastTodoDragEndTime > 300L) {
                                                HapticHelper.success(context)
                                                if (selectedStatusFilter == "ACTIVE" && !isDone) {
                                                    exitingTodoIds = exitingTodoIds + todo.id
                                                    scope.launch {
                                                        delay(200)
                                                        localTodos = localTodos.filter { it.id != todo.id }
                                                        exitingTodoIds = exitingTodoIds - todo.id
                                                        repository.updateTodoStatus(todo.id, TaskStatus.COMPLETED)
                                                    }
                                                } else if (selectedStatusFilter == "DONE" && isDone) {
                                                    exitingTodoIds = exitingTodoIds + todo.id
                                                    scope.launch {
                                                        delay(200)
                                                        localTodos = localTodos.filter { it.id != todo.id }
                                                        exitingTodoIds = exitingTodoIds - todo.id
                                                        repository.updateTodoStatus(todo.id, TaskStatus.TODO)
                                                    }
                                                } else {
                                                    val newStatus = if (isDone) TaskStatus.TODO else TaskStatus.COMPLETED
                                                    scope.launch {
                                                        repository.updateTodoStatus(todo.id, newStatus)
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
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
}

        1 -> {
                    // --- VIEW 2: DAY TIMELINE (VISUAL TIME-BLOCKING) ---
                    val cal = GlobalTimeManager.getCurrentCalendar(userSettings.timeFormat24Hour)
                    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                    val currentMinute = cal.get(Calendar.MINUTE)
                    val currentTotalMinutes = currentHour * 60 + currentMinute
                    val hour = currentHour
                    val isCurrentHour = isViewingToday && (currentHour == hour)

                    var liveMinuteOfDay by remember(userSettings.timeFormat24Hour) {
                        mutableIntStateOf(currentTotalMinutes)
                    }
                    LaunchedEffect(userSettings.timeFormat24Hour) {
                        while (true) {
                            val c = GlobalTimeManager.getCurrentCalendar(userSettings.timeFormat24Hour)
                            liveMinuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                            kotlinx.coroutines.delay(1000L)
                        }
                    }

                    val sortedBlocks = remember(timeBlocks) {
                        timeBlocks.sortedBy { it.startMinute }
                    }

                    // Fixed scrolling axis starting from 08:00 (8:00 AM) and ending at 22:00 (10:00 PM)
                    val hours = remember { (8..22).toList() }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hours) { hour ->
                            val hourMinute = hour * 60
                            val isHourActive = isViewingToday && (currentHour == hour)
                            val hourBlocks = sortedBlocks.filter { block ->
                                if (hour == 8) block.startMinute < 9 * 60
                                else if (hour == 22) block.startMinute >= 22 * 60
                                else block.startMinute >= hourMinute && block.startMinute < (hourMinute + 60)
                            }
                            val timeLabelWidth = if (userSettings.timeFormat24Hour) 52.dp else 68.dp

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Horizontal Grid Line intersecting with time label on the left axis
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pinned time label to absolute left edge with Apple-style accent yellow
                                    Text(
                                        text = GlobalTimeManager.formatHour(hour, userSettings.timeFormat24Hour),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isHourActive) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (isHourActive) BrandAmberEnd else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.width(timeLabelWidth)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Continuous horizontal grid line extending across the width of the Timeline
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(
                                                if (isHourActive) BrandAmberEnd.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                            )
                                    )
                                }

                                if (hourBlocks.isEmpty()) {
                                    Spacer(modifier = Modifier.height(28.dp))
                                } else {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = timeLabelWidth + 8.dp, top = 6.dp, bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(hourBlocks, key = { it.id }) { block ->
                                            val blockColor = try {
                                                Color(android.graphics.Color.parseColor(block.color))
                                            } catch (_: Exception) {
                                                WorkIndigo
                                            }

                                            val blockDate = block.date.ifBlank { selectedPlanDate.ifBlank { todayDateStr } }
                                            val isPassed = GlobalTimeManager.isEventElapsed(
                                                startMinute = block.startMinute,
                                                durationMinutes = block.durationMinutes,
                                                eventDate = blockDate,
                                                currentDate = todayDateStr,
                                                currentMinuteOfDay = liveMinuteOfDay,
                                                is24Hour = userSettings.timeFormat24Hour
                                            )

                                            val cardAlpha by animateFloatAsState(
                                                targetValue = if (isPassed) 0.45f else 1f,
                                                animationSpec = tween(durationMillis = 350),
                                                label = "timelineEventDimAlpha"
                                            )

                                            // Proportional card height based on scheduled duration
                                            val proportionalHeight = ((block.durationMinutes.coerceIn(15, 360) / 60f) * 68).dp
                                            val cardHeight = maxOf(56.dp, proportionalHeight)

                                            // Vertical placement offset based on startMinute within the hour
                                            val minuteOffset = (block.startMinute - hourMinute).coerceIn(0, 59)
                                            if (minuteOffset > 0) {
                                                Spacer(modifier = Modifier.height(((minuteOffset / 60f) * 24).dp))
                                            }

                                            // Time-Block Card with Universal Swipe-to-Delete
                                            UniversalSwipeableItem(
                                                modifier = Modifier.width(260.dp),
                                                enableSwipe = (hourBlocks.size <= 1),
                                                itemId = block.id,
                                                cornerRadius = 14.dp,
                                                onDelete = {
                                                    HapticHelper.heavy(context)
                                                    scope.launch {
                                                        repository.deleteTimeBlock(block.id)
                                                    }
                                                }
                                            ) {
                                                SquircleCard(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(cardHeight)
                                                        .graphicsLayer { alpha = cardAlpha },
                                                    cornerRadius = 14.dp,
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(blockColor)
                                                            )
                                                            Column(modifier = Modifier.weight(1f)) {
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
                                                                    text = GlobalTimeManager.formatTimeRange(block.startMinute, block.durationMinutes, userSettings.timeFormat24Hour),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        // Status Pill Badge Fix (Never allow vertical text wrapping)
                                                        Box(
                                                            modifier = Modifier
                                                                .wrapContentWidth()
                                                                .clip(RoundedCornerShape(999.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isPassed) "Past" else "Scheduled",
                                                                fontSize = 11.sp,
                                                                color = if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant else blockColor,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false
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
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
    }
}
}

        // Universal Floating Action Button (+) at bottom-right (Tasks view only, removed from Timeline view)
        if (pagerState.currentPage == 0) {
            val fabBgColor by animateColorAsState(
                targetValue = if (projects.isEmpty()) WorkIndigo else BrandAmberStart,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                ),
                label = "fabBgColor"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(fabBgColor)
                    .clickable {
                        HapticHelper.click(context)
                        if (projects.isEmpty()) {
                            showCreateProjectSheet = true
                        } else {
                            taskToEdit = null
                            showCreateTaskSheet = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (projects.isEmpty()) "Create First Project" else "Add",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // New / Edit Task Bottom Sheet
        if (showCreateTaskSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var taskTitle by remember(taskToEdit, showCreateTaskSheet) { mutableStateOf(taskToEdit?.title ?: "") }
            var selectedProjId by remember(taskToEdit, activeProject, showCreateTaskSheet) {
                mutableStateOf(taskToEdit?.projectId ?: activeProject?.id ?: projects.firstOrNull()?.id ?: "")
            }
            val isInitiallyInPlan = remember(taskToEdit, planImportedTaskIds) {
                taskToEdit?.let { planImportedTaskIds.contains(it.id) } ?: false
            }
            var includeInPlan by remember(taskToEdit, showCreateTaskSheet) { mutableStateOf(isInitiallyInPlan) }
            var isSubmitting by remember { mutableStateOf(false) }

            fun dismissTaskSheet() {
                if (!isSubmitting) {
                    isSubmitting = true
                    sheetState.animateDismiss(scope) {
                        showCreateTaskSheet = false
                        taskToEdit = null
                    }
                }
            }

            val submitTask: () -> Unit = {
                val trimmed = taskTitle.trim().take(20)
                if (trimmed.isNotBlank() && !isSubmitting) {
                    isSubmitting = true
                    HapticHelper.success(context)
                    scope.launch {
                        try {
                            val pId = if (selectedProjId.isNotBlank() && projects.any { it.id == selectedProjId }) {
                                selectedProjId
                            } else if (activeProject != null) {
                                activeProject.id
                            } else if (projects.isNotEmpty()) {
                                projects.first().id
                            } else {
                                repository.addProject("General", null, "#5E5CE6", 8, "")
                            }

                            val taskDueDate = taskToEdit?.dueDate ?: repository.getTodayString()
                            val editing = taskToEdit

                            val saveJob = launch(Dispatchers.IO) {
                                if (editing != null) {
                                    val updated = editing.copy(
                                        projectId = pId,
                                        title = trimmed,
                                        dueDate = taskDueDate
                                    )
                                    repository.updateTodo(updated)
                                    if (includeInPlan) {
                                        repository.importTaskToPlan(editing.id)
                                    } else {
                                        repository.removeTaskFromPlan(editing.id)
                                    }
                                } else {
                                    val newTaskId = repository.addTodo(
                                        title = trimmed,
                                        projectId = pId,
                                        priority = TaskPriority.MEDIUM,
                                        estimatedMinutes = 50,
                                        dueDate = taskDueDate
                                    )
                                    if (includeInPlan) {
                                        repository.importTaskToPlan(newTaskId)
                                    }
                                    selectedProjectIdFilter = pId
                                    selectedStatusFilter = "ACTIVE"
                                }
                            }

                            sheetState.smoothDismiss()
                            saveJob.join()
                            showCreateTaskSheet = false
                            taskToEdit = null
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissTaskSheet() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 24.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Modal Header with directly aligned State Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (taskToEdit != null) "Edit Task" else "Create Task",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlanStateToggle(
                                isActive = includeInPlan,
                                onToggle = { includeInPlan = it }
                            )
                            IconButton(
                                onClick = { dismissTaskSheet() },
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
                    }

                    // Project selector chips when multiple projects exist
                    if (projects.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Project",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(projects, key = { it.id }) { proj ->
                                    val isSelected = (selectedProjId.ifBlank { activeProject?.id } == proj.id)
                                    val pColor = try {
                                        Color(android.graphics.Color.parseColor(proj.color))
                                    } catch (_: Exception) {
                                        WorkIndigo
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isSelected) pColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                                color = if (isSelected) pColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .clickable {
                                                HapticHelper.selection(context)
                                                selectedProjId = proj.id
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(pColor)
                                            )
                                            Text(
                                                text = proj.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it.take(20) },
                        label = { Text("Task Title") },
                        placeholder = { Text("What needs to get done?") },
                        supportingText = {
                            Text(
                                text = "${taskTitle.length}/20",
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitTask() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Create / Save CTA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (taskTitle.isNotBlank()) WorkIndigo else WorkIndigo.copy(alpha = 0.5f))
                            .clickable(enabled = taskTitle.isNotBlank() && !isSubmitting) {
                                submitTask()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (taskToEdit != null) "Save Changes" else "Create Task",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // New Project Bottom Sheet
        if (showCreateProjectSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var projName by remember { mutableStateOf("") }
            var selectedNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
            var showNotePicker by remember { mutableStateOf(false) }
            var selectedDeadline by remember { mutableStateOf<SelectedDeadlineDate?>(null) }
            var isDeadlinePickerExpanded by remember { mutableStateOf(false) }
            var isSubmittingProject by remember { mutableStateOf(false) }

            val isFormValid = ProjectDeadlineValidator.isProjectFormValid(
                title = projName,
                selectedDeadline = selectedDeadline,
                isSubmitting = isSubmittingProject
            )

            fun dismissProjectSheet() {
                if (!isSubmittingProject) {
                    isSubmittingProject = true
                    sheetState.animateDismiss(scope) {
                        showCreateProjectSheet = false
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissProjectSheet() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Project",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { dismissProjectSheet() },
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
                        value = projName,
                        onValueChange = { projName = it.take(20) },
                        label = { Text("Project Title") },
                        placeholder = { Text("Enter project title") },
                        supportingText = { Text("${projName.length}/20", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Position the 'Notes' option directly below the title field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WorkIndigo.copy(alpha = 0.12f))
                                    .clickable {
                                        HapticHelper.click(context)
                                        showNotePicker = !showNotePicker
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach Notes",
                                    tint = WorkIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Display selected note tags with remove button
                        if (selectedNoteIds.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(selectedNoteIds.toList()) { noteId ->
                                    val note = quickNotes.find { it.id == noteId }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "TAG",
                                                color = Color(0xFFFF3B30),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = note?.title?.ifBlank { "Note" } ?: "Note",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        selectedNoteIds = selectedNoteIds - noteId
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Picker drop-down / expanded list
                        if (showNotePicker) {
                            SquircleCard(
                                cornerRadius = 12.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Select Notes to Link:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (quickNotes.isEmpty()) {
                                        Text(
                                            text = "No notes yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        quickNotes.take(6).forEach { note ->
                                            val isAttached = selectedNoteIds.contains(note.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isAttached) WorkIndigo.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable {
                                                        HapticHelper.selection(context)
                                                        selectedNoteIds = if (isAttached) {
                                                            selectedNoteIds - note.id
                                                        } else {
                                                            selectedNoteIds + note.id
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = note.title.ifBlank { "Untitled Note" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isAttached) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isAttached) WorkIndigo else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isAttached) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = WorkIndigo,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Universal Mandatory Deadline Picker & Expandable Drop-Down
                    UniversalDeadlinePicker(
                        selectedDeadline = selectedDeadline,
                        isExpanded = isDeadlinePickerExpanded,
                        onToggleExpand = { isDeadlinePickerExpanded = !isDeadlinePickerExpanded },
                        onDateSelected = { date ->
                            selectedDeadline = date
                            isDeadlinePickerExpanded = false
                        }
                    )

                    // Final Create Project Action Button with Mandatory Form Lock
                    val submitButtonBg = if (isFormValid) WorkIndigo else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val submitButtonTextColor = if (isFormValid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(submitButtonBg)
                            .clickable(enabled = isFormValid && !isSubmittingProject) {
                                if (isFormValid && !isSubmittingProject) {
                                    isSubmittingProject = true
                                    HapticHelper.success(context)
                                    scope.launch {
                                        val deadlineIso = selectedDeadline?.toIsoString()
                                        val saveJob = launch(Dispatchers.IO) {
                                            val newProjId = repository.addProject(
                                                name = projName.trim().take(20),
                                                description = deadlineIso,
                                                color = "#5E5CE6",
                                                targetPomodoros = 8,
                                                linkedNoteIds = selectedNoteIds.joinToString(",")
                                            )
                                            repository.importProjectToPlan(newProjId)
                                        }
                                        sheetState.smoothDismiss()
                                        saveJob.join()
                                        showCreateProjectSheet = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Project",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = submitButtonTextColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Add Time Block Bottom Sheet
        if (showAddTimeBlockSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var blockTitle by remember { mutableStateOf("") }
            var startH by remember { mutableStateOf("10") }
            var duration by remember { mutableStateOf("60") }
            var isSubmittingTimeBlock by remember { mutableStateOf(false) }

            fun dismissTimeBlockSheet() {
                if (!isSubmittingTimeBlock) {
                    isSubmittingTimeBlock = true
                    sheetState.animateDismiss(scope) {
                        showAddTimeBlockSheet = false
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissTimeBlockSheet() },
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
                            text = "Add Time Block",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { dismissTimeBlockSheet() },
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
                        value = blockTitle,
                        onValueChange = { blockTitle = it.take(20) },
                        label = { Text("Block Title (max 20)") },
                        supportingText = { Text("${blockTitle.length}/20", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                        placeholder = { Text("e.g. Deep Work: Architecture") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = startH,
                            onValueChange = { startH = it },
                            label = { Text("Start Hour (8-22)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("Duration (mins)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(WorkIndigo)
                            .clickable(enabled = blockTitle.isNotBlank() && !isSubmittingTimeBlock) {
                                val sHour = startH.toIntOrNull() ?: 10
                                val dMins = duration.toIntOrNull() ?: 60
                                if (blockTitle.isNotBlank() && !isSubmittingTimeBlock) {
                                    isSubmittingTimeBlock = true
                                    HapticHelper.success(context)
                                    scope.launch {
                                        repository.addTimeBlock(blockTitle.trim().take(20), sHour * 60, dMins, "#5E5CE6", null)
                                        sheetState.smoothDismiss()
                                        showAddTimeBlockSheet = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Schedule Block",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Viewing Linked Note Sheet (Clickable Note Tag Target)
        if (viewingLinkedNote != null) {
            val note = viewingLinkedNote!!
            val noteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var isClosingNote by remember { mutableStateOf(false) }

            fun dismissLinkedNote() {
                if (!isClosingNote) {
                    isClosingNote = true
                    noteSheetState.animateDismiss(scope) {
                        viewingLinkedNote = null
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { dismissLinkedNote() },
                sheetState = noteSheetState,
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF3B30))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TAG",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = note.title.ifBlank { "Untitled Note" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { dismissLinkedNote() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )

                    Text(
                        text = note.content.ifBlank { "(Empty note)" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    if (note.noteType.isNotBlank()) {
                        Text(
                            text = "Category: ${note.noteType}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Two-Tier Project Notes Management Modal
        if (managingNotesProject != null) {
            val proj = projects.find { it.id == managingNotesProject!!.id } ?: managingNotesProject!!
            ProjectNotesManagementModal(
                project = proj,
                allNotes = quickNotes,
                onDismiss = { managingNotesProject = null },
                onAttachNote = { noteId ->
                    scope.launch {
                        repository.attachNoteToProject(proj.id, noteId)
                    }
                },
                onDetachNote = { noteId ->
                    scope.launch {
                        repository.detachNoteFromProject(proj.id, noteId)
                    }
                },
                onReorderNotes = { newOrderedIds ->
                    scope.launch {
                        repository.reorderProjectNotes(proj.id, newOrderedIds)
                    }
                },
                onViewNote = { note ->
                    viewingLinkedNote = note
                }
            )
        }
    }
}

private fun parseDeadlineDate(iso: String): SelectedDeadlineDate? {
    val parts = iso.split("-")
    if (parts.size == 3) {
        val y = parts[0].toIntOrNull() ?: return null
        val m = (parts[1].toIntOrNull() ?: return null) - 1
        val d = parts[2].toIntOrNull() ?: return null
        return SelectedDeadlineDate(y, m, d)
    }
    return null
}

/**
 * Fluid Two-Tier Project Notes Management System.
 * Tier 1: Pinned Project Notes (ordered list with move up/down and detach controls)
 * Tier 2: Available Notes Library (unattached notes with one-tap attach action)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectNotesManagementModal(
    project: Project,
    allNotes: List<QuickNote>,
    onDismiss: () -> Unit,
    onAttachNote: (String) -> Unit,
    onDetachNote: (String) -> Unit,
    onReorderNotes: (List<String>) -> Unit,
    onViewNote: (QuickNote) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var isDismissing by remember { mutableStateOf(false) }

    fun safeDismiss() {
        if (!isDismissing) {
            isDismissing = true
            sheetState.animateDismiss(scope, onDismiss)
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    val pinnedNoteIds = remember(project.linkedNoteIds) {
        if (project.linkedNoteIds.isBlank()) emptyList()
        else project.linkedNoteIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val pinnedNotes = remember(pinnedNoteIds, allNotes) {
        pinnedNoteIds.mapNotNull { id -> allNotes.find { it.id == id } }
    }

    val availableNotes = remember(pinnedNoteIds, allNotes, searchQuery) {
        allNotes.filter { note ->
            note.id !in pinnedNoteIds &&
                    (searchQuery.isBlank() ||
                            note.title.contains(searchQuery, ignoreCase = true) ||
                            note.content.contains(searchQuery, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = { safeDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Project Notes Management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Active Project: ${project.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandAmberStart,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { safeDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // TIER 1: PINNED PROJECT NOTES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TIER 1 • PINNED NOTES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(BrandAmberStart.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${pinnedNotes.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandAmberStart
                            )
                        }
                    }
                }

                if (pinnedNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pinnedNotes, key = { _, note -> note.id }) { index, note ->
                            SquircleCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                cornerRadius = 14.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Note Title & Preview
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onViewNote(note) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandAmberStart
                                            )
                                            Text(
                                                text = note.title.ifBlank { "Untitled Note" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = note.content.take(45).ifBlank { "No content" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Controls: Direct Grab Handle, Up, Down, Detach
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Direct Grab Handle (≡) for fluid Drag-to-Reorder
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .directReorderHandle(
                                                    onDragStart = { triggerPickupHaptic(context, hapticFeedback) },
                                                    onDragEnd = {},
                                                    onDragCancel = {},
                                                    onVerticalDragDelta = { dy ->
                                                        val shift = if (dy > 28f && index < pinnedNotes.size - 1) 1 else if (dy < -28f && index > 0) -1 else 0
                                                        if (shift != 0) {
                                                            HapticHelper.selection(context)
                                                            val mutable = pinnedNoteIds.toMutableList()
                                                            val temp = mutable[index]
                                                            mutable[index] = mutable[index + shift]
                                                            mutable[index + shift] = temp
                                                            onReorderNotes(mutable)
                                                        }
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ReorderGrabHandleIcon(
                                                color = BrandAmberStart,
                                                size = 16.dp
                                            )
                                        }

                                        // Move Up Button
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    HapticHelper.click(context)
                                                    val mutable = pinnedNoteIds.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index - 1]
                                                    mutable[index - 1] = temp
                                                    onReorderNotes(mutable)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Text(
                                                text = "▲",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (index > 0) BrandAmberStart else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            )
                                        }

                                        // Move Down Button
                                        IconButton(
                                            onClick = {
                                                if (index < pinnedNotes.size - 1) {
                                                    HapticHelper.click(context)
                                                    val mutable = pinnedNoteIds.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index + 1]
                                                    mutable[index + 1] = temp
                                                    onReorderNotes(mutable)
                                                }
                                            },
                                            enabled = index < pinnedNotes.size - 1,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Text(
                                                text = "▼",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (index < pinnedNotes.size - 1) BrandAmberStart else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            )
                                        }

                                        // Detach Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(StatusUrgent.copy(alpha = 0.12f))
                                                .clickable {
                                                    HapticHelper.click(context)
                                                    onDetachNote(note.id)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "Detach",
                                                color = StatusUrgent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                thickness = 1.dp
            )

            // TIER 2: AVAILABLE NOTES LIBRARY
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TIER 2 • AVAILABLE LIBRARY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${availableNotes.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Search field if many notes
                if (allNotes.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter available notes...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        singleLine = true
                    )
                }

                if (availableNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableNotes, key = { it.id }) { note ->
                            SquircleCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                cornerRadius = 14.dp,
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onViewNote(note) }
                                    ) {
                                        Text(
                                            text = note.title.ifBlank { "Untitled Note" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = note.content.take(50).ifBlank { "No content" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BrandAmberStart)
                                            .clickable {
                                                HapticHelper.click(context)
                                                onAttachNote(note.id)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "+ Attach",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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

