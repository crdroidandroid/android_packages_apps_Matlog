/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.LogDisplayOptions
import com.pluscubed.logcat.ui.PREF_SCRUBBER

object PreferenceHelper {

    private const val WIDGET_EXISTS_PREFIX = "widget_"

    // read often while drawing the list, so cached until clearCache()
    @Volatile
    private var textSize = -1f
    @Volatile
    private var defaultLogLevel: Char? = null
    @Volatile
    private var displayLimit = -1

    private fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun clearCache() {
        defaultLogLevel = null
        textSize = -1f
        displayLimit = -1
        LogDisplayOptions.invalidate()
    }

    private fun widgetKey(appWidgetId: Int) = WIDGET_EXISTS_PREFIX + appWidgetId

    fun getWidgetExistsPreference(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(widgetKey(appWidgetId), false)

    fun setWidgetExistsPreference(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { putBoolean(widgetKey(it), true) } }
    }

    fun clearWidgetExistsPreference(context: Context, appWidgetId: Int) {
        prefs(context).edit { remove(widgetKey(appWidgetId)) }
    }

    fun remapWidgetExistsPreference(context: Context, oldId: Int, newId: Int) {
        prefs(context).edit {
            putBoolean(widgetKey(newId), true)
            remove(widgetKey(oldId))
        }
    }

    private fun intPreference(context: Context, keyRes: Int, defaultRes: Int): Int {
        val default = context.getString(defaultRes)
        return prefs(context).getString(context.getString(keyRes), default)?.toIntOrNull() ?: default.toInt()
    }

    fun getDisplayLimitPreference(context: Context): Int {
        if (displayLimit == -1) {
            displayLimit = intPreference(context, R.string.pref_display_limit, R.string.pref_display_limit_default)
        }
        return displayLimit
    }

    fun getLogLinePeriodPreference(context: Context): Int =
        intPreference(context, R.string.pref_log_line_period, R.string.pref_log_line_period_default)

    fun setDisplayLimitPreference(context: Context, value: Int) {
        prefs(context).edit { putString(context.getString(R.string.pref_display_limit), value.toString()) }
    }

    fun setLogLinePeriodPreference(context: Context, value: Int) {
        prefs(context).edit { putString(context.getString(R.string.pref_log_line_period), value.toString()) }
    }

    fun getDefaultLogLevelPreference(context: Context): Char {
        defaultLogLevel?.let { return it }
        val verbose = context.getString(R.string.log_level_value_verbose)
        val level = prefs(context).getString(context.getString(R.string.pref_default_log_level), verbose)
            ?.firstOrNull() ?: verbose[0]
        defaultLogLevel = level
        return level
    }

    fun getTextSizePreference(context: Context): Float {
        if (textSize == -1f) {
            val value = prefs(context).getString(
                context.getString(R.string.pref_text_size),
                context.getString(R.string.text_size_medium_value),
            )
            val dimen = when (value) {
                context.getString(R.string.text_size_xsmall_value) -> R.dimen.text_size_xsmall
                context.getString(R.string.text_size_small_value) -> R.dimen.text_size_small
                context.getString(R.string.text_size_medium_value) -> R.dimen.text_size_medium
                context.getString(R.string.text_size_large_value) -> R.dimen.text_size_large
                else -> R.dimen.text_size_xlarge
            }
            textSize = context.resources.getDimension(dimen)
        }
        return textSize
    }

    fun getExpandedByDefaultPreference(context: Context): Boolean =
        prefs(context).getBoolean(context.getString(R.string.pref_expanded_by_default), false)

    fun getBuffers(context: Context): Set<String> {
        val default = context.resources.getStringArray(R.array.pref_buffer_choice_values).toSet()
        return prefs(context).getStringSet(context.getString(R.string.pref_buffer), default) ?: default
    }

    fun getIncludeDeviceInfoPreference(context: Context): Boolean =
        prefs(context).getBoolean(context.getString(R.string.pref_include_device_info), true)

    fun setIncludeDeviceInfoPreference(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(context.getString(R.string.pref_include_device_info), value) }
    }

    fun isScrubberEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_SCRUBBER, false)
}
