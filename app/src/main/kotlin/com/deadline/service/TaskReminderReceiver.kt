package com.deadline.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.deadline.MainActivity

/**
 * Handles the Scheduled Task Reminder & Phone Ringtone Alarm Pipeline:
 * 1. Advance Notification Protocol: Heads-up notification exactly 30 minutes prior.
 * 2. Primary Ringtone Alarm Integration: Persistent alarm notification + user's primary call ringtone.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PRE_TASK_ALERT = "com.deadline.action.PRE_TASK_ALERT"
        const val ACTION_AT_TIME_ALARM = "com.deadline.action.AT_TIME_ALARM"
        const val ACTION_DISMISS_ALARM = "com.deadline.action.DISMISS_ALARM"

        const val CHANNEL_PRE_ALERT = "channel_task_advance_heads_up"
        const val CHANNEL_AT_TIME_ALARM = "channel_task_primary_ringtone_alarm"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_START_MINUTE = "extra_start_minute"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Restore scheduled reminders across device boot or package replace
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                val repo = com.deadline.data.local.DeadlineRepository(context.applicationContext)
                repo.loadTodayTimeBlocks()
            } catch (_: Exception) {}
            return
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: "default_task"
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Scheduled Task"
        val startMinute = intent.getIntExtra(EXTRA_START_MINUTE, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())

        when (action) {
            ACTION_PRE_TASK_ALERT -> {
                showPreScheduledAlert(context, taskId, taskTitle, startMinute, notificationId)
            }
            ACTION_AT_TIME_ALARM -> {
                showAtTimeMandatoryAlarm(context, taskId, taskTitle, startMinute, notificationId)
            }
            ACTION_DISMISS_ALARM -> {
                dismissAlarm(context, notificationId)
            }
        }
    }

    /**
     * Advance Notification Protocol:
     * Triggers a heads-up notification exactly 30 minutes prior to a scheduled task's start time.
     */
    private fun showPreScheduledAlert(
        context: Context,
        taskId: String,
        taskTitle: String,
        startMinute: Int,
        notificationId: Int
    ) {
        createNotificationChannels(context)

        val timeFormatted = formatTimeOfDay(startMinute)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PRE_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming: $taskTitle")
            .setContentText("Starts in 30 minutes at $timeFormatted")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Primary Ringtone Alarm Integration:
     * Plays the user's primary phone call ringtone as the audible alarm and posts an alarm notification.
     */
    private fun showAtTimeMandatoryAlarm(
        context: Context,
        taskId: String,
        taskTitle: String,
        startMinute: Int,
        notificationId: Int
    ) {
        createNotificationChannels(context)

        // Play the user's primary phone call ringtone as the audible alarm
        AlarmRingtonePlayer.play(context)

        val timeFormatted = formatTimeOfDay(startMinute)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action button to silence primary ringtone and cancel notification
        val dismissIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AT_TIME_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Task Starting: $taskTitle")
            .setContentText("Scheduled task is starting now ($timeFormatted)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(contentPendingIntent, true)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss Alarm", dismissPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun dismissAlarm(context: Context, notificationId: Int) {
        AlarmRingtonePlayer.stop()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up legacy channels if present
            try {
                notificationManager.deleteNotificationChannel("channel_task_pre_alert")
                notificationManager.deleteNotificationChannel("channel_task_mandatory_alarm")
            } catch (_: Exception) {}

            // 1. Advance Alert Channel: High importance with vibration & lights for heads-up presentation
            val preAlertChannel = NotificationChannel(
                CHANNEL_PRE_ALERT,
                "Advance Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Advance 30-minute heads-up notice for scheduled tasks"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(preAlertChannel)

            // 2. At-Time Mandatory Alarm Channel: High importance + primary phone call ringtone sound
            val primaryCallRingtoneUri = AlarmRingtonePlayer.getPrimaryCallRingtoneUri(context)

            val mandatoryAlarmChannel = NotificationChannel(
                CHANNEL_AT_TIME_ALARM,
                "Mandatory Task Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent at-time alarms with user's primary phone call ringtone"
                setSound(
                    primaryCallRingtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(mandatoryAlarmChannel)
        }
    }

    private fun formatTimeOfDay(startMinute: Int): String {
        val h = startMinute / 60
        val m = startMinute % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }
}
