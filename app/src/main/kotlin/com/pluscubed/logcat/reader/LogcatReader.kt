/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import org.omnirom.logcat.R
import com.pluscubed.logcat.helper.LogcatHelper
import com.pluscubed.logcat.helper.PreferenceHelper

class LogcatReader private constructor(private val process: Process) : LineReader {

    private val reader: BufferedReader = process.inputStream.bufferedReader(Charsets.UTF_8)

    @Volatile
    var isKilled = false
        private set

    @Throws(IOException::class)
    override fun readLine(): String? = if (isKilled) null else reader.readLine()

    fun ready(): Boolean = try {
        !isKilled && reader.ready()
    } catch (e: IOException) {
        false
    }

    override fun killQuietly() {
        if (isKilled) return
        isKilled = true
        runCatching { process.destroy() }
    }

    companion object {
        private const val TAG = "LogcatReader"

        // everything logcat still holds, across all buffers
        fun dumpAll(context: Context): List<CharSequence> {
            val buffers = context.resources.getStringArray(R.array.pref_buffer_choice_values).toList()
            return try {
                val process = ProcessBuilder(LogcatHelper.buildArgs(buffers, dump = true))
                    .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
                    .start()
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
                } finally {
                    process.destroy()
                }
            } catch (e: IOException) {
                Log.w(TAG, "Unable to dump logcat", e)
                emptyList()
            }
        }

        @Throws(IOException::class)
        fun open(context: Context, since: String? = null): LogcatReader =
            open(PreferenceHelper.getBuffers(context), since)

        @Throws(IOException::class)
        fun open(
            buffers: Collection<String>,
            since: String?,
            filterSpecs: List<String> = emptyList(),
        ): LogcatReader {
            val process = ProcessBuilder(LogcatHelper.buildArgs(buffers, since, filterSpecs))
                .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
                .start()
            return LogcatReader(process)
        }
    }
}
