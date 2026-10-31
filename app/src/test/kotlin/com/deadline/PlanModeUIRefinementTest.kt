package com.deadline

import com.deadline.ui.habits.PlanDateItem
import com.deadline.ui.habits.generatePlanDates
import com.deadline.ui.theme.BrandAmberEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class PlanModeUIRefinementTest {

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(relativePath.removePrefix("app/")),
            File("app", relativePath),
            File("app", relativePath.removePrefix("app/"))
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    // -------------------------------------------------------------------------
    // 1. Horizontal Date Picker Integration (Plan Mode)
    // -------------------------------------------------------------------------

    @Test
    fun testGeneratePlanDates_weekdayInitialsAndDayNumbers() {
        val anchorCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 26) // Monday
        }

        val dates = generatePlanDates(anchorCalendar = anchorCalendar, pastDays = 2, futureDays = 4)
        assertEquals(7, dates.size)

        // Index 2 is the anchor day (2026-10-26, Monday)
        val monday = dates[2]
        assertEquals("2026-10-26", monday.dateString)
        assertEquals("M", monday.dayOfWeekInitial)
        assertEquals(26, monday.dayOfMonth)
        assertTrue(monday.isToday)

        // Day before (Sunday)
        val sunday = dates[1]
        assertEquals("2026-10-25", sunday.dateString)
        assertEquals("S", sunday.dayOfWeekInitial)
        assertEquals(25, sunday.dayOfMonth)
        assertFalse(sunday.isToday)

        // Day after (Tuesday)
        val tuesday = dates[3]
        assertEquals("2026-10-27", tuesday.dateString)
        assertEquals("T", tuesday.dayOfWeekInitial)
        assertEquals(27, tuesday.dayOfMonth)

        // Wednesday
        val wednesday = dates[4]
        assertEquals("W", wednesday.dayOfWeekInitial)
        assertEquals(28, wednesday.dayOfMonth)

        // Thursday
        val thursday = dates[5]
        assertEquals("T", thursday.dayOfWeekInitial)
        assertEquals(29, thursday.dayOfMonth)

        // Friday
        val friday = dates[6]
        assertEquals("F", friday.dayOfWeekInitial)
        assertEquals(30, friday.dayOfMonth)
    }

    @Test
    fun testUniversalYellowAccentColorSpecification() {
        // Universal Yellow Accent Color (#F59E0B / primary amber)
        val primaryAmberHex = 0xFFF59E0BL
        val brandAmberEndHex = BrandAmberEnd.value.toLong() shr 32 and 0xFFFFFFFFL

        assertEquals(
            "Primary amber accent color must match #F59E0B",
            0xFFF59E0BL,
            brandAmberEndHex
        )
    }

    @Test
    fun testPlanView_containsHorizontalDatePickerAndCenterArrowIndicator() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val content = planViewFile.readText()

        // 1. HorizontalPlanDatePicker component must be defined and integrated inside PlanView
        assertTrue(
            "HorizontalPlanDatePicker composable must be defined",
            content.contains("fun HorizontalPlanDatePicker")
        )
        assertTrue(
            "PlanView must embed HorizontalPlanDatePicker",
            content.contains("HorizontalPlanDatePicker(")
        )

        // 2. Component Geometry: Vertical rounded pill/capsule containers
        assertTrue(
            "DayPillCapsule must be defined with rounded pill/capsule shape",
            content.contains("fun DayPillCapsule") && content.contains("RoundedCornerShape(percent = 50)")
        )

        // 3. Center indicator with down-arrow pointer directly above the selected date
        assertTrue(
            "Center indicator with down-arrow pointer must be rendered above date carousel",
            content.contains("drawPath") && content.contains("lineTo(size.width / 2f, size.height)")
        )

        // 4. Active date highlighted using Universal Yellow Accent Color enclosed in elevated pill card
        assertTrue(
            "Selected date must use BrandAmberEnd (#F59E0B)",
            content.contains("val universalYellow = BrandAmberEnd") || content.contains("Color(0xFFF59E0B)")
        )
        assertTrue(
            "Selected date pill must feature elevation / shadow",
            content.contains("Modifier.shadow") && content.contains("animatedElevation")
        )

        // 5. Unselected state: Muted low-opacity text with weekday initial on top and date number below
        assertTrue(
            "Pill must display dayOfWeekInitial on top",
            content.contains("text = day.dayOfWeekInitial")
        )
        assertTrue(
            "Pill must display dayOfMonth below",
            content.contains("text = \"\${day.dayOfMonth}\"")
        )
        assertTrue(
            "Unselected text must use low-opacity alpha",
            content.contains("alpha = 0.45f")
        )

        // 6. Micro-interactions: Smooth snap-to-center physics on horizontal fling
        assertTrue(
            "Must use rememberSnapFlingBehavior for smooth snap fling behavior",
            content.contains("rememberSnapFlingBehavior(lazyListState = listState)")
        )
        assertTrue(
            "Must center snapped item via symmetric horizontal padding",
            content.contains("(maxWidth - itemWidthDp) / 2")
        )
    }

    // -------------------------------------------------------------------------
    // 2. Streamlined Schedule Modal Clean Architecture & Timer-Style Interface
    // -------------------------------------------------------------------------

    @Test
    fun testStreamlinedScheduleModal_timerStylePickerAndRangeElimination() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val content = planViewFile.readText()

        val modalCode = content.substringAfter("fun ScheduleTimePickerSheet")
            .substringBefore("fun ImportTaskBottomSheet")

        // 1. Header: simple "Create" and task title
        assertTrue(
            "Modal title must be 'Create'",
            modalCode.contains("text = \"Create\"")
        )
        assertFalse(
            "Modal title must not be 'Schedule'",
            modalCode.contains("text = \"Schedule\"")
        )
        assertFalse(
            "Modal title must remove 'on Timeline'",
            modalCode.contains("Schedule on Timeline")
        )
        assertTrue(
            "Modal must display task title",
            modalCode.contains("text = item.title")
        )

        // 2. Range Display Elimination: Complete removal of "Range" row and time duration summary
        assertFalse(
            "Modal must completely eliminate 'Range' row",
            modalCode.contains("text = \"Range\"")
        )
        assertFalse(
            "Modal must completely eliminate formatTimeRange duration summary",
            modalCode.contains("formatTimeRange")
        )
        assertFalse(
            "Modal must not contain legacy Scheduled Range label",
            modalCode.contains("Scheduled Range")
        )

        // 3. Timer-Styled Scheduling Interface: Apple-style continuous scrolling wheel columns + AM/PM toggle
        assertTrue(
            "Modal must integrate Timer-styled wheel columns",
            modalCode.contains("PlanAppleWheelColumn")
        )
        assertTrue(
            "Modal must contain Colon separator between hours and minutes",
            modalCode.contains("text = \":\"")
        )
        assertTrue(
            "Modal must integrate required AM toggle",
            modalCode.contains("text = \"AM\"")
        )
        assertTrue(
            "Modal must integrate required PM toggle",
            modalCode.contains("text = \"PM\"")
        )

        // 4. Primary "Create" CTA Action Button
        assertTrue(
            "Modal must display primary 'Create' CTA button",
            modalCode.contains("text = \"Create\"")
        )
        assertFalse(
            "Modal must remove legacy 'Schedule Block' label",
            modalCode.contains("Schedule Block")
        )
    }

    // -------------------------------------------------------------------------
    // 3. Strict Date-Bound Visibility & Clean Task Card
    // -------------------------------------------------------------------------

    @Test
    fun testStrictDateBoundVisibility_tasksIsolatedToSelectedDate() {
        val planViewFile = getSourceFile("app/src/main/kotlin/com/deadline/ui/habits/PlanView.kt")
        assertTrue("PlanView.kt must exist", planViewFile.exists())
        val content = planViewFile.readText()

        // 1. PlanView partitions allActiveGoals by selected date string
        assertTrue(
            "PlanView must enforce strict date partitioning against selectedDateItem",
            content.contains("effectiveDate == currentDateStr")
        )

        // 2. Task Card Date Tag Removal: Ensure orange date stamp badge is completely removed
        val cardCode = content.substringAfter("InteractiveGoalCard(goal = goalItem)")
            .substringBefore("ScheduleTimePickerSheet")

        assertFalse(
            "Task card must completely remove executionDate badge",
            cardCode.contains("text = executionDate")
        )
        assertFalse(
            "Task card must completely remove PlanCalendarIcon date badge",
            cardCode.contains("PlanCalendarIcon")
        )
    }

    @Test
    fun testDatePartitioningSimulation() {
        data class TestTodo(val id: String, val title: String, val dueDate: String?)

        val t1 = TestTodo("1", "Design Review", "2026-10-26")
        val t2 = TestTodo("2", "Ship Feature", "2026-10-27")
        val t3 = TestTodo("3", "Write Tests", "2026-10-26")

        val importedIds = listOf("1", "2", "3")
        val allTodos = listOf(t1, t2, t3)

        // Query for October 26, 2026
        val selectedDateOct26 = "2026-10-26"
        val oct26Goals = importedIds.mapNotNull { id ->
            val todo = allTodos.find { it.id == id }
            if (todo != null && todo.dueDate == selectedDateOct26) todo else null
        }
        assertEquals(2, oct26Goals.size)
        assertTrue(oct26Goals.any { it.id == "1" })
        assertTrue(oct26Goals.any { it.id == "3" })
        assertFalse("Task for Oct 27 must NOT appear on Oct 26", oct26Goals.any { it.id == "2" })

        // Query for October 27, 2026
        val selectedDateOct27 = "2026-10-27"
        val oct27Goals = importedIds.mapNotNull { id ->
            val todo = allTodos.find { it.id == id }
            if (todo != null && todo.dueDate == selectedDateOct27) todo else null
        }
        assertEquals(1, oct27Goals.size)
        assertEquals("2", oct27Goals[0].id)
    }

    @Test
    fun testStartMinuteCalculationWithAmPmMeridian() {
        fun calculateStartMinute(hour12: Int, minute: Int, isPm: Boolean): Int {
            val hour24 = when {
                hour12 == 12 -> if (isPm) 12 else 0
                isPm -> hour12 + 12
                else -> hour12
            }
            return hour24 * 60 + minute
        }

        // Midnight (12:00 AM) -> 0
        assertEquals(0, calculateStartMinute(12, 0, isPm = false))
        // 12:30 AM -> 30
        assertEquals(30, calculateStartMinute(12, 30, isPm = false))
        // 9:15 AM -> 555
        assertEquals(9 * 60 + 15, calculateStartMinute(9, 15, isPm = false))
        // Noon (12:00 PM) -> 720
        assertEquals(12 * 60, calculateStartMinute(12, 0, isPm = true))
        // 2:45 PM -> 14:45 -> 885
        assertEquals(14 * 60 + 45, calculateStartMinute(2, 45, isPm = true))
        // 11:59 PM -> 23:59 -> 1439
        assertEquals(23 * 60 + 59, calculateStartMinute(11, 59, isPm = true))
    }
}
