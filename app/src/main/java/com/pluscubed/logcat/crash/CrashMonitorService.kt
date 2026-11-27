/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.LogcatHelper
import com.pluscubed.logcat.reader.LogcatReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.omnirom.logcat.R
import java.io.IOException

class CrashMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var reader: LogcatReader? = null
    private var trackingExceptions: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // a changed exception setting needs a logcat with different filters
        val trackExceptions = ExceptionTracker.isBackgroundEnabled(this)
        if (job == null || trackExceptions != trackingExceptions) {
            job?.cancel()
            reader?.killQuietly()
            trackingExceptions = trackExceptions
            job = scope.launch { monitor(trackExceptions) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        reader?.killQuietly()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitor(trackExceptions: Boolean) {
        val monitor = CrashMonitor(this, trackExceptions)
        val filterSpecs = if (trackExceptions) EXCEPTION_FILTER_SPECS else CRASH_FILTER_SPECS
        val idleJob = scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_MS)
                monitor.onIdle()
            }
        }
        var since = LogcatHelper.currentTimestamp()
        try {
            while (currentCoroutineContext().isActive) {
                val logcat = try {
                    LogcatReader.open(LogcatHelper.DEFAULT_BUFFERS, since, filterSpecs)
                } catch (e: IOException) {
                    Log.w(TAG, "Unable to start logcat", e)
                    null
                }
                if (logcat != null) {
                    reader = logcat
                    if (!currentCoroutineContext().isActive) {
                        logcat.killQuietly()
                        break
                    }
                    try {
                        while (currentCoroutineContext().isActive) {
                            val line = logcat.readLine() ?: break
                            val logLine = LogLine.newLogLine(line, false)
                            logLine.timestamp?.let { since = it }
                            // repeated lines after a restart are dropped by the repository
                            monitor.process(logLine, true)
                        }
                    } catch (e: IOException) {
                        Log.w(TAG, "Logcat stream failed", e)
                    } finally {
                        logcat.killQuietly()
                    }
                }
                if (!currentCoroutineContext().isActive) break
                delay(RESTART_DELAY_MS)
            }
        } finally {
            idleJob.cancel()
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, createChannel())
        .setSmallIcon(R.drawable.ic_bug_report)
        .setContentTitle(getString(R.string.matlog_crash_monitor_notification_title))
        .setContentText(getString(R.string.matlog_crash_monitor_notification_text))
        .setOngoing(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, CrashesActivity.intent(this),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.matlog_crash_monitor_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        return CHANNEL_ID
    }

    companion object {
        private const val TAG = "CrashMonitorService"
        private const val CHANNEL_ID = "matlog_crash_monitor"
        private const val NOTIFICATION_ID = 0x4d4c
        private const val RESTART_DELAY_MS = 5000L
        private const val IDLE_CHECK_MS = 1000L

        // only the tags and levels the crash detector looks at
        private val CRASH_FILTER_SPECS = listOf(
            "AndroidRuntime:E",
            "ActivityManager:E",
            "DEBUG:F",
            "*:S",
        )

        // apps log caught exceptions at warning level and above
        private val EXCEPTION_FILTER_SPECS = listOf("*:W")

        @JvmStatic
        fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_crash_monitor_service), false
            )

        private fun shouldRun(context: Context) =
            isEnabled(context) && CrashMonitor.isEnabled(context)

        /** Starts or stops the service to match the current settings. */
        @JvmStatic
        fun update(context: Context) {
            val intent = Intent(context, CrashMonitorService::class.java)
            if (shouldRun(context)) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Unable to start crash monitor", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
