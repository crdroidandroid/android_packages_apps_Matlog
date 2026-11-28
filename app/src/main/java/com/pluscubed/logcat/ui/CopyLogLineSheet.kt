/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluscubed.logcat.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.LogTextActions
import com.pluscubed.logcat.ui.compose.ComposeDialogs
import com.pluscubed.logcat.ui.compose.logText
import com.pluscubed.logcat.ui.compose.segmented
import kotlinx.coroutines.launch
import org.omnirom.logcat.R

// lets the user copy the whole line or a single field of it
object CopyLogLineSheet {

    private const val DATE_LENGTH = 5

    @JvmStatic
    fun show(activity: Activity, line: LogLine) {
        val fields = fields(line)
        ComposeDialogs.show(activity) { dismiss -> CopySheet(fields, dismiss) { LogTextActions.copy(activity, it) } }
    }

    private fun fields(line: LogLine): List<Pair<Int, String>> {
        val fields = ArrayList<Pair<Int, String>>()
        line.originalLine?.let { fields.add(R.string.matlog_copy_field_line to it) }
        if (line.logLevel == -1) return fields

        line.logOutput?.let { fields.add(R.string.matlog_copy_field_message to it) }
        line.tag?.trim()?.takeIf { it.isNotEmpty() }?.let { fields.add(R.string.matlog_column_tag to it) }
        line.packageName?.let { fields.add(R.string.matlog_column_package to it) }
        line.timestamp?.let { timestamp ->
            if (timestamp.length > DATE_LENGTH + 1) {
                fields.add(R.string.matlog_column_date to timestamp.substring(0, DATE_LENGTH))
                fields.add(R.string.matlog_column_time to timestamp.substring(DATE_LENGTH + 1))
            }
        }
        line.uptime?.let { fields.add(R.string.matlog_column_time to it) }
        line.uid?.let { fields.add(R.string.matlog_column_uid to it) }
        if (line.processId != -1) fields.add(R.string.matlog_column_pid to line.processId.toString())
        return fields
    }
}

@Composable
private fun CopySheet(fields: List<Pair<Int, String>>, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.copy_to_clipboard),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            fields.forEachIndexed { index, (label, value) ->
                CopyField(stringResource(label), value, MaterialTheme.shapes.segmented(index == 0, index == fields.lastIndex)) {
                    onCopy(value)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun CopyField(label: String, value: String, shape: Shape, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = value,
            style = MaterialTheme.typography.logText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
