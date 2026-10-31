package com.deadline.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.deadline.ui.components.calculateContinuousDisplacement
import com.deadline.ui.components.calculateContinuousHorizontalDisplacement
import com.deadline.ui.components.calculateHorizontalSlotTargetIndex
import com.deadline.ui.components.calculateSlotTargetIndex
import com.deadline.ui.components.calculateSettleTargetOffset
import com.deadline.ui.components.animateDragToSettle
import com.deadline.ui.components.DEFAULT_DRAG_LONG_PRESS_TIMEOUT_MILLIS
import com.deadline.ui.components.detectFastDragGesturesAfterLongPress
import com.deadline.ui.components.getReorderSpringSpec
import com.deadline.ui.components.triggerPickupHaptic
import com.deadline.ui.components.UniversalAutoScrollCoordinator
import com.deadline.ui.components.ReorderGrabHandleIcon
import com.deadline.ui.components.MorphingLeadingActionIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import com.deadline.ui.components.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Brush
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.FocusTask
import com.deadline.data.local.PomodoroSession
import com.deadline.data.local.PomodoroType
import com.deadline.service.AmbientSoundPlayer
import com.deadline.service.AmbientSoundTrack
import com.deadline.service.PomodoroService
import com.deadline.ui.components.AppleProfileAvatar
import com.deadline.ui.components.CheckmarkCheckIcon
import com.deadline.ui.components.GeometricPauseIcon
import com.deadline.ui.components.GeometricPlayIcon
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.PencilEditIcon
import com.deadline.ui.components.ResetCcwIcon
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.components.StopwatchIcon
import com.deadline.ui.components.WaveformIcon
import com.deadline.ui.theme.StatusUrgent
import com.deadline.ui.components.animateDismiss
import com.deadline.ui.components.smoothDismiss
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Modifier for dynamic tactile touch feedback:
 * Responsive touch feedback combining a spring-based scale micro-animation
 * (scales down to [scaleDown] while pressed) with standard Material ripple indication.
 */
@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = if (enabled) LocalIndication.current else null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Ultra-high-fidelity Focus Timer screen architecture:
 * - OLED Minimalist Dark Theme (#000000 pure black with #141416 surfaces) & Warm Cream Light Theme.
 * - Dynamic State Machine Accent Palette:
 *   - Focus Mode: Vivid Amber / Warm Orange (#FF9500, Glow #FFA726)
 *   - Break Mode: Electric Mint / Emerald Green (#22C55E, Glow #00E676)
 *   - Flow Mode (Long Break): Deep Royal Purple / Violet (#A855F7, Glow #C084FC)
 * - Header & UI Cleanup: Synced badge removed, "Tap to bind timer" banner text removed.
 * - Minimalist Activity Items: Edit pencil & static duration pills removed; swipe-to-edit & swipe-to-delete gestures.
 * - Standard intuitive restart orientation.
 * - Seamless sliding pill segmented tab switcher.
 * - Fluid, natural reordering physics for focus activities.
 * - Half-screen scaled ambient soundscape bottom sheet with smooth downward dismissal.
 * - Ultra-compact 25%-30% screen height activity modals with modernized rounded volume-bar slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    repository: DeadlineRepository,
    ambientPlayer: AmbientSoundPlayer,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    val remainingSeconds by PomodoroService.remainingSeconds.collectAsState()
    val isRunning by PomodoroService.isRunning.collectAsState()
    val currentTaskTitle by PomodoroService.currentTaskTitle.collectAsState()
    val currentSessionType by PomodoroService.currentSessionType.collectAsState()
    val userSettings by repository.userSettings.collectAsState()
    val focusTasks by repository.focusTasks.collectAsState()

    var selectedType by remember { mutableStateOf(PomodoroType.FOCUS) }

    // Dynamic Accent Color Mapping with Fluid Spring Transitions
    val targetAccent = when (selectedType) {
        PomodoroType.FOCUS -> Color(0xFFFF9500)       // Vivid Amber / Warm Orange
        PomodoroType.SHORT_BREAK -> Color(0xFF22C55E) // Electric Mint / Emerald Green
        PomodoroType.LONG_BREAK -> Color(0xFFA855F7)  // Deep Royal Purple / Violet
    }
    val targetGlow = when (selectedType) {
        PomodoroType.FOCUS -> Color(0xFFFFA726)
        PomodoroType.SHORT_BREAK -> Color(0xFF00E676)
        PomodoroType.LONG_BREAK -> Color(0xFFC084FC)
    }

    val activeAccentColor by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "activeAccentColor"
    )
    val activeGlowColor by animateColorAsState(
        targetValue = targetGlow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "activeGlowColor"
    )

    val filteredModeTasks = remember(focusTasks, selectedType) {
        focusTasks
            .filter { it.sessionType.equals(selectedType.name, ignoreCase = true) }
            .sortedBy { it.sortOrder }
    }

    // Fluid drag-and-drop state & physics
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var currentHoverIndex by remember { mutableStateOf<Int?>(null) }
    var isTimerTaskSettling by remember { mutableStateOf(false) }
    var timerTaskSettleJob by remember { mutableStateOf<Job?>(null) }
    var deletingTaskIds by remember { mutableStateOf(setOf<String>()) }

    // Local mutable state for smooth drag-and-drop reordering feedback
    var localModeTasks by remember { mutableStateOf(filteredModeTasks) }
    LaunchedEffect(filteredModeTasks) {
        if (draggedIndex == null) {
            localModeTasks = filteredModeTasks
        }
    }

    val fallbackTask = remember(selectedType) {
        FocusTask(
            id = "timer_mode_${selectedType.name.lowercase()}",
            title = when (selectedType) {
                PomodoroType.FOCUS -> "Focus"
                PomodoroType.SHORT_BREAK -> "Break"
                PomodoroType.LONG_BREAK -> "Flow"
            },
            durationMinutes = 0,
            sessionType = selectedType.name
        )
    }

    var activeFocusTask by remember { mutableStateOf<FocusTask?>(null) }
    val currentSelectedTask = activeFocusTask
        ?: localModeTasks.find { it.title == currentTaskTitle && it.durationMinutes > 0 }
        ?: localModeTasks.firstOrNull()
        ?: fallbackTask

    var showAudioSheet by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<FocusTask?>(null) }
    var livePickerSeconds by remember { mutableStateOf<Int?>(null) }

    val serviceTotalSeconds by PomodoroService.totalDurationSeconds.collectAsState()
    val activePresetState by PomodoroService.activePreset.collectAsState()

    val presetTotalSeconds = activePresetState?.let { preset ->
        when (selectedType) {
            PomodoroType.FOCUS -> preset.focusDuration
            PomodoroType.SHORT_BREAK -> preset.breakDuration
            PomodoroType.LONG_BREAK -> preset.flowDuration
        }
    }

    val totalDurationSeconds = if (serviceTotalSeconds > 0) {
        serviceTotalSeconds
    } else if ((presetTotalSeconds ?: 0) > 0) {
        presetTotalSeconds!!
    } else {
        (currentSelectedTask.durationMinutes * 60)
    }

    LaunchedEffect(currentSessionType) {
        if (selectedType != currentSessionType) {
            selectedType = currentSessionType
        }
    }

    LaunchedEffect(currentTaskTitle, activePresetState) {
        if (currentTaskTitle.isNotBlank() || activePresetState != null) {
            activeFocusTask = null
        }
    }

    // Universal Circular Progress Animation:
    // Triggers smooth filling animation consistently across all modes (Focus, Break, Flow)
    // and dynamically adapts and re-renders seamlessly whenever duration is adjusted or switched.
    val ringFillAnim = remember { Animatable(if (remainingSeconds > 0 && totalDurationSeconds > 0) 1f else 0f) }

    LaunchedEffect(totalDurationSeconds, remainingSeconds) {
        if (totalDurationSeconds > 0 && remainingSeconds > 0 && ringFillAnim.value == 0f) {
            ringFillAnim.snapTo(1f)
        }
    }

    val animationTriggerKey = "${selectedType.name}_${currentSelectedTask.id}_$totalDurationSeconds"
    var previousAnimationKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(animationTriggerKey) {
        if (previousAnimationKey != null && previousAnimationKey != animationTriggerKey) {
            if (totalDurationSeconds > 0 && remainingSeconds > 0) {
                ringFillAnim.snapTo(0f)
                ringFillAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            } else {
                ringFillAnim.snapTo(0f)
            }
        }
        previousAnimationKey = animationTriggerKey
    }

    var playButtonBottomY by remember { mutableFloatStateOf(0f) }
    var silentFocusBottomY by remember { mutableFloatStateOf(0f) }
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightDp = 64.dp
    val itemHeightPx = with(density) { (itemHeightDp + 16.dp).toPx() }
    var measuredTimerItemHeightPx by remember { mutableFloatStateOf(itemHeightPx) }

    val timerLazyListState = rememberLazyListState()
    var timerViewportHeight by remember { mutableFloatStateOf(0f) }
    var timerListBounds by remember { mutableStateOf(Rect.Zero) }
    var draggedInitialPointerInRoot by remember { mutableStateOf<Offset?>(null) }

    val pointerYInViewport = draggedInitialPointerInRoot?.let { pointer ->
        pointer.y + dragOffsetY - timerListBounds.top
    } ?: 0f

    UniversalAutoScrollCoordinator(
        lazyListState = timerLazyListState,
        isDragging = draggedIndex != null,
        pointerYInViewport = pointerYInViewport,
        viewportHeight = timerViewportHeight
    )

    // Mode Switcher Action Lambda
    val switchMode: (PomodoroType) -> Unit = { newType ->
        if (selectedType != newType) {
            HapticHelper.selection(context)
            selectedType = newType

            val preset = PomodoroService.activePreset.value
            val presetDuration = when (newType) {
                PomodoroType.FOCUS -> preset?.focusDuration
                PomodoroType.SHORT_BREAK -> preset?.breakDuration
                PomodoroType.LONG_BREAK -> preset?.flowDuration
            }

            if (presetDuration != null && presetDuration > 0) {
                PomodoroService.resetTimer(context, presetDuration, currentTaskTitle, sessionType = newType)
                scope.launch {
                    ringFillAnim.snapTo(0f)
                    ringFillAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                }
            } else {
                val newTasks = focusTasks.filter { it.sessionType.equals(newType.name, true) }
                if (newTasks.isNotEmpty()) {
                    val targetTask = newTasks.first()
                    activeFocusTask = targetTask
                    PomodoroService.resetTimer(context, targetTask.durationMinutes * 60, targetTask.title, sessionType = newType)
                    scope.launch {
                        ringFillAnim.snapTo(0f)
                        ringFillAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                    }
                } else {
                    activeFocusTask = null
                    PomodoroService.resetTimer(context, 0, "", sessionType = newType)
                    scope.launch {
                        ringFillAnim.snapTo(0f)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootHeightPx = coordinates.size.height.toFloat()
            }
    ) {
        LazyColumn(
            state = timerLazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .onGloballyPositioned { coords ->
                    timerViewportHeight = coords.size.height.toFloat()
                    timerListBounds = coords.boundsInRoot()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "timer_header") {
                // 1. Top Header Bar: Avatar & Crisp "Timer" Title (Synced badge completely removed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppleProfileAvatar(
                        avatarUri = userSettings.avatarUri,
                        onClick = onOpenProfile
                    )
                    Text(
                        text = "Timer",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item(key = "timer_segmented_control") {
                // 2. Segmented Tab Navigation: Clean Sliding Pill Indicator
                FocusModeSegmentedControl(
                    selectedType = selectedType,
                    onTypeSelected = switchMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "timer_dial") {
                // 3. Circular Progress Ring & Center Numerical Display (240x240dp) with Horizontal Swipe Gesture Switching
                val currentModeForSwipe by rememberUpdatedState(selectedType)
                val currentSwitchModeForSwipe by rememberUpdatedState(switchMode)
                var timerDragDeltaX by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { timerDragDeltaX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                timerDragDeltaX += dragAmount
                            },
                            onDragEnd = {
                                val threshold = with(density) { 36.dp.toPx() }
                                val modes = listOf(PomodoroType.FOCUS, PomodoroType.SHORT_BREAK, PomodoroType.LONG_BREAK)
                                val currentIndex = modes.indexOf(currentModeForSwipe)
                                if (timerDragDeltaX < -threshold) {
                                    // Swiped left -> advance mode
                                    if (currentIndex in 0 until modes.lastIndex) {
                                        currentSwitchModeForSwipe(modes[currentIndex + 1])
                                    }
                                } else if (timerDragDeltaX > threshold) {
                                    // Swiped right -> previous mode
                                    if (currentIndex > 0) {
                                        currentSwitchModeForSwipe(modes[currentIndex - 1])
                                    }
                                }
                                timerDragDeltaX = 0f
                            },
                            onDragCancel = { timerDragDeltaX = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val fillProgress = ringFillAnim.value

                val effectiveRingRemaining = if (showTaskDialog && livePickerSeconds != null) livePickerSeconds!! else remainingSeconds
                val effectiveRingTotal = if (showTaskDialog && livePickerSeconds != null) {
                    if (livePickerSeconds!! > 0) livePickerSeconds!! else 1
                } else {
                    if (totalDurationSeconds > 0) totalDurationSeconds else 1
                }

                val targetFraction = (effectiveRingRemaining.toFloat() / effectiveRingTotal.toFloat()).coerceIn(0f, 1f)
                val animatedProgressFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = if (isRunning) tween(durationMillis = 1000, easing = LinearEasing) else spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ringProgressFraction"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    // 1. Neutral grey outline track
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth)
                    )
                    // 2. Dynamic Universal Counter-Clockwise Colored Ring Fill
                    if (effectiveRingRemaining > 0 && fillProgress > 0f && effectiveRingTotal > 0) {
                        val activeSweep = -360f * (animatedProgressFraction * fillProgress)
                        drawArc(
                            color = activeAccentColor,
                            startAngle = -90f,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                val activeDisplaySeconds = if (showTaskDialog && livePickerSeconds != null) {
                    livePickerSeconds!!
                } else {
                    remainingSeconds
                }

                val displayHours = activeDisplaySeconds / 3600
                val displayMins = (activeDisplaySeconds % 3600) / 60
                val displaySecs = activeDisplaySeconds % 60
                val timeString = if (displayHours > 0) {
                    String.format(Locale.US, "%02d:%02d:%02d", displayHours, displayMins, displaySecs)
                } else {
                    String.format(Locale.US, "%02d:%02d", displayMins, displaySecs)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (displayHours > 0) 42.sp else 54.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRunning) "RUNNING" else "PAUSED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }

            item(key = "timer_controls") {
                // 4. Minimalist Three-Button Control Deck with Correct Restart Orientation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Circular Reset Button (↺ Standard Intuitive Direction with dynamic touch feedback)
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .bounceClick(scaleDown = 0.92f) {
                                HapticHelper.click(context)
                                if (totalDurationSeconds > 0) {
                                    PomodoroService.resetTimer(context, totalDurationSeconds, currentSelectedTask.title, sessionType = selectedType)
                                    scope.launch {
                                        ringFillAnim.snapTo(0f)
                                        ringFillAnim.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                                    }
                                } else {
                                    PomodoroService.resetTimer(context, 0, currentSelectedTask.title, sessionType = selectedType)
                                    scope.launch { ringFillAnim.snapTo(0f) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ResetCcwIcon(
                            color = MaterialTheme.colorScheme.onSurface,
                            size = 20.dp
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Center: Oversized Primary Play / Pause Button with dynamic touch feedback
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                playButtonBottomY = pos.y + coordinates.size.height
                            }
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                spotColor = activeGlowColor,
                                ambientColor = activeGlowColor
                            )
                            .clip(CircleShape)
                            .background(activeAccentColor)
                            .bounceClick(scaleDown = 0.92f) {
                                HapticHelper.success(context)
                                if (isRunning) {
                                    PomodoroService.pauseTimer(context)
                                } else {
                                    if (remainingSeconds <= 0 && totalDurationSeconds <= 0) {
                                        // Zero initial state: tap to open picker modal to set duration
                                        taskToEdit = null
                                        showTaskDialog = true
                                    } else {
                                        val startSecs = if (remainingSeconds <= 0) totalDurationSeconds else remainingSeconds
                                        PomodoroService.startTimer(context, startSecs, currentSelectedTask.title, sessionType = selectedType, totalSeconds = totalDurationSeconds)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRunning) {
                            GeometricPauseIcon(color = Color.White, size = 22.dp)
                        } else {
                            GeometricPlayIcon(color = Color.White, size = 24.dp)
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Right: Circular Complete Early Button (✓ with dynamic touch feedback)
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .bounceClick(scaleDown = 0.92f) {
                                HapticHelper.success(context)
                                PomodoroService.stopTimer(context)
                                scope.launch {
                                    val elapsedMinutes = (totalDurationSeconds - remainingSeconds) / 60
                                    val actualDuration = if (elapsedMinutes <= 0) 1 else elapsedMinutes
                                    val session = PomodoroSession(
                                        userId = "default_user",
                                        title = currentSelectedTask.title,
                                        sessionType = selectedType,
                                        durationMinutes = actualDuration,
                                        completed = true
                                    )
                                    repository.savePomodoroSession(session)
                                    PomodoroService.resetTimer(context, totalDurationSeconds, currentSelectedTask.title, sessionType = selectedType)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CheckmarkCheckIcon(
                            color = activeAccentColor,
                            size = 20.dp
                        )
                    }
                }
            }

            item(key = "timer_sound_pill") {
                // 5. Interactive Ambient Sound System Pill Indicator
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SquircleCard(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                silentFocusBottomY = pos.y + coordinates.size.height
                            },
                        cornerRadius = 22.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        onClick = {
                            HapticHelper.click(context)
                            showAudioSheet = true
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            WaveformIcon(color = activeAccentColor, size = 18.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            val activeTrack = ambientPlayer.getActiveTrack()
                            val displayLabel = if (activeTrack == AmbientSoundTrack.NONE) "Silent Focus" else activeTrack.displayName
                            Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item(key = "timer_deck_spacer") {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 6. Focus Activities Deck with Fluid Drag-to-Reorder & Dynamic Gap-Filling Physics
            itemsIndexed(
                items = localModeTasks,
                key = { _, task -> task.id }
            ) { index, task ->
                val isSelected = currentSelectedTask.id == task.id
                val isThisItemDragged = draggedIndex == index
                val isDeleting = task.id in deletingTaskIds

                AnimatedVisibility(
                    visible = !isDeleting,
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
                    FocusActivitySwipeableItem(
                        task = task,
                        isSelected = isSelected,
                        isThisItemDragged = isThisItemDragged,
                        dragOffsetX = dragOffsetX,
                        dragOffsetY = dragOffsetY,
                        nonDraggedDisplacement = 0f,
                        activeAccentColor = activeAccentColor,
                        onSelect = {
                            HapticHelper.selection(context)
                            activeFocusTask = task
                            PomodoroService.updateTaskTitle(task.title)
                            PomodoroService.resetTimer(context, task.durationMinutes * 60, task.title, sessionType = selectedType)
                            scope.launch {
                                ringFillAnim.snapTo(0f)
                                ringFillAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                            }
                        },
                        onEdit = {
                            taskToEdit = task
                            showTaskDialog = true
                        },
                        onDelete = {
                            HapticHelper.heavy(context)
                            val wasRunning = isRunning || PomodoroService.isRunning.value
                            val isThisTaskActive = currentSelectedTask.id == task.id || currentTaskTitle == task.title
                            val shouldHaltAndZero = wasRunning || (isThisTaskActive && (remainingSeconds > 0 || PomodoroService.remainingSeconds.value > 0))

                            if (shouldHaltAndZero) {
                                PomodoroService.stopTimer(context)
                                scope.launch {
                                    ringFillAnim.snapTo(0f)
                                }
                            }
                            deletingTaskIds = deletingTaskIds + task.id
                            scope.launch {
                                delay(200)
                                val updated = localModeTasks.filter { it.id != task.id }
                                localModeTasks = updated
                                deletingTaskIds = deletingTaskIds - task.id
                                if (currentSelectedTask.id == task.id) {
                                    val nextTask = updated.firstOrNull() ?: fallbackTask
                                    activeFocusTask = if (updated.isNotEmpty()) nextTask else null
                                    PomodoroService.updateTaskTitle(nextTask.title)
                                    if (shouldHaltAndZero) {
                                        PomodoroService.resetTimer(context, 0, nextTask.title, sessionType = selectedType, totalSeconds = 0)
                                        ringFillAnim.snapTo(0f)
                                    } else {
                                        PomodoroService.resetTimer(context, nextTask.durationMinutes * 60, nextTask.title, sessionType = selectedType)
                                        ringFillAnim.snapTo(0f)
                                        ringFillAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                                    }
                                } else if (shouldHaltAndZero) {
                                    PomodoroService.resetTimer(context, 0, currentSelectedTask.title, sessionType = selectedType, totalSeconds = 0)
                                    ringFillAnim.snapTo(0f)
                                }
                                withContext(Dispatchers.IO) {
                                    repository.deleteFocusTask(task.id)
                                }
                            }
                        },
                        onDragStart = {
                            timerTaskSettleJob?.cancel()
                            isTimerTaskSettling = false
                            triggerPickupHaptic(context, hapticFeedback)
                            draggedIndex = index
                            currentHoverIndex = index
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDragStartWithOffset = { offset: Offset, boundsInRoot: Rect ->
                            timerTaskSettleJob?.cancel()
                            isTimerTaskSettling = false
                            triggerPickupHaptic(context, hapticFeedback)
                            measuredTimerItemHeightPx = boundsInRoot.height + with(density) { 16.dp.toPx() }
                            draggedIndex = index
                            currentHoverIndex = index
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            draggedInitialPointerInRoot = boundsInRoot.topLeft + offset
                        },
                        onDragEnd = onDragEnd@ {
                            if (isTimerTaskSettling) return@onDragEnd
                            val startIdx = draggedIndex
                            if (startIdx != null) {
                                val effectiveHeight = if (measuredTimerItemHeightPx > 0f) measuredTimerItemHeightPx else itemHeightPx
                                val targetSettleY = calculateSettleTargetOffset(startIdx, startIdx, effectiveHeight)
                                isTimerTaskSettling = true
                                timerTaskSettleJob = scope.launch {
                                    animateDragToSettle(
                                        currentOffset = dragOffsetY,
                                        targetOffset = targetSettleY,
                                        springSpec = getReorderSpringSpec(context)
                                    ) { animatedY ->
                                        dragOffsetY = animatedY
                                    }
                                    withContext(Dispatchers.IO) {
                                        repository.reorderFocusTasks(localModeTasks)
                                    }
                                    HapticHelper.success(context)
                                    draggedIndex = null
                                    currentHoverIndex = null
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    draggedInitialPointerInRoot = null
                                    isTimerTaskSettling = false
                                }
                            } else {
                                draggedIndex = null
                                currentHoverIndex = null
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                draggedInitialPointerInRoot = null
                            }
                        },
                        onDragCancel = onDragCancel@ {
                            if (isTimerTaskSettling) return@onDragCancel
                            val startIdx = draggedIndex
                            if (startIdx != null) {
                                isTimerTaskSettling = true
                                timerTaskSettleJob = scope.launch {
                                    animateDragToSettle(
                                        currentOffset = dragOffsetY,
                                        targetOffset = 0f,
                                        springSpec = getReorderSpringSpec(context)
                                    ) { animatedY ->
                                        dragOffsetY = animatedY
                                    }
                                    withContext(Dispatchers.IO) {
                                        repository.reorderFocusTasks(localModeTasks)
                                    }
                                    draggedIndex = null
                                    currentHoverIndex = null
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    draggedInitialPointerInRoot = null
                                    isTimerTaskSettling = false
                                }
                            } else {
                                draggedIndex = null
                                currentHoverIndex = null
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                draggedInitialPointerInRoot = null
                            }
                        },
                        onDragDelta = onDragDelta@ { dx: Float, _: Float ->
                            if (isTimerTaskSettling) return@onDragDelta
                            dragOffsetX += dx
                        },
                        onVerticalDragDelta = onVerticalDragDelta@ { deltaY: Float ->
                            if (isTimerTaskSettling) return@onVerticalDragDelta
                            dragOffsetY += deltaY
                            val currentIdx = draggedIndex ?: return@onVerticalDragDelta
                            val effectiveHeight = if (measuredTimerItemHeightPx > 0f) measuredTimerItemHeightPx else itemHeightPx
                            val targetIdx = calculateSlotTargetIndex(currentIdx, dragOffsetY, effectiveHeight, localModeTasks.size)
                            if (targetIdx != currentIdx && targetIdx in localModeTasks.indices) {
                                val reordered = localModeTasks.toMutableList()
                                val direction = if (targetIdx > currentIdx) 1 else -1
                                var iterIdx = currentIdx
                                while (iterIdx != targetIdx) {
                                    val nextIdx = iterIdx + direction
                                    Collections.swap(reordered, iterIdx, nextIdx)
                                    iterIdx = nextIdx
                                }
                                val slotsMoved = targetIdx - currentIdx
                                val itemHeightPx = effectiveHeight
                                dragOffsetY -= slotsMoved * itemHeightPx
                                draggedIndex = targetIdx
                                currentHoverIndex = targetIdx
                                localModeTasks = reordered
                                HapticHelper.tick(context)
                            }
                        }
                    )
                }
            }

            item(key = "timer_add_preset_button") {
                // 8. Universal Add Preset Trigger Button (+ Add)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick(scaleDown = 0.96f) {
                            HapticHelper.click(context)
                            taskToEdit = null
                            showTaskDialog = true
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = activeAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item(key = "timer_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Modal Bottom Sheet: Expanded to "Silent Focus" button (~54% screen height) with Apple-Style Dual-Wheel Picker
        if (showTaskDialog) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var taskTitle by remember { mutableStateOf(taskToEdit?.title ?: "") }
            var pickerMode by remember { mutableStateOf(WheelPickerMode.HOUR_MINUTE) }
            var isSubmitting by remember { mutableStateOf(false) }

            fun dismissModal() {
                if (!isSubmitting) {
                    isSubmitting = true
                    sheetState.animateDismiss(scope) {
                        showTaskDialog = false
                        taskToEdit = null
                        livePickerSeconds = null
                    }
                }
            }

            // Both columns initialize at 00
            var selectedLeft by remember { mutableIntStateOf(0) }
            var selectedRight by remember { mutableIntStateOf(0) }

            // Dynamic range based on app's 12H IST vs 24H UTC setting
            val is24HourGlobal = userSettings.timeFormat24Hour
            val leftItems = remember(pickerMode, is24HourGlobal) {
                if (pickerMode == WheelPickerMode.HOUR_MINUTE) {
                    if (is24HourGlobal) (0..23).toList() else (0..12).toList()
                } else {
                    (0..59).toList()
                }
            }
            val rightItems = remember(pickerMode) { (0..59).toList() }

            // Live timer calculation from dual-wheel picker
            val calculatedSeconds = remember(pickerMode, selectedLeft, selectedRight) {
                if (pickerMode == WheelPickerMode.HOUR_MINUTE) {
                    (selectedLeft * 60 + selectedRight) * 60
                } else {
                    selectedLeft * 60 + selectedRight
                }
            }

            // Sync with live timer numerals on the screen above the drawer
            LaunchedEffect(calculatedSeconds) {
                livePickerSeconds = calculatedSeconds
            }

            // Optimized height for balanced vertical spacing (~52% screen height)
            val modalHeight = (configuration.screenHeightDp * 0.52f).coerceAtLeast(420f).dp

            ModalBottomSheet(
                onDismissRequest = {
                    dismissModal()
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = null
            ) {
                // Standardized modal container with sticky bottom action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(modalHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .padding(bottom = 68.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Subtle top drag handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 2.dp, bottom = 2.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )

                        // Header Row: Minimalist Title exclusively "New", Dismiss & Delete Icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (taskToEdit == null) "New" else "Edit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        dismissModal()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Activity Name Input Field: Simple "Name" placeholder in grey text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(activeAccentColor),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (taskTitle.isEmpty()) {
                                        Text(
                                            text = "Name",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        // Centered Duration Mode Switcher ("Hour / Minute" selector button)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 144.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(activeAccentColor.copy(alpha = 0.12f))
                                    .border(BorderStroke(1.2.dp, activeAccentColor.copy(alpha = 0.45f)), RoundedCornerShape(18.dp))
                                    .bounceClick(scaleDown = 0.95f) {
                                        HapticHelper.click(context)
                                        pickerMode = if (pickerMode == WheelPickerMode.HOUR_MINUTE) {
                                            WheelPickerMode.MINUTE_SECOND
                                        } else {
                                            WheelPickerMode.HOUR_MINUTE
                                        }
                                        selectedLeft = 0
                                        selectedRight = 0
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = pickerMode,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) togetherWith
                                            fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
                                    },
                                    label = "timeUnitFlip"
                                ) { mode ->
                                    Text(
                                        text = if (mode == WheelPickerMode.HOUR_MINUTE) "Hour / Minute" else "Minute / Second",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = activeAccentColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Dual-Wheel Picker (Apple iOS style) with continuous scrolling & blurred/faded numbers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Wheel Column (Continuous scrolling wheel interface)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                key(pickerMode, leftItems.size) {
                                    AppleWheelColumn(
                                        items = leftItems,
                                        selectedIndex = selectedLeft,
                                        onSelectedIndexChanged = { selectedLeft = it },
                                        accentColor = activeAccentColor,
                                        itemHeightDp = 38.dp,
                                        visibleCount = 5
                                    )
                                }
                            }

                            // Colon separator
                            Text(
                                text = ":",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeAccentColor,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )

                            // Right Wheel Column (Continuous scrolling wheel interface)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                key(pickerMode, rightItems.size) {
                                    AppleWheelColumn(
                                        items = rightItems,
                                        selectedIndex = selectedRight,
                                        onSelectedIndexChanged = { selectedRight = it },
                                        accentColor = activeAccentColor,
                                        itemHeightDp = 38.dp,
                                        visibleCount = 5
                                    )
                                }
                            }
                        }
                    }

                    // Sticky Bottom-Anchored Primary Action: Prominent full-width "Save" button fixed at the very bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSubmitting) activeAccentColor.copy(alpha = 0.6f) else activeAccentColor)
                                .border(1.dp, activeAccentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                .bounceClick(
                                    scaleDown = 0.96f,
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) return@bounceClick
                                    isSubmitting = true
                                    val name = taskTitle.ifBlank {
                                        when (selectedType) {
                                            PomodoroType.FOCUS -> "Deep Focus"
                                            PomodoroType.SHORT_BREAK -> "Break"
                                            PomodoroType.LONG_BREAK -> "Flow"
                                        }
                                    }
                                    val finalDurationSeconds = if (calculatedSeconds > 0) calculatedSeconds else 60
                                    val finalDurationMinutes = (finalDurationSeconds + 59) / 60
                                    HapticHelper.success(context)
                                    scope.launch {
                                        try {
                                            val savedTask: FocusTask = if (taskToEdit == null) {
                                                repository.addFocusTask(
                                                    title = name,
                                                    durationMinutes = finalDurationMinutes,
                                                    sessionType = selectedType.name
                                                )
                                                FocusTask(
                                                    id = "task_${System.currentTimeMillis()}",
                                                    title = name,
                                                    durationMinutes = finalDurationMinutes,
                                                    sessionType = selectedType.name
                                                )
                                            } else {
                                                val updated = taskToEdit!!.copy(
                                                    title = name,
                                                    durationMinutes = finalDurationMinutes,
                                                    sessionType = selectedType.name
                                                )
                                                repository.updateFocusTask(updated)
                                                updated
                                            }
                                            activeFocusTask = savedTask
                                            PomodoroService.updateTaskTitle(name)
                                            PomodoroService.resetTimer(context, finalDurationSeconds, name, sessionType = selectedType)

                                            // Save-Triggered Counter-Clockwise Ring Fill Animation running concurrently
                                            launch {
                                                ringFillAnim.snapTo(0f)
                                                ringFillAnim.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                                                )
                                            }

                                            // Trigger immediate, fluid slide-down exit animation of the modal
                                            sheetState.smoothDismiss()
                                        } finally {
                                            // Cleanly return to the underlying view only after dismissal animation completes
                                            showTaskDialog = false
                                            taskToEdit = null
                                            livePickerSeconds = null
                                            isSubmitting = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Ambient Audio Selector: Half-Screen Bottom Sheet with Smooth Exit Animation
        if (showAudioSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val halfScreenHeight = (configuration.screenHeightDp * 0.50f).dp

            ModalBottomSheet(
                onDismissRequest = {
                    sheetState.animateDismiss(scope) {
                        showAudioSheet = false
                    }
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(halfScreenHeight)
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 2.dp)) {
                                Text(
                                    text = "Ambient Soundscapes",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Curated binaural & ambient focus frequencies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    sheetState.animateDismiss(scope) {
                                        showAudioSheet = false
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

                        AmbientSoundTrack.values().forEach { track ->
                            val isTrackActive = ambientPlayer.getActiveTrack() == track
                            SquircleCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp,
                                containerColor = if (isTrackActive) activeAccentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    HapticHelper.click(context)
                                    if (track == AmbientSoundTrack.NONE) {
                                        ambientPlayer.stop()
                                    } else {
                                        ambientPlayer.playTrack(track)
                                    }
                                    // Smooth downward dismissal animation on track selection
                                    scope.launch {
                                        sheetState.hide()
                                        showAudioSheet = false
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        WaveformIcon(
                                            color = if (isTrackActive) activeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            size = 18.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = track.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isTrackActive) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = track.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    if (isTrackActive) {
                                        CheckmarkCheckIcon(color = activeAccentColor, size = 18.dp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * Seamless Sliding Pill Segmented Switcher for Focus Modes (Focus, Break, Flow).
 */
@Composable
fun FocusModeSegmentedControl(
    selectedType: PomodoroType,
    onTypeSelected: (PomodoroType) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = remember {
        listOf(
            "Focus" to PomodoroType.FOCUS,
            "Break" to PomodoroType.SHORT_BREAK,
            "Flow" to PomodoroType.LONG_BREAK
        )
    }
    val context = LocalContext.current
    val currentOnTypeSelected by rememberUpdatedState(onTypeSelected)
    val selectedIndex = modes.indexOfFirst { it.second == selectedType }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(4.dp)
    ) {
        val tabCount = modes.size.coerceAtLeast(1)
        val tabWidth = maxWidth / tabCount
        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // Active Indicator Pill with continuous spring tracking
        val targetPillOffsetPx = selectedIndex * tabWidthPx
        val animatedPillOffsetPx by animateFloatAsState(
            targetValue = targetPillOffsetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "pillOffset"
        )
        val pillOffsetDp = with(density) { animatedPillOffsetPx.toDp() }

        // Sliding Active Indicator Pill
        Box(
            modifier = Modifier
                .offset(x = pillOffsetDp)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        )

        // Tab Text Labels on top
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { (label, type) ->
                val isSelected = (selectedType == type)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (selectedType != type) {
                                HapticHelper.selection(context)
                                currentOnTypeSelected(type)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Mode selector for Apple-Style Dual-Wheel Duration Picker.
 */
enum class WheelPickerMode {
    HOUR_MINUTE,
    MINUTE_SECOND
}

/**
 * Apple-Style Vertical Scrolling Cylinder Wheel Column with Infinite Looping Physics (Cupertino-style UIPickerView).
 */
@Composable
internal fun AppleWheelColumn(
    items: List<Int>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    itemHeightDp: androidx.compose.ui.unit.Dp = 38.dp,
    visibleCount: Int = 5
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

    // Calculate center raw index and normalized index based on scroll position
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

    // Auto-recenter quietly when scroll stops to ensure truly infinite scrolling in both directions
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
        // Apple-style Center Selection Lens
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
                        fontSize = 25.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                    )
                }
            }
        }

        // Top subtle gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp * 1.5f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                    )
                )
        )

        // Bottom subtle gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp * 1.5f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
    }
}

/**
 * Minimalist Focus Activity Item with fluid horizontal gestural controls:
 * - Minimalist visual styling: Clean title, active indicator dot, and clean unadorned duration text.
 * - Edit pencil icon and static duration pills/banners completely removed.
 * - Swipe-to-Edit (Right Swipe): Smoothly reveals Action Blue edit background, haptic tick on threshold,
 *   bounces back, and opens the activity editor.
 * - Swipe-to-Delete (Left Swipe): Smoothly reveals Urgent Red delete background, heavy haptic on threshold,
 *   slides out, and executes task deletion.
 * - Long press vertical drag reordering is preserved on the container.
 * - Tap to select task when resting.
 */
private enum class SwipeAction {
    NONE,
    EDIT,
    DELETE
}

/**
 * Minimalist Focus Activity Item with strictly isolated horizontal gestural controls:
 * - Minimalist visual styling: Clean title, active indicator dot, and clean unadorned duration text.
 * - Edit pencil icon and static duration pills/banners completely removed.
 * - Swipe-to-Edit (Right Swipe): Strictly locks to Action Blue edit action, springs back, and triggers onEdit.
 * - Swipe-to-Delete (Left Swipe): Strictly locks to Urgent Red delete action, slides off, and triggers onDelete.
 * - Actions are strictly isolated: no overlapping states, no dual activation, no direction flipping.
 * - Long press vertical drag reordering is preserved on the container.
 * - Tap to select task when resting.
 */
@Composable
private fun FocusActivitySwipeableItem(
    task: FocusTask,
    isSelected: Boolean,
    isThisItemDragged: Boolean,
    dragOffsetX: Float = 0f,
    dragOffsetY: Float,
    nonDraggedDisplacement: Float = 0f,
    activeAccentColor: Color,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragStartWithOffset: ((Offset, Rect) -> Unit)? = null,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onVerticalDragDelta: (Float) -> Unit,
    onDragDelta: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 130.dp.toPx() }

    var itemBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val swipeOffsetX = remember(task.id) { Animatable(0f) }
    val shakeRotation = remember(task.id) { Animatable(0f) }
    var activeSwipeAction by remember(task.id) { mutableStateOf(SwipeAction.NONE) }
    var hasTriggeredHaptic by remember(task.id) { mutableStateOf(false) }
    var isSwipingHorizontal by remember(task.id) { mutableStateOf(false) }

    val springSpec = remember(context) { getReorderSpringSpec<Float>(context) }

    val dragScale by animateFloatAsState(
        targetValue = if (isThisItemDragged) 1.03f else 1f,
        animationSpec = springSpec,
        label = "taskDragScale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isThisItemDragged) with(density) { 16.dp.toPx() } else 0f,
        animationSpec = springSpec,
        label = "taskDragElevation"
    )

    val cardBg by animateColorAsState(
        targetValue = if (isSelected) activeAccentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "cardBg"
    )
    val cardBorder = if (isSelected) {
        BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isThisItemDragged) 100f else 1f)
            .onGloballyPositioned { coords ->
                itemBoundsInRoot = coords.boundsInRoot()
            }
            .graphicsLayer {
                translationX = if (isThisItemDragged) dragOffsetX else 0f
                translationY = if (isThisItemDragged) dragOffsetY else nonDraggedDisplacement
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation
                shape = RoundedCornerShape(18.dp)
                clip = false
            }
    ) {
        // Swipe Background Action Indicators Layer (Strictly isolated by activeSwipeAction)
        if (activeSwipeAction != SwipeAction.NONE && abs(swipeOffsetX.value) > 1f) {
            val isEdit = activeSwipeAction == SwipeAction.EDIT
            val actionBgColor = if (isEdit) Color(0xFF2563EB) else StatusUrgent
            val actionProgress = (abs(swipeOffsetX.value) / thresholdPx).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(actionBgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isEdit) Alignment.CenterStart else Alignment.CenterEnd
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
                    if (isEdit) {
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

        var isDraggingCard by remember(task.id) { mutableStateOf(false) }
        var lastDragEndTime by remember(task.id) { mutableStateOf(0L) }

        val currentOnDragStart by rememberUpdatedState(onDragStart)
        val currentOnDragStartWithOffset by rememberUpdatedState(onDragStartWithOffset)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)
        val currentOnDragCancel by rememberUpdatedState(onDragCancel)
        val currentOnDragDelta by rememberUpdatedState(onDragDelta)
        val currentOnVerticalDragDelta by rememberUpdatedState(onVerticalDragDelta)

        // Minimalist Foreground Surface (with strictly isolated Swipe-to-Edit & Swipe-to-Delete and full-surface drag)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = if (isThisItemDragged) 0f else swipeOffsetX.value
                    rotationZ = shakeRotation.value
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            isDraggingCard = true
                            triggerPickupHaptic(context, hapticFeedback)
                            currentOnDragStartWithOffset?.invoke(offset, itemBoundsInRoot) ?: currentOnDragStart()
                        },
                        onDragEnd = {
                            isDraggingCard = false
                            lastDragEndTime = System.currentTimeMillis()
                            currentOnDragEnd()
                        },
                        onDragCancel = {
                            isDraggingCard = false
                            lastDragEndTime = System.currentTimeMillis()
                            currentOnDragCancel()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDragDelta?.invoke(dragAmount.x, dragAmount.y)
                            currentOnVerticalDragDelta(dragAmount.y)
                        }
                    )
                }
                .pointerInput(task.id, isThisItemDragged) {
                    if (isThisItemDragged) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!isDraggingCard) {
                                isSwipingHorizontal = true
                                hasTriggeredHaptic = false
                                activeSwipeAction = SwipeAction.NONE
                            }
                        },
                        onDragEnd = {
                            if (isDraggingCard) return@detectHorizontalDragGestures
                            coroutineScope.launch {
                                val currentAction = activeSwipeAction
                                val offset = swipeOffsetX.value
                                when (currentAction) {
                                    SwipeAction.EDIT -> {
                                        if (offset >= thresholdPx) {
                                            HapticHelper.click(context)
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            onEdit()
                                        } else {
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                        }
                                    }
                                    SwipeAction.DELETE -> {
                                        if (offset <= -thresholdPx) {
                                            HapticHelper.heavy(context)
                                            onDelete()
                                            // Designated tactile shake feedback animation
                                            coroutineScope.launch {
                                                shakeRotation.animateTo(-2f, tween(35))
                                                shakeRotation.animateTo(1.8f, tween(35))
                                                shakeRotation.animateTo(-1.2f, tween(35))
                                                shakeRotation.animateTo(0f, tween(35))
                                            }
                                            coroutineScope.launch {
                                                val base = swipeOffsetX.value
                                                swipeOffsetX.animateTo(base - 18f, tween(35))
                                                swipeOffsetX.animateTo(base + 14f, tween(35))
                                                swipeOffsetX.animateTo(base - 10f, tween(35))
                                                swipeOffsetX.animateTo(base + 6f, tween(35))
                                                swipeOffsetX.animateTo(-1200f, tween(160))
                                            }
                                        } else {
                                            swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                        }
                                    }
                                    SwipeAction.NONE -> {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                                activeSwipeAction = SwipeAction.NONE
                                isSwipingHorizontal = false
                                hasTriggeredHaptic = false
                            }
                        },
                        onDragCancel = {
                            if (isDraggingCard) return@detectHorizontalDragGestures
                            coroutineScope.launch {
                                swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                activeSwipeAction = SwipeAction.NONE
                                isSwipingHorizontal = false
                                hasTriggeredHaptic = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isDraggingCard || isThisItemDragged) return@detectHorizontalDragGestures

                            // Lock direction once initial movement passes slop (10px threshold)
                            if (activeSwipeAction == SwipeAction.NONE) {
                                val testDelta = swipeOffsetX.value + dragAmount
                                if (testDelta > 10f) {
                                    activeSwipeAction = SwipeAction.EDIT
                                } else if (testDelta < -10f) {
                                    activeSwipeAction = SwipeAction.DELETE
                                } else {
                                    return@detectHorizontalDragGestures
                                }
                            }

                            change.consume()

                            val rawNext = swipeOffsetX.value + dragAmount
                            val clamped = when (activeSwipeAction) {
                                SwipeAction.EDIT -> {
                                    // Strictly positive: cannot cross 0 to negative
                                    if (rawNext < 0f) 0f
                                    else if (rawNext <= maxDragPx) rawNext
                                    else maxDragPx + (rawNext - maxDragPx) * 0.25f
                                }
                                SwipeAction.DELETE -> {
                                    // Strictly negative: cannot cross 0 to positive
                                    if (rawNext > 0f) 0f
                                    else if (rawNext >= -maxDragPx) rawNext
                                    else -maxDragPx + (rawNext - (-maxDragPx)) * 0.25f
                                }
                                SwipeAction.NONE -> 0f
                            }

                            coroutineScope.launch {
                                swipeOffsetX.snapTo(clamped)
                            }

                            val crossed = when (activeSwipeAction) {
                                SwipeAction.EDIT -> clamped >= thresholdPx
                                SwipeAction.DELETE -> clamped <= -thresholdPx
                                SwipeAction.NONE -> false
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
                .clickable {
                    val isCurrentlyDragged = isThisItemDragged
                    val timeSinceDrag = System.currentTimeMillis() - lastDragEndTime
                    if (!isCurrentlyDragged && System.currentTimeMillis() - lastDragEndTime > 300L) {
                        if (!isDraggingCard && !isSwipingHorizontal && !isThisItemDragged && timeSinceDrag > 300L && abs(swipeOffsetX.value) < 5f) {
                            onSelect()
                        }
                    }
                },
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = cardBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Morphing status indicator (Active Dot / Grab Handle ≡) & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MorphingLeadingActionIcon(
                        isDragging = isThisItemDragged,
                        dragHandleColor = activeAccentColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReorderGrabHandleIcon(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                size = 14.dp
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(activeAccentColor)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                // Right: Minimalist unstyled duration text (strictly NO badge, NO capsule, NO pill, NO border)
                Text(
                    text = "${task.durationMinutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) activeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

