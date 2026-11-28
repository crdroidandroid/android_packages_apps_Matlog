/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.omnirom.logcat.R

object DialogHelper {

    @JvmStatic
    fun startRecording(
        filename: String,
        filterQuery: String,
        logLevel: String,
        onPostExecute: Runnable?,
        context: Context,
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            ServiceHelper.startBackgroundServiceIfNotAlreadyRunning(context, filename, filterQuery, logLevel)
            onPostExecute?.let { handler.post(it) }
        }.start()
    }

    @JvmStatic
    fun stopRecordingLog(context: Context) {
        ServiceHelper.stopBackgroundServiceIfRunning(context)
    }

    @JvmStatic
    fun createLogFilename(): String = SaveLogHelper.createLogFilename()

    @JvmStatic
    fun isInvalidFilename(filename: CharSequence?): Boolean {
        val name = filename?.toString().orEmpty()
        return name.isEmpty() || '/' in name || ':' in name || ' ' in name || !name.endsWith(".txt")
    }

    // log level names with the default one marked
    @JvmStatic
    fun logLevelLabels(context: Context): Array<String> {
        val labels = context.resources.getStringArray(R.array.log_levels)
        val default = PreferenceHelper.getDefaultLogLevelPreference(context).toString()
        val index = context.resources.getStringArray(R.array.log_levels_values).indexOf(default)
        if (index >= 0) labels[index] = "${labels[index]} ${context.getString(R.string.default_in_parens)}"
        return labels
    }
}
