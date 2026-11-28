/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Filter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.search.SearchBar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.io.File
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.logcat.R
import com.pluscubed.logcat.crash.CrashMonitorService
import com.pluscubed.logcat.crash.CrashesActivity
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.data.LogLineAdapter
import com.pluscubed.logcat.data.LogLineViewHolder
import com.pluscubed.logcat.data.LogSearch
import com.pluscubed.logcat.data.LogSelection
import com.pluscubed.logcat.data.SendLogDetails
import com.pluscubed.logcat.db.CatlogDBHelper
import com.pluscubed.logcat.filter.FilterRepository
import com.pluscubed.logcat.filter.FiltersActivity
import com.pluscubed.logcat.helper.BuildHelper
import com.pluscubed.logcat.helper.DialogHelper
import com.pluscubed.logcat.helper.LogExporter
import com.pluscubed.logcat.helper.LogTextActions
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.helper.SaveLogHelper
import com.pluscubed.logcat.helper.SavedLogImporter
import com.pluscubed.logcat.intents.Intents
import com.pluscubed.logcat.reader.DmesgReader
import com.pluscubed.logcat.reader.LogcatReader
import com.pluscubed.logcat.reader.LogcatSession
import com.pluscubed.logcat.reader.PstoreReader

class LogcatActivity : AppCompatActivity(), Filter.FilterListener, LogLineViewHolder.OnClickListener,
    LogcatSession.Callback {

    private lateinit var logListAdapter: LogLineAdapter
    private lateinit var logcatSession: LogcatSession
    private lateinit var logSearch: LogSearch
    private lateinit var list: RecyclerView
    private lateinit var searchBar: SearchBar
    private lateinit var findBar: MaterialToolbar
    private lateinit var selectionBar: MaterialToolbar
    private lateinit var findText: MaterialAutoCompleteTextView
    private lateinit var fab: FloatingActionButton
    private lateinit var searchSuggestionsAdapter: SimpleCursorAdapter

    private val selection = LogSelection()
    private var firstLinesReceived = false
    private var appliedFiltersVersion = -1L
    private var searchRegex = false
    private var filterOnSearch = false
    private var selecting = false
    private var resumeAfterSelection = false
    private var searchingString: String? = null
    private var autoscrollToBottom = true
    private var collapsedMode = false
    private var dynamicallyEnteringSearchText = false
    private var currentlyOpenLog: String? = null
    private var kernelMode = false
    private var openLogJob: Job? = null

    // read by the suggestion filter on a worker thread
    private val searchSuggestions: MutableSet<String> = Collections.synchronizedSet(HashSet())

    // held in memory only, lost if the activity is recreated while the picker is open
    private var pendingSelectionExport: String? = null

    private val exportLogLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)
    ) { uri -> uri?.let { LogExporter.exportText(this, it, currentLogText()) } }

    private val exportSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT)
    ) { uri ->
        val text = pendingSelectionExport
        pendingSelectionExport = null
        when {
            uri == null -> Unit
            text != null -> LogExporter.exportText(this, uri, text)
            else -> Toast.makeText(this, R.string.matlog_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private val exportZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LogExporter.MIME_ZIP)
    ) { uri -> uri?.let(::exportLogZip) }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onSettingsClosed(it.resultCode == RESULT_OK && it.data?.getBooleanExtra(EXTRA_BUFFER_CHANGED, false) == true)
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                selecting -> finishSelection()
                isSearchOpen -> closeSearch()
                currentlyOpenLog != null -> startMainLog()
                kernelMode -> setKernelMode(false)
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                ActivityManager.TaskDescription.Builder().setPrimaryColor(attrColor(android.R.attr.colorBackground)).build()
            )
        }
        setContentView(R.layout.activity_logcat)
        onBackPressedDispatcher.addCallback(this, backCallback)

        LogLine.isScrubberEnabled = PreferenceHelper.isScrubberEnabled(this)
        handleShortcut(intent.getStringExtra(EXTRA_SHORTCUT_ACTION))

        logcatSession = LogcatSession(this, lifecycleScope, this)
        CrashMonitorService.update(this)

        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            resumeAfterSelection = false
            if (logcatSession.isRunning) {
                if (logcatSession.isPaused) logcatSession.resume() else logcatSession.pause()
            }
            updateFabStatus()
        }

        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.itemAnimator = null

        searchBar = findViewById(R.id.search_bar)
        setSupportActionBar(searchBar)
        searchBar.setOnClickListener { openSearch(true) }

        findViewById<BottomAppBar>(R.id.bottomAppBar).setOnMenuItemClickListener {
            onBottomMenuItemSelected(it.itemId)
        }

        collapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(this)

        searchSuggestionsAdapter = SimpleCursorAdapter(
            this, R.layout.list_item_dropdown, null, arrayOf(COLUMN_SUGGESTION), intArrayOf(android.R.id.text1), 0,
        ).apply {
            stringConversionColumn = 1
            setFilterQueryProvider(::suggestionsCursor)
        }

        setUpFindBar()
        setUpSelectionBar()
        setUpAdapter()
        startLog()
    }

    private fun handleShortcut(action: String?) {
        if (action != SHORTCUT_RECORD) return
        showProgressBar()
        DialogHelper.startRecording(
            DialogHelper.createLogFilename(),
            "",
            PreferenceHelper.getDefaultLogLevelPreference(this).toString(),
            {
                hideProgressBar()
                finish()
            },
            this,
        )
    }

    // saved searches are the first suggestions offered
    private fun addFiltersToSuggestions() {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val db = CatlogDBHelper(this@LogcatActivity)
                try {
                    db.findFilterItems()
                } finally {
                    db.close()
                }
            }
            saved.forEach { addToAutocompleteSuggestions(it.text) }
        }
    }

    private fun setKernelMode(enabled: Boolean) {
        kernelMode = enabled
        logcatSession.isKernel = enabled
        startMainLog()
    }

    private fun startLog() {
        val intent = intent
        when {
            isViewIntent(intent) -> {
                startMainLog()
                importAndOpen(intent)
            }
            else -> intent.getStringExtra(EXTRA_FILENAME)?.let(::openLogFile) ?: startMainLog()
        }
        doAfterInitialMessage(intent)
    }

    private fun importAndOpen(intent: Intent) {
        SavedLogImporter.importLog(this, intent.data!!) { openLogFile(it) }
    }

    // an intent sent from an external application
    private fun doAfterInitialMessage(intent: Intent) {
        if (intent.action != Intents.ACTION_LAUNCH) return
        intent.getStringExtra(Intents.EXTRA_FILTER)?.takeIf { it.isNotEmpty() }?.let(::setSearchText)
        val level = intent.getStringExtra(Intents.EXTRA_LEVEL)?.takeIf { it.isNotEmpty() } ?: return
        val limit = resources.getStringArray(R.array.log_levels_values).indexOf(level.uppercase(Locale.US))
        if (limit == -1) {
            Toast.makeText(this, getString(R.string.toast_invalid_level, level), Toast.LENGTH_LONG).show()
        } else {
            logListAdapter.logLevelLimit = limit
            logLevelChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if (logListAdapter.itemCount > 0) {
            logListAdapter.notifyDataSetChanged()
            // otherwise the list jumps back to the top
            scrollToBottom()
        }
        updateFabStatus()
        applyUserFilters()
        updateKeepScreenOn()
        // the open saved log was deleted or renamed from the saved logs screen
        currentlyOpenLog?.let { if (!SaveLogHelper.getFile(it).exists()) startMainLog() }
    }

    private fun updateKeepScreenOn() {
        val keepScreenOn = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.matlog_pref_keep_screen_on), false)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyUserFilters() {
        val repository = FilterRepository.get(this)
        val version = repository.version
        if (version == appliedFiltersVersion) return
        appliedFiltersVersion = version
        lifecycleScope.launch {
            logListAdapter.userFilters = withContext(Dispatchers.IO) { repository.activeFilterSet() }
            search(searchingString)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        doAfterInitialMessage(intent)
        if (isViewIntent(intent)) {
            importAndOpen(intent)
            return
        }
        // launched from the widget or notification
        if (intent.action != Intents.ACTION_LAUNCH) intent.getStringExtra(EXTRA_FILENAME)?.let(::openLogFile)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onSettingsClosed(bufferChanged: Boolean) {
        // preferences may have changed
        PreferenceHelper.clearCache()
        collapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(this)
        val newFilterOnSearch = LogSearch.isFilterOnSearch(this)
        if (newFilterOnSearch != filterOnSearch) {
            filterOnSearch = newFilterOnSearch
            search(searchingString)
        }
        if (bufferChanged && currentlyOpenLog == null && !kernelMode) {
            startMainLog()
        } else {
            // the text size might have changed
            expandOrCollapseAll(false)
        }
        logListAdapter.notifyDataSetChanged()
        updateUiForFilename()
    }

    private fun startMainLog() {
        resetDisplayedLog(null)
        firstLinesReceived = false
        showProgressBar()
        logcatSession.start()
        updateFabStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        logcatSession.stop()
    }

    private fun suggestionsCursor(query: CharSequence?): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, COLUMN_SUGGESTION))
        if (query == null) return cursor
        val prefix = query.toString()
        val matches = synchronized(searchSuggestions) {
            searchSuggestions.filter { it.startsWith(prefix, ignoreCase = true) }
        }
        matches.sortedWith(String.CASE_INSENSITIVE_ORDER).take(MAX_SHOWN_SUGGESTIONS).forEachIndexed { index, suggestion ->
            cursor.addRow(arrayOf<Any>(index, suggestion))
        }
        return cursor
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun setUpFindBar() {
        findBar = findViewById(R.id.find_bar)
        findBar.setNavigationOnClickListener { closeSearch() }
        findBar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_more) {
                LogcatOverflowMenu.showFind(findBar, R.id.menu_more, searchRegex, ::onFindMenuItemSelected)
                true
            } else {
                onOptionsItemSelected(item)
            }
        }
        findText = findViewById(R.id.find_text)
        findText.setAdapter(searchSuggestionsAdapter)
        findText.doAfterTextChanged {
            if (!dynamicallyEnteringSearchText) search(it?.toString())
            dynamicallyEnteringSearchText = false
        }
        findText.setOnEditorActionListener { _, _, _ ->
            findText.dismissDropDown()
            setKeyboardVisible(false)
            true
        }
    }

    private val isSearchOpen: Boolean
        get() = findBar.visibility == View.VISIBLE

    private fun openSearch(showKeyboard: Boolean) {
        if (selecting) return
        searchBar.visibility = View.GONE
        findBar.visibility = View.VISIBLE
        updateSearchUi()
        if (showKeyboard) setKeyboardVisible(true)
    }

    private fun closeSearch() {
        if (!isSearchOpen) return
        setKeyboardVisible(false)
        findText.dismissDropDown()
        findText.setText(null)
        findBar.visibility = View.GONE
        searchBar.visibility = if (selecting) View.GONE else View.VISIBLE
    }

    private fun setKeyboardVisible(visible: Boolean) {
        val imm = getSystemService(InputMethodManager::class.java)
        if (visible) {
            findText.requestFocus()
            imm.showSoftInput(findText, InputMethodManager.SHOW_IMPLICIT)
        } else {
            findText.clearFocus()
            imm.hideSoftInputFromWindow(findText.windowToken, 0)
        }
    }

    private fun updateSearchUi() {
        if (!::findBar.isInitialized || !::logSearch.isInitialized) return
        val menu = findBar.menu
        val countView = menu.findItem(R.id.menu_search_count).actionView as TextView
        val hitCount = logSearch.hitCount
        countView.text = when {
            !logSearch.isActive -> null
            logSearch.isInvalid -> getString(R.string.matlog_search_invalid)
            logSearch.currentIndex >= 0 -> getString(R.string.matlog_search_position, logSearch.currentIndex + 1, hitCount)
            else -> hitCount.toString()
        }
        countView.setTextColor(attrColor(if (logSearch.isInvalid) R.attr.colorError else R.attr.colorOnSurfaceVariant))
        menu.findItem(R.id.menu_search_previous).isEnabled = hitCount > 0
        menu.findItem(R.id.menu_search_next).isEnabled = hitCount > 0
    }

    private fun refreshSearchHits() {
        if (!logSearch.isActive) return
        logSearch.update(logListAdapter.objects)
        updateSearchUi()
    }

    private fun moveToSearchHit(next: Boolean) {
        val layoutManager = list.layoutManager as LinearLayoutManager
        val lines = logListAdapter.objects
        val oldPosition = logSearch.currentPosition()
        val position = if (next) {
            logSearch.next(lines, layoutManager.findFirstVisibleItemPosition())
        } else {
            logSearch.previous(lines, layoutManager.findLastVisibleItemPosition())
        }
        if (position < 0) return
        autoscrollToBottom = false
        layoutManager.scrollToPositionWithOffset(position, list.height / 3)
        if (oldPosition >= 0) logListAdapter.notifyItemChanged(oldPosition)
        logListAdapter.notifyItemChanged(position)
        updateSearchUi()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_send_log_zip -> showSendLogDialog()
            R.id.menu_settings -> settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.menu_filters -> startActivity(FiltersActivity.intent(this))
            R.id.menu_search_previous -> moveToSearchHit(false)
            R.id.menu_search_next -> moveToSearchHit(true)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onFindMenuItemSelected(itemId: Int) {
        if (itemId == R.id.menu_search_regex) {
            searchRegex = !searchRegex
            search(searchingString)
        }
    }

    override fun onMenuItemClick(itemId: Int, logLine: LogLine?): Boolean {
        logLine ?: return false
        when (itemId) {
            LogLineViewHolder.CONTEXT_MENU_COPY_ID -> CopyLogLineSheet.show(this, logLine)
            LogLineViewHolder.CONTEXT_MENU_INCLUDE_FILTER, LogLineViewHolder.CONTEXT_MENU_EXCLUDE_FILTER ->
                startActivity(
                    FiltersActivity.intentForLine(
                        this, logLine.tag, logLine.packageName, itemId == LogLineViewHolder.CONTEXT_MENU_INCLUDE_FILTER,
                    )
                )
            LogLineViewHolder.CONTEXT_MENU_FILTER_ID -> {
                // not a parsed log line
                if (logLine.processId == -1) return false
                LogcatDialogs.showSearchBy(this, logLine.tag.orEmpty(), logLine.processId, ::setSearchText)
            }
            LogLineViewHolder.CONTEXT_MENU_EXPAND_ALL -> expandOrCollapseAll(true)
            LogLineViewHolder.CONTEXT_MENU_CLEAR -> clearLog()
            LogLineViewHolder.CONTEXT_MENU_SELECT -> toggleSelection(logLine)
            else -> return false
        }
        return true
    }

    override fun onClick(itemView: View, logLine: LogLine?) {
        logLine ?: return
        if (selection.isActive) {
            toggleSelection(logLine)
            return
        }
        logLine.isExpanded = !logLine.isExpanded
        val position = list.getChildAdapterPosition(itemView)
        if (position != RecyclerView.NO_POSITION) logListAdapter.notifyItemChanged(position)
    }

    override fun onLongClick(itemView: View, logLine: LogLine?): Boolean {
        if (logLine == null || !selection.isActive) return false
        toggleSelection(logLine)
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelection(logLine: LogLine) {
        selection.toggle(logLine)
        logListAdapter.notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun onSelectionChanged() {
        if (!selection.isActive) {
            finishSelection()
            return
        }
        if (!selecting) startSelection()
        val count = selection.count
        selectionBar.title = resources.getQuantityString(R.plurals.matlog_selected_count, count, count)
    }

    private fun setUpSelectionBar() {
        selectionBar = findViewById(R.id.selection_bar)
        selectionBar.setNavigationOnClickListener { finishSelection() }
        selectionBar.setOnMenuItemClickListener { onSelectionItemClicked(it.itemId) }
    }

    // the selection bar takes the place of the search bar
    private fun startSelection() {
        selecting = true
        if (isSearchOpen) {
            setKeyboardVisible(false)
            findBar.visibility = View.GONE
        }
        searchBar.visibility = View.GONE
        selectionBar.visibility = View.VISIBLE
        // keep the list still while picking lines
        if (logcatSession.isRunning && !logcatSession.isPaused) {
            logcatSession.pause()
            resumeAfterSelection = true
            updateFabStatus()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun finishSelection() {
        if (!selecting) return
        selecting = false
        selectionBar.visibility = View.GONE
        if (!searchingString.isNullOrEmpty()) {
            findBar.visibility = View.VISIBLE
        } else {
            searchBar.visibility = View.VISIBLE
        }
        selection.clear()
        logListAdapter.notifyDataSetChanged()
        if (resumeAfterSelection) {
            resumeAfterSelection = false
            if (logcatSession.isRunning) logcatSession.resume()
            updateFabStatus()
        }
    }

    private fun selectedText(): String = LogSelection.toText(selection.selectedIn(logListAdapter.trueValues))

    @SuppressLint("NotifyDataSetChanged")
    private fun onSelectionItemClicked(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_selection_select_all -> {
                selection.toggleAll(logListAdapter.objects)
                logListAdapter.notifyDataSetChanged()
                onSelectionChanged()
                return true
            }
            R.id.menu_more -> {
                LogcatOverflowMenu.showSelection(selectionBar, R.id.menu_more) { onSelectionItemClicked(it) }
                return true
            }
            R.id.menu_selection_copy -> LogTextActions.copy(this, selectedText())
            R.id.menu_selection_share -> LogTextActions.share(this, selectedText())
            R.id.menu_selection_export -> {
                pendingSelectionExport = selectedText()
                exportSelectionLauncher.launch(LogExporter.textFilename())
            }
            R.id.menu_selection_extended_copy -> ExtendedCopyActivity.start(this, selectedText())
            else -> return false
        }
        finishSelection()
        return true
    }

    private fun showRecordLogDialog() {
        startActivity(
            Intent(this, RecordLogDialogActivity::class.java)
                .putExtra(RecordLogDialogActivity.EXTRA_QUERY_SUGGESTIONS, suggestionsSnapshot().toTypedArray())
        )
    }

    private fun suggestionsSnapshot(): List<String> = synchronized(searchSuggestions) { searchSuggestions.toList() }

    private fun onBottomMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_more -> LogcatOverflowMenu.showMain(
                findViewById(R.id.bottomAppBar), R.id.menu_more,
                DmesgReader.isAvailable(), kernelMode, PstoreReader.isAvailable(),
            ) { onBottomMenuItemSelected(it) }
            R.id.menu_log_level -> LogcatDialogs.showLogLevel(this, logListAdapter.logLevelLimit) {
                logListAdapter.logLevelLimit = it
                logLevelChanged()
            }
            R.id.menu_open_log -> startActivity(SavedLogsActivity.intent(this))
            R.id.menu_save_log -> LogcatDialogs.showSaveLog(this, newLogFilename(), ::saveLog)
            R.id.menu_export_log -> exportLogLauncher.launch(
                if (kernelMode) SaveLogHelper.createKernelLogFilename() else LogExporter.textFilename()
            )
            R.id.menu_kernel_log -> setKernelMode(!kernelMode)
            R.id.menu_pstore -> startActivity(PstoreActivity.intent(this))
            R.id.menu_record_log -> showRecordLogDialog()
            R.id.menu_save_log_zip -> LogcatDialogs.showSaveLogZip(this) {
                exportZipLauncher.launch(LogExporter.zipFilename())
            }
            R.id.menu_crashes -> startActivity(CrashesActivity.intent(this))
            R.id.menu_saved_searches -> LogcatDialogs.showSavedSearches(
                this, suggestionsSnapshot(), ::setSearchText, ::addToAutocompleteSuggestions,
            )
            else -> return false
        }
        return true
    }

    private fun newLogFilename(): String =
        if (kernelMode) SaveLogHelper.createKernelLogFilename() else DialogHelper.createLogFilename()

    @SuppressLint("NotifyDataSetChanged")
    private fun expandOrCollapseAll(change: Boolean) {
        collapsedMode = change != collapsedMode
        val oldFirstVisibleItem = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        logListAdapter.trueValues.forEach { it.isExpanded = !collapsedMode }
        logListAdapter.notifyDataSetChanged()
        // either keep autoscrolling at the bottom, or keep the first visible line in place
        if (autoscrollToBottom) {
            scrollToBottom()
        } else if (oldFirstVisibleItem != RecyclerView.NO_POSITION) {
            list.scrollToPosition(oldFirstVisibleItem)
        }
    }

    private fun showSendLogDialog() {
        LogcatDialogs.showShareLog(this) { includeDeviceInfo -> sendLog(includeDeviceInfo) }
    }

    private fun sendLog(includeDeviceInfo: Boolean) {
        val lines = currentLogLines()
        val openLog = currentlyOpenLog
        if (openLog == null || includeDeviceInfo) showProgressBar()
        lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) { sendLogDetails(lines, openLog, includeDeviceInfo) }
            hideProgressBar()
            startChooser(this@LogcatActivity, details.subject, "", details.attachmentType, details.attachment)
        }
    }

    private fun exportLogZip(uri: Uri) {
        val includeDeviceInfo = PreferenceHelper.getIncludeDeviceInfoPreference(this)
        val openLog = currentlyOpenLog
        LogExporter.export(this, uri) { out -> SaveLogHelper.writeZip(out, collectLogFiles(openLog, includeDeviceInfo)) }
    }

    // either the one log file, or a zip when device info comes along
    private fun sendLogDetails(lines: List<CharSequence>, openLog: String?, includeDeviceInfo: Boolean): SendLogDetails {
        SaveLogHelper.cleanTemp(this)
        val basename = SaveLogHelper.createLogBasename()
        val files = listOfNotNull(
            if (openLog != null) SaveLogHelper.getFile(openLog) else SaveLogHelper.saveTemporaryFile(this, "$basename.txt", null, lines),
            if (includeDeviceInfo) deviceInfoFile() else null,
        )
        val subject = getString(R.string.subject_log_report)
        return when (files.size) {
            0 -> SendLogDetails(subject, SendLogDetails.AttachmentType.None)
            1 -> SendLogDetails(subject, SendLogDetails.AttachmentType.Text, files[0])
            else -> SaveLogHelper.saveTemporaryZipFile(this, "$basename.zip", files)
                ?.let { SendLogDetails(it.name, SendLogDetails.AttachmentType.Zip, it) }
                ?: SendLogDetails(subject, SendLogDetails.AttachmentType.Text, files[0])
        }
    }

    private fun deviceInfoFile(): File? =
        SaveLogHelper.saveTemporaryFile(this, SaveLogHelper.TEMP_DEVICE_INFO_FILENAME, BuildHelper.buildInformation(), null)

    private fun collectLogFiles(openLog: String?, includeDeviceInfo: Boolean): List<File> {
        SaveLogHelper.cleanTemp(this)
        val files = ArrayList<File>()
        SaveLogHelper.saveTemporaryFile(this, SaveLogHelper.TEMP_LOGCAT_FILENAME, null, LogcatReader.dumpAll(this))
            ?.let(files::add)
        val dmesg = DmesgReader.dump()
        if (dmesg.isNotEmpty()) {
            SaveLogHelper.saveTemporaryFile(this, SaveLogHelper.TEMP_DMESG_FILENAME, null, dmesg)?.let(files::add)
        }
        for (pstoreFile in PstoreReader.list()) {
            val lines = PstoreReader.read(pstoreFile)
            if (lines.isNotEmpty()) {
                SaveLogHelper.saveTemporaryFile(this, "pstore-${pstoreFile.name}.txt", null, lines)?.let(files::add)
            }
        }
        // a zip can't hold two entries with the same name
        if (openLog != null && openLog != SaveLogHelper.TEMP_LOGCAT_FILENAME && openLog != SaveLogHelper.TEMP_DMESG_FILENAME) {
            files.add(SaveLogHelper.getFile(openLog))
        }
        if (includeDeviceInfo) deviceInfoFile()?.let(files::add)
        return files
    }

    private fun currentLogLines(): List<CharSequence> =
        (0 until logListAdapter.itemCount).map { logListAdapter.getItem(it).originalLine.orEmpty() }

    private fun currentLogText(): CharSequence = buildString {
        for (i in 0 until logListAdapter.itemCount) append(logListAdapter.getItem(i).originalLine.orEmpty()).append('\n')
    }

    private fun saveLog(filename: String) {
        val lines = currentLogLines()
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                SaveLogHelper.deleteLogIfExists(filename)
                SaveLogHelper.saveLog(lines, filename)
            }
            if (saved) {
                Toast.makeText(applicationContext, R.string.log_saved, Toast.LENGTH_SHORT).show()
                openLogFile(filename)
            } else {
                Toast.makeText(applicationContext, R.string.unable_to_save_log, Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun openLogFile(filename: String) {
        logcatSession.stop()
        resetDisplayedLog(filename)
        showProgressBar()
        val expanded = !collapsedMode
        openLogJob = lifecycleScope.launch {
            val maxLines = PreferenceHelper.getDisplayLimitPreference(this@LogcatActivity)
            val (lines, truncated) = withContext(Dispatchers.IO) {
                val savedLog = SaveLogHelper.openLog(filename, maxLines)
                savedLog.logLines.map { LogLine.newLogLine(it, expanded) } to savedLog.isTruncated
            }
            if (truncated) {
                val text = resources.getQuantityString(R.plurals.toast_log_truncated, maxLines, maxLines)
                Toast.makeText(this@LogcatActivity, text, Toast.LENGTH_LONG).show()
            }
            hideProgressBar()
            lines.forEach {
                logListAdapter.addWithFilter(it, "")
                addToAutocompleteSuggestions(it)
            }
            logListAdapter.notifyDataSetChanged()
            refreshSearchHits()
            scrollToBottom()
        }
    }

    private fun hideProgressBar() {
        findViewById<View>(R.id.progress_bar).visibility = View.GONE
    }

    private fun showProgressBar() {
        findViewById<View>(R.id.progress_bar).visibility = View.VISIBLE
    }

    private fun resetDisplayedLog(filename: String?) {
        // a file still loading would otherwise land in the new log
        openLogJob?.cancel()
        openLogJob = null
        finishSelection()
        logListAdapter.clear()
        refreshSearchHits()
        currentlyOpenLog = filename
        if (filename != null && kernelMode) {
            kernelMode = false
            logcatSession.isKernel = false
        }
        collapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(this)
        addFiltersToSuggestions()
        updateUiForFilename()
        resetFilter()
    }

    private fun updateUiForFilename() {
        val openLog = currentlyOpenLog
        val hint = when {
            openLog != null -> getString(R.string.matlog_search_hint_file, openLog)
            kernelMode -> getString(R.string.matlog_search_hint_kernel)
            else -> getString(R.string.matlog_search_hint_log)
        }
        searchBar.hint = hint
        findText.hint = hint
        val showBack = openLog != null || kernelMode
        searchBar.setNavigationIcon(if (showBack) R.drawable.ic_arrow_back else R.drawable.ic_search)
        searchBar.setNavigationContentDescription(if (showBack) R.string.matlog_back else R.string.search)
        searchBar.setNavigationOnClickListener {
            if (showBack) onBackPressedDispatcher.onBackPressed() else openSearch(true)
        }
        // recordings only cover logcat
        findViewById<BottomAppBar>(R.id.bottomAppBar).menu.findItem(R.id.menu_record_log).isVisible = !kernelMode
    }

    private fun resetFilter() {
        val defaultLevel = PreferenceHelper.getDefaultLogLevelPreference(this).toString()
        logListAdapter.logLevelLimit = resources.getStringArray(R.array.log_levels_values).indexOf(defaultLevel).coerceAtLeast(0)
        logLevelChanged()
    }

    private fun setUpAdapter() {
        filterOnSearch = LogSearch.isFilterOnSearch(this)
        logSearch = LogSearch(
            attrColor(R.attr.colorTertiaryContainer), attrColor(R.attr.colorOnTertiaryContainer),
            attrColor(R.attr.colorPrimary), attrColor(R.attr.colorOnPrimary),
        )
        logListAdapter = LogLineAdapter(this, selection, logSearch)
        list.adapter = logListAdapter
        list.setHasFixedSize(true)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // autoscroll only while the bottom of the list is in view
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                autoscrollToBottom = layoutManager.findLastCompletelyVisibleItemPosition() == logListAdapter.itemCount - 1
            }
        })
        ScrollButtons(
            list, findViewById(R.id.scroll_top), findViewById(R.id.scroll_bottom),
            {
                autoscrollToBottom = false
                list.scrollToPosition(0)
            },
            {
                autoscrollToBottom = true
                scrollToBottom()
            },
        )
    }

    // sets the search text without popping up suggestions, which only help while typing
    private fun setSearchText(text: String) {
        search(text)
        openSearch(false)
        dynamicallyEnteringSearchText = true
        findText.setText(text, false)
        findText.setSelection(findText.length())
    }

    private fun search(filterText: String?) {
        searchingString = filterText
        logSearch.setQuery(filterText, searchRegex)
        logListAdapter.searchRegex = searchRegex
        logListAdapter.getFilter().filter(filterQuery(), this)
        updateSearchUi()
    }

    // in highlight mode the search text only marks matches
    private fun filterQuery(): String? = if (filterOnSearch) searchingString else ""

    private fun updateFabStatus() {
        if (!logcatSession.isRunning) return
        fab.setImageResource(if (logcatSession.isPaused) R.drawable.ic_play_arrow_white_24dp else R.drawable.ic_pause_white_24dp)
    }

    override fun onFilterComplete(count: Int) {
        // always scroll to the bottom when searching
        if (count > 0) list.scrollToPosition(count - 1)
        refreshSearchHits()
    }

    private fun logLevelChanged() {
        search(searchingString)
    }

    private fun addToAutocompleteSuggestions(logLine: LogLine) {
        logLine.tag?.trim()?.takeIf { it.isNotEmpty() }?.let(::addToAutocompleteSuggestions)
    }

    private fun addToAutocompleteSuggestions(text: String) {
        if (searchSuggestions.size < MAX_NUM_SUGGESTIONS) searchSuggestions.add(text)
    }

    private fun scrollToBottom() {
        val count = logListAdapter.itemCount
        if (count > 0) list.scrollToPosition(count - 1)
    }

    override fun createLogLine(line: String): LogLine = LogLine.newLogLine(line, !collapsedMode)

    @SuppressLint("NotifyDataSetChanged")
    override fun onLogLines(lines: List<LogLine>) {
        if (!firstLinesReceived) {
            firstLinesReceived = true
            hideProgressBar()
        }
        val query = filterQuery()
        lines.forEach {
            logListAdapter.addWithFilter(it, query)
            addToAutocompleteSuggestions(it)
        }
        val excess = logListAdapter.trueValues.size - PreferenceHelper.getDisplayLimitPreference(this)
        if (excess > 0) {
            logListAdapter.removeFirst(excess)
            if (selection.isActive) {
                selection.retainIn(logListAdapter.trueValues)
                onSelectionChanged()
            }
        } else {
            logListAdapter.notifyDataSetChanged()
        }
        refreshSearchHits()
        if (autoscrollToBottom) scrollToBottom()
    }

    private fun clearLog() {
        if (currentlyOpenLog != null) {
            startMainLog()
            return
        }
        finishSelection()
        logListAdapter.clear()
        refreshSearchHits()
    }

    private fun attrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        try {
            return ta.getColor(0, 0)
        } finally {
            ta.recycle()
        }
    }

    companion object {
        const val EXTRA_FILENAME = "filename"
        private const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
        private const val EXTRA_BUFFER_CHANGED = "bufferChanged"
        private const val SHORTCUT_RECORD = "record"
        private const val COLUMN_SUGGESTION = "suggestion"
        private const val MAX_NUM_SUGGESTIONS = 1000
        private const val MAX_SHOWN_SUGGESTIONS = 8

        private fun isViewIntent(intent: Intent?): Boolean =
            intent?.action == Intent.ACTION_VIEW && intent.data != null

        fun startChooser(
            context: Context,
            subject: String,
            body: String,
            attachmentType: SendLogDetails.AttachmentType,
            attachment: File?,
        ) {
            val send = Intent(Intent.ACTION_SEND)
                .setType(attachmentType.mimeType)
                .putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, body)
            if (attachment != null) {
                val uri = FileProvider.getUriForFile(context, "org.omnirom.logcat.provider", attachment)
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.clipData = ClipData.newUri(context.contentResolver, attachment.name, uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getText(R.string.send_log_title)))
        }
    }
}
