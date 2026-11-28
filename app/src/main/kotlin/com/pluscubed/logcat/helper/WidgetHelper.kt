/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.widget.RemoteViews
import com.pluscubed.logcat.LogcatRecordingService
import org.omnirom.logcat.R
import com.pluscubed.logcat.RecordingWidgetProvider
import com.pluscubed.logcat.ui.RecordLogDialogActivity

object WidgetHelper {

    private const val WIDE_WIDGET_WIDTH = 120f

    fun updateWidgets(
        context: Context,
        appWidgetIds: IntArray = findAppWidgetIds(context),
        serviceRunning: Boolean = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService::class.java),
    ) {
        val manager = AppWidgetManager.getInstance(context)
        // android sometimes keeps stale app widget ids around
        appWidgetIds.filter { PreferenceHelper.getWidgetExistsPreference(context, it) }.forEach { id ->
            val pendingIntent = pendingIntent(context)
            val layouts = mapOf(
                SizeF(48f, 48f) to widgetViews(context, R.layout.widget_recording, serviceRunning, pendingIntent),
                SizeF(WIDE_WIDGET_WIDTH, 48f) to
                    widgetViews(context, R.layout.widget_recording_wide, serviceRunning, pendingIntent),
            )
            manager.updateAppWidget(id, RemoteViews(layouts))
        }
    }

    private fun widgetViews(context: Context, layout: Int, running: Boolean, pendingIntent: PendingIntent) =
        RemoteViews(context.packageName, layout).apply {
            setImageViewResource(R.id.record_badge_image_view, if (running) R.drawable.ic_widget_stop else R.drawable.ic_widget_record)
            setInt(
                R.id.record_badge_image_view, "setBackgroundResource",
                if (running) R.drawable.bg_widget_button_recording else R.drawable.bg_widget_button_idle,
            )
            setContentDescription(
                R.id.record_badge_image_view,
                context.getString(if (running) R.string.matlog_stop else R.string.record_log),
            )
            if (layout == R.layout.widget_recording_wide) {
                setTextViewText(
                    R.id.record_label,
                    context.getString(if (running) R.string.widget_status_recording else R.string.start_recording_log),
                )
            }
            setOnClickPendingIntent(android.R.id.background, pendingIntent)
        }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordLogDialogActivity::class.java)
            .setAction(RecordingWidgetProvider.ACTION_RECORD_OR_STOP)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun findAppWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, RecordingWidgetProvider::class.java))
}
