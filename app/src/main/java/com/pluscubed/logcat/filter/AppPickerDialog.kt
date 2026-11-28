/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.filter

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogLoadingIndicator
import com.pluscubed.logcat.ui.compose.MatlogTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

private class InstalledApp(val packageName: String, val label: String, val isSystem: Boolean)

// single selection returns as soon as an app is tapped
@Composable
fun AppPickerDialog(
    multiSelect: Boolean,
    onDismiss: () -> Unit,
    onSelected: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.matlog_filter_pick_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    leadingIcon = { MatlogIcon(R.drawable.ic_search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val list = apps
                    if (list == null) {
                        MatlogLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val shown = remember(list, query) {
                            list.filter {
                                query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                                    it.packageName.contains(query, ignoreCase = true)
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(shown, key = { it.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    multiSelect = multiSelect,
                                    checked = app.packageName in selected,
                                    onClick = {
                                        if (multiSelect) {
                                            selected = if (app.packageName in selected) {
                                                selected - app.packageName
                                            } else {
                                                selected + app.packageName
                                            }
                                        } else {
                                            onSelected(setOf(app.packageName))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MatlogTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    if (multiSelect) {
                        MatlogTextButton(
                            onClick = { onSelected(selected) },
                            enabled = selected.isNotEmpty(),
                        ) { Text(stringResource(android.R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, multiSelect: Boolean, checked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            val bitmap = icon
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                MatlogIcon(R.drawable.ic_android, contentDescription = null, modifier = Modifier.size(40.dp).padding(8.dp))
            }
        },
        overlineContent = if (app.isSystem) {
            { Text(stringResource(R.string.matlog_filter_system_app)) }
        } else {
            null
        },
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (multiSelect) {
            { Checkbox(checked = checked, onCheckedChange = { onClick() }) }
        } else {
            null
        },
    )
}

private fun loadApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .map { info ->
            InstalledApp(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private const val ICON_SIZE_PX = 96
