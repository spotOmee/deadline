package com.deadline.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deadline.data.local.DeadlineRepository
import com.deadline.data.local.Habit
import com.deadline.domain.GlobalTimeManager
import com.deadline.ui.components.AppleHeader
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.SquircleCard
import com.deadline.ui.theme.BrandAmberStart
import com.deadline.ui.theme.BreakGreen
import com.deadline.ui.theme.WorkIndigo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Ultimate Analytics Dashboard & Heatmap:
 * - Hero Section: Dynamic, color-coded Circular Progress Rings for Focus (Amber),
 *   Break (Green), and Flow (Poetry/Purple) states with live numerical counter animations.
 * - True Consistency Heatmap: Active heatmap grid with a compact, subtle, grey month header,
 *   mapping color intensity dynamically based on habit completion and focus density.
 * - Scrollable Modular Grid: Soft-edged surface cards with rounded corners (Material 3),
 *   logically grouped into Habit Consistency -> Productivity Breakdown -> Recent Achievements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repository: DeadlineRepository,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory(repository)),
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.analyticsUiState.collectAsStateWithLifecycle()
    val userSettings by repository.userSettings.collectAsState()
    val habits by repository.habits.collectAsState(initial = emptyList())
    val todayCompletions by repository.todayCompletions.collectAsState()
    val monthHabitCompletions by repository.monthHabitCompletions.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top App Bar: Avatar & Title "Analytics"
        AppleHeader(
            title = "Analytics",
            avatarUri = userSettings.avatarUri,
            onProfileClick = onOpenProfile,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Dynamic Circular Progress Hub (nested Circular Progress Rings for Focus, Break, Flow)
            item {
                DynamicCircularProgressHub(
                    uiState = uiState
                )
            }

            // 2. True Consistency Heatmap Card (Compact, subtle grey month header, dynamic density)
            item {
                TrueConsistencyHeatmapCard(
                    monthlyFocusMinutes = uiState.monthlyActivityMinutes,
                    monthHabitCompletions = monthHabitCompletions,
                    habits = habits,
                    timeFormat24Hour = userSettings.timeFormat24Hour
                )
            }

            // 3. Productivity Breakdown Grid
            item {
                ProductivityBreakdownGrid(uiState = uiState)
            }

            // 4. Recent Achievements Section
            item {
                RecentAchievementsSection(uiState = uiState)
            }

            item {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}

/**
 * Dynamic Circular Progress Hub:
 * Nested Circular Progress Rings for Focus (Amber), Break (Green), and Flow (Purple) states.
 * Rings initialize at zero and animate smoothly to reflect real-time progress.
 */
@Composable
private fun DynamicCircularProgressHub(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    var hasAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasAppeared = true
    }

    val focusTarget = if (uiState.focusTargetMinutes > 0) uiState.focusTargetMinutes else 100
    val breakTarget = if (uiState.breakTargetMinutes > 0) uiState.breakTargetMinutes else 20
    val flowTarget = if (uiState.flowTargetMinutes > 0) uiState.flowTargetMinutes else 30

    val focusProgress = if (hasAppeared) (uiState.totalFocusMinutes.toFloat() / focusTarget.toFloat()).coerceIn(0f, 1f) else 0f
    val breakProgress = if (hasAppeared) (uiState.totalBreakMinutes.toFloat() / breakTarget.toFloat()).coerceIn(0f, 1f) else 0f
    val flowProgress = if (hasAppeared) (uiState.totalFlowMinutes.toFloat() / flowTarget.toFloat()).coerceIn(0f, 1f) else 0f

    val animatedFocusFraction by animateFloatAsState(
        targetValue = focusProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "animatedFocusFraction"
    )
    val animatedBreakFraction by animateFloatAsState(
        targetValue = breakProgress,
        animationSpec = tween(durationMillis = 1000, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "animatedBreakFraction"
    )
    val animatedFlowFraction by animateFloatAsState(
        targetValue = flowProgress,
        animationSpec = tween(durationMillis = 1000, delayMillis = 240, easing = FastOutSlowInEasing),
        label = "animatedFlowFraction"
    )

    val totalActiveMinutes = uiState.totalFocusMinutes + uiState.totalBreakMinutes + uiState.totalFlowMinutes
    val animatedTotalMinutes by animateIntAsState(
        targetValue = if (hasAppeared) totalActiveMinutes else 0,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "animatedTotalMinutes"
    )

    SquircleCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nested concentric rings
                Box(
                    modifier = Modifier.size(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 9.dp.toPx()
                        val gap = 4.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)

                        val rFocus = (size.minDimension / 2f) - (strokeWidth / 2f)
                        val rBreak = rFocus - strokeWidth - gap
                        val rFlow = rBreak - strokeWidth - gap

                        val rings = listOf(
                            Triple(rFocus, Color(0xFFFF9500), animatedFocusFraction),
                            Triple(rBreak, Color(0xFF22C55E), animatedBreakFraction),
                            Triple(rFlow, Color(0xFFA855F7), animatedFlowFraction)
                        )

                        rings.forEach { (radius, color, progress) ->
                            // Background track
                            drawCircle(
                                color = color.copy(alpha = 0.15f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                            // Progress arc
                            if (progress > 0f) {
                                val sweep = -360f * progress
                                drawArc(
                                    color = color,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Center Hub Counter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${animatedTotalMinutes}m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // Legend & Breakdown
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HubStateMetricRow(
                        label = "Focus",
                        minutes = uiState.totalFocusMinutes,
                        targetMinutes = uiState.focusTargetMinutes,
                        sessionsCount = uiState.focusSessionsCount,
                        accentColor = Color(0xFFFF9500)
                    )
                    HubStateMetricRow(
                        label = "Break",
                        minutes = uiState.totalBreakMinutes,
                        targetMinutes = uiState.breakTargetMinutes,
                        sessionsCount = uiState.breakSessionsCount,
                        accentColor = Color(0xFF22C55E)
                    )
                    HubStateMetricRow(
                        label = "Flow",
                        minutes = uiState.totalFlowMinutes,
                        targetMinutes = uiState.flowTargetMinutes,
                        sessionsCount = uiState.flowSessionsCount,
                        accentColor = Color(0xFFA855F7)
                    )
                }
            }
        }
    }
}

@Composable
private fun HubStateMetricRow(
    label: String,
    minutes: Int,
    targetMinutes: Int,
    sessionsCount: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$sessionsCount sessions",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }

        Text(
            text = "${minutes}m",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
    }
}

/**
 * True Consistency Heatmap Card:
 * Active heatmap grid with a compact, subtle, and grey month header.
 * Maps color intensity dynamically based on habit completion and focus session density.
 */
@Composable
private fun TrueConsistencyHeatmapCard(
    monthlyFocusMinutes: Map<String, Int>,
    monthHabitCompletions: Map<String, Set<String>>,
    habits: List<Habit>,
    timeFormat24Hour: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cal = remember(timeFormat24Hour) { GlobalTimeManager.getCurrentCalendar(timeFormat24Hour) }
    val monthTitle = remember(timeFormat24Hour) {
        val sdf = SimpleDateFormat("MMMM", Locale.US).apply {
            timeZone = GlobalTimeManager.getTimeZone(timeFormat24Hour)
        }
        sdf.format(cal.time)
    }

    val daysInMonth = remember(timeFormat24Hour) { cal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val currentDay = remember(timeFormat24Hour) { cal.get(Calendar.DAY_OF_MONTH) }
    val year = remember(timeFormat24Hour) { cal.get(Calendar.YEAR) }
    val month = remember(timeFormat24Hour) { cal.get(Calendar.MONTH) + 1 }

    val daysInPrevMonth = remember(timeFormat24Hour) {
        val prevCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOffset = remember(timeFormat24Hour) {
        val firstCal = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        (firstCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    val totalSlots = firstDayOffset + daysInMonth
    val displayWeeks = (totalSlots + 6) / 7

    SquircleCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Compact, subtle, and grey month header with completion density legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                )

                // Subtle density legend (grey with progressive green indicator dots)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Less",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                    listOf(0.20f, 0.45f, 0.72f, 1.0f).forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BreakGreen.copy(alpha = alpha))
                        )
                    }
                    Text(
                        text = "More",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }

            // 2. Day-of-Week Column Headers (Mon-Sun in compact subtle grey)
            val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayHeaders.forEach { header ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 3. Active Heatmap Grid mapping color intensity dynamically based on completion density
            val capsuleShape = RoundedCornerShape(percent = 50)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (w in 0 until displayWeeks) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0 until 7) {
                            val slotIndex = w * 7 + col

                            if (slotIndex < firstDayOffset) {
                                // Preceding month bleed (muted subtle grey)
                                val bleedDay = daysInPrevMonth - firstDayOffset + 1 + slotIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(capsuleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$bleedDay",
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            } else if (slotIndex < firstDayOffset + daysInMonth) {
                                // Current month active day
                                val dayNumber = slotIndex - firstDayOffset + 1
                                val dateKey = String.format(Locale.US, "%04d-%02d-%02d", year, month, dayNumber)
                                val isToday = dayNumber == currentDay
                                val isFuture = dayNumber > currentDay

                                // Dynamic Completion Density Calculation:
                                // Combines habit completion count and focus session activity
                                val completedHabitsOnDate = monthHabitCompletions.values.count { it.contains(dateKey) }
                                val habitDensity = if (habits.isNotEmpty()) completedHabitsOnDate.toFloat() / habits.size.toFloat() else 0f
                                val focusMinutes = monthlyFocusMinutes[dateKey] ?: 0
                                val focusDensity = (focusMinutes.toFloat() / 50f).coerceIn(0f, 1f)
                                val compositeDensity = maxOf(habitDensity, focusDensity)

                                // Dynamic color intensity scaling based on completion density
                                val cellColor = when {
                                    isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                                    compositeDensity >= 0.75f -> BreakGreen
                                    compositeDensity >= 0.50f -> BreakGreen.copy(alpha = 0.72f)
                                    compositeDensity >= 0.25f -> BreakGreen.copy(alpha = 0.45f)
                                    compositeDensity > 0f -> BreakGreen.copy(alpha = 0.22f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                                }

                                val textColor = when {
                                    compositeDensity >= 0.50f -> Color.Black
                                    isToday || compositeDensity > 0f -> MaterialTheme.colorScheme.onSurface
                                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(capsuleShape)
                                        .background(cellColor)
                                        .then(
                                            if (isToday) Modifier.border(
                                                width = 1.2.dp,
                                                color = BreakGreen,
                                                shape = capsuleShape
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isToday || compositeDensity >= 0.50f) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            } else {
                                // Succeeding month bleed (muted subtle grey)
                                val bleedDay = slotIndex - (firstDayOffset + daysInMonth) + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(capsuleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$bleedDay",
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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

/**
 * Group 2: Productivity Breakdown
 * 2x2 modular grid using soft-edged surface cards with rounded corners.
 */
@Composable
private fun ProductivityBreakdownGrid(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Adherence & Habit Tracker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductivityMetricCard(
                title = "Adherence",
                value = "${uiState.adherencePercent}%",
                subtext = if (uiState.adherencePercent >= 80) "Optimal Continuum" else if (uiState.adherencePercent > 0) "In Progress" else "No Activity",
                accentColor = BrandAmberStart,
                modifier = Modifier.weight(1f)
            )

            ProductivityMetricCard(
                title = "Habit Tracker",
                value = "${uiState.completedHabitsCount}/${uiState.totalHabitsCount}",
                subtext = if (uiState.totalHabitsCount > 0 && uiState.completedHabitsCount >= uiState.totalHabitsCount) "All Complete" else "Daily Target",
                accentColor = BreakGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Pomodoro Focus & Daily Tasks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductivityMetricCard(
                title = "Pomodoro Focus",
                value = "${uiState.completedSessionsCount}/${uiState.dailyGoalSessions}",
                subtext = if (uiState.completedSessionsCount >= uiState.dailyGoalSessions && uiState.dailyGoalSessions > 0) "Goal Reached" else "Target Sessions",
                accentColor = WorkIndigo,
                modifier = Modifier.weight(1f)
            )

            ProductivityMetricCard(
                title = "Daily Tasks",
                value = "${uiState.completedTasksCount}/${uiState.totalTasksCount}",
                subtext = if (uiState.totalTasksCount > 0 && uiState.completedTasksCount >= uiState.totalTasksCount) "Agenda Cleared" else "Tasks Scheduled",
                accentColor = Color(0xFF0EA5E9), // Clean Sky Blue
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Soft-edged modular surface card with rounded corners (Material 3).
 */
@Composable
private fun ProductivityMetricCard(
    title: String,
    value: String,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    SquircleCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Group 3: Recent Achievements
 * Consistency Streak, Success Rate, and Dynamic Milestone Badges.
 */
@Composable
private fun RecentAchievementsSection(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row: Consistency Streak & Success Rate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductivityMetricCard(
                title = "Consistency Streak",
                value = "${uiState.currentStreak}d",
                subtext = if (uiState.currentStreak > 0) "Active Streak" else "Start Today",
                accentColor = Color(0xFFFF9500),
                modifier = Modifier.weight(1f)
            )

            ProductivityMetricCard(
                title = "Success Rate",
                value = "${uiState.successRate}%",
                subtext = if (uiState.successRate >= 80) "High Output" else if (uiState.successRate > 0) "Steady Progress" else "Pending Activity",
                accentColor = BreakGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Milestone Badges Card
        SquircleCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ACTIVE MILESTONES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )

                val milestones = listOf(
                    Triple("Deep Focus Pioneer", uiState.totalFocusMinutes >= 25, Color(0xFFFF9500)),
                    Triple("Streak Keeper", uiState.currentStreak >= 1, Color(0xFF22C55E)),
                    Triple("Habit Architect", uiState.completedHabitsCount >= 1, Color(0xFF0EA5E9)),
                    Triple("Flow Champion", uiState.totalFlowMinutes > 0 || uiState.completedSessionsCount >= 2, Color(0xFFA855F7))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    milestones.take(2).forEach { (title, isEarned, color) ->
                        MilestoneBadgeChip(
                            title = title,
                            isEarned = isEarned,
                            accentColor = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    milestones.drop(2).forEach { (title, isEarned, color) ->
                        MilestoneBadgeChip(
                            title = title,
                            isEarned = isEarned,
                            accentColor = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneBadgeChip(
    title: String,
    isEarned: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isEarned) accentColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isEarned) accentColor.copy(alpha = 0.30f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
                ),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isEarned) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f))
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isEarned) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isEarned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
