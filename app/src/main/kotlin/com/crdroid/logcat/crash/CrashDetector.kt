/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.crash

import com.crdroid.logcat.data.LogLine

class CrashDetector(private val listener: Listener) {

    fun interface Listener {
        fun onCrashDetected(
            type: CrashType,
            packageName: String,
            timestamp: String,
            lines: List<LogLine>,
            live: Boolean,
        )
    }

    private val collectors = listOf(JavaCollector(), NativeCollector(), AnrCollector())

    fun process(line: LogLine, live: Boolean, now: Long) {
        collectors.forEach { it.process(line, live, now) }
    }

    fun onIdle(now: Long) {
        collectors.forEach { it.onIdle(now) }
    }

    private abstract inner class Collector(private val type: CrashType) {
        private var first: LogLine? = null
        private val lines = mutableListOf<LogLine>()
        private var gap = 0
        private var lastUpdate = 0L
        private var live = false

        abstract fun isStart(line: LogLine): Boolean

        abstract fun belongsTo(first: LogLine, line: LogLine): Boolean

        abstract fun packageName(lines: List<LogLine>): String?

        fun process(line: LogLine, live: Boolean, now: Long) {
            val current = first
            if (current != null) {
                if (!isStart(line)) {
                    if (belongsTo(current, line)) {
                        if (lines.size < MAX_LINES) lines += line
                        gap = 0
                        lastUpdate = now
                    } else if (++gap > MAX_GAP) {
                        finish()
                    }
                    return
                }
                finish()
            }
            if (isStart(line)) {
                first = line
                lines.clear()
                lines += line
                gap = 0
                lastUpdate = now
                this.live = live
            }
        }

        fun onIdle(now: Long) {
            if (first != null && now - lastUpdate > IDLE_TIMEOUT_MS) {
                finish()
            }
        }

        private fun finish() {
            val start = first ?: return
            listener.onCrashDetected(
                type = type,
                packageName = packageName(lines) ?: UNKNOWN_PACKAGE,
                timestamp = start.timestamp.orEmpty(),
                lines = lines.toList(),
                live = live,
            )
            first = null
            lines.clear()
        }
    }

    private inner class JavaCollector : Collector(CrashType.JAVA) {
        override fun isStart(line: LogLine) =
            line.cleanTag == "AndroidRuntime" &&
                (line.output.startsWith("FATAL EXCEPTION: ") || line.output.startsWith(SYSTEM_FATAL))

        override fun belongsTo(first: LogLine, line: LogLine) =
            line.cleanTag == "AndroidRuntime" && line.processId == first.processId

        // "Process: com.example.app, PID: 1234"; system_server crashes have no such line
        override fun packageName(lines: List<LogLine>) =
            if (lines.firstOrNull()?.output?.startsWith(SYSTEM_FATAL) == true) {
                SYSTEM_PACKAGE
            } else {
                lines.firstNotNullOfOrNull { line ->
                    line.output.takeIf { it.startsWith("Process: ") }
                        ?.removePrefix("Process: ")
                        ?.substringBefore(',')
                        ?.trim()
                }
            }
    }

    private inner class NativeCollector : Collector(CrashType.NATIVE) {
        override fun isStart(line: LogLine) =
            line.cleanTag == "DEBUG" && line.output.trim() == NATIVE_HEADER

        override fun belongsTo(first: LogLine, line: LogLine) =
            line.cleanTag == "DEBUG" && line.processId == first.processId

        // "pid: 1234, tid: 1234, name: main  >>> com.example.app <<<"
        override fun packageName(lines: List<LogLine>) = lines.firstNotNullOfOrNull { line ->
            val start = line.output.indexOf(">>> ")
            val end = line.output.indexOf(" <<<")
            if (start != -1 && end > start) line.output.substring(start + 4, end).trim() else null
        }
    }

    private inner class AnrCollector : Collector(CrashType.ANR) {
        override fun isStart(line: LogLine) =
            line.cleanTag == "ActivityManager" && line.output.startsWith("ANR in ")

        override fun belongsTo(first: LogLine, line: LogLine) =
            line.cleanTag == "ActivityManager" && line.processId == first.processId

        // "ANR in com.example.app (com.example.app/.MainActivity)"
        override fun packageName(lines: List<LogLine>) =
            lines.firstOrNull()?.output?.removePrefix("ANR in ")?.substringBefore(' ')?.trim()
    }

    private val LogLine.cleanTag: String
        get() = tag?.trim().orEmpty()

    private val LogLine.output: String
        get() = logOutput.orEmpty()

    private companion object {
        const val NATIVE_HEADER = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"
        const val UNKNOWN_PACKAGE = "unknown"
        const val SYSTEM_FATAL = "*** FATAL EXCEPTION IN SYSTEM PROCESS: "
        const val SYSTEM_PACKAGE = "android"
        const val MAX_GAP = 30
        const val MAX_LINES = 1000
        const val IDLE_TIMEOUT_MS = 1500L
    }
}
