/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.crash

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.crdroid.logcat.R
import com.crdroid.logcat.data.LogLine

class CrashMonitor(context: Context, trackExceptions: Boolean = true) {

    private val appContext = context.applicationContext
    private val repository = CrashRepository.get(appContext)
    private val exceptions = if (trackExceptions) ExceptionTracker(appContext) else null

    private val detector = CrashDetector { type, packageName, timestamp, lines, live ->
        val crash = Crash(
            type = type,
            packageName = packageName,
            timestamp = timestamp,
            detectedAt = System.currentTimeMillis(),
            log = lines.joinToString("\n") { it.originalLine.orEmpty() },
        )
        val id = repository.insert(crash)
        if (id != -1L && live) {
            CrashNotifier.notify(appContext, crash.copy(id = id))
        }
    }

    fun process(line: LogLine, live: Boolean) = synchronized(detector) {
        val now = SystemClock.elapsedRealtime()
        detector.process(line, live, now)
        exceptions?.process(line, now)
    }

    fun onIdle() = synchronized(detector) {
        val now = SystemClock.elapsedRealtime()
        detector.onIdle(now)
        exceptions?.onIdle(now)
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_crash_detection), true
            )

        fun areNotificationsEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_crash_notifications), true
            )
    }
}
