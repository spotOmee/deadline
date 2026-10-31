package com.deadline.domain

import com.deadline.data.local.TimeBlock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Global Time Architecture Manager.
 *
 * Controls dynamic time standards across the application:
 * - OFF (false): 12-Hour Indian Standard Time (IST, UTC+05:30) lock.
 * - ON (true): 24-Hour Global Timing (UTC) standard.
 */
object GlobalTimeManager {
    val IST_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

    const val LABEL_12H_IST = "12 hour IST"
    const val LABEL_24H_GLOBAL = "24 hour Global Timing"

    /**
     * Resolves the active TimeZone based on the 24-hour global toggle:
     * - true: UTC standard
     * - false: Indian Standard Time (IST, UTC+05:30)
     */
    fun getTimeZone(is24Hour: Boolean): TimeZone {
        return if (is24Hour) UTC_TIME_ZONE else IST_TIME_ZONE
    }

    /**
     * Returns the short timezone designation tag ("UTC" or "IST").
     */
    fun getTimeZoneLabel(is24Hour: Boolean): String {
        return if (is24Hour) "UTC" else "IST"
    }

    /**
     * Returns the exact toggle display label specified in requirements:
     * - true: "24 hour Global Timing"
     * - false: "12 hour IST"
     */
    fun getToggleLabel(is24Hour: Boolean): String {
        return if (is24Hour) LABEL_24H_GLOBAL else LABEL_12H_IST
    }

    /**
     * Formats an hour index (0-23) for day timeline displays:
     * - 24-hour UTC: "00:00", "09:00", "14:00"
     * - 12-hour IST: "12:00 AM", "09:00 AM", "02:00 PM"
     */
    fun formatHour(hour: Int, is24Hour: Boolean): String {
        return if (is24Hour) {
            String.format(Locale.US, "%02d:00", hour)
        } else {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format(Locale.US, "%02d:00 %s", h, amPm)
        }
    }

    /**
     * Formats start and duration minute ranges for time-block entries:
     * - 24-hour UTC: "14:00 - 15:30 (90m)"
     * - 12-hour IST: "02:00 PM - 03:30 PM (90m)"
     */
    fun formatTimeRange(startMinute: Int, durationMinutes: Int, is24Hour: Boolean): String {
        val endMinute = startMinute + durationMinutes
        val startH = startMinute / 60
        val startM = startMinute % 60
        val endH = endMinute / 60
        val endM = endMinute % 60

        return if (is24Hour) {
            String.format(Locale.US, "%02d:%02d - %02d:%02d (%dm)", startH, startM, endH, endM, durationMinutes)
        } else {
            fun to12(h: Int, m: Int): String {
                val clampedH = h % 24
                val hr = if (clampedH == 0) 12 else if (clampedH > 12) clampedH - 12 else clampedH
                val amPm = if (clampedH < 12) "AM" else "PM"
                return String.format(Locale.US, "%02d:%02d %s", hr, m, amPm)
            }
            String.format(Locale.US, "%s - %s (%dm)", to12(startH, startM), to12(endH, endM), durationMinutes)
        }
    }

    /**
     * Determines whether a scheduled event's time has elapsed.
     * An event is in a passed state if:
     * 1. Its scheduled date is prior to the current date.
     * 2. Its scheduled date is today, and the current minute of the day has reached or passed
     *    the event's scheduled completion time (startMinute + durationMinutes).
     * Events on future dates are not elapsed.
     */
    fun isEventElapsed(
        startMinute: Int,
        durationMinutes: Int = 0,
        eventDate: String = "",
        currentDate: String = "",
        currentMinuteOfDay: Int = -1,
        is24Hour: Boolean = false
    ): Boolean {
        val today = if (currentDate.isNotBlank()) currentDate else getTodayDateString(is24Hour)
        val curMin = if (currentMinuteOfDay >= 0) currentMinuteOfDay else getCurrentMinuteOfDay(is24Hour)

        if (eventDate.isNotBlank()) {
            if (eventDate < today) return true
            if (eventDate > today) return false
        }

        val effectiveEnd = if (durationMinutes > 0) startMinute + durationMinutes else startMinute
        return curMin >= effectiveEnd
    }

    /**
     * Returns the current minute of day (0..1439) in the active timezone.
     */
    fun getCurrentMinuteOfDay(is24Hour: Boolean = false): Int {
        val cal = getCurrentCalendar(is24Hour)
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /**
     * Evaluates if a TimeBlock's scheduled duration has completely elapsed.
     */
    fun isTimeBlockPassed(
        block: TimeBlock,
        selectedDate: String = "",
        todayDate: String = "",
        currentMinuteOfDay: Int = -1,
        is24Hour: Boolean = false
    ): Boolean {
        val blockDate = block.date.ifBlank { selectedDate }
        return isEventElapsed(
            startMinute = block.startMinute,
            durationMinutes = block.durationMinutes,
            eventDate = blockDate,
            currentDate = todayDate,
            currentMinuteOfDay = currentMinuteOfDay,
            is24Hour = is24Hour
        )
    }

    /**
     * Formats a millisecond epoch timestamp for task, note, and log views:
     * - 24-hour UTC: "Oct 17, 14:30 UTC" (or with seconds: "Oct 17, 14:30:45 UTC")
     * - 12-hour IST: "Oct 17, 02:30 PM IST" (or with seconds: "Oct 17, 02:30:45 PM IST")
     */
    fun formatTimestamp(timestampMs: Long, is24Hour: Boolean, includeSeconds: Boolean = false): String {
        val pattern = if (is24Hour) {
            if (includeSeconds) "MMM d, HH:mm:ss 'UTC'" else "MMM d, HH:mm 'UTC'"
        } else {
            if (includeSeconds) "MMM d, hh:mm:ss a 'IST'" else "MMM d, hh:mm a 'IST'"
        }
        val sdf = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = getTimeZone(is24Hour)
        }
        return sdf.format(Date(timestampMs))
    }

    /**
     * Formats only time component (hh:mm a / HH:mm) with timezone context:
     * - 24-hour UTC: "14:30" (or "14:30:45")
     * - 12-hour IST: "02:30 PM" (or "02:30:45 PM")
     */
    fun formatTimeOnly(timestampMs: Long, is24Hour: Boolean, includeSeconds: Boolean = false): String {
        val pattern = if (is24Hour) {
            if (includeSeconds) "HH:mm:ss" else "HH:mm"
        } else {
            if (includeSeconds) "hh:mm:ss a" else "hh:mm a"
        }
        val sdf = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = getTimeZone(is24Hour)
        }
        return sdf.format(Date(timestampMs))
    }

    /**
     * Formats date in active timezone: "MMM dd, yyyy"
     */
    fun formatDate(timestampMs: Long, is24Hour: Boolean): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US).apply {
            timeZone = getTimeZone(is24Hour)
        }
        return sdf.format(Date(timestampMs))
    }

    /**
     * Computes today's date string (YYYY-MM-DD) synchronized with the active timezone.
     */
    fun getTodayDateString(is24Hour: Boolean): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = getTimeZone(is24Hour)
        }
        return sdf.format(Date())
    }

    /**
     * Returns a Calendar instance configured with the active timezone.
     */
    fun getCurrentCalendar(is24Hour: Boolean): Calendar {
        return Calendar.getInstance(getTimeZone(is24Hour), Locale.US)
    }

    /**
     * Returns live formatted current time.
     */
    fun getCurrentTimeFormatted(is24Hour: Boolean, includeSeconds: Boolean = false): String {
        return formatTimeOnly(System.currentTimeMillis(), is24Hour, includeSeconds)
    }
}
