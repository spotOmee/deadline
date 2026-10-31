package com.deadline

import com.deadline.data.local.TimeBlock
import com.deadline.domain.GlobalTimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Mission Requirements:
 * 1. Timeline Vertical Grid & Time Axis Restoration:
 *    - Reverts the "Timeline" tab layout to display a continuous 24-hour / 12-hour vertical time axis with aligned horizontal grid lines.
 *    - Time Range: Fixed scrolling axis from 08:00 (8:00 AM) to 22:00 (10:00 PM).
 *    - Format Toggle: Strictly adheres to 12-hour (IST) and 24-hour (global) user settings toggle.
 *    - Axis Positioning: Pins time labels to the absolute left edge.
 *    - Horizontal Grid Lines: Extends lines from each hour mark across screen width, intersecting left axis labels.
 *    - Event Placement & Scaling: Positions events vertically based on scheduled start time with proportional card height.
 * 2. Passed Event Visual State:
 *    - Immediate transition to visually dimmed state once an event's time has elapsed.
 *    - Logic handles past dates, today's elapsed time, and future dates accurately.
 */
class TimelineDisplayAndEventStateTest {

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
    // 1. Timeline Vertical Grid & Time Axis Restoration (12h IST & 24h Global)
    // -------------------------------------------------------------------------

    @Test
    fun testProjectsScreen_verticalTimelineGridAndAxisRestoredWith12h24hToggle() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. Timeline sub-tab view exists
        assertTrue(
            "ProjectsScreen must contain DAY TIMELINE view section",
            content.contains("// --- VIEW 2: DAY TIMELINE (VISUAL TIME-BLOCKING) ---")
        )

        // 2. Continuous vertical scrolling axis fixed from 08:00 to 22:00
        assertTrue(
            "Timeline view must define fixed scrolling axis from 08:00 to 22:00",
            content.contains("(8..22).toList()")
        )
        assertTrue(
            "ProjectsScreen timeline must iterate hours along vertical axis",
            content.contains("items(hours) { hour ->")
        )

        // 3. Time display dynamically formatted with 12h / 24h toggle from userSettings
        assertTrue(
            "Timeline axis must render GlobalTimeManager.formatHour with userSettings.timeFormat24Hour",
            content.contains("GlobalTimeManager.formatHour(hour, userSettings.timeFormat24Hour)")
        )

        // 4. Time labels pinned to absolute left edge
        assertTrue(
            "Time labels must be aligned to the start (absolute left edge)",
            content.contains("textAlign = TextAlign.Start")
        )

        // 5. Horizontal grid line extends across screen width, intersecting time label
        assertTrue(
            "Row must vertically center time label and horizontal grid line for precise intersection",
            content.contains("verticalAlignment = Alignment.CenterVertically")
        )
        assertTrue(
            "Horizontal grid line must extend across screen width with 1.dp height",
            content.contains(".weight(1f)") && content.contains(".height(1.dp)")
        )

        // 6. Proportional card height scaling based on duration
        assertTrue(
            "Event card height must be proportional to durationMinutes within timeline grid",
            content.contains("durationMinutes.coerceIn(15, 360) / 60f")
        )

        // 7. Events positioned vertically based on scheduled start time
        assertTrue(
            "Events must be offset vertically based on startMinute within the hour",
            content.contains("minuteOffset = (block.startMinute - hourMinute)")
        )

        // 8. Sorting and swipe-to-delete are preserved
        assertTrue(
            "Timeline view must sort timeBlocks by startMinute",
            content.contains("timeBlocks.sortedBy { it.startMinute }")
        )
        assertTrue(
            "TimeBlock card must wrap UniversalSwipeableItem with deleteTimeBlock",
            content.contains("UniversalSwipeableItem(") &&
            content.contains("repository.deleteTimeBlock(block.id)")
        )

        // 9. Empty state placeholder removed so grid lines remain clean and uninterrupted
        assertFalse(
            "Empty state placeholder must be removed from timeline so grid lines remain clean and uninterrupted",
            content.contains("StandardEmptyState(") &&
            content.contains("title = \"No scheduled events\"")
        )

        // 10. Apple-style accent yellow used for timeline active hour and grid line
        assertTrue(
            "ProjectsScreen timeline must use Apple-style accent yellow (BrandAmberEnd)",
            content.contains("BrandAmberEnd") &&
            content.contains("color = if (isHourActive) BrandAmberEnd")
        )
    }

    @Test
    fun testPlanScreen_verticalTimelineGridAndAxisRestoredWith12h24hToggle() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        assertTrue("PlanScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. Fixed scrolling axis 08:00 to 22:00
        assertTrue(
            "PlanScreen must define fixed scrolling axis from 08:00 to 22:00",
            content.contains("(8..22).toList()")
        )

        // 2. Renders GlobalTimeManager.formatHour with userSettings.timeFormat24Hour pinned on left
        assertTrue(
            "PlanScreen must render GlobalTimeManager.formatHour with userSettings.timeFormat24Hour",
            content.contains("GlobalTimeManager.formatHour(hour, userSettings.timeFormat24Hour)")
        )

        // 3. TimeBlockCard scales proportionally with duration
        assertTrue(
            "TimeBlockCard in PlanScreen must scale proportionally with durationMinutes",
            content.contains("durationMinutes.coerceIn(15, 360) / 60f")
        )

        // 4. Apple-style accent yellow (BrandAmberEnd) for active hour
        assertTrue(
            "PlanScreen timeline must use Apple-style accent yellow (BrandAmberEnd)",
            content.contains("BrandAmberEnd") &&
            content.contains("color = if (isCurrentHour) BrandAmberEnd")
        )
    }

    @Test
    fun testGlobalTimeManager_timelineAxis12hAnd24hFormatting() {
        // Range 08:00 (8) to 22:00 (22)
        // 24-hour Global format (HH:MM)
        assertEquals("08:00", GlobalTimeManager.formatHour(8, true))
        assertEquals("09:00", GlobalTimeManager.formatHour(9, true))
        assertEquals("12:00", GlobalTimeManager.formatHour(12, true))
        assertEquals("18:00", GlobalTimeManager.formatHour(18, true))
        assertEquals("22:00", GlobalTimeManager.formatHour(22, true))

        // 12-hour IST format (hh:00 a)
        assertEquals("08:00 AM", GlobalTimeManager.formatHour(8, false))
        assertEquals("09:00 AM", GlobalTimeManager.formatHour(9, false))
        assertEquals("12:00 PM", GlobalTimeManager.formatHour(12, false))
        assertEquals("06:00 PM", GlobalTimeManager.formatHour(18, false))
        assertEquals("10:00 PM", GlobalTimeManager.formatHour(22, false))
    }

    // -------------------------------------------------------------------------
    // 2. Passed Event Visual State & Dimming Transition
    // -------------------------------------------------------------------------

    @Test
    fun testProjectsScreen_eventDimmingTransitionArchitecture() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/projects/ProjectsScreen.kt")
        assertTrue("ProjectsScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. Evaluates isPassed using GlobalTimeManager.isEventElapsed
        assertTrue(
            "ProjectsScreen must evaluate isPassed using GlobalTimeManager.isEventElapsed",
            content.contains("val isPassed = GlobalTimeManager.isEventElapsed(")
        )

        // 2. Animates dimming transition for passed status
        assertTrue(
            "ProjectsScreen must implement animateFloatAsState for card dimming",
            content.contains("val cardAlpha by animateFloatAsState(") &&
            content.contains("if (isPassed) 0.45f else 1f")
        )

        // 3. Applies dimming alpha to card graphicsLayer
        assertTrue(
            "SquircleCard must apply alpha in graphicsLayer",
            content.contains("graphicsLayer { alpha = cardAlpha }")
        )

        // 4. Dynamic status badge reflects Past vs Scheduled state
        assertTrue(
            "Status pill badge must transition text to Past when passed",
            content.contains("text = if (isPassed) \"Past\" else \"Scheduled\"")
        )

        // 5. Reactive time ticker observes live minute changes for immediate transition
        assertTrue(
            "ProjectsScreen must maintain liveMinuteOfDay ticker for immediate transition",
            content.contains("liveMinuteOfDay") &&
            content.contains("delay(1000L)")
        )
    }

    @Test
    fun testPlanScreen_timeBlockCardSupportsDimmedVisualState() {
        val file = getSourceFile("app/src/main/kotlin/com/deadline/ui/plan/PlanScreen.kt")
        assertTrue("PlanScreen.kt must exist", file.exists())
        val content = file.readText()

        // 1. TimeBlockCard accepts isPassed parameter
        assertTrue(
            "TimeBlockCard must accept isPassed parameter",
            content.contains("isPassed: Boolean = false")
        )

        // 2. Implements dimming animation
        assertTrue(
            "TimeBlockCard must animate cardAlpha with 0.45f dimming target",
            content.contains("val cardAlpha by animateFloatAsState(") &&
            content.contains("if (isPassed) 0.45f else 1f")
        )

        // 3. Applies dimming alpha to SquircleCard graphicsLayer
        assertTrue(
            "TimeBlockCard graphicsLayer must apply alpha = cardAlpha",
            content.contains("alpha = cardAlpha")
        )

        // 4. Status pill badge indicates Past status
        assertTrue(
            "TimeBlockCard badge must show Past status when isPassed",
            content.contains("text = if (isPassed) \"Past\" else \"Scheduled\"")
        )
    }

    // -------------------------------------------------------------------------
    // 3. Event Elapsed Logic Verification
    // -------------------------------------------------------------------------

    @Test
    fun testEventElapsed_pastDatesAndFutureDates() {
        val today = "2026-10-29"
        val pastDate = "2026-10-15"
        val futureDate = "2026-11-05"

        // Any event on a past date is elapsed regardless of start/end minute
        assertTrue(
            "Event on past date must be elapsed",
            GlobalTimeManager.isEventElapsed(
                startMinute = 14 * 60,
                durationMinutes = 60,
                eventDate = pastDate,
                currentDate = today,
                currentMinuteOfDay = 8 * 60
            )
        )

        // Any event on a future date is NOT elapsed regardless of current minute
        assertFalse(
            "Event on future date must NOT be elapsed",
            GlobalTimeManager.isEventElapsed(
                startMinute = 8 * 60,
                durationMinutes = 60,
                eventDate = futureDate,
                currentDate = today,
                currentMinuteOfDay = 23 * 60
            )
        )
    }

    @Test
    fun testEventElapsed_todayElapsedTransitionsImmediately() {
        val today = "2026-10-29"
        // Event scheduled for 09:00 - 10:00 (540 to 600 min)
        val startMin = 9 * 60
        val durationMin = 60
        val endMin = startMin + durationMin // 600

        // 1. One minute before event finishes (09:59 AM) -> Still active, NOT elapsed
        assertFalse(
            "Event must not be elapsed while in progress",
            GlobalTimeManager.isEventElapsed(
                startMinute = startMin,
                durationMinutes = durationMin,
                eventDate = today,
                currentDate = today,
                currentMinuteOfDay = 599
            )
        )

        // 2. Exactly at completion time (10:00 AM) -> Elapsed!
        assertTrue(
            "Event must immediately transition to elapsed at completion time",
            GlobalTimeManager.isEventElapsed(
                startMinute = startMin,
                durationMinutes = durationMin,
                eventDate = today,
                currentDate = today,
                currentMinuteOfDay = 600
            )
        )

        // 3. Ten minutes after completion (10:10 AM) -> Elapsed!
        assertTrue(
            "Event must remain elapsed after completion time",
            GlobalTimeManager.isEventElapsed(
                startMinute = startMin,
                durationMinutes = durationMin,
                eventDate = today,
                currentDate = today,
                currentMinuteOfDay = 610
            )
        )
    }

    @Test
    fun testTimeBlockPassed_evaluationMatchesTimeBlockEntity() {
        val today = "2026-10-29"
        val morningBlock = TimeBlock(
            id = "tb-arch",
            title = "Morning Standup",
            date = today,
            startMinute = 9 * 60, // 540
            durationMinutes = 30, // 570
            color = "#5E5CE6"
        )

        // Prior to end (09:20 AM = 560 min)
        assertFalse(
            "Morning standup must NOT be passed before 09:30 AM",
            GlobalTimeManager.isTimeBlockPassed(
                block = morningBlock,
                todayDate = today,
                currentMinuteOfDay = 560
            )
        )

        // Exactly at end (09:30 AM = 570 min)
        assertTrue(
            "Morning standup must be passed at 09:30 AM",
            GlobalTimeManager.isTimeBlockPassed(
                block = morningBlock,
                todayDate = today,
                currentMinuteOfDay = 570
            )
        )

        // After end (09:31 AM = 571 min)
        assertTrue(
            "Morning standup must be passed after 09:30 AM",
            GlobalTimeManager.isTimeBlockPassed(
                block = morningBlock,
                todayDate = today,
                currentMinuteOfDay = 571
            )
        )
    }
}
