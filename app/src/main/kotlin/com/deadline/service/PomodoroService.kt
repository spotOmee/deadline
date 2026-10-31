package com.deadline.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.deadline.data.local.ItemTimerPreset
import com.deadline.data.local.PomodoroType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PomodoroService : Service() {

    companion object {
        const val CHANNEL_ID = "deadline_pomodoro_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_SESSION_TYPE = "EXTRA_SESSION_TYPE"
        const val EXTRA_TOTAL_SECONDS = "EXTRA_TOTAL_SECONDS"

        private const val PREFS_NAME = "deadline_pomodoro_prefs"
        private const val KEY_REMAINING_SECONDS = "remaining_seconds"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_TASK_TITLE = "task_title"
        private const val KEY_SESSION_TYPE = "session_type"
        private const val KEY_TOTAL_SECONDS = "total_seconds"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"

        // Global StateFlow for UI binding
        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentTaskTitle = MutableStateFlow("")
        val currentTaskTitle: StateFlow<String> = _currentTaskTitle.asStateFlow()

        private val _currentSessionType = MutableStateFlow(PomodoroType.FOCUS)
        val currentSessionType: StateFlow<PomodoroType> = _currentSessionType.asStateFlow()

        private val _totalDurationSeconds = MutableStateFlow(0)
        val totalDurationSeconds: StateFlow<Int> = _totalDurationSeconds.asStateFlow()

        // Explicit per-item timer preset engine state
        private val _activePreset = MutableStateFlow<ItemTimerPreset?>(null)
        val activePreset: StateFlow<ItemTimerPreset?> = _activePreset.asStateFlow()

        private val _focusSeconds = MutableStateFlow(25 * 60)
        val focusSeconds: StateFlow<Int> = _focusSeconds.asStateFlow()

        private val _breakSeconds = MutableStateFlow(5 * 60)
        val breakSeconds: StateFlow<Int> = _breakSeconds.asStateFlow()

        private val _flowSeconds = MutableStateFlow(15 * 60)
        val flowSeconds: StateFlow<Int> = _flowSeconds.asStateFlow()

        fun commitPreset(
            context: Context,
            preset: ItemTimerPreset,
            taskTitle: String = "",
            sessionType: PomodoroType = PomodoroType.FOCUS
        ) {
            commitPreset(
                context = context,
                focusDuration = preset.focusDuration,
                breakDuration = preset.breakDuration,
                flowDuration = preset.flowDuration,
                taskTitle = taskTitle,
                sessionType = sessionType
            )
        }

        fun commitPreset(
            context: Context,
            focusDuration: Int,
            breakDuration: Int,
            flowDuration: Int,
            taskTitle: String = "",
            sessionType: PomodoroType = PomodoroType.FOCUS
        ) {
            val preset = ItemTimerPreset(focusDuration, breakDuration, flowDuration)
            _activePreset.value = preset
            _focusSeconds.value = focusDuration
            _breakSeconds.value = breakDuration
            _flowSeconds.value = flowDuration

            val durationToLoad = when (sessionType) {
                PomodoroType.FOCUS -> focusDuration
                PomodoroType.SHORT_BREAK -> breakDuration
                PomodoroType.LONG_BREAK -> flowDuration
            }

            // Set timer state to Ready / Paused with the configured values pre-loaded (do not auto-start countdown)
            resetTimer(
                context = context,
                seconds = durationToLoad,
                title = taskTitle,
                sessionType = sessionType,
                totalSeconds = durationToLoad
            )
        }

        fun commitPreset(
            preset: ItemTimerPreset,
            taskTitle: String = "",
            sessionType: PomodoroType = PomodoroType.FOCUS
        ) {
            _activePreset.value = preset
            _focusSeconds.value = preset.focusDuration
            _breakSeconds.value = preset.breakDuration
            _flowSeconds.value = preset.flowDuration
            _currentTaskTitle.value = taskTitle
            _currentSessionType.value = sessionType
            val durationToLoad = when (sessionType) {
                PomodoroType.FOCUS -> preset.focusDuration
                PomodoroType.SHORT_BREAK -> preset.breakDuration
                PomodoroType.LONG_BREAK -> preset.flowDuration
            }
            _remainingSeconds.value = durationToLoad
            _totalDurationSeconds.value = durationToLoad
            _isRunning.value = false
        }

        @Volatile
        private var activeServiceInstance: PomodoroService? = null

        private var isInitialized = false

        fun init(context: Context) {
            if (isInitialized) return
            isInitialized = true
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedSeconds = prefs.getInt(KEY_REMAINING_SECONDS, 0)
                val running = prefs.getBoolean(KEY_IS_RUNNING, false)
                val title = prefs.getString(KEY_TASK_TITLE, "") ?: ""
                val sessionTypeName = prefs.getString(KEY_SESSION_TYPE, PomodoroType.FOCUS.name) ?: PomodoroType.FOCUS.name
                val totalSecs = prefs.getInt(KEY_TOTAL_SECONDS, 0)
                val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)

                val parsedType = try {
                    PomodoroType.valueOf(sessionTypeName)
                } catch (_: Exception) {
                    PomodoroType.FOCUS
                }
                _currentSessionType.value = parsedType
                _totalDurationSeconds.value = if (totalSecs > 0) totalSecs else 0
                _currentTaskTitle.value = title

                // Ensure no lingering timer notification or channel exists
                dismissTimerNotification(context)

                if (running && lastUpdate > 0) {
                    val elapsedSec = ((System.currentTimeMillis() - lastUpdate) / 1000).toInt()
                    val remaining = (savedSeconds - elapsedSec).coerceAtLeast(0)
                    _remainingSeconds.value = remaining
                    _isRunning.value = remaining > 0
                    if (remaining > 0) {
                        startTimer(context, remaining, title, parsedType, _totalDurationSeconds.value)
                    } else {
                        persistState(context, 0, false, title, parsedType, _totalDurationSeconds.value)
                    }
                } else {
                    _remainingSeconds.value = savedSeconds
                    _isRunning.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun persistState(
            context: Context,
            seconds: Int,
            running: Boolean,
            title: String,
            sessionType: PomodoroType = _currentSessionType.value,
            totalSeconds: Int = _totalDurationSeconds.value
        ) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt(KEY_REMAINING_SECONDS, seconds)
                    .putBoolean(KEY_IS_RUNNING, running)
                    .putString(KEY_TASK_TITLE, title)
                    .putString(KEY_SESSION_TYPE, sessionType.name)
                    .putInt(KEY_TOTAL_SECONDS, totalSeconds)
                    .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateTaskTitle(title: String) {
            _currentTaskTitle.value = title
        }

        fun updateSessionType(sessionType: PomodoroType) {
            _currentSessionType.value = sessionType
        }

        fun startTimer(
            context: Context,
            seconds: Int,
            title: String,
            sessionType: PomodoroType = _currentSessionType.value,
            totalSeconds: Int = if (seconds > 0) seconds else _totalDurationSeconds.value
        ) {
            _remainingSeconds.value = seconds
            _isRunning.value = true
            _currentTaskTitle.value = title
            _currentSessionType.value = sessionType
            if (totalSeconds > 0) {
                _totalDurationSeconds.value = totalSeconds
            }
            persistState(context, seconds, true, title, sessionType, _totalDurationSeconds.value)

            val intent = Intent(context, PomodoroService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SESSION_TYPE, sessionType.name)
                putExtra(EXTRA_TOTAL_SECONDS, _totalDurationSeconds.value)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun pauseTimer(context: Context) {
            _isRunning.value = false
            persistState(context, _remainingSeconds.value, false, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
            try {
                activeServiceInstance?.timerJob?.cancel()
                activeServiceInstance?.dismissForegroundNotification()
                activeServiceInstance?.stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activeServiceInstance = null
            try {
                dismissTimerNotification(context)
                context.stopService(Intent(context, PomodoroService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun resumeTimer(context: Context) {
            _isRunning.value = true
            persistState(context, _remainingSeconds.value, true, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
            val intent = Intent(context, PomodoroService::class.java).apply { action = ACTION_RESUME }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopTimer(context: Context) {
            _remainingSeconds.value = 0
            _isRunning.value = false
            persistState(context, 0, false, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
            try {
                activeServiceInstance?.timerJob?.cancel()
                activeServiceInstance?.dismissForegroundNotification()
                activeServiceInstance?.stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activeServiceInstance = null
            try {
                dismissTimerNotification(context)
                context.stopService(Intent(context, PomodoroService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun resetTimer(
            context: Context,
            seconds: Int,
            title: String,
            sessionType: PomodoroType = _currentSessionType.value,
            totalSeconds: Int = seconds
        ) {
            _remainingSeconds.value = seconds
            _currentTaskTitle.value = title
            _isRunning.value = false
            _currentSessionType.value = sessionType
            if (totalSeconds > 0) {
                _totalDurationSeconds.value = totalSeconds
            }
            persistState(context, seconds, false, title, sessionType, _totalDurationSeconds.value)
            try {
                activeServiceInstance?.timerJob?.cancel()
                activeServiceInstance?.dismissForegroundNotification()
                activeServiceInstance?.stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activeServiceInstance = null
            try {
                dismissTimerNotification(context)
                context.stopService(Intent(context, PomodoroService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun dismissTimerNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager?.deleteNotificationChannel(CHANNEL_ID)
                }
            } catch (_: Exception) {}
        }
    }

    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        dismissForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val totalSeconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val sessionTypeName = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: _currentSessionType.value.name
                val totalSecs = intent.getIntExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                val parsedType = try { PomodoroType.valueOf(sessionTypeName) } catch (_: Exception) { PomodoroType.FOCUS }

                _currentTaskTitle.value = title
                _remainingSeconds.value = totalSeconds
                _currentSessionType.value = parsedType
                if (totalSecs > 0) _totalDurationSeconds.value = totalSecs
                _isRunning.value = true
                persistState(this, totalSeconds, true, title, parsedType, _totalDurationSeconds.value)
                dismissForegroundNotification()
                launchCountdown()
            }
            ACTION_PAUSE -> {
                _isRunning.value = false
                timerJob?.cancel()
                persistState(this, _remainingSeconds.value, false, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
                dismissForegroundNotification()
                stopSelf()
            }
            ACTION_RESUME -> {
                _isRunning.value = true
                persistState(this, _remainingSeconds.value, true, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
                dismissForegroundNotification()
                launchCountdown()
            }
            ACTION_STOP -> {
                _isRunning.value = false
                _remainingSeconds.value = 0
                timerJob?.cancel()
                persistState(this, 0, false, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
                dismissForegroundNotification()
                stopSelf()
            }
            ACTION_RESET -> {
                val totalSeconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val sessionTypeName = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: _currentSessionType.value.name
                val totalSecs = intent.getIntExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                val parsedType = try { PomodoroType.valueOf(sessionTypeName) } catch (_: Exception) { PomodoroType.FOCUS }

                _currentTaskTitle.value = title
                _remainingSeconds.value = totalSeconds
                _currentSessionType.value = parsedType
                if (totalSecs > 0) _totalDurationSeconds.value = totalSecs
                _isRunning.value = false
                timerJob?.cancel()
                persistState(this, totalSeconds, false, title, parsedType, _totalDurationSeconds.value)
                dismissForegroundNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    internal fun dismissForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager?.deleteNotificationChannel(CHANNEL_ID)
            }
        } catch (_: Exception) {}
    }

    private fun launchCountdown() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_remainingSeconds.value > 0 && _isRunning.value) {
                delay(1000)
                _remainingSeconds.value -= 1
                persistState(this@PomodoroService, _remainingSeconds.value, _isRunning.value, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
            }
            if (_remainingSeconds.value <= 0) {
                _remainingSeconds.value = 0
                _isRunning.value = false
                persistState(this@PomodoroService, 0, false, _currentTaskTitle.value, _currentSessionType.value, _totalDurationSeconds.value)
                dismissForegroundNotification()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        dismissForegroundNotification()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        super.onDestroy()
    }
}
