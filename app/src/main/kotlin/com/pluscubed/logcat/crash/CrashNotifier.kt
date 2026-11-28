/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.omnirom.logcat.R

object CrashNotifier {

    private const val CHANNEL_ID = "matlog_crashes"
    private const val PREVIEW_LINES = 8

    fun notify(context: Context, crash: Crash) {
        if (!CrashMonitor.areNotificationsEnabled(context)) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val appLabel = appLabel(context, crash.packageName)
        val title = context.getString(
            if (crash.type == CrashType.ANR) R.string.matlog_crash_notification_title_anr
            else R.string.matlog_crash_notification_title,
            appLabel
        )
        val preview = crash.log.lineSequence()
            .drop(1)
            .take(PREVIEW_LINES)
            .joinToString("\n") { it.substringAfter("): ", it) }
        val requestCode = crash.id.toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bug_report)
            .setContentTitle(title)
            .setContentText(context.getString(crash.type.labelRes))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, requestCode,
                    CrashesActivity.intent(context, crash.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0, context.getString(R.string.matlog_crash_copy),
                PendingIntent.getBroadcast(
                    context, requestCode,
                    CrashActionReceiver.copyIntent(context, crash.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0, context.getString(R.string.matlog_crash_share),
                PendingIntent.getActivity(
                    context, requestCode,
                    CrashesActivity.shareIntent(context, crash.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        try {
            manager.notify(crash.packageName, requestCode, notification)
        } catch (e: SecurityException) {
            // notification permission revoked
        }
    }

    fun cancel(context: Context, crash: Crash) {
        NotificationManagerCompat.from(context).cancel(crash.packageName, crash.id.toInt())
    }

    fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.matlog_crash_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.matlog_crash_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
