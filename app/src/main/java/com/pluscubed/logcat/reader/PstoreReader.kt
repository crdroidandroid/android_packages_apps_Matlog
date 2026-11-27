/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PstoreFile(val name: String, val size: Long, val modified: Long) {

    val kind: Kind
        get() = when {
            name.startsWith("console-ramoops") -> Kind.CONSOLE
            name.startsWith("dmesg-ramoops") -> Kind.DMESG
            name.startsWith("pmsg-ramoops") -> Kind.PMSG
            name.startsWith("ftrace-ramoops") -> Kind.FTRACE
            else -> Kind.OTHER
        }

    enum class Kind { CONSOLE, DMESG, PMSG, FTRACE, OTHER }
}

// reads /sys/fs/pstore through the matlog_dmesg helper
object PstoreReader {

    private const val TAG = "PstoreReader"
    private const val COMMAND_LIST = "l"
    private const val COMMAND_FILE = "p"

    // android_pmsg_log_header_t followed by android_log_header_t
    private const val PMSG_MAGIC = 'l'.code.toByte()
    private const val PMSG_HEADER_SIZE = 7
    private const val LOG_HEADER_SIZE = 11
    private const val LOG_ID_EVENTS = 2
    private const val LOG_ID_SECURITY = 6
    private const val LOG_ID_STATS = 5

    @JvmStatic
    fun isAvailable(): Boolean = DmesgReader.hasHelper()

    @JvmStatic
    fun list(): List<PstoreFile> {
        if (!isAvailable()) return emptyList()
        return try {
            DmesgReader.connect(COMMAND_LIST).use { socket ->
                socket.inputStream.bufferedReader(Charsets.UTF_8).readLines().mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size != 3) return@mapNotNull null
                    PstoreFile(parts[0], parts[1].toLongOrNull() ?: 0L, (parts[2].toLongOrNull() ?: 0L) * 1000)
                }.sortedBy { it.name }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to list pstore", e)
            emptyList()
        }
    }

    // pmsg holds binary logcat records, which are turned into logcat lines
    @JvmStatic
    fun read(file: PstoreFile): List<String> {
        val bytes = try {
            DmesgReader.connect("$COMMAND_FILE${file.name}\n").use { socket -> socket.inputStream.readBytes() }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to read ${file.name}", e)
            return emptyList()
        }
        return if (file.kind == PstoreFile.Kind.PMSG) decodePmsg(bytes) else String(bytes, Charsets.UTF_8).lines()
    }

    private fun decodePmsg(bytes: ByteArray): List<String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        val lines = ArrayList<String>()
        var pos = 0
        while (pos + PMSG_HEADER_SIZE + LOG_HEADER_SIZE < bytes.size) {
            if (bytes[pos] != PMSG_MAGIC) {
                pos++
                continue
            }
            val length = buffer.getShort(pos + 1).toInt() and 0xffff
            if (length <= PMSG_HEADER_SIZE + LOG_HEADER_SIZE + 1 || pos + length > bytes.size) {
                pos++
                continue
            }
            val uid = buffer.getShort(pos + 3).toInt() and 0xffff
            val pid = buffer.getShort(pos + 5).toInt() and 0xffff
            val logId = bytes[pos + PMSG_HEADER_SIZE].toInt() and 0xff
            val seconds = buffer.getInt(pos + PMSG_HEADER_SIZE + 3).toLong() and 0xffffffffL
            val nanos = buffer.getInt(pos + PMSG_HEADER_SIZE + 7).toLong() and 0xffffffffL
            val start = pos + PMSG_HEADER_SIZE + LOG_HEADER_SIZE
            val end = pos + length
            pos = end
            // these buffers carry binary payloads
            if (logId == LOG_ID_EVENTS || logId == LOG_ID_STATS || logId == LOG_ID_SECURITY) continue

            val priority = bytes[start].toInt()
            val tagEnd = indexOfZero(bytes, start + 1, end)
            val tag = String(bytes, start + 1, tagEnd - start - 1, Charsets.UTF_8)
            val messageStart = minOf(tagEnd + 1, end)
            val messageEnd = indexOfZero(bytes, messageStart, end)
            val message = String(bytes, messageStart, messageEnd - messageStart, Charsets.UTF_8).trimEnd('\n')
            val prefix = "${format.format(Date(seconds * 1000 + nanos / 1_000_000))} " +
                "${levelChar(priority)}/$tag($uid:$pid): "
            message.split('\n').forEach { lines.add(prefix + it) }
        }
        return lines
    }

    private fun indexOfZero(bytes: ByteArray, from: Int, to: Int): Int {
        for (i in from until to) {
            if (bytes[i].toInt() == 0) return i
        }
        return to
    }

    private fun levelChar(priority: Int): Char = when (priority) {
        3 -> 'D'
        4 -> 'I'
        5 -> 'W'
        6 -> 'E'
        7 -> 'F'
        else -> 'V'
    }
}
