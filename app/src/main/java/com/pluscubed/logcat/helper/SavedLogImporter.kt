/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R
import java.io.File

// copies a log opened from another app into the saved logs so it can be viewed
object SavedLogImporter {

    private const val TAG = "SavedLogImporter"

    fun interface Callback {
        fun onImported(filename: String)
    }

    @JvmStatic
    fun importLog(activity: ComponentActivity, uri: Uri, callback: Callback) {
        val context = activity.applicationContext
        activity.lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    copy(context, uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to import $uri", e)
                    null
                }
            }
            if (name == null) {
                Toast.makeText(context, R.string.matlog_saved_logs_import_failed, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, R.string.matlog_saved_logs_imported, Toast.LENGTH_SHORT).show()
                callback.onImported(name)
            }
        }
    }

    private fun copy(context: Context, uri: Uri): String {
        val dir = SaveLogHelper.getSavedLogsDirectory()
        val target = uniqueFile(dir, displayName(context, uri))
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target.name
    }

    private fun displayName(context: Context, uri: Uri): String {
        val queried = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return (queried ?: uri.lastPathSegment)
            ?.substringAfterLast('/')
            ?.replace(':', '_')
            ?.takeIf { it.isNotBlank() }
            ?: SaveLogHelper.createLogFilename()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var index = 1
        while (file.exists()) {
            file = File(dir, "$base ($index)$extension")
            index++
        }
        return file
    }
}
