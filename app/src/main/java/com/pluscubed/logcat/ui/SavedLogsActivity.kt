/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.pluscubed.logcat.data.SendLogDetails
import com.pluscubed.logcat.helper.DialogHelper
import com.pluscubed.logcat.helper.LogExporter
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogIconButton
import com.pluscubed.logcat.ui.compose.MatlogLoadingIndicator
import com.pluscubed.logcat.ui.compose.MatlogTextButton
import com.pluscubed.logcat.ui.compose.MatlogTheme
import java.io.File
import java.io.FileFilter
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

internal class SavedLogFile(val file: File, val lines: Int) {
    val name: String = file.name
    val modified: Long = file.lastModified()
    val size: Long = file.length()
}

internal enum class SortBy(val labelRes: Int) {
    DATE(R.string.matlog_saved_logs_sort_date),
    NAME(R.string.matlog_saved_logs_sort_name),
    SIZE(R.string.matlog_saved_logs_sort_size),
    LINES(R.string.matlog_saved_logs_sort_lines),
}

class SavedLogsActivity : ComponentActivity() {

    // bumped after every change on disk to reload the list
    private val version = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatlogTheme {
                SavedLogsScreen(
                    version = version.intValue,
                    onOpen = ::openLog,
                    onRename = ::renameLog,
                    onShare = ::shareLog,
                    onExport = { file, uri ->
                        LogExporter.export(this, uri) { out -> file.inputStream().use { it.copyTo(out) } }
                    },
                    onDelete = ::deleteLogs,
                    onBack = { finish() },
                )
            }
        }
    }

    private fun openLog(file: File) {
        startActivity(
            Intent(this, LogcatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_FILENAME, file.name)
        )
        finish()
    }

    private fun renameLog(file: File, newName: String) {
        lifecycleScope.launch {
            val renamed = withContext(Dispatchers.IO) {
                val target = File(file.parentFile, newName)
                !target.exists() && file.renameTo(target)
            }
            if (!renamed) {
                Toast.makeText(this@SavedLogsActivity, R.string.matlog_saved_logs_rename_failed, Toast.LENGTH_SHORT).show()
            }
            version.intValue++
        }
    }

    private fun shareLog(file: File) {
        LogcatActivity.startChooser(this, file.name, "", SendLogDetails.AttachmentType.Text, file)
    }

    private fun deleteLogs(files: Collection<File>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { files.forEach { it.delete() } }
            version.intValue++
        }
    }

    companion object {
        const val EXTRA_FILENAME = "filename"

        private const val PREF_SORT_BY = "matlog_saved_logs_sort_by"
        private const val PREF_SORT_ASCENDING = "matlog_saved_logs_sort_ascending"

        @JvmStatic
        fun intent(context: Context): Intent = Intent(context, SavedLogsActivity::class.java)

        private fun loadLogs(): List<SavedLogFile> {
            val files = SaveLogHelper.getSavedLogsDirectory().listFiles(FileFilter { file ->
                file.isFile && !file.name.endsWith(".zip")
            }) ?: return emptyList()
            return files.map { SavedLogFile(it, countLines(it)) }
        }

        private fun countLines(file: File): Int = try {
            var lines = 0
            var last = '\n'.code
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    for (i in 0 until read) {
                        if (buffer[i] == '\n'.code.toByte()) lines++
                    }
                    if (read > 0) last = buffer[read - 1].toInt()
                }
            }
            if (last != '\n'.code) lines + 1 else lines
        } catch (e: Exception) {
            0
        }

        private fun comparator(sortBy: SortBy, ascending: Boolean): Comparator<SavedLogFile> {
            val base: Comparator<SavedLogFile> = when (sortBy) {
                SortBy.DATE -> compareBy { it.modified }
                SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortBy.SIZE -> compareBy { it.size }
                SortBy.LINES -> compareBy { it.lines }
            }
            return if (ascending) base else base.reversed()
        }

        internal fun readSort(context: Context): Pair<SortBy, Boolean> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val sortBy = SortBy.values().firstOrNull { it.name == prefs.getString(PREF_SORT_BY, null) } ?: SortBy.DATE
            return sortBy to prefs.getBoolean(PREF_SORT_ASCENDING, false)
        }

        internal fun writeSort(context: Context, sortBy: SortBy, ascending: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_SORT_BY, sortBy.name)
                .putBoolean(PREF_SORT_ASCENDING, ascending)
                .apply()
        }

        internal fun sorted(logs: List<SavedLogFile>, sortBy: SortBy, ascending: Boolean) =
            logs.sortedWith(comparator(sortBy, ascending))

        internal suspend fun load(): List<SavedLogFile> = withContext(Dispatchers.IO) { loadLogs() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SavedLogsScreen(
    version: Int,
    onOpen: (File) -> Unit,
    onRename: (File, String) -> Unit,
    onShare: (File) -> Unit,
    onExport: (File, android.net.Uri) -> Unit,
    onDelete: (Collection<File>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val logs by produceState<List<SavedLogFile>?>(null, version) { value = SavedLogsActivity.load() }
    var sort by remember { mutableStateOf(SavedLogsActivity.readSort(context)) }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showSort by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedLogFile?>(null) }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    var exportingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)
    ) { uri ->
        val path = exportingPath
        exportingPath = null
        if (uri != null && path != null) onExport(File(path), uri)
    }

    val list = logs?.let { SavedLogsActivity.sorted(it, sort.first, sort.second) }
    // drop selections of files that are gone
    val selection = list?.let { current -> selected.filterTo(HashSet()) { name -> current.any { it.name == name } } }
        ?: emptySet()
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) {
                            pluralStringResource(R.plurals.matlog_selected_count, selection.size, selection.size)
                        } else {
                            stringResource(R.string.matlog_saved_logs)
                        }
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        MatlogIconButton(onClick = { selected = emptySet() }) {
                            MatlogIcon(R.drawable.ic_close, contentDescription = stringResource(R.string.matlog_back))
                        }
                    } else {
                        BackButton(onBack)
                    }
                },
                actions = {
                    if (selecting) {
                        MatlogIconButton(onClick = {
                            val all = list.orEmpty().map { it.name }.toSet()
                            selected = if (selection == all) emptySet() else all
                        }) {
                            MatlogIcon(R.drawable.ic_select_all, contentDescription = stringResource(R.string.matlog_select_all))
                        }
                        MatlogIconButton(onClick = {
                            deleting = list.orEmpty().filter { it.name in selection }.map { it.file }
                        }) {
                            MatlogIcon(R.drawable.ic_delete, contentDescription = stringResource(R.string.matlog_saved_logs_delete))
                        }
                    } else if (!list.isNullOrEmpty()) {
                        MatlogIconButton(onClick = { showSort = true }) {
                            MatlogIcon(R.drawable.ic_sort, contentDescription = stringResource(R.string.matlog_saved_logs_sort))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                list == null -> MatlogLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    stringResource(R.string.no_saved_logs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.name }) { log ->
                        val isSelected = log.name in selection
                        SavedLogItem(
                            log = log,
                            selected = isSelected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    selected = if (isSelected) selection - log.name else selection + log.name
                                } else {
                                    onOpen(log.file)
                                }
                            },
                            onLongClick = {
                                selected = if (isSelected) selection - log.name else selection + log.name
                            },
                            onOpen = { onOpen(log.file) },
                            onRename = { renaming = log },
                            onShare = { onShare(log.file) },
                            onExport = {
                                exportingPath = log.file.absolutePath
                                exportLauncher.launch(log.name)
                            },
                            onDelete = { deleting = listOf(log.file) },
                        )
                    }
                }
            }
        }
    }

    if (showSort) {
        SortDialog(
            initial = sort,
            onDismiss = { showSort = false },
            onDone = { sortBy, ascending ->
                showSort = false
                sort = sortBy to ascending
                SavedLogsActivity.writeSort(context, sortBy, ascending)
            },
        )
    }

    renaming?.let { log ->
        RenameDialog(
            name = log.name,
            existing = list.orEmpty().map { it.name }.toSet() - log.name,
            onDismiss = { renaming = null },
            onRename = { newName ->
                renaming = null
                if (newName != log.name) onRename(log.file, newName)
            },
        )
    }

    deleting?.let { files ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.matlog_saved_logs_delete)) },
            text = {
                Text(
                    if (files.size == 1) {
                        stringResource(R.string.matlog_saved_logs_delete_one, files.first().name)
                    } else {
                        pluralStringResource(R.plurals.matlog_saved_logs_delete_many, files.size, files.size)
                    }
                )
            },
            confirmButton = {
                MatlogTextButton(onClick = {
                    deleting = null
                    selected = emptySet()
                    onDelete(files)
                }) { Text(stringResource(R.string.matlog_saved_logs_delete)) }
            },
            dismissButton = {
                MatlogTextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedLogItem(
    log: SavedLogFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val details = listOf(
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(log.modified)),
        Formatter.formatShortFileSize(context, log.size),
        pluralStringResource(R.plurals.matlog_saved_logs_lines, log.lines, log.lines),
    ).joinToString(" · ")

    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            MatlogIcon(
                if (selected) R.drawable.ic_check_circle else R.drawable.ic_description,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(log.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details) },
        trailingContent = if (selecting) {
            null
        } else {
            {
                Box {
                    MatlogIconButton(onClick = { menuExpanded = true }) {
                        MatlogIcon(R.drawable.ic_more_vert, contentDescription = stringResource(R.string.matlog_more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        listOf(
                            R.string.matlog_saved_logs_open to onOpen,
                            R.string.matlog_saved_logs_rename to onRename,
                            R.string.matlog_share to onShare,
                            R.string.matlog_export to onExport,
                            R.string.matlog_saved_logs_delete to onDelete,
                        ).forEach { (labelRes, action) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    menuExpanded = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SortDialog(
    initial: Pair<SortBy, Boolean>,
    onDismiss: () -> Unit,
    onDone: (SortBy, Boolean) -> Unit,
) {
    var sortBy by remember { mutableStateOf(initial.first) }
    var ascending by remember { mutableStateOf(initial.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.matlog_saved_logs_sort)) },
        text = {
            Column {
                SortBy.values().forEach { entry ->
                    RadioRow(stringResource(entry.labelRes), sortBy == entry) { sortBy = entry }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                RadioRow(stringResource(R.string.matlog_saved_logs_sort_descending), !ascending) { ascending = false }
                RadioRow(stringResource(R.string.matlog_saved_logs_sort_ascending), ascending) { ascending = true }
            }
        },
        confirmButton = {
            MatlogTextButton(onClick = { onDone(sortBy, ascending) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            MatlogTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun RenameDialog(
    name: String,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var value by remember { mutableStateOf(name) }
    val trimmed = value.trim()
    val error = when {
        DialogHelper.isInvalidFilename(trimmed) -> R.string.enter_good_filename
        trimmed in existing -> R.string.matlog_saved_logs_name_exists
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.matlog_saved_logs_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(stringResource(error)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            MatlogTextButton(onClick = { onRename(trimmed) }, enabled = error == null) {
                Text(stringResource(R.string.matlog_saved_logs_rename))
            }
        },
        dismissButton = {
            MatlogTextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
