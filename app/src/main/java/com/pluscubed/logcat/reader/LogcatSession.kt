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

    private var job: Job? = null

    @Volatile
    private var reader: LogcatReader? = null

    @Volatile
    var isPaused = false
        private set

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start() {
        stop()
        val maxLines = PreferenceHelper.getDisplayLimitPreference(context)
        val buffer = ArrayDeque<LogLine>()
        job = scope.launch {
            launch(Dispatchers.IO) { read(buffer, maxLines) }
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                if (!isPaused) {
                    flush(buffer)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        reader?.killQuietly()
        reader = null
        isPaused = false
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    private suspend fun read(buffer: ArrayDeque<LogLine>, maxLines: Int) {
        val logcat = try {
            LogcatReader.open(context)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to start logcat", e)
            return
        }
        reader = logcat
        try {
            while (currentCoroutineContext().isActive) {
                val line = logcat.readLine() ?: break
                val logLine = callback.createLogLine(line)
                synchronized(buffer) {
                    buffer.addLast(logLine)
                    while (buffer.size > maxLines) {
                        buffer.removeFirst()
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Logcat stream ended", e)
        } finally {
            logcat.killQuietly()
        }
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
    }
}
