/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluscubed.logcat.ui.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omnirom.logcat.R

private val OuterCorner = 20.dp
private val InnerCorner = 4.dp
private val DisabledAlpha = 0.38f

enum class PreferencePosition { Single, Top, Middle, Bottom }

private val LocalPreferencePosition = compositionLocalOf { PreferencePosition.Single }

private fun preferenceShape(position: PreferencePosition): Shape = when (position) {
    PreferencePosition.Single -> RoundedCornerShape(OuterCorner)
    PreferencePosition.Top -> RoundedCornerShape(OuterCorner, OuterCorner, InnerCorner, InnerCorner)
    PreferencePosition.Middle -> RoundedCornerShape(InnerCorner)
    PreferencePosition.Bottom -> RoundedCornerShape(InnerCorner, InnerCorner, OuterCorner, OuterCorner)
}

class PreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val container = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = container,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                        shape = IconButtonDefaults.smallRoundShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.matlog_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = container,
                    scrolledContainerColor = container,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun PreferenceGroup(title: String? = null, content: PreferenceGroupScope.() -> Unit) {
    val items = PreferenceGroupScope().apply(content).items
    Column {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                val position = when {
                    items.size == 1 -> PreferencePosition.Single
                    index == 0 -> PreferencePosition.Top
                    index == items.lastIndex -> PreferencePosition.Bottom
                    else -> PreferencePosition.Middle
                }
                CompositionLocalProvider(LocalPreferencePosition provides position, content = item)
            }
        }
    }
}

@Composable
fun BasePreference(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    widget: (@Composable () -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else DisabledAlpha
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(preferenceShape(LocalPreferencePosition.current))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .alpha(contentAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (widget != null) {
            Spacer(Modifier.width(16.dp))
            widget()
        }
    }
}

@Composable
fun ClickablePreference(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    BasePreference(
        title = title,
        icon = icon,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
fun SwitchPreference(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasePreference(
        title = title,
        icon = icon,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                interactionSource = interactionSource,
                thumbContent = {
                    Crossfade(checked, animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) {
                        Icon(
                            imageVector = if (it) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
            )
        },
    )
}

@Composable
fun ListPreference(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    ClickablePreference(
        title = title,
        icon = icon,
        summary = summary ?: options.firstOrNull { it.first == value }?.second,
        onClick = { showDialog = true },
    )
    if (!showDialog) return
    AlertDialog(
        onDismissRequest = { showDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    DialogOptionRow(
                        label = label,
                        modifier = Modifier.selectable(
                            selected = key == value,
                            role = Role.RadioButton,
                            onClick = {
                                showDialog = false
                                if (key != value) onValueChange(key)
                            },
                        ),
                    ) {
                        RadioButton(selected = key == value, onClick = null)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
fun MultiSelectListPreference(
    title: String,
    icon: ImageVector,
    options: List<Pair<String, String>>,
    values: Set<String>,
    onValuesChange: (Set<String>) -> Unit,
    summary: String? = null,
    message: String? = null,
    allowEmpty: Boolean = true,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    ClickablePreference(title = title, icon = icon, summary = summary, onClick = { showDialog = true })
    if (!showDialog) return
    var selected by remember { mutableStateOf(values) }
    AlertDialog(
        onDismissRequest = { showDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                options.forEach { (key, label) ->
                    val checked = key in selected
                    DialogOptionRow(
                        label = label,
                        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) {
                            selected = if (it) selected + key else selected - key
                        },
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allowEmpty || selected.isNotEmpty(),
                onClick = {
                    showDialog = false
                    if (selected != values) onValuesChange(selected)
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

// validate returns an error message, or null when the input is fine
@Composable
fun TextInputPreference(
    title: String,
    icon: ImageVector,
    value: String,
    summary: String?,
    validate: (String) -> String?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    ClickablePreference(title = title, icon = icon, summary = summary, onClick = { showDialog = true })
    if (!showDialog) return
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val submit = {
        val input = text.text.trim()
        error = validate(input)
        if (error == null) {
            showDialog = false
            if (input != value) onValueChange(input)
        }
    }
    val errorText = error
    AlertDialog(
        onDismissRequest = { showDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { message -> @Composable { Text(message) } },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = submit) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
internal fun DialogOptionRow(label: String, modifier: Modifier, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
