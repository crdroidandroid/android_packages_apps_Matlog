/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemProperties
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException

// reads the kernel log from the matlog_dmesg helper
class DmesgReader private constructor(private val socket: LocalSocket) : LineReader {

    private val reader: BufferedReader = socket.inputStream.bufferedReader(Charsets.UTF_8)

    @Volatile
    private var isKilled = false

    @Throws(IOException::class)
    override fun readLine(): String? = if (isKilled) null else reader.readLine()

    override fun killQuietly() {
        if (isKilled) return
        isKilled = true
        runCatching { socket.shutdownInput() }
        runCatching { socket.close() }
    }

    companion object {
        private const val TAG = "DmesgReader"
        private const val SOCKET_NAME = "matlog_dmesg"
        private const val COMMAND_DUMP = 'd'
        private const val COMMAND_FOLLOW = 'f'
        private const val KERNEL_BUFFER = "kernel"
        private const val PROP_LOGD_KERNEL = "ro.logd.kernel"

        @JvmStatic
        fun hasHelper(): Boolean = File("/dev/socket/$SOCKET_NAME").exists()

        @JvmStatic
        fun isAvailable(): Boolean = hasHelper() || SystemProperties.getBoolean(PROP_LOGD_KERNEL, false)

        // falls back to logd's kernel buffer when the helper isn't installed
        @JvmStatic
        @Throws(IOException::class)
        fun open(since: String?): LineReader =
            if (hasHelper()) connect(COMMAND_FOLLOW) else LogcatReader.open(listOf(KERNEL_BUFFER), since)

        @JvmStatic
        fun dump(): List<String> {
            if (!hasHelper()) return emptyList()
            return try {
                connect(COMMAND_DUMP).use { reader -> generateSequence { reader.readLine() }.toList() }
            } catch (e: IOException) {
                Log.w(TAG, "Unable to read the kernel log", e)
                emptyList()
            }
        }

        @Throws(IOException::class)
        private fun connect(command: Char): DmesgReader {
            val socket = LocalSocket()
            try {
                socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.RESERVED))
                socket.outputStream.write(command.code)
                socket.outputStream.flush()
            } catch (e: IOException) {
                socket.close()
                throw e
            }
            return DmesgReader(socket)
        }
    }
}
