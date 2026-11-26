/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.filter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

class FiltersActivity : ComponentActivity() {

    private lateinit var repository: FilterRepository

    // null shows the list, NEW_FILTER_ID a blank editor, anything else edits that filter
    private val editingId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FilterRepository.get(this)
        editingId.value = savedInstanceState?.getLong(STATE_EDITING_ID, NO_EDITOR)
            ?.takeIf { it != NO_EDITOR }
        setContent {
            MatlogTheme {
                val id = editingId.value
                if (id == null) {
                    FilterListScreen(
                        repository = repository,
                        onAdd = { editingId.value = NEW_FILTER_ID },
                        onEdit = { editingId.value = it.id },
                        onToggle = { filter, enabled -> runIo { repository.setEnabled(filter.id, enabled) } },
                        onDeleteAll = { runIo { repository.deleteAll() } },
                        onImport = ::importFilters,
                        onExport = ::exportFilters,
                        onBack = { finish() },
                    )
                } else {
                    BackHandler { editingId.value = null }
                    FilterEditorScreen(
                        repository = repository,
                        filterId = id.takeIf { it != NEW_FILTER_ID },
                        onSave = { filter ->
                            editingId.value = null
                            runIo { repository.save(filter) }
                        },
                        onDelete = { filter ->
                            editingId.value = null
                            runIo { repository.delete(filter.id) }
                        },
                        onBack = { editingId.value = null },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_EDITING_ID, editingId.value ?: NO_EDITOR)
    }

    private fun runIo(block: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { block() }
    }

    private fun importFilters(uri: Uri) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    val filters = FilterJson.decode(text)
                    repository.addAll(filters)
                    filters.size
                }.getOrNull()
            }
            val message = if (count == null) {
                getString(R.string.matlog_filters_import_failed)
            } else {
                resources.getQuantityString(R.plurals.matlog_filters_imported, count, count)
            }
            Toast.makeText(this@FiltersActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportFilters(uri: Uri) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val text = FilterJson.encode(repository.getAll())
                    contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
                }.isSuccess
            }
            Toast.makeText(
                this@FiltersActivity,
                if (success) R.string.matlog_filters_exported else R.string.matlog_filters_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val STATE_EDITING_ID = "editing_id"
        private const val NO_EDITOR = -1L
        private const val NEW_FILTER_ID = 0L
        private const val EXPORT_FILENAME = "matlog-filters.json"

        @JvmStatic
        fun intent(context: Context): Intent = Intent(context, FiltersActivity::class.java)

        fun exportFilename() = EXPORT_FILENAME
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterListScreen(
    repository: FilterRepository,
    onAdd: () -> Unit,
    onEdit: (UserFilter) -> Unit,
    onToggle: (UserFilter, Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onImport: (Uri) -> Unit,
    onExport: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val changes by repository.changes.collectAsState()
    val filters by produceState<List<UserFilter>?>(null, changes) {
        value = withContext(Dispatchers.IO) { repository.getAll() }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matlog_filters)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.matlog_more_options)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.matlog_filters_import)) },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            },
                        )
                        if (!filters.isNullOrEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.matlog_filters_export)) },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch(FiltersActivity.exportFilename())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.matlog_filters_delete_all)) },
                                onClick = {
                                    menuExpanded = false
                                    confirmDeleteAll = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.matlog_filter_add))
            }
        },
    ) { padding ->
        val list = filters
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyFilters(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                items(list, key = { it.id }) { filter ->
                    FilterListItem(
                        filter = filter,
                        onClick = { onEdit(filter) },
                        onToggle = { onToggle(filter, it) },
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.matlog_filters_delete_all)) },
            text = { Text(stringResource(R.string.matlog_filters_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    onDeleteAll()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterListItem(filter: UserFilter, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val summary = filterSummary(filter)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (filter.including) Icons.Filled.FilterAlt else Icons.Filled.FilterAltOff,
                contentDescription = stringResource(
                    if (filter.including) R.string.matlog_filter_include else R.string.matlog_filter_exclude
                ),
            )
        },
        headlineContent = {
            Text(
                filter.name?.takeIf { it.isNotBlank() } ?: summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                if (filter.name.isNullOrBlank()) {
                    stringResource(
                        if (filter.including) R.string.matlog_filter_including else R.string.matlog_filter_excluding
                    )
                } else {
                    summary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(checked = filter.enabled, onCheckedChange = onToggle)
        },
    )
}

@Composable
private fun filterSummary(filter: UserFilter): String {
    val conditions = buildList {
        if (filter.levels.isNotEmpty()) {
            add(
                stringResource(
                    R.string.matlog_filter_condition,
                    stringResource(R.string.matlog_filter_levels),
                    UserFilter.LEVELS.filter { it in filter.levels }.joinToString(" ") { UserFilter.levelLetter(it) }
                )
            )
        }
        filter.uid?.let { add(stringResource(R.string.matlog_filter_condition, stringResource(R.string.matlog_filter_uid), it)) }
        filter.pid?.let { add(stringResource(R.string.matlog_filter_condition, stringResource(R.string.matlog_filter_pid), it)) }
        filter.packageName?.let { add(stringResource(R.string.matlog_filter_condition, stringResource(R.string.matlog_filter_package), it)) }
        filter.tag?.let { add(stringResource(R.string.matlog_filter_condition, stringResource(R.string.matlog_filter_tag), it)) }
        filter.content?.let { add(stringResource(R.string.matlog_filter_condition, stringResource(R.string.matlog_filter_content), it)) }
    }
    return conditions.joinToString(" · ")
}

@Composable
private fun EmptyFilters(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.FilterAlt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.matlog_filters_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.matlog_filters_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterEditorScreen(
    repository: FilterRepository,
    filterId: Long?,
    onSave: (UserFilter) -> Unit,
    onDelete: (UserFilter) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val existing by produceState<UserFilter?>(null, filterId) {
        value = filterId?.let { id -> withContext(Dispatchers.IO) { repository.get(id) } }
    }
    // wait for an existing filter before seeding the fields
    if (filterId != null && existing == null) return
    val initial = existing ?: UserFilter()

    var name by remember(initial) { mutableStateOf(initial.name.orEmpty()) }
    var including by remember(initial) { mutableStateOf(initial.including) }
    var levels by remember(initial) { mutableStateOf(initial.levels) }
    var uid by remember(initial) { mutableStateOf(initial.uid.orEmpty()) }
    var pid by remember(initial) { mutableStateOf(initial.pid.orEmpty()) }
    var packageName by remember(initial) { mutableStateOf(initial.packageName.orEmpty()) }
    var tag by remember(initial) { mutableStateOf(initial.tag.orEmpty()) }
    var content by remember(initial) { mutableStateOf(initial.content.orEmpty()) }

    fun buildFilter() = initial.copy(
        name = name.trim().ifEmpty { null },
        including = including,
        levels = levels,
        uid = uid.trim().ifEmpty { null },
        pid = pid.trim().ifEmpty { null },
        packageName = packageName.trim().ifEmpty { null },
        tag = tag.trim().ifEmpty { null },
        content = content.ifEmpty { null },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.matlog_filter_new else R.string.matlog_filter_edit))
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    existing?.let { filter ->
                        IconButton(onClick = { onDelete(filter) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.matlog_filter_delete))
                        }
                    }
                    IconButton(onClick = {
                        val filter = buildFilter()
                        if (filter.hasConditions) {
                            onSave(filter)
                        } else {
                            Toast.makeText(context, R.string.matlog_filter_no_conditions, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.matlog_filter_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterTextField(name, { name = it }, R.string.matlog_filter_name)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = including,
                    onClick = { including = true },
                    label = { Text(stringResource(R.string.matlog_filter_include)) },
                )
                FilterChip(
                    selected = !including,
                    onClick = { including = false },
                    label = { Text(stringResource(R.string.matlog_filter_exclude)) },
                )
            }

            Text(
                stringResource(R.string.matlog_filter_levels),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserFilter.LEVELS.forEach { level ->
                    FilterChip(
                        selected = level in levels,
                        onClick = { levels = if (level in levels) levels - level else levels + level },
                        label = { Text(UserFilter.levelLetter(level)) },
                    )
                }
            }

            FilterTextField(tag, { tag = it }, R.string.matlog_filter_tag)
            FilterTextField(content, { content = it }, R.string.matlog_filter_content)
            FilterTextField(packageName, { packageName = it }, R.string.matlog_filter_package)
            FilterTextField(
                uid, { uid = it }, R.string.matlog_filter_uid,
                supportingRes = R.string.matlog_filter_uid_hint,
            )
            FilterTextField(
                pid, { pid = it }, R.string.matlog_filter_pid,
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    supportingRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        supportingText = if (supportingRes != null) {
            { Text(stringResource(supportingRes)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
