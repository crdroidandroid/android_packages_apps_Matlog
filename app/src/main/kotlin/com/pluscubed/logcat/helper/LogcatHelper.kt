/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import java.util.Locale

object LogcatHelper {

    const val BUFFER_MAIN = "main"
    const val BUFFER_SYSTEM = "system"
    const val BUFFER_CRASH = "crash"

    // what logcat reads when no buffer is given
    val DEFAULT_BUFFERS: Set<String> = setOf(BUFFER_MAIN, BUFFER_SYSTEM, BUFFER_CRASH)

    fun buildArgs(
        buffers: Collection<String>,
        since: String? = null,
        filterSpecs: List<String> = emptyList(),
        dump: Boolean = false,
    ): List<String> {
        val args = mutableListOf("logcat", "-v", "time", "-v", "uid")
        if (dump) {
            args += "-d"
        }
        if (buffers.toSet() != DEFAULT_BUFFERS) {
            buffers.distinct().forEach { args += listOf("-b", it) }
        }
        if (since != null) {
            args += listOf("-T", since)
        }
        args += filterSpecs
        return args
    }

    fun currentTimestamp(): String {
        val now = System.currentTimeMillis()
        return String.format(Locale.US, "%d.%03d", now / 1000, now % 1000)
    }
}
