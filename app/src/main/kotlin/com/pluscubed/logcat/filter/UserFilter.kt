/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.filter

import android.util.Log
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.util.LogLineAdapterUtil

data class UserFilter(
    val id: Long = 0,
    val name: String? = null,
    val including: Boolean = true,
    val levels: Set<Int> = emptySet(),
    val uid: String? = null,
    val pid: String? = null,
    val packageName: String? = null,
    val tag: String? = null,
    val content: String? = null,
    val enabled: Boolean = true,
    val tagRegex: Boolean = false,
    val contentRegex: Boolean = false,
    val packageRegex: Boolean = false,
    // epoch millis, inclusive
    val since: Long? = null,
    val until: Long? = null,
) {

    val hasConditions: Boolean
        get() = levels.isNotEmpty() || !uid.isNullOrBlank() || !pid.isNullOrBlank() ||
            !packageName.isNullOrBlank() || !tag.isNullOrBlank() || !content.isNullOrEmpty() ||
            since != null || until != null

    // an invalid pattern never matches
    private val tagPattern by lazy { if (tagRegex) tag?.trim()?.let { compile(it) } else null }
    private val contentPattern by lazy { if (contentRegex) content?.let { compile(it) } else null }
    private val packagePattern by lazy { if (packageRegex) packageName?.trim()?.let { compile(it) } else null }

    fun matches(line: LogLine): Boolean =
        (levels.isEmpty() || line.logLevel in levels) &&
            uid.equalsOrUnset(line.uid) &&
            pid.equalsOrUnset(line.processId.takeIf { it != -1 }?.toString()) &&
            matchesTag(line.tag?.trim()) &&
            matchesContent(line.logOutput) &&
            // resolved lazily, only when a package condition is set
            matchesPackage(if (packageName.isNullOrBlank()) null else line.packageName) &&
            inTimeRange(line)

    private fun matchesTag(value: String?): Boolean = when {
        tag.isNullOrBlank() -> true
        tagRegex -> value != null && tagPattern?.matches(value) == true
        else -> tag.trim() == value
    }

    private fun matchesContent(value: String?): Boolean = when {
        content.isNullOrEmpty() -> true
        contentRegex -> value != null && contentPattern?.containsMatchIn(value) == true
        else -> value?.contains(content) == true
    }

    private fun matchesPackage(value: String?): Boolean = when {
        packageName.isNullOrBlank() -> true
        packageRegex -> value != null && packagePattern?.matches(value) == true
        else -> packageName.trim() == value
    }

    // lines without a usable timestamp are let through
    private fun inTimeRange(line: LogLine): Boolean {
        if (since == null && until == null) return true
        val time = line.timeMillis
        if (time < 0) return true
        return (since == null || time >= since) && (until == null || time <= until)
    }

    private fun String?.equalsOrUnset(value: String?) =
        isNullOrBlank() || trim() == value

    companion object {
        // display order, matching logcat letters
        val LEVELS = listOf(
            Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, LogLineAdapterUtil.LOG_WTF
        )

        fun isValidRegex(pattern: String): Boolean = compile(pattern) != null

        private fun compile(pattern: String): Regex? = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: IllegalArgumentException) {
            null
        }

        fun levelLetter(level: Int): String = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            LogLineAdapterUtil.LOG_WTF -> "F"
            else -> "?"
        }
    }
}
