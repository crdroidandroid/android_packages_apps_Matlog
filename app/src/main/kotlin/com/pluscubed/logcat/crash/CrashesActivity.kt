/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R
import com.pluscubed.logcat.data.SendLogDetails
import com.pluscubed.logcat.filter.FiltersActivity
import com.pluscubed.logcat.helper.LogExporter
import com.pluscubed.logcat.helper.LogTextActions
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.intents.Intents
import com.pluscubed.logcat.ui.LogcatActivity
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogIconButton
import com.pluscubed.logcat.ui.compose.MatlogTextButton
import com.pluscubed.logcat.ui.compose.MatlogTheme
import com.pluscubed.logcat.ui.compose.logText

class CrashesActivity : ComponentActivity() {

    private val selectedCrashId = mutableStateOf<Long?>(null)
    private val selectedExceptionId = mutableStateOf<Long?>(null)
    private val selectedTab = mutableIntStateOf(TAB_CRASHES)
    private var exportingCrashId: Long? = null
    private var exportingExceptionId: Long? = null

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)) { uri ->
            val crashId = exportingCrashId
            val exceptionId = exportingExceptionId
            exportingCrashId = null
            exportingExceptionId = null
            if (uri == null) return@registerForActivityResult
            if (crashId != null) exportCrash(crashId, uri)
            if (exceptionId != null) exportException(exceptionId, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedCrashId.value = savedInstanceState?.getLong(EXTRA_CRASH_ID, -1L)
            ?.takeIf { it != -1L }
            ?: crashIdFrom(intent)
        selectedExceptionId.value = savedInstanceState?.getLong(STATE_EXCEPTION_ID, -1L)?.takeIf { it != -1L }
        selectedTab.intValue = savedInstanceState?.getInt(STATE_TAB, TAB_CRASHES) ?: TAB_CRASHES
        exportingCrashId = savedInstanceState?.getLong(STATE_EXPORTING_ID, -1L)?.takeIf { it != -1L }
        exportingExceptionId = savedInstanceState?.getLong(STATE_EXPORTING_EXCEPTION_ID, -1L)?.takeIf { it != -1L }
        val repository = CrashRepository.get(this)
        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }
        setContent {
            MatlogTheme {
                val crashId by selectedCrashId
                val exceptionId by selectedExceptionId
                val crash = crashId
                val exception = exceptionId
                when {
                    crash != null -> {
                        BackHandler { selectedCrashId.value = null }
                        CrashDetailsScreen(
                            repository = repository,
                            crashId = crash,
                            onBack = { selectedCrashId.value = null },
                            onShare = { shareCrash(it) },
                            onExport = {
                                exportingCrashId = it.id
                                exportLauncher.launch(LogExporter.textFilename())
                            },
                            onDelete = {
                                selectedCrashId.value = null
                                lifecycleScope.launch(Dispatchers.IO) { repository.dismiss(it.id) }
                            },
                        )
                    }
                    exception != null -> {
                        BackHandler { selectedExceptionId.value = null }
                        ExceptionDetailsScreen(
                            repository = repository,
                            exceptionId = exception,
                            onBack = { selectedExceptionId.value = null },
                            onShare = { shareException(it) },
                            onExport = {
                                exportingExceptionId = it.id
                                exportLauncher.launch(LogExporter.textFilename())
                            },
                            onShowInLog = { showInLog(it) },
                            onExcludeTag = {
                                startActivity(FiltersActivity.intentForLine(this, it.tag, null, false))
                            },
                            onDelete = {
                                selectedExceptionId.value = null
                                lifecycleScope.launch(Dispatchers.IO) { repository.dismissException(it.id) }
                            },
                        )
                    }
                    else -> CrashesHomeScreen(
                        repository = repository,
                        tab = selectedTab.intValue,
                        onTabChange = { selectedTab.intValue = it },
                        onCrashClick = { selectedCrashId.value = it.id },
                        onExceptionClick = { selectedExceptionId.value = it.id },
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        crashIdFrom(intent)?.let {
            selectedExceptionId.value = null
            selectedTab.intValue = TAB_CRASHES
            selectedCrashId.value = it
        }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != ACTION_SHARE_CRASH) return
        val id = crashIdFrom(intent) ?: return
        lifecycleScope.launch {
            val crash = withContext(Dispatchers.IO) { CrashRepository.get(this@CrashesActivity).get(id) }
            crash?.let { shareCrash(it) }
        }
    }

    private fun shareCrash(crash: Crash) {
        CrashNotifier.cancel(this, crash)
        shareText(crash.log, crash.packageName, R.string.matlog_crash_share_subject)
    }

    private fun shareException(exception: ExceptionInfo) {
        shareText(exception.log, exception.packageName, R.string.matlog_exception_share_subject)
    }

    // shared as a file, the same way as a normal log
    private fun shareText(text: String, packageName: String, subjectRes: Int) {
        lifecycleScope.launch {
            val (file, appLabel) = withContext(Dispatchers.IO) {
                SaveLogHelper.cleanTemp(this@CrashesActivity)
                SaveLogHelper.saveTemporaryFile(
                    this@CrashesActivity, SaveLogHelper.createLogFilename(), text, null
                ) to CrashNotifier.appLabel(this@CrashesActivity, packageName)
            }
            if (file == null) {
                Toast.makeText(this@CrashesActivity, R.string.matlog_crash_share_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            LogcatActivity.startChooser(
                this@CrashesActivity,
                getString(subjectRes, appLabel),
                "",
                SendLogDetails.AttachmentType.Text,
                file
            )
        }
    }

    private fun showInLog(exception: ExceptionInfo) {
        startActivity(
            Intent(this, LogcatActivity::class.java)
                .setAction(Intents.ACTION_LAUNCH)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Intents.EXTRA_FILTER, exception.exception)
        )
    }

    private fun exportCrash(id: Long, uri: Uri) {
        lifecycleScope.launch {
            val crash = withContext(Dispatchers.IO) { CrashRepository.get(this@CrashesActivity).get(id) }
            exportText(crash?.log, uri)
        }
    }

    private fun exportException(id: Long, uri: Uri) {
        lifecycleScope.launch {
            val exception = withContext(Dispatchers.IO) { CrashRepository.get(this@CrashesActivity).getException(id) }
            exportText(exception?.log, uri)
        }
    }

    private fun exportText(text: String?, uri: Uri) {
        if (text == null) {
            Toast.makeText(this, R.string.matlog_export_failed, Toast.LENGTH_LONG).show()
            return
        }
        LogExporter.exportText(this, uri, text)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedCrashId.value?.let { outState.putLong(EXTRA_CRASH_ID, it) }
        selectedExceptionId.value?.let { outState.putLong(STATE_EXCEPTION_ID, it) }
        outState.putInt(STATE_TAB, selectedTab.intValue)
        exportingCrashId?.let { outState.putLong(STATE_EXPORTING_ID, it) }
        exportingExceptionId?.let { outState.putLong(STATE_EXPORTING_EXCEPTION_ID, it) }
    }

    private fun crashIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_CRASH_ID, -1L)?.takeIf { it != -1L }

    companion object {
        private const val EXTRA_CRASH_ID = "crash_id"
        private const val STATE_EXCEPTION_ID = "exception_id"
        private const val STATE_TAB = "tab"
        private const val STATE_EXPORTING_ID = "exporting_crash_id"
        private const val STATE_EXPORTING_EXCEPTION_ID = "exporting_exception_id"
        private const val ACTION_SHARE_CRASH = "org.omnirom.logcat.action.SHARE_CRASH"
        internal const val TAB_CRASHES = 0
        internal const val TAB_EXCEPTIONS = 1

        fun shareIntent(context: Context, crashId: Long): Intent =
            intent(context, crashId).setAction(ACTION_SHARE_CRASH)

        fun intent(context: Context, crashId: Long? = null): Intent =
            Intent(context, CrashesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { crashId?.let { putExtra(EXTRA_CRASH_ID, it) } }
    }
}

private enum class ExceptionSort(val labelRes: Int) {
    LAST_SEEN(R.string.matlog_exceptions_sort_last_seen),
    COUNT(R.string.matlog_exceptions_sort_count),
    APP(R.string.matlog_exceptions_sort_app),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashesHomeScreen(
    repository: CrashRepository,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onCrashClick: (Crash) -> Unit,
    onExceptionClick: (ExceptionInfo) -> Unit,
    onBack: () -> Unit,
) {
    val changes by repository.changes.collectAsState()
    val crashes by produceState<List<Crash>?>(null, changes) {
        value = withContext(Dispatchers.IO) { repository.getAll() }
    }
    val exceptions by produceState<List<ExceptionInfo>?>(null, changes) {
        value = withContext(Dispatchers.IO) { repository.getExceptions() }
    }
    var confirmClear by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ExceptionSort.LAST_SEEN) }
    var showSort by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val onExceptions = tab == CrashesActivity.TAB_EXCEPTIONS
    val hasItems = if (onExceptions) !exceptions.isNullOrEmpty() else !crashes.isNullOrEmpty()

    BackHandler(enabled = onExceptions && searching) {
        searching = false
        query = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matlog_crashes)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (onExceptions && !exceptions.isNullOrEmpty()) {
                        MatlogIconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            MatlogIcon(
                                if (searching) R.drawable.ic_close else R.drawable.ic_search,
                                contentDescription = stringResource(R.string.matlog_exceptions_search)
                            )
                        }
                        MatlogIconButton(onClick = { showSort = true }) {
                            MatlogIcon(
                                R.drawable.ic_sort,
                                contentDescription = stringResource(R.string.matlog_exceptions_sort)
                            )
                        }
                    }
                    if (hasItems) {
                        MatlogIconButton(onClick = { confirmClear = true }) {
                            MatlogIcon(
                                R.drawable.ic_delete_sweep,
                                contentDescription = stringResource(R.string.matlog_crash_clear_all)
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = !onExceptions,
                    onClick = { onTabChange(CrashesActivity.TAB_CRASHES) },
                    text = { Text(stringResource(R.string.matlog_crashes)) },
                )
                Tab(
                    selected = onExceptions,
                    onClick = { onTabChange(CrashesActivity.TAB_EXCEPTIONS) },
                    text = { Text(stringResource(R.string.matlog_exceptions)) },
                )
            }
            if (onExceptions) {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { MatlogIcon(R.drawable.ic_search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.matlog_exceptions_search)) },
                        singleLine = true,
                    )
                }
                ExceptionList(exceptions, query, sort, onExceptionClick)
            } else {
                CrashList(crashes, onCrashClick)
            }
        }
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text(stringResource(R.string.matlog_exceptions_sort)) },
            text = {
                Column {
                    ExceptionSort.values().forEach { option ->
                        ListItem(
                            modifier = Modifier.clickable {
                                sort = option
                                showSort = false
                            },
                            leadingContent = { RadioButton(selected = sort == option, onClick = null) },
                            headlineContent = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
            },
            confirmButton = {
                MatlogTextButton(onClick = { showSort = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.matlog_crash_clear_all)) },
            text = {
                Text(
                    stringResource(
                        if (onExceptions) R.string.matlog_exceptions_clear_all_message
                        else R.string.matlog_crash_clear_all_message
                    )
                )
            },
            confirmButton = {
                MatlogTextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) {
                        if (onExceptions) repository.dismissAllExceptions() else repository.dismissAll()
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                MatlogTextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CrashList(crashes: List<Crash>?, onCrashClick: (Crash) -> Unit) {
    when {
        crashes == null -> Unit
        crashes.isEmpty() -> EmptyState(R.drawable.ic_bug_report, R.string.matlog_crashes_empty)
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(crashes, key = { it.id }) { crash ->
                CrashListItem(crash, onClick = { onCrashClick(crash) })
            }
        }
    }
}

@Composable
private fun ExceptionList(
    exceptions: List<ExceptionInfo>?,
    query: String,
    sort: ExceptionSort,
    onExceptionClick: (ExceptionInfo) -> Unit,
) {
    val context = LocalContext.current
    val shown = remember(exceptions, query, sort) {
        exceptions.orEmpty()
            .filter {
                query.isBlank() || it.exception.contains(query, true) || it.packageName.contains(query, true) ||
                    it.topFrame.contains(query, true) || it.tag.contains(query, true) ||
                    it.message.contains(query, true)
            }
            .let { list ->
                when (sort) {
                    ExceptionSort.LAST_SEEN -> list.sortedByDescending { it.lastSeen }
                    ExceptionSort.COUNT -> list.sortedByDescending { it.count }
                    ExceptionSort.APP -> list.sortedWith(
                        compareBy<ExceptionInfo, String>(String.CASE_INSENSITIVE_ORDER) {
                            CrashNotifier.appLabel(context, it.packageName)
                        }.thenByDescending { it.lastSeen }
                    )
                }
            }
    }
    when {
        exceptions == null -> Unit
        exceptions.isEmpty() -> EmptyState(R.drawable.ic_bug_report, R.string.matlog_exceptions_empty)
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { exception ->
                ExceptionListItem(exception, onClick = { onExceptionClick(exception) })
            }
        }
    }
}

@Composable
private fun CrashListItem(crash: Crash, onClick: () -> Unit) {
    val appInfo = rememberAppInfo(crash.packageName)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { AppIcon(appInfo.icon) },
        headlineContent = {
            Text(appInfo.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.matlog_crash_summary,
                    stringResource(crash.type.labelRes),
                    crash.timestamp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ExceptionListItem(exception: ExceptionInfo, onClick: () -> Unit) {
    val appInfo = rememberAppInfo(exception.packageName)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { AppIcon(appInfo.icon) },
        overlineContent = { Text(appInfo.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        headlineContent = {
            Text(exception.shortName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOf(
                    exception.topFrame,
                    DateUtils.getRelativeTimeSpanString(exception.lastSeen).toString(),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (exception.muted) {
                Text(
                    stringResource(R.string.matlog_exception_muted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Badge { Text(stringResource(R.string.matlog_exception_count, exception.count)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashDetailsScreen(
    repository: CrashRepository,
    crashId: Long,
    onBack: () -> Unit,
    onShare: (Crash) -> Unit,
    onExport: (Crash) -> Unit,
    onDelete: (Crash) -> Unit,
) {
    val context = LocalContext.current
    val changes by repository.changes.collectAsState()
    val crash by produceState<Crash?>(null, crashId, changes) {
        value = withContext(Dispatchers.IO) { repository.get(crashId) }
    }
    val current = crash
    val appInfo = rememberAppInfo(current?.packageName.orEmpty())

    LaunchedEffect(current) {
        current?.let { CrashNotifier.cancel(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current != null) appInfo.label else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (current != null) {
                        MatlogIconButton(onClick = {
                            CrashActionReceiver.copyToClipboard(context, current)
                            Toast.makeText(context, R.string.matlog_crash_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            MatlogIcon(
                                R.drawable.ic_copy,
                                contentDescription = stringResource(R.string.matlog_crash_copy)
                            )
                        }
                        MatlogIconButton(onClick = { onShare(current) }) {
                            MatlogIcon(
                                R.drawable.ic_share_white_24dp,
                                contentDescription = stringResource(R.string.matlog_crash_share)
                            )
                        }
                        MatlogIconButton(onClick = { onExport(current) }) {
                            MatlogIcon(
                                R.drawable.ic_download,
                                contentDescription = stringResource(R.string.matlog_export)
                            )
                        }
                        MatlogIconButton(onClick = { onDelete(current) }) {
                            MatlogIcon(
                                R.drawable.ic_delete,
                                contentDescription = stringResource(R.string.matlog_crash_delete)
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                leadingContent = { AppIcon(appInfo.icon) },
                headlineContent = { Text(current.packageName) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.matlog_crash_summary,
                            stringResource(current.type.labelRes),
                            current.timestamp
                        )
                    )
                },
            )
            LogText(current.log)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionDetailsScreen(
    repository: CrashRepository,
    exceptionId: Long,
    onBack: () -> Unit,
    onShare: (ExceptionInfo) -> Unit,
    onExport: (ExceptionInfo) -> Unit,
    onShowInLog: (ExceptionInfo) -> Unit,
    onExcludeTag: (ExceptionInfo) -> Unit,
    onDelete: (ExceptionInfo) -> Unit,
) {
    val context = LocalContext.current
    val changes by repository.changes.collectAsState()
    val exception by produceState<ExceptionInfo?>(null, exceptionId, changes) {
        value = withContext(Dispatchers.IO) { repository.getException(exceptionId) }
    }
    val current = exception
    val appInfo = rememberAppInfo(current?.packageName.orEmpty())
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(current?.shortName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (current != null) {
                        MatlogIconButton(onClick = { LogTextActions.copy(context, current.log) }) {
                            MatlogIcon(
                                R.drawable.ic_copy,
                                contentDescription = stringResource(R.string.matlog_crash_copy)
                            )
                        }
                        MatlogIconButton(onClick = { onShare(current) }) {
                            MatlogIcon(
                                R.drawable.ic_share_white_24dp,
                                contentDescription = stringResource(R.string.matlog_crash_share)
                            )
                        }
                        Box {
                            MatlogIconButton(onClick = { menuOpen = true }) {
                                MatlogIcon(
                                    R.drawable.ic_more_vert,
                                    contentDescription = stringResource(R.string.matlog_more_options)
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuItem(R.string.matlog_exception_show_in_log) {
                                    menuOpen = false
                                    onShowInLog(current)
                                }
                                MenuItem(
                                    if (current.muted) R.string.matlog_exception_unmute
                                    else R.string.matlog_exception_mute
                                ) {
                                    menuOpen = false
                                    scope.launch(Dispatchers.IO) {
                                        repository.setExceptionMuted(current.id, !current.muted)
                                    }
                                }
                                if (current.tag.isNotEmpty()) {
                                    MenuItem(R.string.matlog_exception_exclude_tag) {
                                        menuOpen = false
                                        onExcludeTag(current)
                                    }
                                }
                                MenuItem(R.string.matlog_export) {
                                    menuOpen = false
                                    onExport(current)
                                }
                                MenuItem(R.string.matlog_crash_delete) {
                                    menuOpen = false
                                    onDelete(current)
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                leadingContent = { AppIcon(appInfo.icon) },
                overlineContent = { Text(appInfo.label) },
                headlineContent = { Text(current.exception) },
                supportingContent = {
                    Column {
                        if (current.message.isNotEmpty()) Text(current.message)
                        Text(
                            stringResource(
                                R.string.matlog_exception_seen,
                                current.count,
                                DateUtils.getRelativeTimeSpanString(current.firstSeen).toString(),
                                DateUtils.getRelativeTimeSpanString(current.lastSeen).toString(),
                            )
                        )
                        if (current.tag.isNotEmpty()) Text(stringResource(R.string.matlog_exception_tag, current.tag))
                        if (current.muted) Text(stringResource(R.string.matlog_exception_muted))
                    }
                },
            )
            LogText(current.log)
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

@Composable
private fun LogText(log: String) {
    SelectionContainer(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = log,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.logText,
            softWrap = false,
        )
    }
}

@Composable
private fun EmptyState(icon: Int, textRes: Int) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MatlogIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(textRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        MatlogIcon(
            R.drawable.ic_bug_report,
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(8.dp),
        )
    }
}

private class AppInfo(val label: String, val icon: ImageBitmap?)

@Composable
private fun rememberAppInfo(packageName: String): AppInfo {
    val context = LocalContext.current
    val info by produceState(AppInfo(packageName, null), packageName) {
        value = withContext(Dispatchers.IO) {
            val icon = runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                    .asImageBitmap()
            }.getOrNull()
            AppInfo(CrashNotifier.appLabel(context, packageName), icon)
        }
    }
    return info
}

private const val ICON_SIZE_PX = 96
