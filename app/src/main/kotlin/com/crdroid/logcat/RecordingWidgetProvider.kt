/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.crdroid.logcat.helper.PreferenceHelper
import com.crdroid.logcat.helper.WidgetHelper

class RecordingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // remembered because android can keep stale widget ids around
        PreferenceHelper.setWidgetExistsPreference(context, appWidgetIds)
        WidgetHelper.updateWidgets(context, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { PreferenceHelper.clearWidgetExistsPreference(context, it) }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
            PreferenceHelper.remapWidgetExistsPreference(context, old, new)
        }
    }

    companion object {
        const val ACTION_RECORD_OR_STOP = "com.pluscubed.logcat.action.RECORD_OR_STOP"
    }
}
