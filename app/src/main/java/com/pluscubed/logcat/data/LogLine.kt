/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.util.Log
import com.pluscubed.logcat.reader.ScrubberUtils
import com.pluscubed.logcat.util.LogLineAdapterUtil
import com.pluscubed.logcat.util.UtilLogger
import java.util.regex.Pattern

class LogLine {

    var logLevel: Int = 0
    var tag: String? = null
    var processId: Int = -1
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
                    .append(processId)
                    .append("): ")
                    .append(logOutput)
            }
        }

    val processIdText: String
        get() = convertLogLevelToChar(logLevel).toString()

    companion object {
        private const val TIMESTAMP_LENGTH = 19

        // level/tag(pid), with an optional extra number seen on some ZTE devices
        private val LOG_PATTERN: Pattern =
            Pattern.compile("(\\w)/([^(]+)\\(\\s*(\\d+)(?:\\*\\s*\\d+)?\\): ")

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
                logLine.processId = matcher.group(3)!!.toInt()
                logLine.logOutput = originalLine.substring(matcher.end())
            } else {
                log.d("Line doesn't match pattern: %s", originalLine)
                logLine.logOutput = originalLine
                logLine.logLevel = -1
            }
            return logLine
        }

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
