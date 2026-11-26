/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

object LogExporter {

    const val MIME_TEXT = "text/plain"
    const val MIME_ZIP = "application/zip"

    private const val TAG = "LogExporter"

    fun interface Writer {
        @Throws(IOException::class)
        fun write(out: OutputStream)
    }

    @JvmStatic
    fun textFilename(): String = SaveLogHelper.createLogFilename()

    @JvmStatic
    fun zipFilename(): String = SaveLogHelper.createLogBasename() + ".zip"

    @JvmStatic
    fun exportText(activity: ComponentActivity, uri: Uri, text: CharSequence) {
        export(activity, uri) { out -> out.write(text.toString().toByteArray()) }
    }

    // writer runs on a background thread
    @JvmStatic
    fun export(activity: ComponentActivity, uri: Uri, writer: Writer) {
        val context = activity.applicationContext
        activity.lifecycleScope.launch {
            val exported = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Unable to open $uri")
                    BufferedOutputStream(stream).use { writer.write(it) }
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Export failed", e)
                    false
                }
            }
            Toast.makeText(
                context,
                if (exported) R.string.matlog_export_done else R.string.matlog_export_failed,
                if (exported) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }
}
