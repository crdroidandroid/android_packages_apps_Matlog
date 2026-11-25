/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.content.Context
import com.pluscubed.logcat.helper.LogcatHelper
import com.pluscubed.logcat.helper.PreferenceHelper
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.IOException

class LogcatReader private constructor(private val process: Process) : Closeable {

    private val reader: BufferedReader = process.inputStream.bufferedReader(Charsets.UTF_8)

    @Volatile
    var isKilled = false
        private set

    @Throws(IOException::class)
    fun readLine(): String? = if (isKilled) null else reader.readLine()

    fun ready(): Boolean = try {
        !isKilled && reader.ready()
    } catch (e: IOException) {
        false
    }

    fun killQuietly() {
        if (isKilled) return
        isKilled = true
        runCatching { process.destroy() }
    }

    override fun close() = killQuietly()

    companion object {
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun open(context: Context, since: String? = null): LogcatReader =
            open(PreferenceHelper.getBuffers(context), since)

        @JvmStatic
        @Throws(IOException::class)
        fun open(buffers: Collection<String>, since: String?): LogcatReader {
            val process = ProcessBuilder(LogcatHelper.buildArgs(buffers, since))
                .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
                .start()
            return LogcatReader(process)
        }
    }
}
