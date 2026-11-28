/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.crash

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.crdroid.logcat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CrashActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY) return
        val id = intent.getLongExtra(EXTRA_CRASH_ID, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crash = CrashRepository.get(context).get(id) ?: return@launch
                copyToClipboard(context, crash)
                CrashNotifier.cancel(context, crash)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_COPY = "com.crdroid.logcat.action.COPY_CRASH"
        private const val EXTRA_CRASH_ID = "crash_id"

        fun copyIntent(context: Context, crashId: Long): Intent =
            Intent(context, CrashActionReceiver::class.java)
                .setAction(ACTION_COPY)
                .putExtra(EXTRA_CRASH_ID, crashId)

        fun copyToClipboard(context: Context, crash: Crash) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.matlog_crashes), crash.log)
            )
        }
    }
}
