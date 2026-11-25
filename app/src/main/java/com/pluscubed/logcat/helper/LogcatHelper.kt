/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import java.util.Locale

object LogcatHelper {

    const val BUFFER_MAIN = "main"
    const val BUFFER_EVENTS = "events"
    const val BUFFER_RADIO = "radio"

    // "main" maps to logcat's default set so crash output stays visible
    private val MAIN_BUFFERS = listOf("main", "system", "crash")

    @JvmStatic
    fun buildArgs(buffers: Collection<String>, since: String? = null): List<String> {
        val args = mutableListOf("logcat", "-v", "time")
        if (buffers.isNotEmpty() && !(buffers.size == 1 && buffers.contains(BUFFER_MAIN))) {
            buffers.flatMap { if (it == BUFFER_MAIN) MAIN_BUFFERS else listOf(it) }
                .distinct()
                .forEach { args += listOf("-b", it) }
        }
        if (since != null) {
            args += listOf("-T", since)
        }
        return args
    }

    @JvmStatic
    fun currentTimestamp(): String {
        val now = System.currentTimeMillis()
        return String.format(Locale.US, "%d.%03d", now / 1000, now % 1000)
    }
}
