/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.data

import android.util.Log
import com.crdroid.logcat.helper.PackageNameResolver
import com.crdroid.logcat.reader.ScrubberUtils
import com.crdroid.logcat.util.LogLineAdapterUtil
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

    // seconds since boot, only set for kernel lines
    var uptime: String? = null
        private set
    private var kernelPriority = -1
    private var hasKernelTag = false

    val isKernel: Boolean
        get() = uptime != null

    var logOutput: String? = null
        set(value) {
            field = if (isScrubberEnabled && value != null) ScrubberUtils.scrubLine(value) else value
        }

    val originalLine: String?
        get() {
            if (logLevel == -1) {
                return logOutput
            }
            if (isKernel) {
                return buildString {
                    append('[').append(uptime!!.padStart(KERNEL_UPTIME_WIDTH)).append("] ")
                    if (kernelPriority >= 0) append('<').append(kernelPriority).append("> ")
                    if (hasKernelTag) append(tag).append(": ")
                    append(logOutput)
                }
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

        // "[    12.345678] <6> message" from the helper, "<6>[    12.345678] message" from pstore
        // panic dumps and "[    12.345678] message" from the pstore console
        private val KERNEL_PATTERN: Pattern =
            Pattern.compile("(?:<([0-7])>)?\\[\\s*(\\d+)\\.(\\d{6})](?: <([0-7])>)? ?")

        // a leading "subsystem:" or "driver device:" becomes the tag
        private val KERNEL_TAG_PATTERN: Pattern = Pattern.compile("([^\\s:\\[\\]]{1,40}(?: [^\\s:\\[\\]]{1,40})?): ")

        private const val KERNEL_TAG = "kernel"
        private const val KERNEL_UPTIME_WIDTH = 12

        var isScrubberEnabled = false

        fun newLogLine(originalLine: String, expanded: Boolean): LogLine {
            if (originalLine.startsWith('[') || originalLine.startsWith('<')) {
                newKernelLine(originalLine, expanded)?.let { return it }
            }

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
                logLine.logOutput = originalLine
                logLine.logLevel = -1
            }
            return logLine
        }

        fun kernelUptimeMicros(line: String): Long? {
            if (!line.startsWith('[') && !line.startsWith('<')) return null
            val matcher = KERNEL_PATTERN.matcher(line)
            if (!matcher.lookingAt()) return null
            return matcher.group(2)!!.toLongOrNull()?.let { it * 1_000_000 + matcher.group(3)!!.toLong() }
        }

        private fun newKernelLine(originalLine: String, expanded: Boolean): LogLine? {
            val matcher = KERNEL_PATTERN.matcher(originalLine)
            if (!matcher.lookingAt()) return null
            val logLine = LogLine()
            logLine.isExpanded = expanded
            logLine.uptime = "${matcher.group(2)}.${matcher.group(3)}"
            logLine.kernelPriority = (matcher.group(1) ?: matcher.group(4))?.toInt() ?: -1
            logLine.logLevel = kernelLogLevel(logLine.kernelPriority)

            val message = originalLine.substring(matcher.end())
            val tagMatcher = KERNEL_TAG_PATTERN.matcher(message)
            if (tagMatcher.lookingAt()) {
                logLine.tag = tagMatcher.group(1)
                logLine.hasKernelTag = true
                logLine.logOutput = message.substring(tagMatcher.end())
            } else {
                logLine.tag = KERNEL_TAG
                logLine.logOutput = message
            }
            return logLine
        }

        private fun kernelLogLevel(priority: Int): Int = when (priority) {
            -1 -> Log.INFO
            0, 1, 2 -> LogLineAdapterUtil.LOG_WTF
            3 -> Log.ERROR
            4 -> Log.WARN
            5, 6 -> Log.INFO
            else -> Log.DEBUG
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
