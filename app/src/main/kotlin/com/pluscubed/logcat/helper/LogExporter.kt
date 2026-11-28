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
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

object LogExporter {

    const val MIME_TEXT = "text/plain"
    const val MIME_ZIP = "application/zip"

    private const val TAG = "LogExporter"

    fun textFilename(): String = SaveLogHelper.createLogFilename()

    fun zipFilename(): String = SaveLogHelper.createZipFilename()

    fun exportText(activity: ComponentActivity, uri: Uri, text: CharSequence) {
        export(activity, uri) { out -> out.write(text.toString().toByteArray()) }
    }

    // writer runs on a background thread
    fun export(activity: ComponentActivity, uri: Uri, writer: (OutputStream) -> Unit) {
        val context = activity.applicationContext
        activity.lifecycleScope.launch {
            val exported = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Unable to open $uri")
                    BufferedOutputStream(stream).use { writer(it) }
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
