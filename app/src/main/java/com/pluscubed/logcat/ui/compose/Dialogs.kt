/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.pluscubed.logcat.ui.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pluscubed.logcat.helper.DialogHelper
import org.omnirom.logcat.R

private const val MAX_SUGGESTIONS = 5

// hosts a Compose dialog over a View based activity until it is dismissed
object ComposeDialogs {

    @JvmStatic
    fun show(activity: Activity, content: @Composable (dismiss: () -> Unit) -> Unit) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val host = ComposeView(activity)
        val dismiss: () -> Unit = { root.post { root.removeView(host) } }
        host.setContent { MatlogTheme(surface = false) { content(dismiss) } }
        root.addView(host, ViewGroup.LayoutParams(0, 0))
    }
}

@Composable
fun MatlogDialog(
    onDismiss: () -> Unit,
    icon: Int,
    title: String,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = { DialogButton(android.R.string.cancel, onDismiss) },
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { MatlogIcon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

@Composable
fun DialogButton(text: Int, onClick: () -> Unit, enabled: Boolean = true) {
    MatlogTextButton(onClick = onClick, enabled = enabled) { Text(stringResource(text)) }
}

@Composable
fun DialogMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun ChoiceDialog(
    icon: Int,
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    MatlogDialog(onDismiss = onDismiss, icon = icon, title = title) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                DialogOptionRow(
                    label = label,
                    modifier = Modifier.selectable(selected = index == selected, role = Role.RadioButton) {
                        onDismiss()
                        onSelect(index)
                    },
                ) {
                    RadioButton(selected = index == selected, onClick = null)
                }
            }
        }
    }
}

@Composable
fun ActionRow(label: String, icon: Int, onClick: () -> Unit) {
    DialogOptionRow(label = label, modifier = Modifier.clickable(onClick = onClick)) {
        MatlogIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DialogOptionRow(
        label = label,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Crossfade(checked) { on ->
                    MatlogIcon(
                        id = if (on) R.drawable.ic_check else R.drawable.ic_close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            },
        )
    }
}

// a text field offering the suggestions that start with what was typed
@Composable
fun SuggestionTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.text
    val matches = remember(query, suggestions) {
        if (query.isEmpty()) emptyList() else suggestions
            .filter { it.startsWith(query, ignoreCase = true) && it != query }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .take(MAX_SUGGESTIONS)
    }
    val showMenu = expanded && matches.isNotEmpty()
    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                expanded = false
                onDone()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(TextFieldValue(suggestion, TextRange(suggestion.length)))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options[selected],
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

// stays open with an error until the filename is valid
@Composable
fun FilenameDialog(
    icon: Int,
    title: String,
    filename: String,
    onDismiss: () -> Unit,
    onFilename: (String) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(filename, TextRange(0, (filename.length - ".txt".length).coerceAtLeast(0))))
    }
    var invalid by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val submit: () -> Unit = {
        if (DialogHelper.isInvalidFilename(text.text)) {
            invalid = true
        } else {
            onFilename(text.text)
            onDismiss()
        }
    }
    MatlogDialog(
        onDismiss = onDismiss,
        icon = icon,
        title = title,
        confirmButton = { DialogButton(android.R.string.ok, submit) },
    ) {
        DialogMessage(stringResource(R.string.enter_filename))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                invalid = false
            },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                @Composable { Text(stringResource(R.string.enter_good_filename)) }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        extraContent()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
