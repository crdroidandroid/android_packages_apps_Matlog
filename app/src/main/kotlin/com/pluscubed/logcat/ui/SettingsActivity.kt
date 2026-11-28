/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager
import org.omnirom.logcat.R
import com.pluscubed.logcat.crash.CrashMonitorService
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.ui.compose.MatlogTheme

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var bufferChanged = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            getString(R.string.pref_buffer) -> {
                bufferChanged = true
                publishResult()
            }
            getString(R.string.matlog_pref_crash_detection),
            getString(R.string.matlog_pref_crash_monitor_service),
            getString(R.string.matlog_pref_exception_monitor) -> CrashMonitorService.update(this)
            PREF_SCRUBBER -> LogLine.isScrubberEnabled = PreferenceHelper.isScrubberEnabled(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        bufferChanged = savedInstanceState?.getBoolean(STATE_BUFFER_CHANGED) ?: false
        publishResult()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        setContent {
            MatlogTheme {
                SettingsScreen(onBack = ::finish)
            }
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_BUFFER_CHANGED, bufferChanged)
    }

    // LogcatActivity restarts the log when the buffers change
    private fun publishResult() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_BUFFER_CHANGED, bufferChanged))
    }

    private companion object {
        const val EXTRA_BUFFER_CHANGED = "bufferChanged"
        const val STATE_BUFFER_CHANGED = "buffer_changed"
    }
}
