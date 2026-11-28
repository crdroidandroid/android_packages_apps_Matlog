/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.crdroid.logcat.R
import com.crdroid.logcat.data.SearchCriteria
import com.crdroid.logcat.db.CatlogDBHelper
import com.crdroid.logcat.db.FilterItem
import com.crdroid.logcat.helper.DialogHelper
import com.crdroid.logcat.helper.PreferenceHelper
import com.crdroid.logcat.ui.compose.ActionRow
import com.crdroid.logcat.ui.compose.ChoiceDialog
import com.crdroid.logcat.ui.compose.ComposeDialogs
import com.crdroid.logcat.ui.compose.DialogButton
import com.crdroid.logcat.ui.compose.DialogMessage
import com.crdroid.logcat.ui.compose.FilenameDialog
import com.crdroid.logcat.ui.compose.MatlogDialog
import com.crdroid.logcat.ui.compose.MatlogIcon
import com.crdroid.logcat.ui.compose.MatlogIconButton
import com.crdroid.logcat.ui.compose.SuggestionTextField
import com.crdroid.logcat.ui.compose.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// the log screen's dialogs
object LogcatDialogs {

    fun showSearchBy(activity: Activity, tag: String, pid: Int, onSearch: (String) -> Unit) {
        ComposeDialogs.show(activity) { dismiss ->
            val search = { query: String ->
                dismiss()
                onSearch(query)
            }
            MatlogDialog(onDismiss = dismiss, icon = R.drawable.ic_filter, title = stringResource(R.string.filter_choice)) {
                ActionRow(
                    label = stringResource(R.string.matlog_search_by_item, stringResource(R.string.filter_choice_tag), tag),
                    icon = R.drawable.ic_label,
                ) { search(SearchCriteria.TAG_KEYWORD + if (' ' in tag) "\"$tag\"" else tag) }
                ActionRow(
                    label = stringResource(R.string.matlog_search_by_item, stringResource(R.string.filter_choice_pid), pid.toString()),
                    icon = R.drawable.ic_numbers,
                ) { search(SearchCriteria.PID_KEYWORD + pid) }
            }
        }
    }

    fun showLogLevel(activity: Activity, selected: Int, onSelect: (Int) -> Unit) {
        val labels = DialogHelper.logLevelLabels(activity).toList()
        ComposeDialogs.show(activity) { dismiss ->
            ChoiceDialog(
                icon = R.drawable.ic_tune,
                title = stringResource(R.string.log_level),
                options = labels,
                selected = selected,
                onDismiss = dismiss,
                onSelect = onSelect,
            )
        }
    }

    // includeDeviceInfo is remembered as the new default
    fun showShareLog(activity: Activity, onShare: (Boolean) -> Unit) {
        showDeviceInfoDialog(activity, zip = false, onConfirm = onShare)
    }

    fun showSaveLogZip(activity: Activity, onSave: () -> Unit) {
        showDeviceInfoDialog(activity, zip = true) { onSave() }
    }

    private fun showDeviceInfoDialog(activity: Activity, zip: Boolean, onConfirm: (Boolean) -> Unit) {
        ComposeDialogs.show(activity) { dismiss ->
            var include by remember { mutableStateOf(PreferenceHelper.getIncludeDeviceInfoPreference(activity)) }
            MatlogDialog(
                onDismiss = dismiss,
                icon = if (zip) R.drawable.ic_save_zip else R.drawable.ic_share_white_24dp,
                title = stringResource(if (zip) R.string.save_log_zip else R.string.share_log),
                confirmButton = {
                    DialogButton(android.R.string.ok, {
                        dismiss()
                        onConfirm(include)
                    })
                },
            ) {
                if (zip) DialogMessage(stringResource(R.string.matlog_save_zip_summary))
                SwitchRow(stringResource(R.string.text_include_device_info), include) {
                    include = it
                    PreferenceHelper.setIncludeDeviceInfoPreference(activity, it)
                }
            }
        }
    }

    fun showSaveLog(activity: Activity, filename: String, onSave: (String) -> Unit) {
        ComposeDialogs.show(activity) { dismiss ->
            FilenameDialog(
                icon = R.drawable.ic_save,
                title = stringResource(R.string.save_log),
                filename = filename,
                onDismiss = dismiss,
                onFilename = onSave,
            )
        }
    }

    // onAdded hears about every newly saved search, onSelect about the one picked
    fun showSavedSearches(
        activity: Activity,
        suggestions: List<String>,
        onSelect: (String) -> Unit,
        onAdded: (String) -> Unit,
    ) {
        ComposeDialogs.show(activity) { dismiss ->
            SavedSearchesDialog(suggestions, dismiss, onSelect, onAdded)
        }
    }
}

@Composable
private fun SavedSearchesDialog(
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdded: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filters by remember { mutableStateOf(emptyList<FilterItem>()) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        filters = withContext(Dispatchers.IO) { context.filterDb { findFilterItems() }.sorted() }
    }
    MatlogDialog(
        onDismiss = onDismiss,
        icon = R.drawable.ic_saved_search,
        title = stringResource(R.string.matlog_saved_searches),
    ) {
        DialogMessage(stringResource(R.string.message_filters))
        filters.forEach { filter ->
            SavedSearchRow(
                text = filter.text,
                onClick = {
                    onDismiss()
                    onSelect(filter.text)
                },
                onDelete = {
                    filters = filters - filter
                    scope.launch(Dispatchers.IO) { context.filterDb { deleteFilter(filter.id) } }
                },
            )
        }
        ActionRow(stringResource(R.string.add_filter_ellipsis), R.drawable.ic_add) { adding = true }
    }
    if (adding) {
        AddSearchDialog(suggestions, onDismiss = { adding = false }) { text ->
            adding = false
            scope.launch {
                val added = withContext(Dispatchers.IO) { context.filterDb { addFilter(text) } }
                // null means it was already saved
                if (added != null) {
                    filters = (filters + added).sorted()
                    onAdded(text)
                }
            }
        }
    }
}

@Composable
private fun SavedSearchRow(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MatlogIcon(R.drawable.ic_saved_search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MatlogIconButton(onClick = onDelete) {
            MatlogIcon(R.drawable.ic_delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

@Composable
private fun AddSearchDialog(suggestions: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val submit = {
        val trimmed = text.text.trim()
        if (trimmed.isEmpty()) onDismiss() else onAdd(trimmed)
    }
    MatlogDialog(
        onDismiss = onDismiss,
        icon = R.drawable.ic_add,
        title = stringResource(R.string.add_filter),
        confirmButton = { DialogButton(android.R.string.ok, submit) },
    ) {
        SuggestionTextField(
            value = text,
            onValueChange = { text = it },
            suggestions = suggestions,
            label = stringResource(R.string.text_filter_text),
            modifier = Modifier.focusRequester(focusRequester),
            onDone = submit,
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun <T> android.content.Context.filterDb(block: CatlogDBHelper.() -> T): T {
    val db = CatlogDBHelper(this)
    try {
        return db.block()
    } finally {
        db.close()
    }
}
