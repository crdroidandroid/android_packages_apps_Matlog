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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

class FiltersActivity : ComponentActivity() {

    private lateinit var repository: FilterRepository

    // null shows the list, NEW_FILTER_ID a blank editor, anything else edits that filter
    private val editingId = mutableStateOf<Long?>(null)

    // set when opened from a log line; the editor then closes the screen when done
    private var template: UserFilter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FilterRepository.get(this)
        template = templateFrom(intent)
        editingId.value = if (savedInstanceState != null) {
            savedInstanceState.getLong(STATE_EDITING_ID, NO_EDITOR).takeIf { it != NO_EDITOR }
        } else {
            template?.let { NEW_FILTER_ID }
        }
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
                        onAddApps = { packages ->
                            runIo { repository.addAll(packages.map { UserFilter(packageName = it) }) }
                        },
                        onImport = ::importFilters,
                        onExport = ::exportFilters,
                        onBack = { finish() },
                    )
                } else {
                    val fromLine = template != null && id == NEW_FILTER_ID
                    BackHandler { closeEditor(fromLine) }
                    FilterEditorScreen(
                        repository = repository,
                        filterId = id.takeIf { it != NEW_FILTER_ID },
                        template = if (fromLine) template else null,
                        onSave = { filter ->
                            if (fromLine) {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) { repository.save(filter) }
                                    finish()
                                }
                            } else {
                                editingId.value = null
                                runIo { repository.save(filter) }
                            }
                        },
                        onDelete = { filter ->
                            editingId.value = null
                            runIo { repository.delete(filter.id) }
                        },
                        onBack = { closeEditor(fromLine) },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_EDITING_ID, editingId.value ?: NO_EDITOR)
    }

    private fun closeEditor(fromLine: Boolean) {
        if (fromLine) {
            finish()
        } else {
            editingId.value = null
        }
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

        private const val EXTRA_TAG = "tag"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_INCLUDING = "including"

        @JvmStatic
        fun intent(context: Context): Intent = Intent(context, FiltersActivity::class.java)

        // opens a new filter seeded with the line's tag and app
        @JvmStatic
        fun intentForLine(context: Context, tag: String?, packageName: String?, including: Boolean): Intent =
            intent(context)
                .putExtra(EXTRA_TAG, tag?.trim())
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_INCLUDING, including)

        private fun templateFrom(intent: Intent?): UserFilter? {
            if (intent?.hasExtra(EXTRA_INCLUDING) != true) return null
            return UserFilter(
                including = intent.getBooleanExtra(EXTRA_INCLUDING, true),
                tag = intent.getStringExtra(EXTRA_TAG)?.takeIf { it.isNotEmpty() },
                packageName = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() },
            )
        }

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
    onAddApps: (Set<String>) -> Unit,
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
    var pickApps by remember { mutableStateOf(false) }

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
                        MatlogIcon(
                            R.drawable.ic_more_vert,
                            contentDescription = stringResource(R.string.matlog_more_options)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.matlog_filter_by_apps)) },
                            onClick = {
                                menuExpanded = false
                                pickApps = true
                            },
                        )
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
                MatlogIcon(R.drawable.ic_add, contentDescription = stringResource(R.string.matlog_filter_add))
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

    if (pickApps) {
        AppPickerDialog(
            multiSelect = true,
            onDismiss = { pickApps = false },
            onSelected = { packages ->
                pickApps = false
                onAddApps(packages)
            },
        )
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
            MatlogIcon(
                if (filter.including) R.drawable.ic_filter else R.drawable.ic_filter_alt_off,
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
        filter.packageName?.let { add(condition(R.string.matlog_filter_package, it, filter.packageRegex)) }
        filter.tag?.let { add(condition(R.string.matlog_filter_tag, it, filter.tagRegex)) }
        filter.content?.let { add(condition(R.string.matlog_filter_content, it, filter.contentRegex)) }
        if (filter.since != null || filter.until != null) {
            val any = stringResource(R.string.matlog_filter_time_any)
            add(
                stringResource(
                    R.string.matlog_filter_condition,
                    stringResource(R.string.matlog_filter_time_range),
                    "${filter.since?.let { formatDateTime(it) } ?: any} – ${filter.until?.let { formatDateTime(it) } ?: any}"
                )
            )
        }
    }
    return conditions.joinToString(" · ")
}

@Composable
private fun condition(labelRes: Int, value: String, regex: Boolean): String {
    val label = stringResource(labelRes)
    return stringResource(
        R.string.matlog_filter_condition,
        if (regex) stringResource(R.string.matlog_filter_regex_label, label) else label,
        value,
    )
}

@Composable
private fun EmptyFilters(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MatlogIcon(
                R.drawable.ic_filter,
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
    template: UserFilter?,
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
    val initial = existing ?: template ?: UserFilter()

    var name by remember(initial) { mutableStateOf(initial.name.orEmpty()) }
    var including by remember(initial) { mutableStateOf(initial.including) }
    var levels by remember(initial) { mutableStateOf(initial.levels) }
    var uid by remember(initial) { mutableStateOf(initial.uid.orEmpty()) }
    var pid by remember(initial) { mutableStateOf(initial.pid.orEmpty()) }
    var packageName by remember(initial) { mutableStateOf(initial.packageName.orEmpty()) }
    var tag by remember(initial) { mutableStateOf(initial.tag.orEmpty()) }
    var content by remember(initial) { mutableStateOf(initial.content.orEmpty()) }
    var tagRegex by remember(initial) { mutableStateOf(initial.tagRegex) }
    var contentRegex by remember(initial) { mutableStateOf(initial.contentRegex) }
    var packageRegex by remember(initial) { mutableStateOf(initial.packageRegex) }
    var since by remember(initial) { mutableStateOf(initial.since) }
    var until by remember(initial) { mutableStateOf(initial.until) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickingSince by remember { mutableStateOf<Boolean?>(null) }

    fun buildFilter() = initial.copy(
        name = name.trim().ifEmpty { null },
        including = including,
        levels = levels,
        uid = uid.trim().ifEmpty { null },
        pid = pid.trim().ifEmpty { null },
        packageName = packageName.trim().ifEmpty { null },
        tag = tag.trim().ifEmpty { null },
        content = content.ifEmpty { null },
        tagRegex = tagRegex,
        contentRegex = contentRegex,
        packageRegex = packageRegex,
        since = since,
        until = until,
    )

    fun validationError(filter: UserFilter): Int? = when {
        !filter.hasConditions -> R.string.matlog_filter_no_conditions
        (filter.tagRegex && filter.tag?.let { !UserFilter.isValidRegex(it) } == true) ||
            (filter.contentRegex && filter.content?.let { !UserFilter.isValidRegex(it) } == true) ||
            (filter.packageRegex && filter.packageName?.let { !UserFilter.isValidRegex(it) } == true) ->
            R.string.matlog_filter_invalid_regex
        filter.since != null && filter.until != null && filter.since > filter.until ->
            R.string.matlog_filter_bad_range
        else -> null
    }

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
                            MatlogIcon(R.drawable.ic_delete, contentDescription = stringResource(R.string.matlog_filter_delete))
                        }
                    }
                    IconButton(onClick = {
                        val filter = buildFilter()
                        val error = validationError(filter)
                        if (error == null) {
                            onSave(filter)
                        } else {
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        MatlogIcon(R.drawable.ic_check, contentDescription = stringResource(R.string.matlog_filter_save))
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

            FilterTextField(
                tag, { tag = it }, R.string.matlog_filter_tag,
                regex = tagRegex, onRegexChange = { tagRegex = it },
            )
            FilterTextField(
                content, { content = it }, R.string.matlog_filter_content,
                regex = contentRegex, onRegexChange = { contentRegex = it },
            )
            FilterTextField(
                packageName, { packageName = it }, R.string.matlog_filter_package,
                regex = packageRegex, onRegexChange = { packageRegex = it },
                trailingExtra = {
                    IconButton(onClick = { showAppPicker = true }) {
                        MatlogIcon(R.drawable.ic_apps, contentDescription = stringResource(R.string.matlog_filter_pick_app))
                    }
                },
            )
            FilterTextField(
                uid, { uid = it }, R.string.matlog_filter_uid,
                supportingRes = R.string.matlog_filter_uid_hint,
            )
            FilterTextField(
                pid, { pid = it }, R.string.matlog_filter_pid,
                keyboardType = KeyboardType.Number,
            )

            Text(
                stringResource(R.string.matlog_filter_time_range),
                style = MaterialTheme.typography.titleSmall,
            )
            TimeRangeRow(R.string.matlog_filter_since, since, { pickingSince = true }, { since = null })
            TimeRangeRow(R.string.matlog_filter_until, until, { pickingSince = false }, { until = null })
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            multiSelect = false,
            onDismiss = { showAppPicker = false },
            onSelected = { packages ->
                showAppPicker = false
                packages.firstOrNull()?.let {
                    packageName = it
                    packageRegex = false
                }
            },
        )
    }

    pickingSince?.let { forSince ->
        DateTimePickerDialog(
            initial = if (forSince) since else until,
            endOfMinute = !forSince,
            onDismiss = { pickingSince = null },
            onPicked = { time ->
                if (forSince) since = time else until = time
                pickingSince = null
            },
        )
    }
}

@Composable
private fun TimeRangeRow(labelRes: Int, value: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPick),
        headlineContent = { Text(stringResource(labelRes)) },
        supportingContent = {
            Text(value?.let { formatDateTime(it) } ?: stringResource(R.string.matlog_filter_time_any))
        },
        trailingContent = if (value != null) {
            {
                IconButton(onClick = onClear) {
                    MatlogIcon(R.drawable.ic_close, contentDescription = stringResource(R.string.matlog_filter_clear))
                }
            }
        } else {
            null
        },
    )
}

private fun formatDateTime(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(millis))

// picks a date, then a time; the end of a range covers the whole chosen minute
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initial: Long?,
    endOfMinute: Boolean,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(initial) { Instant.ofEpochMilli(initial ?: System.currentTimeMillis()).atZone(zone) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val time = if (endOfMinute) {
                        LocalTime.of(timeState.hour, timeState.minute, 59, 999_000_000)
                    } else {
                        LocalTime.of(timeState.hour, timeState.minute)
                    }
                    onPicked(LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli())
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { TimeInput(state = timeState) },
        )
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    supportingRes: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    regex: Boolean? = null,
    onRegexChange: (Boolean) -> Unit = {},
    trailingExtra: (@Composable () -> Unit)? = null,
) {
    val invalidRegex = regex == true && value.isNotBlank() && !UserFilter.isValidRegex(value)
    val supporting = if (invalidRegex) R.string.matlog_filter_invalid_regex else supportingRes
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        supportingText = if (supporting != null) {
            { Text(stringResource(supporting)) }
        } else {
            null
        },
        isError = invalidRegex,
        trailingIcon = if (regex != null || trailingExtra != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingExtra?.invoke()
                    if (regex != null) RegexToggle(regex, onRegexChange)
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun RegexToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val description = stringResource(R.string.matlog_filter_regex)
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            ".*",
            fontWeight = FontWeight.Bold,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
