/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.data

import android.content.Context
import androidx.preference.PreferenceManager
import com.crdroid.logcat.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// not thread safe, use from the main thread only
class LogDisplayOptions private constructor(
    columns: Set<String>,
    dateFormat: String,
    timeFormat: String,
) {

    val showDate = COLUMN_DATE in columns
    val showTime = COLUMN_TIME in columns
    val showUid = COLUMN_UID in columns
    val showPid = COLUMN_PID in columns
    val showPackage = COLUMN_PACKAGE in columns
    val showTag = COLUMN_TAG in columns

    val hasDetails = showDate || showTime || showUid || showPid || showPackage

    private val rawTimestamp = dateFormat == DEFAULT_DATE_FORMAT && timeFormat == DEFAULT_TIME_FORMAT
    private val parser = SimpleDateFormat(LOGCAT_TIMESTAMP_FORMAT, Locale.US)
    private val dateFormatter = formatterOrNull(dateFormat)
    private val timeFormatter = formatterOrNull(timeFormat)
    private val calendar = Calendar.getInstance()

    // kernel lines only carry the time since boot
    fun hasDetails(line: LogLine): Boolean =
        if (line.isKernel) showDate || showTime else hasDetails && line.processId != -1

    // date, time, uid and package; the pid has its own column
    fun details(line: LogLine): String {
        val parts = ArrayList<String>(3)
        timestamp(line)?.let { parts.add(it) }
        if (showUid) line.uid?.let { parts.add(it) }
        if (showPackage) line.packageName?.let { parts.add(it) }
        return parts.joinToString("  ")
    }

    private fun timestamp(line: LogLine): String? {
        if (!showDate && !showTime) return null
        line.uptime?.let { return "[$it]" }
        val raw = line.timestamp ?: return null
        if (rawTimestamp && raw.length == LOGCAT_TIMESTAMP_FORMAT.length) {
            return when {
                showDate && showTime -> raw
                showDate -> raw.substring(0, 5)
                else -> raw.substring(6)
            }
        }
        val millis = parse(raw) ?: return raw
        return listOfNotNull(
            if (showDate) dateFormatter?.format(millis) ?: raw.substring(0, 5) else null,
            if (showTime) timeFormatter?.format(millis) ?: raw.substring(6) else null,
        ).joinToString(" ")
    }

    // logcat leaves out the year, so assume the most recent one
    private fun parse(raw: String): Long? {
        val parsed = try {
            parser.parse(raw)
        } catch (e: ParseException) {
            null
        } ?: return null
        val now = System.currentTimeMillis()
        calendar.time = parsed
        calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
        if (calendar.timeInMillis > now + DAY_MS) calendar.add(Calendar.YEAR, -1)
        return calendar.timeInMillis
    }

    companion object {
        const val COLUMN_DATE = "date"
        const val COLUMN_TIME = "time"
        const val COLUMN_UID = "uid"
        const val COLUMN_PID = "pid"
        const val COLUMN_PACKAGE = "package"
        const val COLUMN_TAG = "tag"

        const val DEFAULT_DATE_FORMAT = "MM-dd"
        const val DEFAULT_TIME_FORMAT = "HH:mm:ss.SSS"

        private const val LOGCAT_TIMESTAMP_FORMAT = "MM-dd HH:mm:ss.SSS"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        private val DEFAULT_COLUMNS =
            setOf(COLUMN_DATE, COLUMN_TIME, COLUMN_PID, COLUMN_PACKAGE, COLUMN_TAG)

        private var cached: LogDisplayOptions? = null

        fun get(context: Context): LogDisplayOptions = cached ?: load(context).also { cached = it }

        fun invalidate() {
            cached = null
        }

        fun isValidFormat(pattern: String): Boolean =
            pattern.isNotBlank() && formatterOrNull(pattern) != null

        // carries over the old "show pid & timestamp" switch
        fun migrate(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val key = context.getString(R.string.matlog_pref_log_columns)
            if (prefs.contains(key)) return
            val showDetails = prefs.getBoolean(context.getString(R.string.pref_show_timestamp), true)
            prefs.edit()
                .putStringSet(key, if (showDetails) DEFAULT_COLUMNS else setOf(COLUMN_TAG))
                .apply()
        }

        private fun load(context: Context): LogDisplayOptions {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return LogDisplayOptions(
                columns = prefs.getStringSet(context.getString(R.string.matlog_pref_log_columns), null)
                    ?: DEFAULT_COLUMNS,
                dateFormat = prefs.getString(context.getString(R.string.matlog_pref_date_format), null)
                    ?: DEFAULT_DATE_FORMAT,
                timeFormat = prefs.getString(context.getString(R.string.matlog_pref_time_format), null)
                    ?: DEFAULT_TIME_FORMAT,
            )
        }

        private fun formatterOrNull(pattern: String): SimpleDateFormat? = try {
            SimpleDateFormat(pattern, Locale.getDefault())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
