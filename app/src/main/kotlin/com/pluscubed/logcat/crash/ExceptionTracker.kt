/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.content.Context
import androidx.preference.PreferenceManager
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.LogLine

// counts exceptions in memory and writes them out in batches
class ExceptionTracker(context: Context) {

    private class Pending(var event: ExceptionEvent, var count: Int, val firstSeen: Long, var lastSeen: Long)

    private val repository = CrashRepository.get(context.applicationContext)
    private val detector = ExceptionDetector(::onException)
    private val pending = LinkedHashMap<String, Pending>()

    // last counted timestamp per fingerprint, so lines replayed after a restart are not counted again
    private val lastTimestamps = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > MAX_CACHED
    }
    private val muted = HashSet<String>()
    private var lastFlush = 0L

    fun process(line: LogLine, now: Long) {
        detector.process(line, now)
    }

    fun onIdle(now: Long) {
        detector.onIdle(now)
        if (pending.isNotEmpty() && now - lastFlush >= FLUSH_INTERVAL_MS) flush(now)
    }

    private fun onException(event: ExceptionEvent) {
        val fingerprint = event.fingerprint
        if (fingerprint in muted) return
        val last = lastTimestamps[fingerprint] ?: repository.exceptionState(fingerprint)?.let { (timestamp, isMuted) ->
            if (isMuted) {
                muted += fingerprint
                return
            }
            timestamp
        }
        if (last != null && event.timestamp.isNotEmpty() && event.timestamp <= last) return
        if (event.timestamp.isNotEmpty()) lastTimestamps[fingerprint] = event.timestamp

        val now = System.currentTimeMillis()
        val entry = pending[fingerprint]
        if (entry == null) {
            pending[fingerprint] = Pending(event, 1, now, now)
        } else {
            entry.event = event
            entry.count++
            entry.lastSeen = now
        }
    }

    private fun flush(now: Long) {
        lastFlush = now
        val batch = pending.values.toList()
        pending.clear()
        batch.forEach { repository.recordException(it.event, it.count, it.firstSeen, it.lastSeen) }
        repository.notifyExceptionsChanged()
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 3000L
        private const val MAX_CACHED = 1024

        fun isBackgroundEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.matlog_pref_exception_monitor), false
            )
    }
}
