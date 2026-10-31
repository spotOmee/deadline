package com.deadline.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules and manages the Scheduled Task Reminder & Phone Ringtone Alarm Pipeline:
 * 1. Advance Notification Protocol: exactly 30 minutes prior to scheduled task time.
 * 2. Primary Ringtone Alarm Integration: configured with the System Alarm Manager,
 *    firing precisely at the scheduled task time with the user's primary call ringtone.
 */
object TaskReminderScheduler {

    const val ADVANCE_NOTICE_MINUTES = 30

    /**
     * Schedules both stages of the reminder protocol for a scheduled task.
     */
    fun scheduleTaskReminders(
        context: Context,
        taskId: String,
        taskTitle: String,
        dateStr: String,
        startMinute: Int
    ) {
        val scheduledTimeMillis = calculateScheduledTimestamp(dateStr, startMinute)
        val now = System.currentTimeMillis()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val baseRequestCode = taskId.hashCode()
        val preAlertRequestCode = baseRequestCode * 31 + 1
        val atTimeAlarmRequestCode = baseRequestCode * 31 + 2

        // Stage 1: Advance Heads-Up Notification Protocol (exactly 30 minutes prior)
        val preAlertTimeMillis = scheduledTimeMillis - (ADVANCE_NOTICE_MINUTES * 60 * 1000L)
        if (preAlertTimeMillis > now) {
            val preAlertIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = TaskReminderReceiver.ACTION_PRE_TASK_ALERT
                putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
                putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
                putExtra(TaskReminderReceiver.EXTRA_START_MINUTE, startMinute)
                putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, baseRequestCode)
            }
            val preAlertPendingIntent = PendingIntent.getBroadcast(
                context,
                preAlertRequestCode,
                preAlertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExactAlarm(alarmManager, preAlertTimeMillis, preAlertPendingIntent, isAlarmClock = false)
        }

        // Stage 2: Primary Ringtone Alarm Integration (fires precisely at scheduled task time)
        if (scheduledTimeMillis > now) {
            val atTimeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = TaskReminderReceiver.ACTION_AT_TIME_ALARM
                putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
                putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
                putExtra(TaskReminderReceiver.EXTRA_START_MINUTE, startMinute)
                putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, baseRequestCode)
            }
            val atTimePendingIntent = PendingIntent.getBroadcast(
                context,
                atTimeAlarmRequestCode,
                atTimeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExactAlarm(alarmManager, scheduledTimeMillis, atTimePendingIntent, isAlarmClock = true)
        }
    }

    /**
     * Cancels both the advance heads-up alert and at-time mandatory alarm for a task.
     */
    fun cancelTaskReminders(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val baseRequestCode = taskId.hashCode()
        val preAlertRequestCode = baseRequestCode * 31 + 1
        val atTimeAlarmRequestCode = baseRequestCode * 31 + 2

        val preAlertIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_PRE_TASK_ALERT
        }
        val preAlertPendingIntent = PendingIntent.getBroadcast(
            context,
            preAlertRequestCode,
            preAlertIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (preAlertPendingIntent != null) {
            alarmManager.cancel(preAlertPendingIntent)
            preAlertPendingIntent.cancel()
        }

        val atTimeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_AT_TIME_ALARM
        }
        val atTimePendingIntent = PendingIntent.getBroadcast(
            context,
            atTimeAlarmRequestCode,
            atTimeIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (atTimePendingIntent != null) {
            alarmManager.cancel(atTimePendingIntent)
            atTimePendingIntent.cancel()
        }

        // Cancel any active notification and silence any active ringtone alarm
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancel(baseRequestCode)
            com.deadline.service.AlarmRingtonePlayer.stop()
        } catch (_: Exception) {}
    }

    /**
     * Configures the System Alarm Manager with high-precision wakeup scheduling.
     * Uses setAlarmClock for at-time alarms to guarantee precise execution through Doze mode,
     * with graceful fallbacks across Android API levels.
     */
    private fun setExactAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        isAlarmClock: Boolean = false
    ) {
        if (isAlarmClock) {
            try {
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
                return
            } catch (_: Exception) {}
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    fun calculateScheduledTimestamp(dateStr: String, startMinute: Int): Long {
        val parts = dateStr.split("-")
        val cal = Calendar.getInstance()
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
            val m = (parts[1].toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
            val d = parts[2].toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
        }
        val hour = (startMinute / 60).coerceIn(0, 23)
        val minute = (startMinute % 60).coerceIn(0, 59)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
