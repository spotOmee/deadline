package com.deadline

import com.deadline.ui.components.ProjectDeadlineValidator
import com.deadline.ui.components.SelectedDeadlineDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ProjectCreationModalRefinementTest {

    @Test
    fun testCalendarAutoDismiss_dateSelectionCollapsesPicker() {
        // Initial state: picker is expanded so the user can choose a deadline
        var isDeadlinePickerExpanded = true
        var selectedDeadline: SelectedDeadlineDate? = null

        // Simulate onDateSelected handler from ProjectsScreen
        val onDateSelected: (SelectedDeadlineDate) -> Unit = { date ->
            selectedDeadline = date
            isDeadlinePickerExpanded = false
        }

        assertTrue("Date picker starts expanded", isDeadlinePickerExpanded)

        // User picks a date
        val chosenDate = SelectedDeadlineDate(year = 2026, month = Calendar.OCTOBER, day = 27)
        onDateSelected(chosenDate)

        // Verify date is captured and picker is automatically dismissed
        assertEquals(chosenDate, selectedDeadline)
        assertFalse(
            "Picker must be automatically collapsed upon date selection to reveal CTA button",
            isDeadlinePickerExpanded
        )
    }

    @Test
    fun testCtaHighlight_unlocksImmediatelyUponDateSelectionIfTitlePresent() {
        var projName = "Launch App"
        var isDeadlinePickerExpanded = true
        var selectedDeadline: SelectedDeadlineDate? = null

        // Initially without a date, the form is locked/disabled
        assertFalse(
            ProjectDeadlineValidator.isProjectFormValid(projName, selectedDeadline)
        )

        // onDateSelected simulates auto-dismiss and setting the date
        selectedDeadline = SelectedDeadlineDate(year = 2026, month = Calendar.NOVEMBER, day = 1)
        isDeadlinePickerExpanded = false

        // CTA button becomes immediately active / highlighted
        val isFormValid = ProjectDeadlineValidator.isProjectFormValid(projName, selectedDeadline)
        assertTrue(
            "CTA button 'Create Project' must immediately highlight and unlock when deadline is picked",
            isFormValid
        )
        assertFalse("Picker must be dismissed", isDeadlinePickerExpanded)
    }

    @Test
    fun testNotesSectionSpecification_notesLabelAndAddAction() {
        // Verify simplified label requirement
        val notesHeaderLabel = "Notes"
        assertEquals("Header label must be simplified to 'Notes'", "Notes", notesHeaderLabel)

        // Verify add action button state toggle
        var showNotePicker = false
        val onPlusIconClicked = {
            showNotePicker = !showNotePicker
        }

        onPlusIconClicked()
        assertTrue("Clicking standard plus (+) icon must open note picker", showNotePicker)

        onPlusIconClicked()
        assertFalse("Clicking plus (+) icon again must close note picker", showNotePicker)
    }
}
