/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.helper

import android.app.ActivityManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.crdroid.logcat.LogcatRecordingService

object ServiceHelper {

    @Synchronized
    fun stopBackgroundServiceIfRunning(context: Context) {
        if (checkIfServiceIsRunning(context, LogcatRecordingService::class.java)) {
            context.stopService(Intent(context, LogcatRecordingService::class.java))
        }
    }

    @Synchronized
    fun startBackgroundServiceIfNotAlreadyRunning(context: Context, filename: String, queryFilter: String, level: String) {
        if (checkIfServiceIsRunning(context, LogcatRecordingService::class.java)) return
        context.startService(
            Intent(context, LogcatRecordingService::class.java)
                .putExtra(LogcatRecordingService.EXTRA_FILENAME, filename)
                .putExtra(LogcatRecordingService.EXTRA_SINCE, LogcatHelper.currentTimestamp())
                .putExtra(LogcatRecordingService.EXTRA_QUERY_FILTER, queryFilter)
                .putExtra(LogcatRecordingService.EXTRA_LEVEL, level)
        )
    }

    // getRunningServices still reports the caller's own services
    @Suppress("DEPRECATION")
    fun checkIfServiceIsRunning(context: Context, service: Class<out Service>): Boolean {
        val component = ComponentName(context, service)
        return context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .orEmpty()
            .any { it.service == component }
    }
}
