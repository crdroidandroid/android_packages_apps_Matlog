/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.omnirom.logcat.OmniApp
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.data.SearchCriteria
import com.pluscubed.logcat.filter.FilterRepository
import com.pluscubed.logcat.helper.LogcatHelper
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.helper.WidgetHelper
import com.pluscubed.logcat.reader.LogcatReader
import com.pluscubed.logcat.ui.LogcatActivity
import com.pluscubed.logcat.util.LogLineAdapterUtil

// records the log to a saved file until stopped
class LogcatRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var job: Job? = null
    private var reader: LogcatReader? = null

    @Volatile
    private var killed = false

    // paused recordings keep logcat running but drop incoming lines
    @Volatile
    private var paused = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE_RECORDING -> setPaused(true)
                ACTION_RESUME_RECORDING -> setPaused(false)
                else -> kill()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_STOP_RECORDING).apply {
            addAction(ACTION_PAUSE_RECORDING)
            addAction(ACTION_RESUME_RECORDING)
            addDataScheme(URI_SCHEME)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        WidgetHelper.updateWidgets(applicationContext, serviceRunning = true)
        if (job == null && intent != null) {
            job = scope.launch {
                record(intent)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        kill()
        scope.cancel()
        unregisterReceiver(receiver)
        stopForeground(STOP_FOREGROUND_REMOVE)
        WidgetHelper.updateWidgets(applicationContext, serviceRunning = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setPaused(value: Boolean) {
        paused = value
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(applicationContext, LogcatActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, OmniApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setTicker(getText(R.string.notification_ticker))
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(if (paused) R.string.matlog_recording_paused else R.string.notification_title))
            .setContentText(
                getString(if (paused) R.string.matlog_recording_paused_summary else R.string.matlog_recording_summary)
            )
            .setContentIntent(contentIntent)
        if (paused) {
            builder.addAction(
                R.drawable.ic_play_arrow_white_24dp, getString(R.string.matlog_resume),
                controlIntent(ACTION_RESUME_RECORDING, 2),
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_white_24dp, getString(R.string.matlog_pause),
                controlIntent(ACTION_PAUSE_RECORDING, 1),
            )
        }
        builder.addAction(R.drawable.ic_stop, getString(R.string.matlog_stop), controlIntent(ACTION_STOP_RECORDING, 3))
        return builder.build()
    }

    private fun controlIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName).setData(Uri.parse("$URI_SCHEME://$action"))
        return PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun record(intent: Intent) {
        val filename = intent.getStringExtra(EXTRA_FILENAME) ?: return
        val searchCriteria = SearchCriteria(intent.getStringExtra(EXTRA_QUERY_FILTER))
        val logLevelLimit = resources.getStringArray(R.array.log_levels_values)
            .indexOf(intent.getStringExtra(EXTRA_LEVEL)).coerceAtLeast(0)
        val repository = FilterRepository.get(this)
        var filtersVersion = repository.version
        var userFilters = repository.activeFilterSet()
        val logLinePeriod = PreferenceHelper.getLogLinePeriodPreference(applicationContext).coerceAtLeast(1)

        SaveLogHelper.deleteLogIfExists(filename)
        val logcat = open(intent.getStringExtra(EXTRA_SINCE) ?: LogcatHelper.currentTimestamp()) ?: return
        val pending = StringBuilder()
        try {
            var lineCount = 0
            while (!killed) {
                val line = logcat.readLine() ?: break
                if (paused) continue
                if (repository.version != filtersVersion) {
                    filtersVersion = repository.version
                    userFilters = repository.activeFilterSet()
                }
                if (!searchCriteria.isEmpty || logLevelLimit > 0 || !userFilters.isEmpty) {
                    val logLine = LogLine.newLogLine(line, false)
                    val accepted = searchCriteria.matches(logLine) &&
                        LogLineAdapterUtil.logLevelIsAcceptableGivenLogLevelLimit(logLine.logLevel, logLevelLimit) &&
                        userFilters.accepts(logLine)
                    if (!accepted) continue
                }
                pending.append(line).append('\n')
                // flushed now and then so a long recording doesn't run out of memory
                if (++lineCount % logLinePeriod == 0) {
                    SaveLogHelper.saveLog(pending, filename)
                    pending.setLength(0)
                }
            }
        } catch (e: IOException) {
            // the stream ends when the recording is stopped
        } finally {
            kill()
            if (SaveLogHelper.saveLog(pending, filename)) {
                toast(R.string.log_saved, Toast.LENGTH_SHORT)
                showSavedFile(filename)
            } else {
                toast(R.string.unable_to_save_log, Toast.LENGTH_LONG)
            }
        }
    }

    private fun open(since: String): LogcatReader? {
        val logcat = try {
            LogcatReader.open(this, since)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to start logcat", e)
            return null
        }
        synchronized(lock) {
            // stopped while logcat was starting
            if (killed) {
                logcat.killQuietly()
                return null
            }
            reader = logcat
        }
        toast(R.string.log_recording_started, Toast.LENGTH_SHORT)
        return logcat
    }

    private fun showSavedFile(filename: String) {
        startActivity(
            Intent(applicationContext, LogcatActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(EXTRA_FILENAME, filename)
        )
    }

    private fun toast(stringResId: Int, length: Int) {
        mainHandler.post { Toast.makeText(this, stringResId, length).show() }
    }

    private fun kill() {
        synchronized(lock) {
            killed = true
            reader?.killQuietly()
        }
    }

    companion object {
        const val URI_SCHEME = "catlog_recording_service"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_SINCE = "since"
        const val EXTRA_QUERY_FILTER = "filter"
        const val EXTRA_LEVEL = "level"
        private const val TAG = "LogcatRecordingService"
        private const val ACTION_STOP_RECORDING = "com.pluscubed.catlog.action.STOP_RECORDING"
        private const val ACTION_PAUSE_RECORDING = "org.omnirom.logcat.action.PAUSE_RECORDING"
        private const val ACTION_RESUME_RECORDING = "org.omnirom.logcat.action.RESUME_RECORDING"
        private val NOTIFICATION_ID = R.string.notification_title
    }
}
