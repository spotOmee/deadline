package com.deadline.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Crisp, single-weight SF Symbols / Lucide stroke vector icons.
 * Follows Apple HIG stroke weight standards (1.75dp inactive, 2.25dp active).
 */

@Composable
fun BarChartIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    isActive: Boolean = false
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        // 3 vertical bars: left medium, center tall, right low
        val barW = w * 0.18f
        val gap = w * 0.12f
        val startX = (w - (barW * 3 + gap * 2)) / 2f
        val bottomY = h * 0.85f

        // Bar 1 (left)
        val h1 = h * 0.45f
        drawRoundRect(
            color = color,
            topLeft = Offset(startX, bottomY - h1),
            size = Size(barW, h1),
            cornerRadius = CornerRadius(barW / 2, barW / 2),
            style = if (isActive) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw)
        )

        // Bar 2 (center, tallest)
        val h2 = h * 0.70f
        drawRoundRect(
            color = color,
            topLeft = Offset(startX + barW + gap, bottomY - h2),
            size = Size(barW, h2),
            cornerRadius = CornerRadius(barW / 2, barW / 2),
            style = if (isActive) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw)
        )

        // Bar 3 (right)
        val h3 = h * 0.30f
        drawRoundRect(
            color = color,
            topLeft = Offset(startX + (barW + gap) * 2, bottomY - h3),
            size = Size(barW, h3),
            cornerRadius = CornerRadius(barW / 2, barW / 2),
            style = if (isActive) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw)
        )
    }
}

@Composable
fun HabitsSlidersIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    isActive: Boolean = false
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        // Line 1 (top) with knob on right
        val y1 = h * 0.28f
        drawLine(
            color = color,
            start = Offset(w * 0.15f, y1),
            end = Offset(w * 0.85f, y1),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = w * 0.09f,
            center = Offset(w * 0.65f, y1),
            style = if (isActive) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw)
        )

        // Line 2 (bottom) with knob on left
        val y2 = h * 0.72f
        drawLine(
            color = color,
            start = Offset(w * 0.15f, y2),
            end = Offset(w * 0.85f, y2),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = w * 0.09f,
            center = Offset(w * 0.35f, y2),
            style = if (isActive) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw)
        )
    }
}

@Composable
fun StopwatchIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    isActive: Boolean = true,
    isFilled: Boolean = false,
    isAnimating: Boolean = false,
    fillColor: Color = Color.Unspecified
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp

    val infiniteTransition = rememberInfiniteTransition(label = "stopwatchAnim")
    val animatedAngle by if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "handAngle"
        )
    } else {
        remember { mutableFloatStateOf(60f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        // Top button stem & knob
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.08f),
            end = Offset(w * 0.5f, h * 0.20f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.40f, h * 0.08f),
            end = Offset(w * 0.60f, h * 0.08f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Main stopwatch circular body
        val center = Offset(w * 0.5f, h * 0.60f)
        val radius = w * 0.35f

        // Active Fill inside the circular body
        if (isFilled || (isActive && fillColor != Color.Unspecified)) {
            val resolvedFill = if (fillColor != Color.Unspecified) fillColor else color.copy(alpha = 0.25f)
            drawCircle(
                color = resolvedFill,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = sw)
        )

        // Clock hand pointing according to animatedAngle (0 deg is 12 o'clock, 60 deg is 2 o'clock)
        val angleRad = Math.toRadians((animatedAngle - 90.0)).toFloat()
        val handLen = radius * 0.58f
        val handEnd = Offset(
            center.x + handLen * kotlin.math.cos(angleRad),
            center.y + handLen * kotlin.math.sin(angleRad)
        )

        drawLine(
            color = color,
            start = center,
            end = handEnd,
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Center pivot dot for high-precision stopwatch visual
        drawCircle(
            color = color,
            radius = sw * 1.0f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun FolderLayersIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    isActive: Boolean = false
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        val folderPath = Path().apply {
            moveTo(w * 0.15f, h * 0.32f)
            lineTo(w * 0.40f, h * 0.32f)
            lineTo(w * 0.48f, h * 0.42f)
            lineTo(w * 0.85f, h * 0.42f)
            lineTo(w * 0.85f, h * 0.78f)
            lineTo(w * 0.15f, h * 0.78f)
            close()
        }

        drawPath(
            path = folderPath,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Inner checklist mark
        val checkPath = Path().apply {
            moveTo(w * 0.32f, h * 0.60f)
            lineTo(w * 0.44f, h * 0.68f)
            lineTo(w * 0.68f, h * 0.52f)
        }
        drawPath(
            path = checkPath,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun JournalBookIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    isActive: Boolean = false
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        // Open book contour
        val leftPage = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            cubicTo(w * 0.40f, h * 0.22f, w * 0.25f, h * 0.22f, w * 0.14f, h * 0.26f)
            lineTo(w * 0.14f, h * 0.78f)
            cubicTo(w * 0.25f, h * 0.74f, w * 0.40f, h * 0.74f, w * 0.50f, h * 0.80f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            cubicTo(w * 0.60f, h * 0.22f, w * 0.75f, h * 0.22f, w * 0.86f, h * 0.26f)
            lineTo(w * 0.86f, h * 0.78f)
            cubicTo(w * 0.75f, h * 0.74f, w * 0.60f, h * 0.74f, w * 0.50f, h * 0.80f)
            close()
        }

        drawPath(
            path = leftPage,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = rightPage,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Spine line
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.28f),
            end = Offset(w * 0.50f, h * 0.80f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun WaveformIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.75.dp.toPx()

        val bars = listOf(0.35f, 0.75f, 1.0f, 0.60f, 0.40f)
        val gap = w / (bars.size + 1)

        bars.forEachIndexed { i, factor ->
            val x = gap * (i + 1)
            val barH = h * factor * 0.7f
            drawLine(
                color = color,
                start = Offset(x, (h - barH) / 2),
                end = Offset(x, (h + barH) / 2),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FireIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            cubicTo(w * 0.65f, h * 0.25f, w * 0.85f, h * 0.45f, w * 0.85f, h * 0.68f)
            cubicTo(w * 0.85f, h * 0.88f, w * 0.68f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.32f, h * 0.95f, w * 0.15f, h * 0.88f, w * 0.15f, h * 0.68f)
            cubicTo(w * 0.15f, h * 0.45f, w * 0.35f, h * 0.25f, w * 0.5f, h * 0.05f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
fun NotesDocumentIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    isActive: Boolean = false
) {
    val strokeWidth = if (isActive) 2.25.dp else 1.75.dp
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = strokeWidth.toPx()

        // Document body with folded top-right corner
        val foldSize = w * 0.22f
        val docPath = Path().apply {
            moveTo(w * 0.20f, h * 0.12f)
            lineTo(w * 0.80f - foldSize, h * 0.12f)
            lineTo(w * 0.80f, h * 0.12f + foldSize)
            lineTo(w * 0.80f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.88f)
            close()
        }
        drawPath(
            path = docPath,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Fold crease
        val foldPath = Path().apply {
            moveTo(w * 0.80f - foldSize, h * 0.12f)
            lineTo(w * 0.80f - foldSize, h * 0.12f + foldSize)
            lineTo(w * 0.80f, h * 0.12f + foldSize)
        }
        drawPath(
            path = foldPath,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Three horizontal text lines inside the note document
        val lineStartX = w * 0.32f
        val lineEndX = w * 0.68f
        drawLine(
            color = color,
            start = Offset(lineStartX, h * 0.44f),
            end = Offset(lineEndX, h * 0.44f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(lineStartX, h * 0.58f),
            end = Offset(lineEndX, h * 0.58f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(lineStartX, h * 0.72f),
            end = Offset(w * 0.52f, h * 0.72f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun UndoIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    enabled: Boolean = true
) {
    val drawColor = if (enabled) color else color.copy(alpha = 0.35f)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.dp.toPx()

        // Curved arrow pointing left
        val arrowHead = Path().apply {
            moveTo(w * 0.42f, h * 0.18f)
            lineTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.42f, h * 0.58f)
        }
        drawPath(
            path = arrowHead,
            color = drawColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val arcPath = Path().apply {
            moveTo(w * 0.22f, h * 0.38f)
            cubicTo(w * 0.55f, h * 0.38f, w * 0.82f, h * 0.48f, w * 0.82f, h * 0.78f)
        }
        drawPath(
            path = arcPath,
            color = drawColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun RedoIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    enabled: Boolean = true
) {
    val drawColor = if (enabled) color else color.copy(alpha = 0.35f)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.dp.toPx()

        // Curved arrow pointing right
        val arrowHead = Path().apply {
            moveTo(w * 0.58f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.38f)
            lineTo(w * 0.58f, h * 0.58f)
        }
        drawPath(
            path = arrowHead,
            color = drawColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val arcPath = Path().apply {
            moveTo(w * 0.78f, h * 0.38f)
            cubicTo(w * 0.45f, h * 0.38f, w * 0.18f, h * 0.48f, w * 0.18f, h * 0.78f)
        }
        drawPath(
            path = arcPath,
            color = drawColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun PinIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    isPinned: Boolean = false
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.75.dp.toPx()

        val pinHead = Path().apply {
            moveTo(w * 0.35f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.65f)
            lineTo(w * 0.65f, h * 0.75f)
            lineTo(w * 0.55f, h * 0.55f)
            lineTo(w * 0.35f, h * 0.65f)
            lineTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.45f, h * 0.35f)
            close()
        }
        drawPath(
            path = pinHead,
            color = color,
            style = if (isPinned) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Needle tip
        drawLine(
            color = color,
            start = Offset(w * 0.32f, h * 0.58f),
            end = Offset(w * 0.10f, h * 0.90f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun EllipsisIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.08f

        drawCircle(color = color, radius = r, center = Offset(w * 0.20f, h * 0.5f))
        drawCircle(color = color, radius = r, center = Offset(w * 0.50f, h * 0.5f))
        drawCircle(color = color, radius = r, center = Offset(w * 0.80f, h * 0.5f))
    }
}

@Composable
fun PersonIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.dp.toPx()

        // Head
        drawCircle(
            color = color,
            radius = w * 0.24f,
            center = Offset(w * 0.50f, h * 0.32f),
            style = Stroke(width = sw)
        )

        // Shoulders / torso arc
        val torso = Path().apply {
            moveTo(w * 0.15f, h * 0.88f)
            cubicTo(w * 0.18f, h * 0.62f, w * 0.82f, h * 0.62f, w * 0.85f, h * 0.88f)
        }
        drawPath(
            path = torso,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ResetCcwIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.2.dp.toPx()
        val r = w * 0.35f
        val cx = w * 0.5f
        val cy = h * 0.52f

        // Standard counter-clockwise circular refresh/undo loop
        // The arc flows up the right, reaches the top, and connects to the arrowhead pointing left.
        val rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)
        val arcPath = Path().apply {
            arcTo(rect, startAngleDegrees = -80f, sweepAngleDegrees = 270f, forceMoveTo = true)
        }
        drawPath(
            path = arcPath,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )

        // Arrow tip at top pointing counter-clockwise (curving to the left)
        val tipAngleRad = Math.toRadians(-102.0)
        val tipX = cx + r * kotlin.math.cos(tipAngleRad).toFloat()
        val tipY = cy + r * kotlin.math.sin(tipAngleRad).toFloat()

        // Direction along curve: tangent pointing left & slightly down
        val arrowHead = Path().apply {
            // Upper barb (back and up)
            moveTo(tipX + w * 0.14f, tipY - h * 0.11f)
            // Tip pointing left along top curve
            lineTo(tipX, tipY)
            // Lower barb (back and down towards center)
            lineTo(tipX + w * 0.12f, tipY + h * 0.13f)
        }
        drawPath(
            path = arrowHead,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun GeometricPlayIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Center offset compensation for visual balance
        val left = w * 0.32f
        val right = w * 0.78f
        val top = h * 0.22f
        val bottom = h * 0.78f
        val midY = h * 0.50f

        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, midY)
            lineTo(left, bottom)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
fun GeometricPauseIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.22f
        val barH = h * 0.60f
        val topY = (h - barH) / 2f
        val r = CornerRadius(barW / 2, barW / 2)

        // Left bar
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.24f, topY),
            size = Size(barW, barH),
            cornerRadius = r
        )
        // Right bar
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.54f, topY),
            size = Size(barW, barH),
            cornerRadius = r
        )
    }
}

@Composable
fun CheckmarkCheckIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.4.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.30f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun PencilEditIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.7.dp.toPx()

        // Angled pencil body
        val path = Path().apply {
            moveTo(w * 0.68f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.32f)
            lineTo(w * 0.36f, h * 0.80f)
            lineTo(w * 0.16f, h * 0.84f)
            lineTo(w * 0.20f, h * 0.64f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun CloudSyncStatusIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.6.dp.toPx()

        val cloud = Path().apply {
            moveTo(w * 0.22f, h * 0.68f)
            cubicTo(w * 0.08f, h * 0.68f, w * 0.08f, h * 0.48f, w * 0.24f, h * 0.46f)
            cubicTo(w * 0.26f, h * 0.26f, w * 0.50f, h * 0.24f, w * 0.58f, h * 0.36f)
            cubicTo(w * 0.66f, h * 0.28f, w * 0.84f, h * 0.32f, w * 0.86f, h * 0.48f)
            cubicTo(w * 0.96f, h * 0.50f, w * 0.96f, h * 0.68f, w * 0.84f, h * 0.68f)
            close()
        }
        drawPath(
            path = cloud,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Checkmark inside cloud
        val check = Path().apply {
            moveTo(w * 0.42f, h * 0.52f)
            lineTo(w * 0.48f, h * 0.58f)
            lineTo(w * 0.60f, h * 0.44f)
        }
        drawPath(
            path = check,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun PlanCalendarIcon(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 1.8.dp.toPx()

        // Calendar body rounded rect
        val rectTop = h * 0.22f
        val rectHeight = h * 0.70f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, rectTop),
            size = Size(w * 0.72f, rectHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = sw)
        )

        // Top binder rings
        drawLine(
            color = color,
            start = Offset(w * 0.32f, h * 0.10f),
            end = Offset(w * 0.32f, h * 0.26f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.68f, h * 0.10f),
            end = Offset(w * 0.68f, h * 0.26f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Header separator line
        drawLine(
            color = color,
            start = Offset(w * 0.14f, h * 0.44f),
            end = Offset(w * 0.86f, h * 0.44f),
            strokeWidth = sw * 0.85f
        )

        // Calendar grid dots / marks
        val dotRadius = 1.25.dp.toPx()
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.34f, h * 0.62f))
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.50f, h * 0.62f))
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.66f, h * 0.62f))
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.34f, h * 0.78f))
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.50f, h * 0.78f))
        drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.66f, h * 0.78f))
    }
}

/**
 * Apple HIG Standard Reorder Grab Handle Icon (≡).
 * Three rounded horizontal bars with crisp stroke geometry.
 */
@Composable
fun ReorderGrabHandleIcon(
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.72f
        val startX = (w - barW) / 2f
        val strokeW = 1.75.dp.toPx()

        val y1 = h * 0.28f
        val y2 = h * 0.50f
        val y3 = h * 0.72f

        drawLine(
            color = color,
            start = Offset(startX, y1),
            end = Offset(startX + barW, y1),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(startX, y2),
            end = Offset(startX + barW, y2),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(startX, y3),
            end = Offset(startX + barW, y3),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Smoothly morphs a leading action indicator (e.g. checkbox or status dot) into a drag handle (≡)
 * when lifted, providing fluid tactile feedback with zero visual jitter.
 */
@Composable
fun MorphingLeadingActionIcon(
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    dragHandleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Crossfade(
        targetState = isDragging,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "morphActionIcon",
        modifier = modifier
    ) { dragging ->
        if (dragging) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                ReorderGrabHandleIcon(
                    color = dragHandleColor,
                    size = 18.dp
                )
            }
        } else {
            content()
        }
    }
}

