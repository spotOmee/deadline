package com.deadline.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.deadline.domain.HeadlineScaler

/**
 * Calculates a dynamic headline font size (TextUnit) based on text length to prevent UI overflow.
 */
fun calculateDynamicHeadlineSize(
    text: String,
    baseSize: TextUnit = 16.sp,
    minSize: TextUnit = 12.sp
): TextUnit {
    val len = HeadlineScaler.clamp(text).length
    return when {
        len <= 10 -> baseSize
        len <= 15 -> ((baseSize.value * 2f + minSize.value) / 3f).sp
        else -> minSize
    }
}

/**
 * Composable that displays a strictly clamped (max 20 chars) and dynamically scaled headline.
 */
@Composable
fun DynamicHeadlineText(
    text: String,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = 16.sp,
    minSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = LocalTextStyle.current,
    textDecoration: TextDecoration? = null
) {
    val clampedText = HeadlineScaler.clamp(text)
    val dynamicSize = calculateDynamicHeadlineSize(clampedText, baseSize, minSize)
    Text(
        text = clampedText,
        modifier = modifier,
        style = style.copy(fontSize = dynamicSize),
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textDecoration = textDecoration
    )
}
