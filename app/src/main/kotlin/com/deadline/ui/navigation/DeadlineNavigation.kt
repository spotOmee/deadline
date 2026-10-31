package com.deadline.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.service.PomodoroService
import com.deadline.ui.components.BarChartIcon
import com.deadline.ui.components.FolderLayersIcon
import com.deadline.ui.components.HabitsSlidersIcon
import com.deadline.ui.components.HapticHelper
import com.deadline.ui.components.NotesDocumentIcon
import com.deadline.ui.components.StopwatchIcon
import com.deadline.ui.theme.AmberGradient
import com.deadline.ui.theme.BrandAmberStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DeadlineTab {
    STATS,
    HABITS,
    TIMER,
    PROJECTS,
    NOTES
}

/**
 * Resolves the designated startup screen navigation destination from preference strings or indices.
 * Fallback defaults to DeadlineTab.TIMER if unknown or unconfigured.
 */
fun resolveStartupTab(screenKey: String?): DeadlineTab {
    if (screenKey.isNullOrBlank()) return DeadlineTab.TIMER
    return when (screenKey.uppercase().trim()) {
        "TIMER" -> DeadlineTab.TIMER
        "STATS" -> DeadlineTab.STATS
        "HABITS" -> DeadlineTab.HABITS
        "PROJECTS", "TASKS" -> DeadlineTab.PROJECTS
        "NOTES" -> DeadlineTab.NOTES
        "0" -> DeadlineTab.STATS
        "1" -> DeadlineTab.HABITS
        "2" -> DeadlineTab.TIMER
        "3" -> DeadlineTab.PROJECTS
        "4" -> DeadlineTab.NOTES
        else -> DeadlineTab.TIMER
    }
}

/**
 * Evaluates startup tab for navigation graph launch routing.
 */
fun evaluateStartupTab(key: String): DeadlineTab = resolveStartupTab(key)

@Composable
fun DeadlineBottomBar(
    currentTab: DeadlineTab,
    onTabSelected: (DeadlineTab) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    // Persistent timer state detection: active, paused, or scheduled with time remaining > 0
    val remainingSeconds by PomodoroService.remainingSeconds.collectAsState()
    val isRunning by PomodoroService.isRunning.collectAsState()
    val hasTimeRemaining = remainingSeconds > 0

    // Continuous scroll progress across the 5 main sections [0f..4f]
    val continuousPage = if (pagerState != null) {
        (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (DeadlineTab.entries.size - 1).toFloat())
    } else {
        currentTab.ordinal.toFloat()
    }

    val statsWeight = (1f - abs(continuousPage - 0f)).coerceIn(0f, 1f)
    val habitsWeight = (1f - abs(continuousPage - 1f)).coerceIn(0f, 1f)
    val timerWeight = (1f - abs(continuousPage - 2f)).coerceIn(0f, 1f)
    val tasksWeight = (1f - abs(continuousPage - 3f)).coerceIn(0f, 1f)
    val notesWeight = (1f - abs(continuousPage - 4f)).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main bottom bar capsule
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slot 1: Stats
                NavTabItem(
                    title = "Stats",
                    activeWeight = statsWeight,
                    onClick = {
                        currentOnTabSelected(DeadlineTab.STATS)
                    }
                ) { color, isSel ->
                    BarChartIcon(color = color, isActive = isSel)
                }

                // Slot 2: Habits
                NavTabItem(
                    title = "Habits",
                    activeWeight = habitsWeight,
                    onClick = {
                        currentOnTabSelected(DeadlineTab.HABITS)
                    }
                ) { color, isSel ->
                    HabitsSlidersIcon(color = color, isActive = isSel)
                }

                // Slot 3: Center spacer to accommodate elevated Timer FAB
                Spacer(modifier = Modifier.size(56.dp))

                // Slot 4: Projects / Tasks
                NavTabItem(
                    title = "Tasks",
                    activeWeight = tasksWeight,
                    onClick = {
                        currentOnTabSelected(DeadlineTab.PROJECTS)
                    }
                ) { color, isSel ->
                    FolderLayersIcon(color = color, isActive = isSel)
                }

                // Slot 5: Notes
                NavTabItem(
                    title = "Notes",
                    activeWeight = notesWeight,
                    onClick = {
                        currentOnTabSelected(DeadlineTab.NOTES)
                    }
                ) { color, isSel ->
                    NotesDocumentIcon(color = color, isActive = isSel)
                }
            }
        }

        // Center Raised Floating Action Tab (Timer) with dynamic spring scaling, forced active fill & running animation
        val isTimerRunning = isRunning && hasTimeRemaining
        val isForcedActive = hasTimeRemaining
        val isVisuallyActive = isForcedActive || timerWeight > 0.05f
        val timerScale = 1f + 0.08f * (if (isForcedActive) 1f else timerWeight)
        val timerElevation = if (isForcedActive) 16.dp else (6 + 10 * timerWeight).dp

        val spotColor = Color.Black.copy(alpha = 0.35f)
        val ambientColor = Color.Black.copy(alpha = 0.15f)

        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(58.dp)
                .graphicsLayer {
                    scaleX = timerScale
                    scaleY = timerScale
                }
                .shadow(
                    elevation = timerElevation,
                    shape = CircleShape,
                    spotColor = spotColor,
                    ambientColor = ambientColor
                )
                .clip(CircleShape)
                .then(
                    if (isVisuallyActive) {
                        Modifier.background(AmberGradient)
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    }
                )
                .border(
                    width = if (isForcedActive) 1.5.dp else if (timerWeight > 0.05f) (1.5 * timerWeight).dp else 1.dp,
                    color = if (isForcedActive) {
                        Color.White.copy(alpha = 0.70f)
                    } else if (timerWeight > 0.05f) {
                        Color.White.copy(alpha = 0.6f * timerWeight)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    currentOnTabSelected(DeadlineTab.TIMER)
                },
            contentAlignment = Alignment.Center
        ) {
            StopwatchIcon(
                color = if (isVisuallyActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                size = if (isVisuallyActive) 26.dp else 24.dp,
                isActive = isVisuallyActive,
                isFilled = isForcedActive,
                isAnimating = isTimerRunning
            )
        }
    }
}

@Composable
private fun NavTabItem(
    title: String,
    activeWeight: Float,
    onClick: () -> Unit,
    icon: @Composable (color: Color, isSelected: Boolean) -> Unit
) {
    val activeColor = BrandAmberStart
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = lerp(inactiveColor, activeColor, activeWeight)
    val isSelected = activeWeight > 0.5f
    val scale = 1f + 0.08f * activeWeight

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (activeWeight > 0.05f) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f * activeWeight)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        icon(color, isSelected)
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = color
        )
    }
}
