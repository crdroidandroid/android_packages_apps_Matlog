/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.crdroid.logcat.data.LogDisplayOptions
import com.crdroid.logcat.helper.PackageNameResolver
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

class MatlogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder().setThemeOverlay(R.style.Theme_MatLog_Overlay).build(),
        )
        createNotificationChannel()
        PackageNameResolver.init(this)
        LogDisplayOptions.migrate(this)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "com.pluscubed.logcat.notification"
    }
}
