package com.deadline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.ui.theme.BrandAmberStart

/**
 * Bespoke 10-Icon Pixel Art Library.
 * Handcrafted on a 14x14 pixel grid for authentic 8-bit / retro-modern aesthetic.
 */
enum class PixelIconType(val displayName: String, val category: String) {
    CLAUDE_AI("Claude AI", "AI & Intelligence"),
    CODE_BRACKETS("Code Brackets", "Engineering"),
    GLOWING_BRAIN("Glowing Brain", "Mindset"),
    LAUNCHING_ROCKET("Launching Rocket", "Progress"),
    CRESCENT_MOON("Crescent Moon", "Rest & Night"),
    ATOM_SYMBOL("Atom Symbol", "Science"),
    INFINITY_LOOP("Infinity Loop", "Consistency"),
    CHECKMARK("Task Complete", "Productivity"),
    TERMINAL("Terminal", "Developer"),
    TARGET_BULLSEYE("Focus Bullseye", "Targeting");

    companion object {
        fun fromStringOrNull(name: String?): PixelIconType? {
            if (name == null) return null
            if (name.equals("LIGHTNING_BOLT", ignoreCase = true)) return TERMINAL
            return values().find { it.name.equals(name, ignoreCase = true) }
        }

        fun fromString(name: String?): PixelIconType {
            return fromStringOrNull(name) ?: TARGET_BULLSEYE
        }
    }
}

private val CLAUDE_AI_GRID = listOf(
    "00000110000000",
    "01100110011000",
    "00110110110000",
    "00011111100000",
    "00001111000000",
    "11111111111100",
    "11111111111100",
    "00001111000000",
    "00011111100000",
    "00110110110000",
    "01100110011000",
    "00000110000000",
    "00000000000000",
    "00000000000000"
)

private val CODE_BRACKETS_GRID = listOf(
    "00000000000000",
    "00011000011000",
    "00110000110000",
    "01100001100000",
    "11000001100000",
    "01100011000110",
    "00110011001100",
    "00110011001100",
    "01100011000110",
    "11000001100000",
    "01100001100000",
    "00110000110000",
    "00011000011000",
    "00000000000000"
)

private val GLOWING_BRAIN_GRID = listOf(
    "00100000000100",
    "00001110111000",
    "10011111111101",
    "00111011101110",
    "01111101011110",
    "01111101011110",
    "10111110111111",
    "00111111111110",
    "00011101011100",
    "10001111111001",
    "00000110110000",
    "00100000000100",
    "00000000000000",
    "00000000000000"
)

private val LAUNCHING_ROCKET_GRID = listOf(
    "00000000001110",
    "00000000111110",
    "00000001110110",
    "00000011111100",
    "00000111111000",
    "00001111110000",
    "00111111100000",
    "01111111000000",
    "11111111000000",
    "11001110000000",
    "00011100000000",
    "00111000000000",
    "01100000000000",
    "10000000000000"
)

private val CRESCENT_MOON_GRID = listOf(
    "00000001100000",
    "00000111100000",
    "00001111000000",
    "00011110000000",
    "00111100000000",
    "00111100000000",
    "00111100000000",
    "00111100000000",
    "00011110000000",
    "00001111000000",
    "00000111100000",
    "00000001100000",
    "00000000000000",
    "00000000000000"
)

private val ATOM_SYMBOL_GRID = listOf(
    "00011000011000",
    "00110000001100",
    "01100011000110",
    "11000111100011",
    "00001111110000",
    "00011111111000",
    "00011111111000",
    "00001111110000",
    "11000111100011",
    "01100011000110",
    "00110000001100",
    "00011000011000",
    "00000000000000",
    "00000000000000"
)

private val INFINITY_LOOP_GRID = listOf(
    "00000000000000",
    "00000000000000",
    "00111100111100",
    "01100111100110",
    "11000011000011",
    "11000011000011",
    "01100111100110",
    "00111100111100",
    "00000000000000",
    "00000000000000",
    "00000000000000",
    "00000000000000",
    "00000000000000",
    "00000000000000"
)

private val CHECKMARK_GRID = listOf(
    "00000000000000",
    "00000000000110",
    "00000000001110",
    "00000000011100",
    "00000000111000",
    "00000001110000",
    "11000001110000",
    "01100011100000",
    "00110111000000",
    "00011110000000",
    "00001100000000",
    "00000000000000",
    "00000000000000",
    "00000000000000"
)

private val TERMINAL_GRID = listOf(
    "11111111111111",
    "10000000000001",
    "10101010000001",
    "11111111111111",
    "10000000000001",
    "10110000000001",
    "10001100000001",
    "10110011110001",
    "10000011110001",
    "10000000000001",
    "10000000000001",
    "10000000000001",
    "10000000000001",
    "11111111111111"
)

private val TARGET_BULLSEYE_GRID = listOf(
    "00001111110000",
    "00111000011100",
    "01100000000110",
    "01001111110010",
    "10011000011001",
    "10010011001001",
    "10010111101001",
    "10010111101001",
    "10010011001001",
    "10011000011001",
    "01001111110010",
    "01100000000110",
    "00111000011100",
    "00001111110000"
)

val HABIT_ICONS = listOf("⚡", "💧", "🎯", "📚", "🏃", "🧘", "🌲", "☕", "🍎", "💤")

@Composable
fun PixelIcon(
    type: PixelIconType,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val grid = when (type) {
        PixelIconType.CLAUDE_AI -> CLAUDE_AI_GRID
        PixelIconType.CODE_BRACKETS -> CODE_BRACKETS_GRID
        PixelIconType.GLOWING_BRAIN -> GLOWING_BRAIN_GRID
        PixelIconType.LAUNCHING_ROCKET -> LAUNCHING_ROCKET_GRID
        PixelIconType.CRESCENT_MOON -> CRESCENT_MOON_GRID
        PixelIconType.ATOM_SYMBOL -> ATOM_SYMBOL_GRID
        PixelIconType.INFINITY_LOOP -> INFINITY_LOOP_GRID
        PixelIconType.CHECKMARK -> CHECKMARK_GRID
        PixelIconType.TERMINAL -> TERMINAL_GRID
        PixelIconType.TARGET_BULLSEYE -> TARGET_BULLSEYE_GRID
    }

    Canvas(modifier = modifier.size(size)) {
        val rows = grid.size
        val cols = grid[0].length
        val pixelW = this.size.width / cols
        val pixelH = this.size.height / rows

        for (r in 0 until rows) {
            val rowStr = grid[r]
            for (c in 0 until cols) {
                if (rowStr[c] == '1') {
                    drawRect(
                        color = color,
                        topLeft = Offset(c * pixelW, r * pixelH),
                        size = Size(pixelW * 0.94f, pixelH * 0.94f)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Pixel Icon Selector Row (Text labels stripped for clean minimalist aesthetic).
 * Allows quick horizontal scrolling selection of the bespoke 10 pixel icons.
 */
@Composable
fun PixelIconPickerRow(
    selectedIcon: PixelIconType,
    onSelectIcon: (PixelIconType) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = BrandAmberStart
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        items(PixelIconType.values()) { iconType ->
            val isSelected = iconType == selectedIcon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectIcon(iconType) },
                contentAlignment = Alignment.Center
            ) {
                PixelIcon(
                    type = iconType,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                    size = 20.dp
                )
            }
        }
    }
}

/**
 * Consolidated Habit Icons Selector Row (No text labels).
 * Renders the 10 consolidated habit icons from the Habits screen.
 */
@Composable
fun HabitIconPickerRow(
    selectedIcon: String,
    onSelectIcon: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = BrandAmberStart
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        items(HABIT_ICONS) { emoji ->
            val isSelected = emoji == selectedIcon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectIcon(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp
                )
            }
        }
    }
}

/**
 * Habit Icon Expansion:
 * Migrates the 10 Bespoke Pixel Art Library icons into Habit Creation,
 * combined with distinct lifestyle emojis without duplicates (🎯 excluded as TARGET_BULLSEYE represents it).
 */
val EXPANDED_HABIT_ICONS = listOf(
    PixelIconType.CLAUDE_AI.name,
    PixelIconType.CODE_BRACKETS.name,
    PixelIconType.GLOWING_BRAIN.name,
    PixelIconType.LAUNCHING_ROCKET.name,
    PixelIconType.CRESCENT_MOON.name,
    PixelIconType.ATOM_SYMBOL.name,
    PixelIconType.INFINITY_LOOP.name,
    PixelIconType.CHECKMARK.name,
    PixelIconType.TERMINAL.name,
    PixelIconType.TARGET_BULLSEYE.name,
    "⚡", "💧", "📚", "🏃", "🧘", "🌲", "☕", "🍎", "💤"
)

/**
 * Renders either a Bespoke Pixel Art Icon or a classic emoji seamlessly.
 */
@Composable
fun HabitIconDisplay(
    icon: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Unspecified,
    size: Dp = 20.dp
) {
    val pixelType = PixelIconType.fromStringOrNull(icon)
    if (pixelType != null) {
        PixelIcon(
            type = pixelType,
            color = if (tintColor != Color.Unspecified) tintColor else BrandAmberStart,
            modifier = modifier,
            size = size
        )
    } else {
        Text(
            text = icon,
            fontSize = (size.value * 0.95f).sp,
            modifier = modifier
        )
    }
}

