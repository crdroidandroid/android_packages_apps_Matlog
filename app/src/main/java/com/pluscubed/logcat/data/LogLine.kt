/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.util.Log
import com.pluscubed.logcat.helper.PackageNameResolver
import com.pluscubed.logcat.reader.ScrubberUtils
import com.pluscubed.logcat.util.LogLineAdapterUtil
import com.pluscubed.logcat.util.UtilLogger
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.regex.Pattern

class LogLine {

    var logLevel: Int = 0
    var tag: String? = null
    var processId: Int = -1
    var uid: String? = null
    var timestamp: String? = null
    var isExpanded: Boolean = false
    var isHighlighted: Boolean = false

    var logOutput: String? = null
        set(value) {
            field = if (isScrubberEnabled) ScrubberUtils.scrubLine(value) else value
        }

    val originalLine: String?
        get() {
            if (logLevel == -1) {
                return logOutput
            }
            return buildString {
                timestamp?.let { append(it).append(' ') }
                append(convertLogLevelToChar(logLevel))
                    .append('/')
                    .append(tag)
                    .append('(')
                uid?.let { append(it).append(':') }
                append(processId)
                    .append("): ")
                    .append(logOutput)
            }
        }

    val packageName: String? by lazy { PackageNameResolver.resolve(uid) }

    // epoch millis of the timestamp, or -1 when it can't be read
    val timeMillis: Long by lazy { parseTimestamp(timestamp) }

    val processIdText: String
        get() = convertLogLevelToChar(logLevel).toString()

    companion object {
        private const val TIMESTAMP_LENGTH = 19

        // level/tag(uid:pid), uid is optional, plus an extra number seen on some ZTE devices
        private val LOG_PATTERN: Pattern =
            Pattern.compile("(\\w)/([^(]+)\\(\\s*(?:([^\\s:()]+):)?\\s*(\\d+)(?:\\*\\s*\\d+)?\\): ")

        private val log = UtilLogger(LogLine::class.java)

        @JvmField
        var isScrubberEnabled = false

        @JvmStatic
        fun newLogLine(originalLine: String, expanded: Boolean): LogLine {
            val logLine = LogLine()
            logLine.isExpanded = expanded

            var startIdx = 0
            if (originalLine.length >= TIMESTAMP_LENGTH && originalLine[0].isDigit()) {
                logLine.timestamp = originalLine.substring(0, TIMESTAMP_LENGTH - 1)
                startIdx = TIMESTAMP_LENGTH
            }

            val matcher = LOG_PATTERN.matcher(originalLine)
            if (matcher.find(startIdx)) {
                logLine.logLevel = convertCharToLogLevel(matcher.group(1)!![0])
                logLine.tag = matcher.group(2)
                logLine.uid = matcher.group(3)
                logLine.processId = matcher.group(4)!!.toInt()
                logLine.logOutput = originalLine.substring(matcher.end())
            } else {
                log.d("Line doesn't match pattern: %s", originalLine)
                logLine.logOutput = originalLine
                logLine.logLevel = -1
            }
            return logLine
        }

        private const val DAY_MS = 24 * 60 * 60 * 1000L

        // MM-dd HH:mm:ss.SSS; logcat leaves out the year, so assume the most recent one
        private fun parseTimestamp(timestamp: String?): Long {
            if (timestamp == null || timestamp.length < TIMESTAMP_LENGTH - 1) return -1
            fun number(start: Int, length: Int): Int? =
                timestamp.substring(start, start + length).toIntOrNull()
            val month = number(0, 2) ?: return -1
            val day = number(3, 2) ?: return -1
            val hour = number(6, 2) ?: return -1
            val minute = number(9, 2) ?: return -1
            val second = number(12, 2) ?: return -1
            val millis = number(15, 3) ?: return -1
            return try {
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                var year = Instant.ofEpochMilli(now).atZone(zone).year
                var time = toEpochMillis(year, month, day, hour, minute, second, millis, zone)
                if (time > now + DAY_MS) {
                    year -= 1
                    time = toEpochMillis(year, month, day, hour, minute, second, millis, zone)
                }
                time
            } catch (e: DateTimeException) {
                -1
            }
        }

        private fun toEpochMillis(
            year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millis: Int, zone: ZoneId,
        ): Long = LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        private fun convertCharToLogLevel(logLevelChar: Char): Int = when (logLevelChar) {
            'D' -> Log.DEBUG
            'E' -> Log.ERROR
            'I' -> Log.INFO
            'V' -> Log.VERBOSE
            'W' -> Log.WARN
            'F' -> LogLineAdapterUtil.LOG_WTF
            else -> -1
        }

        private fun convertLogLevelToChar(logLevel: Int): Char = when (logLevel) {
            Log.DEBUG -> 'D'
            Log.ERROR -> 'E'
            Log.INFO -> 'I'
            Log.VERBOSE -> 'V'
            Log.WARN -> 'W'
            LogLineAdapterUtil.LOG_WTF -> 'F'
            else -> ' '
        }
    }
}
