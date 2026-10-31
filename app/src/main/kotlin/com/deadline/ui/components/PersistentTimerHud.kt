package com.deadline.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.deadline.data.local.PomodoroType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * In-App Persistent Navigation HUD:
 * Streamlined, persistent compact overlay displayed when navigating away from the Timer screen.
 *
 * Layout Structure:
 * - Left: Small status circle indicating live timer progress and state
 * - Center: Active task name centered (distraction-free, no categorical status labels)
 * - Right: Playback control (Pause/Play) alongside the running timer
 *
 * Dismissal Gesture:
 * - Fluid swipe-up gesture dismisses the overlay from the screen
 */
@Composable
fun PersistentTimerHud(
    visible: Boolean,
    remainingSeconds: Int,
    totalDurationSeconds: Int,
    isRunning: Boolean,
    taskTitle: String,
    sessionType: PomodoroType,
    onHudClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePlayPause: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ),
        exit = fadeOut(tween(160)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ),
        modifier = modifier.zIndex(100f)
    ) {
        val (modeAccent, modeGlow) = when (sessionType) {
            PomodoroType.FOCUS -> Pair(Color(0xFFFF9500), Color(0xFFFFA726))
            PomodoroType.SHORT_BREAK -> Pair(Color(0xFF22C55E), Color(0xFF00E676))
            PomodoroType.LONG_BREAK -> Pair(Color(0xFFA855F7), Color(0xFFC084FC))
        }

        // Pulse animation for active running state
        val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
        val pulseScale by if (isRunning) {
            infiniteTransition.animateFloat(
                initialValue = 0.90f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
        } else {
            remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        }

        val hours = remainingSeconds / 3600
        val mins = (remainingSeconds % 3600) / 60
        val secs = remainingSeconds % 60
        val formattedTime = if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        val effectiveTotal = if (totalDurationSeconds > 0) totalDurationSeconds else 1
        val progressFraction = (remainingSeconds.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)

        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { 36.dp.toPx() }
        val offsetYAnim = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(visible) {
            if (visible) {
                offsetYAnim.snapTo(0f)
            }
        }

        val displayTitle = taskTitle.ifBlank {
            when (sessionType) {
                PomodoroType.FOCUS -> "Focus"
                PomodoroType.SHORT_BREAK -> "Break"
                PomodoroType.LONG_BREAK -> "Flow"
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .offset { IntOffset(0, offsetYAnim.value.roundToInt().coerceAtMost(0)) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetYAnim.value < -dismissThresholdPx) {
                                    offsetYAnim.animateTo(
                                        targetValue = -dismissThresholdPx * 4,
                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                    )
                                    onDismiss?.invoke()
                                } else {
                                    offsetYAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetYAnim.animateTo(0f)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount < 0 || offsetYAnim.value < 0) {
                                change.consume()
                                coroutineScope.launch {
                                    offsetYAnim.snapTo((offsetYAnim.value + dragAmount).coerceAtMost(0f))
                                }
                            }
                        }
                    )
                }
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = modeGlow.copy(alpha = 0.35f),
                    ambientColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHudClick
                ),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, modeAccent.copy(alpha = 0.30f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Small status circle with progress ring and pulsing center dot
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val strokeWidth = 2.5.dp.toPx()
                        // Background track
                        drawCircle(
                            color = modeAccent.copy(alpha = 0.22f),
                            style = Stroke(width = strokeWidth)
                        )
                        // Active countdown progress arc
                        if (progressFraction > 0f) {
                            val activeSweep = -360f * progressFraction
                            drawArc(
                                color = modeAccent,
                                startAngle = -90f,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Inner status dot
                    Box(
                        modifier = Modifier
                            .size(if (isRunning) (8 * pulseScale).dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) modeAccent else modeAccent.copy(alpha = 0.5f))
                    )
                }

                // Center: Active task name centered (distraction-free, zero categorical badges)
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                // Right: Playback control (Pause/Play) alongside running timer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-0.5).sp
                        ),
                        color = modeAccent
                    )

                    if (onTogglePlayPause != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(modeAccent.copy(alpha = 0.15f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onTogglePlayPause
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRunning) {
                                GeometricPauseIcon(color = modeAccent, size = 12.dp)
                            } else {
                                GeometricPlayIcon(color = modeAccent, size = 12.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
