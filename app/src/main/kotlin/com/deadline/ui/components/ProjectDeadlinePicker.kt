package com.deadline.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadline.ui.theme.BrandAmberStart
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Encapsulates an explicitly chosen project deadline date.
 */
data class SelectedDeadlineDate(
    val year: Int,
    val month: Int, // 0-based: 0 = January .. 11 = December
    val day: Int
) {
    fun toIsoString(): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
    }

    fun toDisplayString(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val monthStr = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
        return "$monthStr $day, $year"
    }

    fun toShortDisplayString(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val monthStr = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
        return "$monthStr $day"
    }
}

/**
 * Mandatory Submission Constraint Validator for Project Creation.
 */
object ProjectDeadlineValidator {
    /**
     * Creation button must only become enabled and active once a valid date
     * has been explicitly tapped and selected within the calendar picker,
     * and a non-blank project title is provided.
     */
    fun isProjectFormValid(
        title: String,
        selectedDeadline: SelectedDeadlineDate?,
        isSubmitting: Boolean = false
    ): Boolean {
        return title.trim().isNotBlank() && selectedDeadline != null && !isSubmitting
    }
}

/**
 * Pure mathematical functions for calendar paging and date grid layout.
 */
object CalendarPickerMath {
    const val INITIAL_PAGE_INDEX = 24
    const val TOTAL_PAGE_COUNT = 120

    /**
     * Resolves (Year, Month 0-based) for a given horizontal pager page index.
     */
    fun getMonthForPageIndex(
        anchorCalendar: Calendar,
        pageIndex: Int,
        initialPageIndex: Int = INITIAL_PAGE_INDEX
    ): Pair<Int, Int> {
        val cal = (anchorCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, pageIndex - initialPageIndex)
        }
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    /**
     * Days in the given month, handling leap years.
     */
    fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            0, 2, 4, 6, 7, 9, 11 -> 31
            3, 5, 8, 10 -> 30
            1 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    private val monthTable = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)

    /**
     * Offset for Monday-first 7-column layout (0 = Mon .. 6 = Sun).
     */
    fun getFirstDayOfWeekOffset(year: Int, month: Int): Int {
        val y = if (month < 2) year - 1 else year
        val dowSun = (y + y / 4 - y / 100 + y / 400 + monthTable[month] + 1) % 7
        return (dowSun + 6) % 7
    }

    /**
     * Total number of week rows needed for this month's grid.
     */
    fun getTotalRows(year: Int, month: Int): Int {
        val daysInMonth = getDaysInMonth(year, month)
        val offset = getFirstDayOfWeekOffset(year, month)
        return (offset + daysInMonth + 6) / 7
    }

    /**
     * Resolves the 1-based day of month for a cell at (row, col) in a 7-column grid,
     * or null if the slot falls outside the month.
     */
    fun getDayForCell(row: Int, col: Int, year: Int, month: Int): Int? {
        val offset = getFirstDayOfWeekOffset(year, month)
        val daysInMonth = getDaysInMonth(year, month)
        val day = row * 7 + col - offset + 1
        return if (day in 1..daysInMonth) day else null
    }

    private val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    /**
     * Formats Month and Year for the sub-header (e.g., "October 2026").
     */
    fun formatMonthHeader(year: Int, month: Int): String {
        val name = if (month in 0..11) monthNames[month] else (month + 1).toString()
        return "$name $year"
    }
}

/**
 * Universal Mandatory Deadline Picker Component.
 * Features:
 * 1. Prominent universal yellow button with white text labeled 'Set Project Deadline'.
 * 2. Animated smooth expansion of the dropdown section (60-120 FPS high-fluidity spring).
 * 3. Horizontal swipeable fling-based calendar picker with Month sub-header and numerical date grid.
 */
@Composable
fun UniversalDeadlinePicker(
    selectedDeadline: SelectedDeadlineDate?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDateSelected: (SelectedDeadlineDate) -> Unit,
    modifier: Modifier = Modifier,
    buttonLabel: String = "Set Project Deadline"
) {
    val context = LocalContext.current
    val universalYellow = BrandAmberStart // 0xFFFF9F1C

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Prominent Universal Yellow Button labeled 'Set Project Deadline' / 'Set Task Deadline'
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(universalYellow)
                .clickable {
                    HapticHelper.click(context)
                    onToggleExpand()
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                PlanCalendarIcon(
                    color = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (selectedDeadline != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = selectedDeadline.toShortDisplayString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Animated Dropdown Expansion (Optimized 60-120 FPS fluidity)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 180,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 140,
                    easing = FastOutSlowInEasing
                )
            ),
            modifier = Modifier.graphicsLayer { clip = true }
        ) {
            HorizontalSwipeableCalendarPicker(
                selectedDeadline = selectedDeadline,
                onDateSelected = onDateSelected
            )
        }
    }
}

/**
 * Horizontal fling-based swipeable calendar picker.
 * Features Month sub-header above a selectable numerical date grid with seamless sideways swiping.
 */
@Composable
fun HorizontalSwipeableCalendarPicker(
    selectedDeadline: SelectedDeadlineDate?,
    onDateSelected: (SelectedDeadlineDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseCalendar = remember { Calendar.getInstance() }
    val todayYear = remember { baseCalendar.get(Calendar.YEAR) }
    val todayMonth = remember { baseCalendar.get(Calendar.MONTH) }
    val todayDay = remember { baseCalendar.get(Calendar.DAY_OF_MONTH) }

    val pagerState = rememberPagerState(
        initialPage = CalendarPickerMath.INITIAL_PAGE_INDEX,
        pageCount = { CalendarPickerMath.TOTAL_PAGE_COUNT }
    )

    SquircleCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Horizontal fling-based pager for seamless sideways month swiping
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                val (year, month) = remember(pageIndex) {
                    CalendarPickerMath.getMonthForPageIndex(baseCalendar, pageIndex)
                }
                val monthTitle = remember(year, month) {
                    CalendarPickerMath.formatMonthHeader(year, month)
                }
                val daysInMonth = remember(year, month) {
                    CalendarPickerMath.getDaysInMonth(year, month)
                }
                val firstDayOffset = remember(year, month) {
                    CalendarPickerMath.getFirstDayOfWeekOffset(year, month)
                }
                val totalRows = remember(year, month) {
                    CalendarPickerMath.getTotalRows(year, month)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Month & Date Hierarchy: Smaller sub-header showing Month name above selectable date grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            CalendarNavChevron(
                                isLeft = true,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = {
                                if (pagerState.currentPage < CalendarPickerMath.TOTAL_PAGE_COUNT - 1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            CalendarNavChevron(
                                isLeft = false,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Weekday Column Sub-Headers (Mon..Sun)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Selectable Numerical Date Grid
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (row in 0 until totalRows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (col in 0 until 7) {
                                    val dayNum = row * 7 + col - firstDayOffset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val isSelected = selectedDeadline?.let {
                                            it.year == year && it.month == month && it.day == dayNum
                                        } == true
                                        val isToday = todayYear == year && todayMonth == month && todayDay == dayNum

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> BrandAmberStart
                                                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable {
                                                    HapticHelper.selection(context)
                                                    onDateSelected(SelectedDeadlineDate(year, month, dayNum))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> BrandAmberStart
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
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

/**
 * Minimalist chevron arrow for calendar navigation.
 */
@Composable
private fun CalendarNavChevron(
    isLeft: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(10.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (isLeft) {
                moveTo(w * 0.7f, h * 0.15f)
                lineTo(w * 0.3f, h * 0.5f)
                lineTo(w * 0.7f, h * 0.85f)
            } else {
                moveTo(w * 0.3f, h * 0.15f)
                lineTo(w * 0.7f, h * 0.5f)
                lineTo(w * 0.3f, h * 0.85f)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
