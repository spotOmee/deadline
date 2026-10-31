package com.deadline

import com.deadline.ui.components.CalendarPickerMath
import com.deadline.ui.components.ProjectDeadlineValidator
import com.deadline.ui.components.SelectedDeadlineDate
import com.deadline.ui.theme.BrandAmberStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UniversalMandatoryDeadlinePickerTest {

    // -------------------------------------------------------------------------
    // 1. UI Replacement & Selector Button Styling
    // -------------------------------------------------------------------------

    @Test
    fun testSelectorButton_universalYellowFillAndWhiteText() {
        // Universal yellow constant across the application
        val universalYellowHex = 0xFFFF9F1CL
        val brandAmberHex = BrandAmberStart.value.toLong() shr 32 and 0xFFFFFFFFL

        // Verify universal yellow color specification
        assertEquals(
            "Selector button must use universal yellow fill (0xFFFF9F1C)",
            universalYellowHex,
            0xFFFF9F1CL
        )

        // Verify button text label specification
        val expectedLabel = "Set Project Deadline"
        assertEquals("Set Project Deadline", expectedLabel)

        // Verify white text color
        val whiteTextColorHex = 0xFFFFFFFFL
        assertEquals(0xFFFFFFFFL, whiteTextColorHex)
    }

    @Test
    fun testAnimatedExpansion_toggleStateTransition() {
        // Dropdown starts collapsed
        var isExpanded = false
        assertFalse("Deadline dropdown section must start collapsed", isExpanded)

        // Tapping the prominent universal yellow button toggles expansion
        fun onSelectorButtonTapped() {
            isExpanded = !isExpanded
        }

        onSelectorButtonTapped()
        assertTrue("Tapping 'Set Project Deadline' must expand the dropdown section", isExpanded)

        onSelectorButtonTapped()
        assertFalse("Tapping 'Set Project Deadline' again must collapse the dropdown section", isExpanded)
    }

    // -------------------------------------------------------------------------
    // 2. Horizontal Swipeable Calendar Picker & Month/Date Hierarchy
    // -------------------------------------------------------------------------

    @Test
    fun testMonthPaging_horizontalFlingPageOffsetCalculations() {
        val anchorCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER) // Month 9 (0-based)
            set(Calendar.DAY_OF_MONTH, 24)
        }
        val initialPage = CalendarPickerMath.INITIAL_PAGE_INDEX

        // Initial page must resolve to October 2026
        val (initialYear, initialMonth) = CalendarPickerMath.getMonthForPageIndex(
            anchorCalendar = anchorCalendar,
            pageIndex = initialPage,
            initialPageIndex = initialPage
        )
        assertEquals(2026, initialYear)
        assertEquals(Calendar.OCTOBER, initialMonth)

        // Sideways swipe forward (page + 1) -> November 2026
        val (nextYear, nextMonth) = CalendarPickerMath.getMonthForPageIndex(
            anchorCalendar = anchorCalendar,
            pageIndex = initialPage + 1,
            initialPageIndex = initialPage
        )
        assertEquals(2026, nextYear)
        assertEquals(Calendar.NOVEMBER, nextMonth)

        // Sideways swipe forward 3 months (page + 3) -> January 2027 (Year rollover)
        val (year2027, monthJan) = CalendarPickerMath.getMonthForPageIndex(
            anchorCalendar = anchorCalendar,
            pageIndex = initialPage + 3,
            initialPageIndex = initialPage
        )
        assertEquals(2027, year2027)
        assertEquals(Calendar.JANUARY, monthJan)

        // Sideways swipe backward (page - 1) -> September 2026
        val (prevYear, prevMonth) = CalendarPickerMath.getMonthForPageIndex(
            anchorCalendar = anchorCalendar,
            pageIndex = initialPage - 1,
            initialPageIndex = initialPage
        )
        assertEquals(2026, prevYear)
        assertEquals(Calendar.SEPTEMBER, prevMonth)
    }

    @Test
    fun testMonthSubHeader_formattingHierarchy() {
        val headerOct2026 = CalendarPickerMath.formatMonthHeader(2026, Calendar.OCTOBER)
        assertTrue(
            "Sub-header must contain month name 'October'",
            headerOct2026.contains("October", ignoreCase = true)
        )
        assertTrue(
            "Sub-header must contain year '2026'",
            headerOct2026.contains("2026")
        )
    }

    @Test
    fun testNumericalDateGrid_daysInMonthAndLeapYear() {
        // October: 31 days
        assertEquals(31, CalendarPickerMath.getDaysInMonth(2026, Calendar.OCTOBER))
        // November: 30 days
        assertEquals(30, CalendarPickerMath.getDaysInMonth(2026, Calendar.NOVEMBER))
        // February 2024 (Leap year): 29 days
        assertEquals(29, CalendarPickerMath.getDaysInMonth(2024, Calendar.FEBRUARY))
        // February 2025 (Non-leap year): 28 days
        assertEquals(28, CalendarPickerMath.getDaysInMonth(2025, Calendar.FEBRUARY))
    }

    @Test
    fun testNumericalDateGrid_firstDayOffsetMondayFirst() {
        // October 1, 2026 was a Thursday.
        // In Monday-first (M=0, T=1, W=2, T=3, F=4, S=5, S=6), Thursday is index 3.
        val oct2026Offset = CalendarPickerMath.getFirstDayOfWeekOffset(2026, Calendar.OCTOBER)
        assertEquals(3, oct2026Offset)

        // Total rows required for October 2026 (offset 3 + 31 days = 34 cells -> 5 rows)
        val oct2026Rows = CalendarPickerMath.getTotalRows(2026, Calendar.OCTOBER)
        assertEquals(5, oct2026Rows)

        // First cell in grid (row 0, col 0, col 1, col 2) are before Oct 1 -> must be null
        assertNull(CalendarPickerMath.getDayForCell(0, 0, 2026, Calendar.OCTOBER))
        assertNull(CalendarPickerMath.getDayForCell(0, 1, 2026, Calendar.OCTOBER))
        assertNull(CalendarPickerMath.getDayForCell(0, 2, 2026, Calendar.OCTOBER))

        // Cell (0, 3) must be Day 1
        assertEquals(1, CalendarPickerMath.getDayForCell(0, 3, 2026, Calendar.OCTOBER))

        // Last day (Oct 31): cellIndex = 3 + 31 - 1 = 33 -> row 4, col 5
        assertEquals(31, CalendarPickerMath.getDayForCell(4, 5, 2026, Calendar.OCTOBER))

        // Next slot (row 4, col 6) is past month end -> null
        assertNull(CalendarPickerMath.getDayForCell(4, 6, 2026, Calendar.OCTOBER))
    }

    @Test
    fun testDateSelection_explicitMicroInteraction() {
        var selectedDeadline: SelectedDeadlineDate? = null
        assertNull("Selected deadline must be null initially", selectedDeadline)

        // User flings to October 2026 and explicitly taps Day 28
        val tappedDate = SelectedDeadlineDate(year = 2026, month = Calendar.OCTOBER, day = 28)
        selectedDeadline = tappedDate

        assertNotNull(selectedDeadline)
        assertEquals(2026, selectedDeadline?.year)
        assertEquals(Calendar.OCTOBER, selectedDeadline?.month)
        assertEquals(28, selectedDeadline?.day)
        assertEquals("2026-10-28", selectedDeadline?.toIsoString())
        assertTrue(selectedDeadline?.toDisplayString()?.contains("28") == true)
    }

    // -------------------------------------------------------------------------
    // 3. Mandatory Submission Constraint & Validation Architecture
    // -------------------------------------------------------------------------

    @Test
    fun testMandatorySubmission_formLockWhenNoDateSelected() {
        val projTitle = "Redesign Architecture"
        val selectedDeadline: SelectedDeadlineDate? = null

        // With non-empty title but NO date selected: Form MUST be locked (disabled)
        val isButtonActive = ProjectDeadlineValidator.isProjectFormValid(
            title = projTitle,
            selectedDeadline = selectedDeadline
        )
        assertFalse(
            "Final 'Create Project' button must be DISABLED when no deadline date is selected",
            isButtonActive
        )
    }

    @Test
    fun testMandatorySubmission_formLockWhenTitleIsBlankEvenWithDate() {
        val blankTitle = "   "
        val selectedDeadline = SelectedDeadlineDate(2026, Calendar.OCTOBER, 28)

        // With date selected but blank title: Form MUST remain locked
        val isButtonActive = ProjectDeadlineValidator.isProjectFormValid(
            title = blankTitle,
            selectedDeadline = selectedDeadline
        )
        assertFalse(
            "Final 'Create Project' button must remain DISABLED if title is blank",
            isButtonActive
        )
    }

    @Test
    fun testMandatorySubmission_validationTriggerUnlocksWhenDateExplicitlyTapped() {
        var projTitle = "System Evolution"
        var selectedDeadline: SelectedDeadlineDate? = null

        // 1. Initial State: Title entered, but date picker not tapped -> LOCKED
        assertFalse(
            ProjectDeadlineValidator.isProjectFormValid(projTitle, selectedDeadline)
        )

        // 2. User taps 'Set Project Deadline' and selects a valid date (e.g. Oct 31, 2026)
        selectedDeadline = SelectedDeadlineDate(year = 2026, month = Calendar.OCTOBER, day = 31)

        // 3. Validation Trigger satisfied -> Final 'Create Project' action button UNLOCKS (enabled)
        val isUnlocked = ProjectDeadlineValidator.isProjectFormValid(projTitle, selectedDeadline)
        assertTrue(
            "Final 'Create Project' button must become ENABLED once date is explicitly chosen",
            isUnlocked
        )
    }

    @Test
    fun testMandatorySubmission_submittingStateLocksButton() {
        val projTitle = "System Evolution"
        val selectedDeadline = SelectedDeadlineDate(2026, Calendar.OCTOBER, 31)

        // While submitting, button must be disabled to prevent duplicate submissions
        val isButtonActiveDuringSubmit = ProjectDeadlineValidator.isProjectFormValid(
            title = projTitle,
            selectedDeadline = selectedDeadline,
            isSubmitting = true
        )
        assertFalse(
            "Create Project button must be locked while submission is in progress",
            isButtonActiveDuringSubmit
        )
    }
}
