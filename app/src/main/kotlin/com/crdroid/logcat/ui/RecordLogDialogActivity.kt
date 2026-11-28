/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.crdroid.logcat.LogcatRecordingService
import com.crdroid.logcat.R
import com.crdroid.logcat.RecordingWidgetProvider
import com.crdroid.logcat.helper.DialogHelper
import com.crdroid.logcat.helper.PreferenceHelper
import com.crdroid.logcat.helper.ServiceHelper
import com.crdroid.logcat.helper.WidgetHelper
import com.crdroid.logcat.ui.compose.ActionRow
import com.crdroid.logcat.ui.compose.DialogButton
import com.crdroid.logcat.ui.compose.DropdownField
import com.crdroid.logcat.ui.compose.FilenameDialog
import com.crdroid.logcat.ui.compose.MatlogDialog
import com.crdroid.logcat.ui.compose.MatlogIcon
import com.crdroid.logcat.ui.compose.MatlogTheme
import com.crdroid.logcat.ui.compose.SuggestionTextField

// a translucent activity that only hosts the record dialog
class RecordLogDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == RecordingWidgetProvider.ACTION_RECORD_OR_STOP) {
            WidgetHelper.updateWidgets(applicationContext)
            if (ServiceHelper.checkIfServiceIsRunning(this, LogcatRecordingService::class.java)) {
                DialogHelper.stopRecordingLog(this)
                finish()
                return
            }
        }
        val suggestions = intent?.getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS).orEmpty().toList()
        setContent {
            MatlogTheme(surface = false) {
                RecordLogDialog(suggestions, onDismiss = ::finish) { filename, query, level ->
                    DialogHelper.startRecording(filename, query, level, { finish() }, this)
                }
            }
        }
    }

    companion object {
        const val EXTRA_QUERY_SUGGESTIONS = "suggestions"
    }
}

@Composable
private fun RecordLogDialog(
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onRecord: (filename: String, query: String, level: String) -> Unit,
) {
    val context = LocalContext.current
    val defaultLevel = remember { PreferenceHelper.getDefaultLogLevelPreference(context).toString() }
    val defaultFilename = remember { DialogHelper.createLogFilename() }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(defaultLevel) }
    var starting by rememberSaveable { mutableStateOf(false) }
    var editingFilter by rememberSaveable { mutableStateOf(false) }
    if (starting) return

    FilenameDialog(
        icon = R.drawable.ic_record,
        title = stringResource(R.string.record_log),
        filename = defaultFilename,
        onDismiss = { if (!starting) onDismiss() },
        onFilename = {
            starting = true
            onRecord(it, query, level)
        },
    ) {
        Spacer(Modifier.height(8.dp))
        ActionRow(stringResource(R.string.text_filter_ellipsis), R.drawable.ic_filter) { editingFilter = true }
    }
    if (editingFilter) {
        RecordingFilterDialog(query, level, suggestions, onDismiss = { editingFilter = false }) { newQuery, newLevel ->
            query = newQuery
            level = newLevel
            editingFilter = false
        }
    }
}

@Composable
private fun RecordingFilterDialog(
    query: String,
    level: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onDone: (query: String, level: String) -> Unit,
) {
    val context = LocalContext.current
    val values = stringArrayResource(R.array.log_levels_values)
    val labels = remember { DialogHelper.logLevelLabels(context).toList() }
    var text by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    var selected by remember { mutableStateOf(values.indexOf(level).coerceAtLeast(0)) }
    val submit = { onDone(text.text, values[selected]) }
    MatlogDialog(
        onDismiss = onDismiss,
        icon = R.drawable.ic_filter,
        title = stringResource(R.string.title_filter),
        confirmButton = { DialogButton(android.R.string.ok, submit) },
    ) {
        SuggestionTextField(
            value = text,
            onValueChange = { text = it },
            suggestions = suggestions,
            label = stringResource(R.string.text_filter_text),
            onDone = submit,
        )
        Spacer(Modifier.height(12.dp))
        DropdownField(
            label = stringResource(R.string.log_level),
            options = labels,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
