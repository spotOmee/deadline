package com.deadline

import com.deadline.domain.GlobalTimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GlobalTimeManagerTest {

    @Test
    fun testToggleLabels() {
        // OFF Position: "12 hour IST"
        assertEquals("12 hour IST", GlobalTimeManager.getToggleLabel(false))
        // ON Position: "24 hour Global Timing"
        assertEquals("24 hour Global Timing", GlobalTimeManager.getToggleLabel(true))
    }

    @Test
    fun testTimeZones() {
        assertEquals("Asia/Kolkata", GlobalTimeManager.getTimeZone(false).id)
        assertEquals("UTC", GlobalTimeManager.getTimeZone(true).id)

        assertEquals("IST", GlobalTimeManager.getTimeZoneLabel(false))
        assertEquals("UTC", GlobalTimeManager.getTimeZoneLabel(true))
    }

    @Test
    fun testHourFormatting() {
        // 24-hour UTC format
        assertEquals("00:00", GlobalTimeManager.formatHour(0, true))
        assertEquals("09:00", GlobalTimeManager.formatHour(9, true))
        assertEquals("14:00", GlobalTimeManager.formatHour(14, true))
        assertEquals("23:00", GlobalTimeManager.formatHour(23, true))

        // 12-hour IST format
        assertEquals("12:00 AM", GlobalTimeManager.formatHour(0, false))
        assertEquals("09:00 AM", GlobalTimeManager.formatHour(9, false))
        assertEquals("12:00 PM", GlobalTimeManager.formatHour(12, false))
        assertEquals("02:00 PM", GlobalTimeManager.formatHour(14, false))
        assertEquals("11:00 PM", GlobalTimeManager.formatHour(23, false))
    }

    @Test
    fun testTimeRangeFormatting() {
        // 14:00 to 15:30 (90 minutes) -> startMinute = 14*60 = 840
        val startMinute = 840
        val durationMinutes = 90

        assertEquals(
            "14:00 - 15:30 (90m)",
            GlobalTimeManager.formatTimeRange(startMinute, durationMinutes, true)
        )
        assertEquals(
            "02:00 PM - 03:30 PM (90m)",
            GlobalTimeManager.formatTimeRange(startMinute, durationMinutes, false)
        )
    }

    @Test
    fun testTimestampFormattingAcrossTimezones() {
        // Fixed epoch: 2026-10-17 10:00:00 UTC = 2026-10-17 15:30:00 IST (+05:30)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.OCTOBER, 17, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val epochMs = cal.timeInMillis

        // UTC Standard (24H)
        val utcFormatted = GlobalTimeManager.formatTimestamp(epochMs, true)
        assertTrue("Expected 10:00 in UTC timestamp, got: $utcFormatted", utcFormatted.contains("10:00"))
        assertTrue("Expected UTC tag, got: $utcFormatted", utcFormatted.contains("UTC"))

        // IST Lock (12H)
        val istFormatted = GlobalTimeManager.formatTimestamp(epochMs, false)
        assertTrue("Expected 03:30 PM in IST timestamp, got: $istFormatted", istFormatted.contains("03:30 PM"))
        assertTrue("Expected IST tag, got: $istFormatted", istFormatted.contains("IST"))
    }

    @Test
    fun testTimeOnlyFormatting() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.OCTOBER, 17, 14, 30, 45)
            set(Calendar.MILLISECOND, 0)
        }
        val epochMs = cal.timeInMillis

        // 24H UTC
        val utcTime = GlobalTimeManager.formatTimeOnly(epochMs, is24Hour = true)
        assertEquals("14:30", utcTime)

        val utcTimeWithSec = GlobalTimeManager.formatTimeOnly(epochMs, is24Hour = true, includeSeconds = true)
        assertEquals("14:30:45", utcTimeWithSec)

        // 12H IST (14:30 UTC = 20:00 IST)
        val istTime = GlobalTimeManager.formatTimeOnly(epochMs, is24Hour = false)
        assertEquals("08:00 PM", istTime)

        val istTimeWithSec = GlobalTimeManager.formatTimeOnly(epochMs, is24Hour = false, includeSeconds = true)
        assertEquals("08:00:45 PM", istTimeWithSec)
    }

    @Test
    fun testCalendarAndTimeZoneSwitching() {
        val cal24 = GlobalTimeManager.getCurrentCalendar(is24Hour = true)
        assertEquals("UTC", cal24.timeZone.id)

        val cal12 = GlobalTimeManager.getCurrentCalendar(is24Hour = false)
        assertEquals("Asia/Kolkata", cal12.timeZone.id)
    }

    @Test
    fun testEventElapsedCalculation() {
        val todayStr = "2026-10-29"
        val yesterdayStr = "2026-10-28"
        val tomorrowStr = "2026-10-30"

        // Past date event is always elapsed
        assertTrue(
            "Event on yesterday must be elapsed",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 60,
                eventDate = yesterdayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 400
            )
        )

        // Future date event is never elapsed
        org.junit.Assert.assertFalse(
            "Event on tomorrow must not be elapsed",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 60,
                eventDate = tomorrowStr,
                currentDate = todayStr,
                currentMinuteOfDay = 1200
            )
        )

        // Today: Event ended earlier today (9:00 - 10:00 AM, now is 10:15 AM)
        assertTrue(
            "Event ending at 10:00 AM must be elapsed at 10:15 AM",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 60,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 615
            )
        )

        // Today: Event exactly at completion boundary (9:00 - 10:00 AM, now is exactly 10:00 AM)
        assertTrue(
            "Event ending at 10:00 AM must be elapsed at exactly 10:00 AM (600m)",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 60,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 600
            )
        )

        // Today: Event still in progress (9:00 - 10:00 AM, now is 9:30 AM)
        org.junit.Assert.assertFalse(
            "Event ending at 10:00 AM must NOT be elapsed at 9:30 AM (570m)",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 60,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 570
            )
        )

        // Today: Future event today (2:00 PM - 3:00 PM, now is 10:00 AM)
        org.junit.Assert.assertFalse(
            "Future event today must NOT be elapsed",
            GlobalTimeManager.isEventElapsed(
                startMinute = 840,
                durationMinutes = 60,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 600
            )
        )

        // Event with 0 duration: elapsed once current minute reaches startMinute
        assertTrue(
            "Zero-duration event at 9:00 AM must be elapsed at 9:00 AM",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 0,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 540
            )
        )
        org.junit.Assert.assertFalse(
            "Zero-duration event at 9:00 AM must NOT be elapsed at 8:59 AM",
            GlobalTimeManager.isEventElapsed(
                startMinute = 540,
                durationMinutes = 0,
                eventDate = todayStr,
                currentDate = todayStr,
                currentMinuteOfDay = 539
            )
        )
    }

    @Test
    fun testTimeBlockPassedEvaluation() {
        val todayStr = "2026-10-29"
        val pastBlock = com.deadline.data.local.TimeBlock(
            id = "b1",
            title = "Morning Architecture",
            date = todayStr,
            startMinute = 9 * 60, // 540
            durationMinutes = 60 // Ends at 600
        )

        // Now is 10:30 AM (630 min) -> Passed
        assertTrue(
            "TimeBlock ending at 10:00 AM must be passed at 10:30 AM",
            GlobalTimeManager.isTimeBlockPassed(
                block = pastBlock,
                todayDate = todayStr,
                currentMinuteOfDay = 630
            )
        )

        // Now is 08:30 AM (510 min) -> Not passed
        org.junit.Assert.assertFalse(
            "TimeBlock starting at 09:00 AM must NOT be passed at 08:30 AM",
            GlobalTimeManager.isTimeBlockPassed(
                block = pastBlock,
                todayDate = todayStr,
                currentMinuteOfDay = 510
            )
        )
    }
}
