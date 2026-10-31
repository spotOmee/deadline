package com.deadline.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {
    fun click(context: Context) {
        vibrate(context, 15)
    }

    fun tick(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vibrate(context, 12)
            }
        } catch (_: Exception) {
            vibrate(context, 12)
        }
    }

    fun selection(context: Context) {
        vibrate(context, 10)
    }

    fun longPress(context: Context) {
        vibrate(context, 25)
    }

    fun success(context: Context) {
        vibrate(context, 40)
    }

    fun heavy(context: Context) {
        vibrate(context, 50)
    }

    fun heavyImpact(context: Context) {
        heavy(context)
    }

    fun alert(context: Context) {
        vibrate(context, 80)
    }

    fun warning(context: Context) {
        alert(context)
    }

    private fun vibrate(context: Context, milliseconds: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(milliseconds)
            }
        } catch (_: Exception) {}
    }
}
