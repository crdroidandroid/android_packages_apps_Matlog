/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import java.security.MessageDigest
import com.pluscubed.logcat.data.LogLine

class ExceptionEvent(
    val fingerprint: String,
    val packageName: String,
    val tag: String,
    val exception: String,
    val message: String,
    val topFrame: String,
    val timestamp: String,
    val log: String,
)

// finds stack traces that apps log without crashing
class ExceptionDetector(private val listener: (ExceptionEvent) -> Unit) {

    private class Trace(val context: LogLine?, val lines: MutableList<LogLine>, var lastUpdate: Long) {
        var hasFrames = false
    }

    private val traces = HashMap<String, Trace>()

    // the line before a trace usually carries the app's own message
    private val previous = object : LinkedHashMap<String, LogLine>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LogLine>) = size > MAX_KEYS
    }

    fun process(line: LogLine, now: Long) {
        val tag = line.tag?.trim().orEmpty()
        if (line.logLevel == -1 || line.isKernel || tag in IGNORED_TAGS) return
        val key = "${line.processId}/$tag"
        val output = line.logOutput.orEmpty()

        traces[key]?.let { trace ->
            if (isTraceLine(output)) {
                if (trace.lines.size < MAX_LINES) trace.lines += line
                if (isFrame(output)) trace.hasFrames = true
                trace.lastUpdate = now
                return
            }
            if (!trace.hasFrames && trace.lines.size == 1) {
                traces.remove(key)
            } else {
                finish(key)
            }
        }

        if (isHeader(output)) {
            traces[key] = Trace(previous[key], mutableListOf(line), now)
        }
        previous[key] = line
    }

    fun onIdle(now: Long) {
        traces.entries.filter { now - it.value.lastUpdate > IDLE_TIMEOUT_MS }.map { it.key }.forEach { key ->
            if (traces[key]?.hasFrames == true) finish(key) else traces.remove(key)
        }
    }

    private fun finish(key: String) {
        val trace = traces.remove(key) ?: return
        if (!trace.hasFrames) return
        val lines = trace.lines
        val first = lines.first()
        val outputs = lines.map { it.logOutput.orEmpty().trim() }

        // the deepest cause is the real problem
        val causeIndex = outputs.indexOfLast { it.startsWith(CAUSED_BY) }
        val header = if (causeIndex >= 0) outputs[causeIndex].removePrefix(CAUSED_BY).trim() else outputs.first()
        val exception = header.substringBefore(':').trim()
        val message = header.substringAfter(':', "").trim()
        val frames = outputs.drop(maxOf(causeIndex, 0) + 1).takeWhile { isFrame(it) }
        val packageName = first.packageName ?: UNKNOWN_PACKAGE

        val key = buildString {
            append(packageName).append('|').append(exception)
            frames.take(FINGERPRINT_FRAMES).forEach { append('|').append(normalizeFrame(it)) }
        }
        val contextLine = trace.context?.takeIf { !isTraceLine(it.logOutput.orEmpty()) }
        listener(
            ExceptionEvent(
                fingerprint = sha1(key),
                packageName = packageName,
                tag = first.tag?.trim().orEmpty(),
                exception = exception,
                message = message,
                topFrame = topFrame(outputs.filter { isFrame(it) }, packageName, frames),
                timestamp = first.timestamp.orEmpty(),
                log = (listOfNotNull(contextLine) + lines).joinToString("\n") { it.originalLine.orEmpty() },
            )
        )
    }

    // prefers a frame from the app itself over framework frames
    private fun topFrame(allFrames: List<String>, packageName: String, causeFrames: List<String>): String {
        val frame = allFrames.firstOrNull { it.removePrefix("at ").startsWith("$packageName.") }
            ?: causeFrames.firstOrNull()
            ?: allFrames.firstOrNull()
            ?: return ""
        val call = frame.removePrefix("at ").trim()
        val method = call.substringBefore('(')
        val location = call.substring(method.length)
        val parts = method.split('.')
        return parts.takeLast(2).joinToString(".") + location
    }

    private fun isHeader(output: String) = HEADER.matches(output.trim())

    private fun isFrame(output: String) = output.trimStart().startsWith("at ")

    private fun isTraceLine(output: String): Boolean {
        val trimmed = output.trimStart()
        return trimmed.startsWith("at ") || trimmed.startsWith(CAUSED_BY) || trimmed.startsWith("Suppressed:") ||
            (trimmed.startsWith("... ") && trimmed.endsWith(" more"))
    }

    private fun normalizeFrame(frame: String): String =
        frame.removePrefix("at ").trim().substringBefore('(')
            .replace(SYNTHETIC_LAMBDA, "")
            .replace(LAMBDA_INDEX, "lambda\\$$1")
            .replace(ANONYMOUS_INDEX, "\\$")

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CAUSED_BY = "Caused by:"
        const val UNKNOWN_PACKAGE = "unknown"
        const val MAX_KEYS = 256
        const val MAX_LINES = 200
        const val FINGERPRINT_FRAMES = 5
        const val IDLE_TIMEOUT_MS = 1500L

        // fatal crashes and tombstones belong to the crash collectors
        val IGNORED_TAGS = setOf("AndroidRuntime", "DEBUG")

        // "java.lang.IllegalStateException: message" or a bare class name
        val HEADER = Regex("(?:[A-Za-z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*(?:Exception|Error|Throwable)(?::.*)?")
        val SYNTHETIC_LAMBDA = Regex("\\\$\\\$ExternalSynthetic\\w*")
        val LAMBDA_INDEX = Regex("lambda\\$(\\w+)\\$\\d+")
        val ANONYMOUS_INDEX = Regex("\\$\\d+")
    }
}
