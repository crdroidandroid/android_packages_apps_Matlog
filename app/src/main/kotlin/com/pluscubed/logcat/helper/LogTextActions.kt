/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.SendLogDetails
import com.pluscubed.logcat.ui.LogcatActivity

object LogTextActions {

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val copied = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
            true
        } catch (e: RuntimeException) {
            // clip larger than the binder transaction limit
            false
        }
        Toast.makeText(
            context,
            if (copied) R.string.copied_to_clipboard else R.string.matlog_copy_too_large,
            Toast.LENGTH_SHORT
        ).show()
    }

    // shared as a file, the same way as a normal log
    fun share(activity: ComponentActivity, text: String) {
        activity.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                SaveLogHelper.cleanTemp(activity)
                SaveLogHelper.saveTemporaryFile(activity, SaveLogHelper.createLogFilename(), text, null)
            }
            if (file == null) {
                Toast.makeText(activity, R.string.matlog_selection_share_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            LogcatActivity.startChooser(
                activity,
                activity.getString(R.string.matlog_selection_share_subject),
                "",
                SendLogDetails.AttachmentType.Text,
                file
            )
        }
    }
}
