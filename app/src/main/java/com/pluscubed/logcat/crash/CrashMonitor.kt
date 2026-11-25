/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.pluscubed.logcat.data.LogLine
import org.omnirom.logcat.R

class CrashMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val repository = CrashRepository.get(appContext)

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
        detector.process(line, live, SystemClock.elapsedRealtime())
    }

    fun onIdle() = synchronized(detector) {
        detector.onIdle(SystemClock.elapsedRealtime())
    }

    companion object {
        @JvmStatic
        fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_crash_detection), true
            )

        @JvmStatic
        fun areNotificationsEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_crash_notifications), true
            )
    }
}
