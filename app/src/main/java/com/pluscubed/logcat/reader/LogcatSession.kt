/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.content.Context
import android.util.Log
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class LogcatSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callback: Callback,
) {

    interface Callback {
        fun createLogLine(line: String): LogLine
        fun onLogLines(lines: List<LogLine>)
    }

    private class Run {
        val buffer = ArrayDeque<LogLine>()

        @Volatile
        var reader: LogcatReader? = null
    }

    private var job: Job? = null
    private var run: Run? = null

    @Volatile
    var isPaused = false
        private set

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start() {
        stop()
        val maxLines = PreferenceHelper.getDisplayLimitPreference(context)
        val current = Run()
        run = current
        job = scope.launch {
            launch(Dispatchers.IO) { read(current, maxLines) }
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
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

    private suspend fun read(run: Run, maxLines: Int) {
        val buffer = run.buffer
        var since: String? = null
        val linesAtSince = HashSet<String>()
        var resuming = false
        while (currentCoroutineContext().isActive) {
            val logcat = try {
                LogcatReader.open(context, since)
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
                        synchronized(buffer) {
                            buffer.addLast(logLine)
                            while (buffer.size > maxLines) {
                                buffer.removeFirst()
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Logcat stream failed", e)
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
