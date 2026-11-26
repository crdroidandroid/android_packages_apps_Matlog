/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogTheme
import com.pluscubed.logcat.data.SendLogDetails
import com.pluscubed.logcat.helper.LogExporter
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.ui.LogcatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

class CrashesActivity : ComponentActivity() {

    private val selectedCrashId = mutableStateOf<Long?>(null)
    private var exportingCrashId: Long? = null

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)) { uri ->
            val id = exportingCrashId
            exportingCrashId = null
            if (uri != null && id != null) exportCrash(id, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedCrashId.value = savedInstanceState?.getLong(EXTRA_CRASH_ID, -1L)
            ?.takeIf { it != -1L }
            ?: crashIdFrom(intent)
        exportingCrashId = savedInstanceState?.getLong(STATE_EXPORTING_ID, -1L)?.takeIf { it != -1L }
        val repository = CrashRepository.get(this)
        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }
        setContent {
            MatlogTheme {
                val crashId by selectedCrashId
                val id = crashId
                if (id == null) {
                    CrashListScreen(
                        repository = repository,
                        onCrashClick = { selectedCrashId.value = it.id },
                        onBack = { finish() },
                    )
                } else {
                    BackHandler { selectedCrashId.value = null }
                    CrashDetailsScreen(
                        repository = repository,
                        crashId = id,
                        onBack = { selectedCrashId.value = null },
                        onShare = { crash -> shareCrash(crash) },
                        onExport = { crash ->
                            exportingCrashId = crash.id
                            exportLauncher.launch(LogExporter.textFilename())
                        },
                        onDelete = { crash ->
                            selectedCrashId.value = null
                            lifecycleScope.launch(Dispatchers.IO) { repository.dismiss(crash.id) }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        crashIdFrom(intent)?.let { selectedCrashId.value = it }
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

    // shared as a file, the same way as a normal log
    private fun shareCrash(crash: Crash) {
        lifecycleScope.launch {
            val (file, appLabel) = withContext(Dispatchers.IO) {
                SaveLogHelper.cleanTemp(this@CrashesActivity)
                SaveLogHelper.saveTemporaryFile(
                    this@CrashesActivity, SaveLogHelper.createLogFilename(), crash.log, null
                ) to CrashNotifier.appLabel(this@CrashesActivity, crash.packageName)
            }
            if (file == null) {
                Toast.makeText(this@CrashesActivity, R.string.matlog_crash_share_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            CrashNotifier.cancel(this@CrashesActivity, crash)
            LogcatActivity.startChooser(
                this@CrashesActivity,
                getString(R.string.matlog_crash_share_subject, appLabel),
                "",
                SendLogDetails.AttachmentType.Text,
                file
            )
        }
    }

    private fun exportCrash(id: Long, uri: Uri) {
        lifecycleScope.launch {
            val crash = withContext(Dispatchers.IO) { CrashRepository.get(this@CrashesActivity).get(id) }
            if (crash == null) {
                Toast.makeText(this@CrashesActivity, R.string.matlog_export_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            LogExporter.exportText(this@CrashesActivity, uri, crash.log)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedCrashId.value?.let { outState.putLong(EXTRA_CRASH_ID, it) }
        exportingCrashId?.let { outState.putLong(STATE_EXPORTING_ID, it) }
    }

    private fun crashIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_CRASH_ID, -1L)?.takeIf { it != -1L }

    companion object {
        private const val EXTRA_CRASH_ID = "crash_id"
        private const val STATE_EXPORTING_ID = "exporting_crash_id"
        private const val ACTION_SHARE_CRASH = "org.omnirom.logcat.action.SHARE_CRASH"

        fun shareIntent(context: Context, crashId: Long): Intent =
            intent(context, crashId).setAction(ACTION_SHARE_CRASH)

        @JvmStatic
        @JvmOverloads
        fun intent(context: Context, crashId: Long? = null): Intent =
            Intent(context, CrashesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { crashId?.let { putExtra(EXTRA_CRASH_ID, it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashListScreen(
    repository: CrashRepository,
    onCrashClick: (Crash) -> Unit,
    onBack: () -> Unit,
) {
    val changes by repository.changes.collectAsState()
    val crashes by produceState<List<Crash>?>(null, changes) {
        value = withContext(Dispatchers.IO) { repository.getAll() }
    }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matlog_crashes)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (!crashes.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.matlog_crash_clear_all)
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = crashes
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                items(list, key = { it.id }) { crash ->
                    CrashListItem(crash, onClick = { onCrashClick(crash) })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.matlog_crash_clear_all)) },
            text = { Text(stringResource(R.string.matlog_crash_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { repository.dismissAll() }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
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
                        IconButton(onClick = {
                            CrashActionReceiver.copyToClipboard(context, current)
                            Toast.makeText(context, R.string.matlog_crash_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.matlog_crash_copy)
                            )
                        }
                        IconButton(onClick = { onShare(current) }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.matlog_crash_share)
                            )
                        }
                        IconButton(onClick = { onExport(current) }) {
                            Icon(
                                Icons.Filled.SaveAlt,
                                contentDescription = stringResource(R.string.matlog_export)
                            )
                        }
                        IconButton(onClick = {
                            onDelete(current)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
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
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = current.log,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.matlog_crashes_empty),
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
        Icon(
            Icons.Filled.BugReport,
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
