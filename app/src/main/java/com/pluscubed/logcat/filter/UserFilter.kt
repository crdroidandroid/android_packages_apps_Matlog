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
) {

    val hasConditions: Boolean
        get() = levels.isNotEmpty() || !uid.isNullOrBlank() || !pid.isNullOrBlank() ||
            !packageName.isNullOrBlank() || !tag.isNullOrBlank() || !content.isNullOrEmpty()

    fun matches(line: LogLine): Boolean =
        (levels.isEmpty() || line.logLevel in levels) &&
            uid.equalsOrUnset(line.uid) &&
            pid.equalsOrUnset(line.processId.takeIf { it != -1 }?.toString()) &&
            tag.equalsOrUnset(line.tag?.trim()) &&
            content.containedOrUnset(line.logOutput) &&
            // resolved lazily, only when a package condition is set
            packageName.equalsOrUnset(if (packageName.isNullOrBlank()) null else line.packageName)

    private fun String?.equalsOrUnset(value: String?) =
        isNullOrBlank() || trim() == value

    private fun String?.containedOrUnset(value: String?) =
        isNullOrEmpty() || value?.contains(this) == true

    companion object {
        // display order, matching logcat letters
        @JvmField
        val LEVELS = listOf(
            Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, LogLineAdapterUtil.LOG_WTF
        )

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
