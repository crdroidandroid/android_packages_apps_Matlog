/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.pluscubed.logcat.data.SavedLog

object SaveLogHelper {

    const val TEMP_DEVICE_INFO_FILENAME = "device_info.txt"
    const val TEMP_DMESG_FILENAME = "dmesg.txt"
    const val TEMP_LOGCAT_FILENAME = "logcat.txt"

    private const val TAG = "SaveLogHelper"
    private const val LOG_FILENAME_PREFIX = "logcat-"
    private const val KERNEL_LOG_FILENAME_PREFIX = "dmesg-"
    private const val ZIP_FILENAME_PREFIX = "logs-"
    private const val BUFFER = 0x1000
    private const val CATLOG_DIR = "matlog"
    private const val SHARE_DIR = "share"
    private const val SAVED_LOGS_DIR = "saved_logs"

    // text is written as is, lines each get their own line
    fun saveTemporaryFile(context: Context, filename: String, text: CharSequence?, lines: List<CharSequence>?): File? =
        try {
            File(tempDirectory(context), filename).also { file ->
                file.bufferedWriter(bufferSize = BUFFER).use { out ->
                    if (text != null) {
                        out.append(text)
                    } else {
                        lines?.forEach { out.append(it).append('\n') }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to save $filename", e)
            null
        }

    fun getFile(filename: String): File = File(getSavedLogsDirectory(), filename)

    fun deleteLogIfExists(filename: String) {
        getFile(filename).delete()
    }

    // keeps only the last maxLines lines
    fun openLog(filename: String, maxLines: Int): SavedLog {
        val lines = ArrayDeque<String>()
        var truncated = false
        try {
            getFile(filename).bufferedReader(bufferSize = BUFFER).useLines { sequence ->
                sequence.forEach { line ->
                    lines.addLast(line)
                    if (lines.size > maxLines) {
                        lines.removeFirst()
                        truncated = true
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read $filename", e)
        }
        return SavedLog(lines.toList(), truncated)
    }

    @Synchronized
    fun saveLog(text: CharSequence, filename: String): Boolean = append(filename) { it.append(text) }

    @Synchronized
    fun saveLog(lines: List<CharSequence>, filename: String): Boolean =
        append(filename) { out -> lines.forEach { out.append(it).append('\n') } }

    private fun append(filename: String, write: (Appendable) -> Unit): Boolean = try {
        FileWriter(getFile(filename), true).buffered(BUFFER).use(write)
        true
    } catch (e: IOException) {
        Log.e(TAG, "Unable to save $filename", e)
        false
    }

    private fun tempDirectory(context: Context): File = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    fun getSavedLogsDirectory(): File =
        File(File(Environment.getExternalStorageDirectory(), CATLOG_DIR), SAVED_LOGS_DIR).apply { mkdirs() }

    fun saveTemporaryZipFile(context: Context, filename: String, files: List<File>): File? = try {
        File(tempDirectory(context), filename).also { zip -> zip.outputStream().use { writeZip(it, files) } }
    } catch (e: IOException) {
        Log.e(TAG, "Unable to zip $filename", e)
        null
    }

    @Throws(IOException::class)
    fun writeZip(out: OutputStream, files: List<File>) {
        val zip = ZipOutputStream(out.buffered(BUFFER))
        for (file in files) {
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().buffered(BUFFER).use { it.copyTo(zip, BUFFER) }
        }
        zip.finish()
        zip.flush()
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())

    fun createLogBasename(): String = LOG_FILENAME_PREFIX + timestamp()

    fun createLogFilename(): String = createLogBasename() + ".txt"

    fun createZipFilename(): String = "$ZIP_FILENAME_PREFIX${timestamp()}.zip"

    fun createKernelLogFilename(): String = "$KERNEL_LOG_FILENAME_PREFIX${timestamp()}.txt"

    fun cleanTemp(context: Context) {
        tempDirectory(context).listFiles()?.forEach { it.delete() }
    }
}
