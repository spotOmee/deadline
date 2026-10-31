package com.deadline.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Manages playing and stopping the user's primary phone call ringtone as the audible alarm
 * for the At-Time Mandatory Alarm stage.
 */
object AlarmRingtonePlayer {
    private var currentRingtone: Ringtone? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    /**
     * Resolves the user's primary phone call ringtone URI.
     * Prioritizes TYPE_RINGTONE (incoming call ringtone), with graceful fallbacks.
     */
    fun getPrimaryCallRingtoneUri(context: Context): Uri {
        return try {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } catch (_: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
    }

    @Synchronized
    fun play(context: Context) {
        stop()
        try {
            val ringtoneUri = getPrimaryCallRingtoneUri(context)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Phone call ringtones should loop continuously until dismissed on supported versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }

            ringtone?.play()
            currentRingtone = ringtone

            // Auto-stop safety timeout after 60 seconds if left unattended
            mainHandler.removeCallbacks(autoStopRunnable)
            mainHandler.postDelayed(autoStopRunnable, 60_000L)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        try {
            mainHandler.removeCallbacks(autoStopRunnable)
            currentRingtone?.stop()
        } catch (_: Exception) {}
        currentRingtone = null
    }

    fun isPlaying(): Boolean = currentRingtone?.isPlaying == true
}
