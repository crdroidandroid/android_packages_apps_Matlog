/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.content.Context
import android.util.Log
import com.pluscubed.logcat.crash.CrashMonitor
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callback: Callback,
) {

    interface Callback {
        fun createLogLine(line: String): LogLine
        fun onLogLines(lines: List<LogLine>)
    }

    private class Run(val crashMonitor: CrashMonitor?) {
        val buffer = ArrayDeque<LogLine>()

        @Volatile
        var reader: LineReader? = null
    }

    private var job: Job? = null
    private var run: Run? = null

    @Volatile
    var isPaused = false
        private set

    val isRunning: Boolean
        get() = job?.isActive == true

    // read the kernel log instead of logcat, applied on the next start
    var isKernel = false

    fun start() {
        stop()
        val maxLines = PreferenceHelper.getDisplayLimitPreference(context)
        val kernel = isKernel
        val current = Run(if (!kernel && CrashMonitor.isEnabled(context)) CrashMonitor(context) else null)
        run = current
        job = scope.launch {
            launch(Dispatchers.IO) { read(current, maxLines, kernel) }
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                current.crashMonitor?.let { monitor ->
                    withContext(Dispatchers.IO) { monitor.onIdle() }
                }
                if (!isPaused) {
                    flush(current.buffer)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        run?.reader?.killQuietly()
        run = null
        isPaused = false
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    private suspend fun read(run: Run, maxLines: Int, kernel: Boolean) {
        val buffer = run.buffer
        var since: String? = null
        var lastUptime = -1L
        val linesAtSince = HashSet<String>()
        var resuming = false
        // only crashes logged after the session started are notified
        val startedAt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        while (currentCoroutineContext().isActive) {
            val logcat = try {
                if (kernel) DmesgReader.open(since) else LogcatReader.open(context, since)
            } catch (e: IOException) {
                Log.w(TAG, "Unable to start logcat", e)
                null
            }
            if (logcat != null) {
                run.reader = logcat
                if (!currentCoroutineContext().isActive) {
                    logcat.killQuietly()
                    break
                }
                try {
                    while (currentCoroutineContext().isActive) {
                        val line = logcat.readLine() ?: break
                        val uptime = LogLine.kernelUptimeMicros(line)
                        if (uptime != null) {
                            // the helper replays the whole kernel log after a restart
                            if (resuming && uptime <= lastUptime) continue
                            lastUptime = uptime
                            resuming = false
                        }
                        val timestamp = timestampOf(line)
                        if (timestamp != null) {
                            if (timestamp == since) {
                                // -T is inclusive, skip lines already shown before a restart
                                if (!linesAtSince.add(line) && resuming) continue
                            } else {
                                since = timestamp
                                linesAtSince.clear()
                                linesAtSince.add(line)
                                resuming = false
                            }
                        }
                        val logLine = callback.createLogLine(line)
                        run.crashMonitor?.process(logLine, timestamp != null && timestamp >= startedAt)
                        synchronized(buffer) {
                            buffer.addLast(logLine)
                            while (buffer.size > maxLines) {
                                buffer.removeFirst()
                            }
                        }
                    }
                } catch (e: IOException) {
                    // stop() closes the stream under a blocked read, which is expected
                    if (currentCoroutineContext().isActive) Log.w(TAG, "Logcat stream failed", e)
                } finally {
                    logcat.killQuietly()
                }
            }
            if (!currentCoroutineContext().isActive) break
            Log.w(TAG, "Logcat stopped unexpectedly, restarting")
            resuming = true
            delay(RESTART_DELAY_MS)
        }
    }

    private fun timestampOf(line: String): String? =
        if (line.length >= TIMESTAMP_LENGTH && line[0].isDigit()) {
            line.substring(0, TIMESTAMP_LENGTH)
        } else {
            null
        }

    private fun flush(buffer: ArrayDeque<LogLine>) {
        val lines = synchronized(buffer) {
            if (buffer.isEmpty()) return
            buffer.toList().also { buffer.clear() }
        }
        callback.onLogLines(lines)
    }

    private companion object {
        const val TAG = "LogcatSession"
        const val UPDATE_INTERVAL_MS = 250L
        const val RESTART_DELAY_MS = 2000L
        const val TIMESTAMP_LENGTH = 18
    }
}
