/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.LogDisplayOptions
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.ui.compose.ListPreference
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MultiSelectListPreference
import com.pluscubed.logcat.ui.compose.PreferenceGroup
import com.pluscubed.logcat.ui.compose.SettingsScaffold
import com.pluscubed.logcat.ui.compose.SwitchPreference
import com.pluscubed.logcat.ui.compose.TextInputPreference

private const val MIN_LOG_LINE_PERIOD = 1
private const val MAX_LOG_LINE_PERIOD = 1000
private const val MIN_DISPLAY_LIMIT = 1000
private const val MAX_DISPLAY_LIMIT = 100000
const val PREF_SCRUBBER = "scrubber"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        AppearanceSettings(prefs)
        ConfigurationSettings(prefs)
        CrashSettings(prefs)
        AdvancedSettings(prefs)
    }
}

@Composable
private fun AppearanceSettings(prefs: SharedPreferences) {
    val textSizeKey = stringResource(R.string.pref_text_size)
    val textSize by prefs.stringState(textSizeKey, stringResource(R.string.text_size_medium_value))
    val expandedKey = stringResource(R.string.pref_expanded_by_default)
    val expanded by prefs.booleanState(expandedKey, false)
    val columnsKey = stringResource(R.string.matlog_pref_log_columns)
    val columnOptions = options(R.array.matlog_log_columns, R.array.matlog_log_columns_values)
    val columns by prefs.stringSetState(columnsKey, stringArrayResource(R.array.matlog_log_columns_default).toSet())
    val columnsSummary = stringResource(R.string.matlog_pref_log_columns_summary)
    val dateKey = stringResource(R.string.matlog_pref_date_format)
    val date by prefs.stringState(dateKey, stringResource(R.string.matlog_date_format_default))
    val timeKey = stringResource(R.string.matlog_pref_time_format)
    val time by prefs.stringState(timeKey, stringResource(R.string.matlog_time_format_default))
    val keepScreenOnKey = stringResource(R.string.matlog_pref_keep_screen_on)
    val keepScreenOn by prefs.booleanState(keepScreenOnKey, false)
    val formatError = stringResource(R.string.matlog_invalid_date_format)
    val validateFormat = { input: String -> formatError.takeUnless { LogDisplayOptions.isValidFormat(input) } }

    PreferenceGroup(title = stringResource(R.string.pref_cat_appearance)) {
        item {
            ListPreference(
                title = stringResource(R.string.pref_text_size_title),
                icon = R.drawable.ic_format_size,
                options = options(R.array.text_sizes, R.array.text_size_values),
                value = textSize,
                onValueChange = { prefs.edit { putString(textSizeKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.pref_expanded_by_default_title),
                summary = stringResource(R.string.pref_expanded_by_default_summary),
                icon = R.drawable.ic_unfold_more,
                checked = expanded,
                onCheckedChange = { prefs.edit { putBoolean(expandedKey, it) } },
            )
        }
        item {
            MultiSelectListPreference(
                title = stringResource(R.string.matlog_pref_log_columns_title),
                summary = labelsFor(columnOptions, columns).ifEmpty { columnsSummary },
                message = columnsSummary,
                icon = R.drawable.ic_view_column,
                options = columnOptions,
                values = columns,
                onValuesChange = { prefs.edit { putStringSet(columnsKey, it) } },
            )
        }
        item {
            TextInputPreference(
                title = stringResource(R.string.matlog_pref_date_format_title),
                summary = date,
                icon = R.drawable.ic_calendar_today,
                value = date,
                validate = validateFormat,
                onValueChange = { prefs.edit { putString(dateKey, it) } },
            )
        }
        item {
            TextInputPreference(
                title = stringResource(R.string.matlog_pref_time_format_title),
                summary = time,
                icon = R.drawable.ic_schedule,
                value = time,
                validate = validateFormat,
                onValueChange = { prefs.edit { putString(timeKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_keep_screen_on_title),
                summary = stringResource(R.string.matlog_pref_keep_screen_on_summary),
                icon = R.drawable.ic_light_mode,
                checked = keepScreenOn,
                onCheckedChange = { prefs.edit { putBoolean(keepScreenOnKey, it) } },
            )
        }
    }
}

@Composable
private fun ConfigurationSettings(prefs: SharedPreferences) {
    val bufferKey = stringResource(R.string.pref_buffer)
    val bufferOptions = options(R.array.pref_buffer_choices, R.array.pref_buffer_choice_values)
    val buffers by prefs.stringSetState(bufferKey, stringArrayResource(R.array.pref_buffer_choice_values_default).toSet())
    val simultaneous = stringResource(R.string.simultaneous)
    val levelKey = stringResource(R.string.pref_default_log_level)
    val level by prefs.stringState(levelKey, stringResource(R.string.log_level_value_verbose))
    val filterKey = stringResource(R.string.matlog_pref_filter_on_search)
    val filterOnSearch by prefs.booleanState(filterKey, true)

    PreferenceGroup(title = stringResource(R.string.pref_cat_configuration)) {
        item {
            MultiSelectListPreference(
                title = stringResource(R.string.pref_buffer_title),
                summary = labelsFor(bufferOptions, buffers) + if (buffers.size > 1) simultaneous else "",
                icon = R.drawable.ic_layers,
                options = bufferOptions,
                values = buffers,
                allowEmpty = false,
                onValuesChange = { prefs.edit { putStringSet(bufferKey, it) } },
            )
        }
        item {
            ListPreference(
                title = stringResource(R.string.pref_default_log_level_title),
                icon = R.drawable.ic_tune,
                options = options(R.array.log_levels, R.array.log_levels_values),
                value = level,
                onValueChange = { prefs.edit { putString(levelKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_filter_on_search_title),
                summary = stringResource(R.string.matlog_pref_filter_on_search_summary),
                icon = R.drawable.ic_filter,
                checked = filterOnSearch,
                onCheckedChange = { prefs.edit { putBoolean(filterKey, it) } },
            )
        }
    }
}

@Composable
private fun CrashSettings(prefs: SharedPreferences) {
    val detectionKey = stringResource(R.string.matlog_pref_crash_detection)
    val detection by prefs.booleanState(detectionKey, true)
    val notificationsKey = stringResource(R.string.matlog_pref_crash_notifications)
    val notifications by prefs.booleanState(notificationsKey, true)
    val monitorKey = stringResource(R.string.matlog_pref_crash_monitor_service)
    val monitor by prefs.booleanState(monitorKey, false)
    val exceptionsKey = stringResource(R.string.matlog_pref_exception_monitor)
    val exceptions by prefs.booleanState(exceptionsKey, false)

    PreferenceGroup(title = stringResource(R.string.matlog_pref_cat_crashes)) {
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_crash_detection_title),
                summary = stringResource(R.string.matlog_pref_crash_detection_summary),
                icon = R.drawable.ic_bug_report,
                checked = detection,
                onCheckedChange = { prefs.edit { putBoolean(detectionKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_crash_notifications_title),
                summary = stringResource(R.string.matlog_pref_crash_notifications_summary),
                icon = R.drawable.ic_notifications_active,
                checked = notifications,
                enabled = detection,
                onCheckedChange = { prefs.edit { putBoolean(notificationsKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_crash_monitor_service_title),
                summary = stringResource(R.string.matlog_pref_crash_monitor_service_summary),
                icon = R.drawable.ic_monitor_heart,
                checked = monitor,
                enabled = detection,
                onCheckedChange = { prefs.edit { putBoolean(monitorKey, it) } },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.matlog_pref_exception_monitor_title),
                summary = stringResource(R.string.matlog_pref_exception_monitor_summary),
                icon = R.drawable.ic_warning,
                checked = exceptions,
                enabled = detection && monitor,
                onCheckedChange = { prefs.edit { putBoolean(exceptionsKey, it) } },
            )
        }
    }
}

@Composable
private fun AdvancedSettings(prefs: SharedPreferences) {
    val context = LocalContext.current
    val periodKey = stringResource(R.string.pref_log_line_period)
    val periodDefault = stringResource(R.string.pref_log_line_period_default)
    val period by prefs.intState(periodKey, periodDefault)
    val periodError = stringResource(R.string.pref_log_line_period_error)
    val limitKey = stringResource(R.string.pref_display_limit)
    val limitDefault = stringResource(R.string.pref_display_limit_default)
    val limit by prefs.intState(limitKey, limitDefault)
    val limitError = stringResource(R.string.toast_invalid_display_limit, MIN_DISPLAY_LIMIT, MAX_DISPLAY_LIMIT)
    val scrubber by prefs.booleanState(PREF_SCRUBBER, false)

    PreferenceGroup(title = stringResource(R.string.pref_cat_advanced)) {
        item {
            TextInputPreference(
                title = stringResource(R.string.pref_log_line_period_title),
                summary = stringResource(R.string.pref_log_line_period_summary, period, periodDefault),
                icon = R.drawable.ic_timer,
                value = period.toString(),
                keyboardType = KeyboardType.Number,
                validate = { if (it.toIntOrNull() in MIN_LOG_LINE_PERIOD..MAX_LOG_LINE_PERIOD) null else periodError },
                onValueChange = { PreferenceHelper.setLogLinePeriodPreference(context, it.toInt()) },
            )
        }
        item {
            TextInputPreference(
                title = stringResource(R.string.pref_display_limit_title),
                summary = stringResource(R.string.pref_display_limit_summary, limit, limitDefault),
                icon = R.drawable.ic_numbers,
                value = limit.toString(),
                keyboardType = KeyboardType.Number,
                validate = { if (it.toIntOrNull() in MIN_DISPLAY_LIMIT..MAX_DISPLAY_LIMIT) null else limitError },
                onValueChange = {
                    PreferenceHelper.setDisplayLimitPreference(context, it.toInt())
                    Toast.makeText(context, R.string.toast_pref_changed_restart_required, Toast.LENGTH_LONG).show()
                },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.scrubber),
                summary = stringResource(R.string.scrubber_summary),
                icon = R.drawable.ic_privacy_tip,
                checked = scrubber,
                onCheckedChange = { prefs.edit { putBoolean(PREF_SCRUBBER, it) } },
            )
        }
    }
}

@Composable
private fun options(labels: Int, values: Int): List<Pair<String, String>> =
    stringArrayResource(values).zip(stringArrayResource(labels))

private fun labelsFor(options: List<Pair<String, String>>, values: Set<String>): String =
    options.filter { it.first in values }.joinToString(", ") { it.second }

@Composable
private fun <T> SharedPreferences.observe(key: String, read: SharedPreferences.() -> T): State<T> {
    val state = remember(key) { mutableStateOf(read()) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == key) state.value = prefs.read()
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable
private fun SharedPreferences.booleanState(key: String, default: Boolean) =
    observe(key) { getBoolean(key, default) }

@Composable
private fun SharedPreferences.stringState(key: String, default: String) =
    observe(key) { getString(key, null) ?: default }

@Composable
private fun SharedPreferences.stringSetState(key: String, default: Set<String>) =
    observe(key) { getStringSet(key, null)?.toSet() ?: default }

@Composable
private fun SharedPreferences.intState(key: String, default: String) =
    observe(key) { getString(key, null)?.toIntOrNull() ?: default.toInt() }
