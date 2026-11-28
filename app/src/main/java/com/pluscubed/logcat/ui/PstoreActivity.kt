/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pluscubed.logcat.helper.LogExporter
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.reader.PstoreFile
import com.pluscubed.logcat.reader.PstoreReader
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogIconButton
import com.pluscubed.logcat.ui.compose.MatlogLoadingIndicator
import com.pluscubed.logcat.ui.compose.MatlogTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R

class PstoreActivity : ComponentActivity() {

    private val version = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatlogTheme {
                PstoreScreen(
                    version = version.intValue,
                    onRefresh = { version.intValue++ },
                    onOpen = ::openFile,
                    onExport = ::exportFile,
                    onBack = { finish() },
                )
            }
        }
    }

    // copied into saved logs so the log viewer can search, filter and share it
    private fun openFile(file: PstoreFile) {
        lifecycleScope.launch {
            val filename = withContext(Dispatchers.IO) {
                val name = savedFilename(file)
                if (SaveLogHelper.getFile(name).exists()) return@withContext name
                val lines = PstoreReader.read(file)
                if (lines.isNotEmpty() && SaveLogHelper.saveLog(lines, name)) name else null
            }
            if (filename == null) {
                Toast.makeText(this@PstoreActivity, R.string.matlog_pstore_read_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            startActivity(
                Intent(this@PstoreActivity, LogcatActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(SavedLogsActivity.EXTRA_FILENAME, filename)
            )
            finish()
        }
    }

    private fun exportFile(file: PstoreFile, uri: Uri) {
        LogExporter.export(this, uri) { out ->
            val lines = PstoreReader.read(file)
            out.write(lines.joinToString("\n", postfix = "\n").toByteArray())
        }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context): Intent = Intent(context, PstoreActivity::class.java)

        // the file time tells reboots apart, so opening twice reuses the copy
        @JvmStatic
        fun savedFilename(file: PstoreFile): String =
            "pstore-${file.name}-" +
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(file.modified)) + ".txt"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PstoreScreen(
    version: Int,
    onRefresh: () -> Unit,
    onOpen: (PstoreFile) -> Unit,
    onExport: (PstoreFile, Uri) -> Unit,
    onBack: () -> Unit,
) {
    val files by produceState<List<PstoreFile>?>(null, version) {
        value = null
        value = withContext(Dispatchers.IO) { PstoreReader.list() }
    }
    var exportingName by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)
    ) { uri ->
        val file = files?.firstOrNull { it.name == exportingName }
        exportingName = null
        if (uri != null && file != null) onExport(file, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matlog_pstore)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    MatlogIconButton(onClick = onRefresh) {
                        MatlogIcon(R.drawable.ic_refresh, contentDescription = stringResource(R.string.matlog_pstore_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val list = files
            when {
                list == null -> MatlogLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    stringResource(R.string.matlog_pstore_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.name }) { file ->
                        PstoreItem(
                            file = file,
                            onOpen = { onOpen(file) },
                            onExport = {
                                exportingName = file.name
                                exportLauncher.launch(PstoreActivity.savedFilename(file))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PstoreItem(file: PstoreFile, onOpen: () -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(stringResource(kindLabel(file.kind))) },
        supportingContent = {
            Text(
                listOf(
                    file.name,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.modified)),
                    Formatter.formatShortFileSize(context, file.size),
                ).joinToString("  ·  "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                MatlogIconButton(onClick = { menuOpen = true }) {
                    MatlogIcon(R.drawable.ic_more_vert, contentDescription = stringResource(R.string.matlog_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.matlog_export)) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                }
            }
        },
    )
}

private fun kindLabel(kind: PstoreFile.Kind): Int = when (kind) {
    PstoreFile.Kind.CONSOLE -> R.string.matlog_pstore_console
    PstoreFile.Kind.DMESG -> R.string.matlog_pstore_dmesg
    PstoreFile.Kind.PMSG -> R.string.matlog_pstore_pmsg
    PstoreFile.Kind.FTRACE -> R.string.matlog_pstore_ftrace
    PstoreFile.Kind.OTHER -> R.string.matlog_pstore_other
}
