package com.pluscubed.logcat.ui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filter.FilterListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.search.SearchBar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.pluscubed.logcat.data.FilterAdapter;
import com.pluscubed.logcat.data.LogLine;
import com.pluscubed.logcat.data.LogLineAdapter;
import com.pluscubed.logcat.data.LogLineViewHolder;
import com.pluscubed.logcat.data.LogSelection;
import com.pluscubed.logcat.data.SavedLog;
import com.pluscubed.logcat.data.LogSearch;
import com.pluscubed.logcat.data.SearchCriteria;
import com.pluscubed.logcat.data.SendLogDetails;
import com.pluscubed.logcat.data.SortedFilterArrayAdapter;
import com.pluscubed.logcat.db.CatlogDBHelper;
import com.pluscubed.logcat.db.FilterItem;
import com.pluscubed.logcat.helper.BuildHelper;
import com.pluscubed.logcat.helper.DialogHelper;
import com.pluscubed.logcat.helper.DmesgHelper;
import com.pluscubed.logcat.helper.LogExporter;
import com.pluscubed.logcat.helper.LogTextActions;
import com.pluscubed.logcat.helper.PreferenceHelper;
import com.pluscubed.logcat.helper.SaveLogHelper;
import com.pluscubed.logcat.helper.SavedLogImporter;
import com.pluscubed.logcat.intents.Intents;
import com.pluscubed.logcat.crash.CrashMonitorService;
import com.pluscubed.logcat.crash.CrashesActivity;
import com.pluscubed.logcat.filter.FilterRepository;
import com.pluscubed.logcat.filter.FilterSet;
import com.pluscubed.logcat.filter.FiltersActivity;
import com.pluscubed.logcat.reader.DmesgReader;
import com.pluscubed.logcat.reader.LogcatReader;
import com.pluscubed.logcat.reader.LogcatSession;
import com.pluscubed.logcat.reader.PstoreFile;
import com.pluscubed.logcat.reader.PstoreReader;
import com.pluscubed.logcat.util.ArrayUtil;
import com.pluscubed.logcat.util.LogLineAdapterUtil;
import com.pluscubed.logcat.util.StringUtil;
import com.pluscubed.logcat.util.UtilLogger;

import org.omnirom.logcat.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_CLEAR;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_COPY_ID;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_EXCLUDE_FILTER;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_EXPAND_ALL;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_FILTER_ID;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_INCLUDE_FILTER;
import static com.pluscubed.logcat.data.LogLineViewHolder.CONTEXT_MENU_SELECT;


public class LogcatActivity extends AppCompatActivity implements FilterListener, LogLineViewHolder.OnClickListener,
        LogcatSession.Callback {

    private static final int REQUEST_CODE_SETTINGS = 1;

    // how many suggestions to keep in the autosuggestions text
    private static final int MAX_NUM_SUGGESTIONS = 1000;

    // id requests for access to sdcard
    private static final int DELETE_SAVED_LOG_REQUEST = 1;
    private static final int SEND_LOG_ID_REQUEST = 2;
    private static final int SAVE_LOG_REQUEST = 3;
    private static final int OPEN_LOG_REQUEST = 4;
    private static final int SHOW_RECORD_LOG_REQUEST = 6;
    private static final int SHOW_RECORD_LOG_REQUEST_SHORTCUT = 7;
    private static final int SAVE_LOG_ZIP_REQUEST = 8;

    private static UtilLogger log = new UtilLogger(LogcatActivity.class);

    private LogLineAdapter mLogListAdapter;
    private LogcatSession mLogcatSession;
    private boolean mFirstLinesReceived;
    private long mAppliedFiltersVersion = -1;

    private final LogSelection mSelection = new LogSelection();
    private LogSearch mLogSearch;
    private boolean mSearchRegex;
    private boolean mFilterOnSearch;
    private boolean mSelecting;
    private boolean mResumeAfterSelection;

    private final ActivityResultLauncher<String> mExportLogLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT), uri -> {
                if (uri != null) {
                    LogExporter.exportText(this, uri, getCurrentLogAsCharSequence());
                }
            });

    // held in memory only, lost if the activity is recreated while the picker is open
    private String mPendingSelectionExport;

    private final ActivityResultLauncher<String> mExportSelectionLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(LogExporter.MIME_TEXT), uri -> {
                String text = mPendingSelectionExport;
                mPendingSelectionExport = null;
                if (uri == null) {
                    return;
                }
                if (text != null) {
                    LogExporter.exportText(this, uri, text);
                } else {
                    Toast.makeText(this, R.string.matlog_export_failed, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> mExportZipLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(LogExporter.MIME_ZIP), uri -> {
                if (uri != null) {
                    exportLogZip(uri);
                }
            });

    private String mSearchingString;

    private boolean mAutoscrollToBottom = true;
    private boolean mCollapsedMode;

    private boolean mDynamicallyEnteringSearchText;

    // read by the suggestion filter on a worker thread
    private final Set<String> mSearchSuggestionsSet = Collections.synchronizedSet(new HashSet<>());
    private SimpleCursorAdapter mSearchSuggestionsAdapter;

    private String mCurrentlyOpenLog = null;
    private boolean mKernelMode = false;

    private Handler mHandler;
    private SearchBar mSearchBar;
    private MaterialToolbar mFindBar;
    private MaterialToolbar mSelectionBar;
    private MaterialAutoCompleteTextView mFindText;
    private FloatingActionButton mFab;

    public static void startChooser(Context context, String subject, String body, SendLogDetails.AttachmentType attachmentType, File attachment) {

        Intent actionSendIntent = new Intent(Intent.ACTION_SEND);

        actionSendIntent.setType(attachmentType.getMimeType());
        actionSendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (!body.isEmpty()) {
            actionSendIntent.putExtra(Intent.EXTRA_TEXT, body);
        }
        if (attachment != null) {
            Uri uri = FileProvider.getUriForFile(context, "org.omnirom.logcat.provider", attachment);
            log.d("uri is: %s", uri);
            actionSendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            actionSendIntent.setClipData(ClipData.newUri(context.getContentResolver(), attachment.getName(), uri));
            actionSendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        context.startActivity(Intent.createChooser(actionSendIntent, context.getResources().getText(R.string.send_log_title)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_LONG).show();
            return;
        }

        switch (requestCode) {
            case DELETE_SAVED_LOG_REQUEST:
                startActivity(SavedLogsActivity.intent(this));
                break;
            case SEND_LOG_ID_REQUEST:
                showSendLogDialog();
                break;
            case SAVE_LOG_REQUEST:
                showSaveLogDialog();
                break;
            case SAVE_LOG_ZIP_REQUEST:
                showSaveLogZipDialog();
                break;
            case OPEN_LOG_REQUEST:
                startActivity(SavedLogsActivity.intent(this));
                break;
            case SHOW_RECORD_LOG_REQUEST:
                showRecordLogDialog();
                break;
            case SHOW_RECORD_LOG_REQUEST_SHORTCUT:
                handleShortcuts("record");
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(getAttrColor(android.R.attr.colorBackground)).build();
            setTaskDescription(td);
        }

        setContentView(R.layout.activity_logcat);

        LogLine.isScrubberEnabled = PreferenceHelper.isScrubberEnabled(this);

        handleShortcuts(getIntent().getStringExtra("shortcut_action"));

        mHandler = new Handler(Looper.getMainLooper());
        mLogcatSession = new LogcatSession(this, LifecycleOwnerKt.getLifecycleScope(this), this);
        CrashMonitorService.update(this);

        mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mResumeAfterSelection = false;
                if (mLogcatSession.isRunning()) {
                    if (mLogcatSession.isPaused()) {
                        mLogcatSession.resume();
                    } else {
                        mLogcatSession.pause();
                    }
                }
                updateFabStatus();
                //DialogHelper.stopRecordingLog(LogcatActivity.this);
            }
        });

        ((RecyclerView) findViewById(R.id.list)).setLayoutManager(new LinearLayoutManager(this));

        ((RecyclerView) findViewById(R.id.list)).setItemAnimator(null);

        mSearchBar = findViewById(R.id.search_bar);
        setSupportActionBar(mSearchBar);
        mSearchBar.setOnClickListener(v -> openSearch(true));


        ((BottomAppBar) findViewById(R.id.bottomAppBar)).setOnMenuItemClickListener(
                item -> onBottomMenuItemSelected(item.getItemId()));

        mCollapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(this);

        log.d("initial collapsed mode is %s", mCollapsedMode);

        mSearchSuggestionsAdapter = new SimpleCursorAdapter(this,
                R.layout.list_item_dropdown,
                null,
                new String[]{"suggestion"},
                new int[]{android.R.id.text1},
                0);
        mSearchSuggestionsAdapter.setStringConversionColumn(1);
        mSearchSuggestionsAdapter.setFilterQueryProvider(this::suggestionsCursor);

        setUpFindBar();
        setUpSelectionBar();

        setUpAdapter();

        startLog();
    }

    private void handleShortcuts(String action) {
        if (action == null) return;

        switch (action) {
            case "record":
                String logFilename = DialogHelper.createLogFilename();
                String defaultLogLevel = Character.toString(PreferenceHelper.getDefaultLogLevelPreference(this));
                showProgressBar();
                DialogHelper.startRecording(logFilename, "", defaultLogLevel, new Runnable() {
                    @Override
                    public void run() {
                        hideProgressBar();
                        finish();
                    }
                }, this);

                break;
        }
    }

    private void addFiltersToSuggestions() {
        CatlogDBHelper dbHelper = null;
        try {
            dbHelper = new CatlogDBHelper(this);

            for (FilterItem filterItem : dbHelper.findFilterItems()) {
                addToAutocompleteSuggestions(filterItem.getText());
            }
        } finally {
            if (dbHelper != null) {
                dbHelper.close();
            }
        }

    }

    private void setKernelMode(boolean kernelMode) {
        mKernelMode = kernelMode;
        mLogcatSession.setKernel(kernelMode);
        startMainLog();
    }

    private void startLog() {

        Intent intent = getIntent();

        if (isViewIntent(intent)) {
            startMainLog();
            importAndOpen(intent);
        } else if (intent == null || !intent.hasExtra("filename")) {
            startMainLog();
        } else {
            String filename = intent.getStringExtra("filename");
            openLogFile(filename);
        }

        doAfterInitialMessage(getIntent());


    }

    private static boolean isViewIntent(Intent intent) {
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    private void importAndOpen(Intent intent) {
        SavedLogImporter.importLog(this, intent.getData(), this::openLogFile);
    }

    private void doAfterInitialMessage(Intent intent) {

        // handle an intent that was sent from an external application

        if (intent != null && Intents.ACTION_LAUNCH.equals(intent.getAction())) {

            String filter = intent.getStringExtra(Intents.EXTRA_FILTER);
            String level = intent.getStringExtra(Intents.EXTRA_LEVEL);

            if (!TextUtils.isEmpty(filter)) {
                setSearchText(filter);
            }


            if (!TextUtils.isEmpty(level)) {
                CharSequence[] logLevels = getResources().getStringArray(R.array.log_levels_values);
                int logLevelLimit = ArrayUtil.indexOf(logLevels, level.toUpperCase(Locale.US));

                if (logLevelLimit == -1) {
                    String invalidLevel = getString(R.string.toast_invalid_level, level);
                    Toast.makeText(this, invalidLevel, Toast.LENGTH_LONG).show();
                } else {
                    mLogListAdapter.setLogLevelLimit(logLevelLimit);
                    logLevelChanged();
                }

            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mLogListAdapter.getItemCount() > 0) {
            mLogListAdapter.notifyDataSetChanged();

            // scroll to bottom, since for some reason it always scrolls to the top, which is annoying
            scrollToBottom();
        }

        updateFabStatus();
        applyUserFilters();
        updateKeepScreenOn();

        // the open saved log was deleted or renamed from the saved logs screen
        if (mCurrentlyOpenLog != null && !SaveLogHelper.getFile(mCurrentlyOpenLog).exists()) {
            startMainLog();
        }
    }

    private void updateKeepScreenOn() {
        boolean keepScreenOn = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.matlog_pref_keep_screen_on), false);
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void applyUserFilters() {
        final FilterRepository repository = FilterRepository.get(this);
        final long version = repository.getVersion();
        if (version == mAppliedFiltersVersion) {
            return;
        }
        mAppliedFiltersVersion = version;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FilterSet filterSet = repository.activeFilterSet();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mLogListAdapter.setUserFilters(filterSet);
                        search(mSearchingString);
                    }
                });
            }
        }).start();
    }

    private void restartMainLog() {
        mLogListAdapter.clear();

        startMainLog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        doAfterInitialMessage(intent);

        if (isViewIntent(intent)) {
            importAndOpen(intent);
            return;
        }

        // launched from the widget or notification
        if (intent != null && !Intents.ACTION_LAUNCH.equals(intent.getAction()) && intent.hasExtra("filename")) {
            String filename = intent.getStringExtra("filename");
            openLogFile(filename);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        log.d("onActivityResult()");

        // preferences may have changed
        PreferenceHelper.clearCache();

        mCollapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(getApplicationContext());

        boolean filterOnSearch = LogSearch.isFilterOnSearch(this);
        if (filterOnSearch != mFilterOnSearch) {
            mFilterOnSearch = filterOnSearch;
            search(mSearchingString);
        }


        if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK) {
            onSettingsActivityResult(data);
        }
        mLogListAdapter.notifyDataSetChanged();
        updateUiForFilename();
    }

    private void onSettingsActivityResult(final Intent data) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (data.hasExtra("bufferChanged") && data.getBooleanExtra("bufferChanged", false)
                        && mCurrentlyOpenLog == null && !mKernelMode) {
                    // log buffer changed, so update list
                    restartMainLog();
                } else {
                    // settings activity returned - text size might have changed, so update list
                    expandOrCollapseAll(false);
                    mLogListAdapter.notifyDataSetChanged();
                }
            }
        });

    }

    private void startMainLog() {
        resetDisplayedLog(null);
        if (mLogListAdapter != null) {
            mLogListAdapter.clear();
        }
        mFirstLinesReceived = false;
        showProgressBar();
        mLogcatSession.start();
        updateFabStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        log.d("onPause() called");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.d("onDestroy() called");

        mLogcatSession.stop();
    }

    private MatrixCursor suggestionsCursor(CharSequence query) {
        final MatrixCursor c = new MatrixCursor(new String[]{BaseColumns._ID, "suggestion"});
        List<String> suggestionsForQuery = getSuggestionsForQuery(query == null ? null : query.toString());
        for (int i = 0, suggestionsForQuerySize = suggestionsForQuery.size(); i < Math.min(suggestionsForQuerySize, 8); i++) {
            String suggestion = suggestionsForQuery.get(i);
            c.addRow(new Object[]{i, suggestion});
        }
        return c;
    }

    private List<String> getSuggestionsForQuery(String query) {
        List<String> suggestions = new ArrayList<>(mSearchSuggestionsSet);
        Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);
        List<String> actualSuggestions = new ArrayList<>();
        if (query != null) {
            for (String suggestion : suggestions) {
                if (suggestion.toLowerCase().startsWith(query.toLowerCase())) {
                    actualSuggestions.add(suggestion);
                }
            }
        }
        return actualSuggestions;
    }

    @Override
    public void onBackPressed() {
        if (mSelecting) {
            finishSelection();
        } else if (isSearchOpen()) {
            closeSearch();
        } else if (mCurrentlyOpenLog != null) {
            startMainLog();
        } else if (mKernelMode) {
            setKernelMode(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showingMainLog = mLogcatSession.isRunning();
        /*MenuItem saveLogMenuItem = menu.findItem(R.id.menu_save_log);

        saveLogMenuItem.setEnabled(showingMainLog);
        saveLogMenuItem.setVisible(showingMainLog);

        boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(getApplicationContext(), LogcatRecordingService.class);

        MenuItem recordMenuItem = menu.findItem(R.id.menu_record_log);

        recordMenuItem.setEnabled(!recordingInProgress);
        recordMenuItem.setVisible(!recordingInProgress);*/

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        return true;
    }

    private void setUpFindBar() {
        mFindBar = findViewById(R.id.find_bar);
        mFindBar.setNavigationOnClickListener(v -> closeSearch());
        mFindBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_more) {
                LogcatOverflowMenu.showFind(mFindBar, R.id.menu_more, mSearchRegex, this::onFindMenuItemSelected);
                return true;
            }
            return onOptionsItemSelected(item);
        });
        mFindText = findViewById(R.id.find_text);
        mFindText.setAdapter(mSearchSuggestionsAdapter);
        mFindText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!mDynamicallyEnteringSearchText) {
                    log.d("filtering: %s", s);
                    search(s.toString());
                }
                mDynamicallyEnteringSearchText = false;
            }
        });
        mFindText.setOnEditorActionListener((v, actionId, event) -> {
            mFindText.dismissDropDown();
            setKeyboardVisible(false);
            return true;
        });
    }

    private boolean isSearchOpen() {
        return mFindBar.getVisibility() == View.VISIBLE;
    }

    private void openSearch(boolean showKeyboard) {
        if (mSelecting) {
            return;
        }
        mSearchBar.setVisibility(View.GONE);
        mFindBar.setVisibility(View.VISIBLE);
        updateSearchUi();
        if (showKeyboard) {
            setKeyboardVisible(true);
        }
    }

    private void closeSearch() {
        if (!isSearchOpen()) {
            return;
        }
        setKeyboardVisible(false);
        mFindText.dismissDropDown();
        mFindText.setText(null);
        mFindBar.setVisibility(View.GONE);
        mSearchBar.setVisibility(mSelecting ? View.GONE : View.VISIBLE);
    }

    private void setKeyboardVisible(boolean visible) {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (visible) {
            mFindText.requestFocus();
            imm.showSoftInput(mFindText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            mFindText.clearFocus();
            imm.hideSoftInputFromWindow(mFindText.getWindowToken(), 0);
        }
    }

    private void updateSearchUi() {
        if (mFindBar == null || mLogSearch == null) {
            return;
        }
        Menu menu = mFindBar.getMenu();
        TextView countView = (TextView) menu.findItem(R.id.menu_search_count).getActionView();
        int hitCount = mLogSearch.getHitCount();
        if (!mLogSearch.isActive()) {
            countView.setText(null);
        } else if (mLogSearch.isInvalid()) {
            countView.setText(R.string.matlog_search_invalid);
        } else if (mLogSearch.getCurrentIndex() >= 0) {
            countView.setText(getString(R.string.matlog_search_position, mLogSearch.getCurrentIndex() + 1, hitCount));
        } else {
            countView.setText(String.valueOf(hitCount));
        }
        countView.setTextColor(getAttrColor(mLogSearch.isInvalid()
                ? R.attr.colorError : R.attr.colorOnSurfaceVariant));
        menu.findItem(R.id.menu_search_previous).setEnabled(hitCount > 0);
        menu.findItem(R.id.menu_search_next).setEnabled(hitCount > 0);
    }

    private void refreshSearchHits() {
        if (mLogSearch == null || !mLogSearch.isActive()) {
            return;
        }
        mLogSearch.update(mLogListAdapter.getObjects());
        updateSearchUi();
    }

    private void moveToSearchHit(boolean next) {
        RecyclerView list = findViewById(R.id.list);
        LinearLayoutManager layoutManager = (LinearLayoutManager) list.getLayoutManager();
        List<LogLine> lines = mLogListAdapter.getObjects();
        int oldPosition = mLogSearch.currentPosition();
        int position = next
                ? mLogSearch.next(lines, layoutManager.findFirstVisibleItemPosition())
                : mLogSearch.previous(lines, layoutManager.findLastVisibleItemPosition());
        if (position < 0) {
            return;
        }
        mAutoscrollToBottom = false;
        layoutManager.scrollToPositionWithOffset(position, list.getHeight() / 3);
        if (oldPosition >= 0) {
            mLogListAdapter.notifyItemChanged(oldPosition);
        }
        mLogListAdapter.notifyItemChanged(position);
        updateSearchUi();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_log_level:
                showLogLevelDialog();
                return true;
            case R.id.menu_open_log:
                startActivity(SavedLogsActivity.intent(this));
                return true;
            case R.id.menu_save_log:
                showSaveLogDialog();
                return true;
            case R.id.menu_record_log:
                showRecordLogDialog();
                return true;
            case R.id.menu_send_log_zip:
                showSendLogDialog();
                return true;
            case R.id.menu_save_log_zip:
                showSaveLogZipDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_settings:
                startSettingsActivity();
                return true;
            case R.id.menu_filters:
                startActivity(FiltersActivity.intent(this));
                return true;
            case R.id.menu_search_previous:
                moveToSearchHit(false);
                return true;
            case R.id.menu_search_next:
                moveToSearchHit(true);
                return true;
        }
        return false;
    }

    private void onFindMenuItemSelected(int itemId) {
        if (itemId == R.id.menu_search_regex) {
            mSearchRegex = !mSearchRegex;
            search(mSearchingString);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item, LogLine logLine) {
        if (logLine != null) {
            switch (item.getItemId()) {
                case CONTEXT_MENU_COPY_ID:
                    CopyLogLineSheet.show(this, logLine);
                    return true;
                case CONTEXT_MENU_INCLUDE_FILTER:
                case CONTEXT_MENU_EXCLUDE_FILTER:
                    startActivity(FiltersActivity.intentForLine(this, logLine.getTag(), logLine.getPackageName(),
                            item.getItemId() == CONTEXT_MENU_INCLUDE_FILTER));
                    return true;
                case CONTEXT_MENU_FILTER_ID:

                    if (logLine.getProcessId() == -1) {
                        // invalid line
                        return false;
                    }

                    showSearchByDialog(logLine);
                    return true;
                case CONTEXT_MENU_EXPAND_ALL:
                    expandOrCollapseAll(true);
                    return true;
                case CONTEXT_MENU_CLEAR:
                    clearLog();
                    return true;
                case CONTEXT_MENU_SELECT:
                    toggleSelection(logLine);
                    return true;
            }
        }
        return false;
    }

    @Override
    public void onClick(final View itemView, final LogLine logLine) {
        if (mSelection.isActive()) {
            toggleSelection(logLine);
            return;
        }
        logLine.setExpanded(!logLine.isExpanded());
        mLogListAdapter.notifyItemChanged(((RecyclerView) findViewById(R.id.list)).getChildAdapterPosition(itemView));
    }

    @Override
    public boolean onLongClick(View itemView, LogLine logLine) {
        if (!mSelection.isActive()) {
            return false;
        }
        toggleSelection(logLine);
        return true;
    }

    private void toggleSelection(LogLine logLine) {
        mSelection.toggle(logLine);
        mLogListAdapter.notifyDataSetChanged();
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        if (!mSelection.isActive()) {
            finishSelection();
            return;
        }
        if (!mSelecting) {
            startSelection();
        }
        int count = mSelection.getCount();
        mSelectionBar.setTitle(getResources().getQuantityString(
                R.plurals.matlog_selected_count, count, count));
    }

    private void setUpSelectionBar() {
        mSelectionBar = findViewById(R.id.selection_bar);
        mSelectionBar.setNavigationOnClickListener(v -> finishSelection());
        mSelectionBar.setOnMenuItemClickListener(item -> onSelectionItemClicked(item.getItemId()));
    }

    // the selection bar takes the place of the search bar
    private void startSelection() {
        mSelecting = true;
        if (isSearchOpen()) {
            setKeyboardVisible(false);
            mFindBar.setVisibility(View.GONE);
        }
        mSearchBar.setVisibility(View.GONE);
        mSelectionBar.setVisibility(View.VISIBLE);
        // keep the list still while picking lines
        if (mLogcatSession.isRunning() && !mLogcatSession.isPaused()) {
            mLogcatSession.pause();
            mResumeAfterSelection = true;
            updateFabStatus();
        }
    }

    private void finishSelection() {
        if (!mSelecting) {
            return;
        }
        mSelecting = false;
        mSelectionBar.setVisibility(View.GONE);
        if (mSearchingString != null && !mSearchingString.isEmpty()) {
            mFindBar.setVisibility(View.VISIBLE);
        } else {
            mSearchBar.setVisibility(View.VISIBLE);
        }
        mSelection.clear();
        mLogListAdapter.notifyDataSetChanged();
        if (mResumeAfterSelection) {
            mResumeAfterSelection = false;
            if (mLogcatSession.isRunning()) {
                mLogcatSession.resume();
            }
            updateFabStatus();
        }
    }

    private String selectedText() {
        return LogSelection.toText(mSelection.selectedIn(mLogListAdapter.getTrueValues()));
    }

    private boolean onSelectionItemClicked(int itemId) {
        switch (itemId) {
            case R.id.menu_selection_select_all:
                mSelection.toggleAll(mLogListAdapter.getObjects());
                mLogListAdapter.notifyDataSetChanged();
                onSelectionChanged();
                return true;
            case R.id.menu_selection_copy:
                LogTextActions.copy(this, selectedText());
                finishSelection();
                return true;
            case R.id.menu_selection_share:
                LogTextActions.share(this, selectedText());
                finishSelection();
                return true;
            case R.id.menu_selection_export:
                mPendingSelectionExport = selectedText();
                mExportSelectionLauncher.launch(LogExporter.textFilename());
                finishSelection();
                return true;
            case R.id.menu_selection_extended_copy:
                ExtendedCopyActivity.start(this, selectedText());
                finishSelection();
                return true;
            case R.id.menu_more:
                LogcatOverflowMenu.showSelection(mSelectionBar, R.id.menu_more, this::onSelectionItemClicked);
                return true;
        }
        return false;
    }

    private void showSearchByDialog(final LogLine logLine) {
        final String tag = logLine.getTag();
        final int pid = logLine.getProcessId();
        CharSequence[] items = {
                getString(R.string.matlog_search_by_item, getString(R.string.filter_choice_tag), tag),
                getString(R.string.matlog_search_by_item, getString(R.string.filter_choice_pid), Integer.toString(pid)),
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_choice)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        setSearchText(SearchCriteria.TAG_KEYWORD + (tag.contains(" ") ? '"' + tag + '"' : tag));
                    } else {
                        setSearchText(SearchCriteria.PID_KEYWORD + pid);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRecordLogDialog() {
        // start up the dialog-like activity
        String[] suggestions = ArrayUtil.toArray(new ArrayList<>(mSearchSuggestionsSet), String.class);

        Intent intent = new Intent(LogcatActivity.this, RecordLogDialogActivity.class);
        intent.putExtra(RecordLogDialogActivity.EXTRA_QUERY_SUGGESTIONS, suggestions);

        startActivity(intent);
    }

    private void showFiltersDialog() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e("t", "Started thread");
                final List<FilterItem> filters = new ArrayList<>();

                CatlogDBHelper dbHelper = null;
                try {
                    dbHelper = new CatlogDBHelper(LogcatActivity.this);
                    filters.addAll(dbHelper.findFilterItems());
                } finally {
                    if (dbHelper != null) {
                        dbHelper.close();
                    }
                }

                Collections.sort(filters);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final FilterAdapter filterAdapter = new FilterAdapter(LogcatActivity.this, filters);
                        ListView view = new ListView(LogcatActivity.this);
                        view.setAdapter(filterAdapter);
                        view.setDivider(null);
                        view.setDividerHeight(0);
                        View footer = getLayoutInflater().inflate(R.layout.list_header_add_filter, view, false);
                        view.addFooterView(footer);

                        final AlertDialog dialog = new MaterialAlertDialogBuilder(LogcatActivity.this)
                                .setTitle(R.string.matlog_saved_searches)
                                .setMessage(R.string.message_filters)
                                .setView(view)
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();

                        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                if (position == parent.getCount() - 1) {
                                    showAddFilterDialog(filterAdapter);
                                } else {
                                    // load filter
                                    String text = filterAdapter.getItem(position).getText();
                                    setSearchText(text);
                                    dialog.dismiss();
                                }
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void showAddFilterDialog(final FilterAdapter filterAdapter) {

        // show a popup to add a new filter text
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_new_filter, null, false);
        final AutoCompleteTextView editText = v.findViewById(R.id.filter_text);

        // show suggestions as the user types
        List<String> suggestions = new ArrayList<>(mSearchSuggestionsSet);
        SortedFilterArrayAdapter<String> suggestionAdapter = new SortedFilterArrayAdapter<>(
                this, R.layout.list_item_dropdown, suggestions);
        editText.setAdapter(suggestionAdapter);

        final AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_filter)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleNewFilterText(editText.getText().toString(), filterAdapter);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setView(v)
                .create();

        // when 'Done' is clicked (i.e. enter button), do the same as when "OK" is clicked
        editText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    handleNewFilterText(editText.getText().toString(), filterAdapter);

                    alertDialog.dismiss();
                    return true;
                }
                return false;
            }
        });

        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        editText.requestFocus();
        alertDialog.show();
    }

    protected void handleNewFilterText(String text, final FilterAdapter filterAdapter) {
        final String trimmed = text.trim();
        if (!TextUtils.isEmpty(trimmed)) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    CatlogDBHelper dbHelper = null;
                    FilterItem item = null;
                    try {
                        dbHelper = new CatlogDBHelper(LogcatActivity.this);
                        item = dbHelper.addFilter(trimmed);
                    } finally {
                        if (dbHelper != null) {
                            dbHelper.close();
                        }
                    }

                    final FilterItem finalItem = item;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalItem != null) { // null indicates duplicate
                                filterAdapter.add(finalItem);
                                filterAdapter.sort(FilterItem.DEFAULT_COMPARATOR);
                                filterAdapter.notifyDataSetChanged();

                                addToAutocompleteSuggestions(trimmed);
                            }
                        }
                    });

                }
            }).start();
        }
    }

    private boolean onBottomMenuItemSelected(int itemId) {
        switch (itemId) {
            case R.id.menu_more:
                showOverflowMenu();
                return true;
            case R.id.menu_log_level:
                showLogLevelDialog();
                return true;
            case R.id.menu_open_log:
                startActivity(SavedLogsActivity.intent(LogcatActivity.this));
                return true;
            case R.id.menu_save_log:
                showSaveLogDialog();
                return true;
            case R.id.menu_export_log:
                mExportLogLauncher.launch(mKernelMode
                        ? SaveLogHelper.createKernelLogFilename() : LogExporter.textFilename());
                return true;
            case R.id.menu_kernel_log:
                setKernelMode(!mKernelMode);
                return true;
            case R.id.menu_pstore:
                startActivity(PstoreActivity.intent(LogcatActivity.this));
                return true;
            case R.id.menu_record_log:
                showRecordLogDialog();
                return true;
            case R.id.menu_send_log_zip:
                showSendLogDialog();
                return true;
            case R.id.menu_save_log_zip:
                showSaveLogZipDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_crashes:
                startActivity(CrashesActivity.intent(LogcatActivity.this));
                return true;
            case R.id.menu_settings:
                startSettingsActivity();
                return true;
            case R.id.menu_filters:
                startActivity(FiltersActivity.intent(LogcatActivity.this));
                return true;
            case R.id.menu_saved_searches:
                showFiltersDialog();
                return true;
        }
        return false;
    }

    private void showOverflowMenu() {
        LogcatOverflowMenu.showMain(findViewById(R.id.bottomAppBar), R.id.menu_more,
                DmesgReader.isAvailable(), mKernelMode, PstoreReader.isAvailable(),
                this::onBottomMenuItemSelected);
    }

    private void startSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }

    private void expandOrCollapseAll(boolean change) {

        mCollapsedMode = change != mCollapsedMode;

        int oldFirstVisibleItem = ((LinearLayoutManager) ((RecyclerView) findViewById(R.id.list)).getLayoutManager()).findFirstVisibleItemPosition();

        for (LogLine logLine : mLogListAdapter.getTrueValues()) {
            if (logLine != null) {
                logLine.setExpanded(!mCollapsedMode);
            }
        }

        mLogListAdapter.notifyDataSetChanged();

        // ensure that we either stay autoscrolling at the bottom of the list...

        if (mAutoscrollToBottom) {

            scrollToBottom();

            // ... or that whatever was the previous first visible item is still the current first
            // visible item after expanding/collapsing

        } else if (oldFirstVisibleItem != -1) {

            ((RecyclerView) findViewById(R.id.list)).scrollToPosition(oldFirstVisibleItem);
        }

        supportInvalidateOptionsMenu();
    }

    protected void verifyDelete(final CharSequence[] filenameArray,
                                final boolean[] checkedItems, final DialogInterface parentDialog) {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        int deleteCount = 0;

        for (boolean checkedItem : checkedItems) {
            if (checkedItem) {
                deleteCount++;
            }
        }


        final int finalDeleteCount = deleteCount;

        if (finalDeleteCount > 0) {

            builder.setTitle(R.string.delete_saved_log)
                    .setMessage(getResources().getQuantityString(R.plurals.are_you_sure, finalDeleteCount, finalDeleteCount))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // ok, delete

                            for (int i = 0; i < checkedItems.length; i++) {
                                if (checkedItems[i]) {
                                    SaveLogHelper.deleteLogIfExists(filenameArray[i].toString());
                                }
                            }

                            String toastText = getResources().getQuantityString(R.plurals.files_deleted, finalDeleteCount, finalDeleteCount);
                            Toast.makeText(LogcatActivity.this, toastText, Toast.LENGTH_SHORT).show();

                            dialog.dismiss();
                            parentDialog.dismiss();

                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        }


    }

    private View inflateDeviceInfoView() {
        @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.dialog_send_log, null, false);
        CheckBox checkBox = view.findViewById(android.R.id.checkbox);
        checkBox.setChecked(PreferenceHelper.getIncludeDeviceInfoPreference(this));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceHelper.setIncludeDeviceInfoPreference(LogcatActivity.this, isChecked));
        return view;
    }

    private void showSendLogDialog() {
        final View view = inflateDeviceInfoView();
        final CheckBox checkBox = view.findViewById(android.R.id.checkbox);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_log)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        sendLogToTargetApp(checkBox.isChecked(), false))
                .show();
    }

    private void showSaveLogZipDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_log_zip)
                .setMessage(R.string.matlog_save_zip_summary)
                .setView(inflateDeviceInfoView())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        mExportZipLauncher.launch(LogExporter.zipFilename()))
                .show();
    }

    protected void sendLogToTargetApp(final boolean includeDeviceInfo, final boolean includeDmesg) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mCurrentlyOpenLog == null || includeDeviceInfo || includeDmesg) {
                            showProgressBar();
                        }
                    }
                });
                final SendLogDetails sendLogDetails = getSendLogDetails(includeDeviceInfo, includeDmesg);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        startChooser(LogcatActivity.this, sendLogDetails.getSubject(), sendLogDetails.getBody(),
                                sendLogDetails.getAttachmentType(), sendLogDetails.getAttachment());
                        hideProgressBar();
                    }
                });
            }
        }).start();

    }

    private void exportLogZip(Uri uri) {
        final boolean includeDeviceInfo = PreferenceHelper.getIncludeDeviceInfoPreference(this);
        final String openLog = mCurrentlyOpenLog;
        LogExporter.export(this, uri, out ->
                SaveLogHelper.writeZip(out, collectLogFiles(openLog, includeDeviceInfo)));
    }

    @WorkerThread
    private SendLogDetails getSendLogDetails(boolean includeDeviceInfo, boolean includeDmesg) {
        SendLogDetails sendLogDetails = new SendLogDetails();
        StringBuilder body = new StringBuilder();

        List<File> files = new ArrayList<>();
        SaveLogHelper.cleanTemp(this);
        String logBasename = SaveLogHelper.createLogBasename();

        if (mCurrentlyOpenLog != null) { // use saved log file
            files.add(SaveLogHelper.getFile(mCurrentlyOpenLog));
        } else { // create a temp file to hold the current, unsaved log
            File tempLogFile = SaveLogHelper.saveTemporaryFile(this,
                    logBasename + ".txt", null, getCurrentLogAsListOfStrings());
            files.add(tempLogFile);
        }

        if (includeDeviceInfo) {
            // include device info
            String deviceInfo = BuildHelper.getBuildInformationAsString();
            // or create as separate file called device.txt
            File tempFile = SaveLogHelper.saveTemporaryFile(this,
                    SaveLogHelper.TEMP_DEVICE_INFO_FILENAME, deviceInfo, null);
            files.add(tempFile);
        }

        if (includeDmesg) {
            File tempDmsgFile = SaveLogHelper.saveTemporaryFile(this,
                    SaveLogHelper.TEMP_DMESG_FILENAME, null, DmesgHelper.getDmsg());
            files.add(tempDmsgFile);
        }

        sendLogDetails.setBody(body.toString());
        sendLogDetails.setSubject(getString(R.string.subject_log_report));

        // either zip up multiple files or just attach the one file
        switch (files.size()) {
            case 0: // no attachments
                sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.None);
                break;
            case 1: // one plaintext file attachment
                sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.Text);
                sendLogDetails.setAttachment(files.get(0));
                break;
            default: // 2 files - need to zip them up
                File zipFile = SaveLogHelper.saveTemporaryZipFile(this, logBasename + ".zip", files);

                sendLogDetails.setSubject(zipFile.getName());
                sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.Zip);
                sendLogDetails.setAttachment(zipFile);
                break;
        }

        return sendLogDetails;
    }

    @WorkerThread
    private List<File> collectLogFiles(String openLog, boolean includeDeviceInfo) {
        List<File> files = new ArrayList<>();
        SaveLogHelper.cleanTemp(this);

        files.add(SaveLogHelper.saveTemporaryFile(this,
                SaveLogHelper.TEMP_LOGCAT_FILENAME, null, LogcatReader.dumpAll(this)));

        List<CharSequence> dmesg = DmesgHelper.getDmsg();
        if (!dmesg.isEmpty()) {
            files.add(SaveLogHelper.saveTemporaryFile(this, SaveLogHelper.TEMP_DMESG_FILENAME, null, dmesg));
        }

        for (PstoreFile pstoreFile : PstoreReader.list()) {
            List<CharSequence> pstoreLines = new ArrayList<>(PstoreReader.read(pstoreFile));
            if (!pstoreLines.isEmpty()) {
                files.add(SaveLogHelper.saveTemporaryFile(this,
                        "pstore-" + pstoreFile.getName() + ".txt", null, pstoreLines));
            }
        }

        // a zip can't hold two entries with the same name
        if (openLog != null && !openLog.equals(SaveLogHelper.TEMP_LOGCAT_FILENAME)
                && !openLog.equals(SaveLogHelper.TEMP_DMESG_FILENAME)) {
            files.add(SaveLogHelper.getFile(openLog));
        }

        if (includeDeviceInfo) {
            files.add(SaveLogHelper.saveTemporaryFile(this,
                    SaveLogHelper.TEMP_DEVICE_INFO_FILENAME, BuildHelper.getBuildInformationAsString(), null));
        }
        return files;
    }

    private List<CharSequence> getCurrentLogAsListOfStrings() {

        List<CharSequence> result = new ArrayList<>(mLogListAdapter.getItemCount());

        for (int i = 0; i < mLogListAdapter.getItemCount(); i++) {
            result.add(mLogListAdapter.getItem(i).getOriginalLine());
        }

        return result;
    }

    private CharSequence getCurrentLogAsCharSequence() {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < mLogListAdapter.getItemCount(); i++) {
            stringBuilder.append(mLogListAdapter.getItem(i).getOriginalLine()).append('\n');
        }

        return stringBuilder;
    }

    private void showSaveLogDialog() {
        DialogHelper.showFilenameSuggestingDialog(this, null, this::saveLog, R.string.save_log,
                mKernelMode ? SaveLogHelper.createKernelLogFilename() : DialogHelper.createLogFilename());
    }

    private void saveLog(final String filename) {

        // do in background to avoid jankiness

        final List<CharSequence> logLines = getCurrentLogAsListOfStrings();

        new Thread(new Runnable() {
            @Override
            public void run() {
                SaveLogHelper.deleteLogIfExists(filename);
                final boolean saved = SaveLogHelper.saveLog(logLines, filename);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (saved) {
                            Toast.makeText(getApplicationContext(), R.string.log_saved, Toast.LENGTH_SHORT).show();
                            openLogFile(filename);
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.unable_to_save_log, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();

    }

    private void openLogFile(final String filename) {

        // do in background to avoid jank

        final AsyncTask<Void, Void, List<LogLine>> openFileTask = new AsyncTask<Void, Void, List<LogLine>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                resetDisplayedLog(filename);

                showProgressBar();
            }

            @Override
            protected List<LogLine> doInBackground(Void... params) {

                // remove any lines at the beginning if necessary
                final int maxLines = PreferenceHelper.getDisplayLimitPreference(LogcatActivity.this);
                SavedLog savedLog = SaveLogHelper.openLog(filename, maxLines);
                List<String> lines = savedLog.getLogLines();
                List<LogLine> logLines = new ArrayList<>();
                for (String line : lines) {
                    logLines.add(LogLine.newLogLine(line, !mCollapsedMode));
                }

                // notify the user if the saved file was truncated
                if (savedLog.isTruncated()) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            String toastText = getResources().getQuantityString(R.plurals.toast_log_truncated, maxLines, maxLines);
                            Toast.makeText(LogcatActivity.this, toastText, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                return logLines;
            }

            @Override
            protected void onPostExecute(List<LogLine> logLines) {
                super.onPostExecute(logLines);
                hideProgressBar();

                for (LogLine logLine : logLines) {
                    mLogListAdapter.addWithFilter(logLine, "", false);
                    addToAutocompleteSuggestions(logLine);

                }
                mLogListAdapter.notifyDataSetChanged();
                refreshSearchHits();

                // scroll to bottom
                scrollToBottom();
            }
        };

        mLogcatSession.stop();
        openFileTask.execute((Void) null);
    }

    void hideProgressBar() {
        findViewById(R.id.progress_bar).setVisibility(View.GONE);
    }

    private void showProgressBar() {
        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
    }


    public void resetDisplayedLog(String filename) {
        finishSelection();
        mLogListAdapter.clear();
        refreshSearchHits();
        mCurrentlyOpenLog = filename;
        if (filename != null && mKernelMode) {
            mKernelMode = false;
            mLogcatSession.setKernel(false);
        }
        mCollapsedMode = !PreferenceHelper.getExpandedByDefaultPreference(getApplicationContext());
        addFiltersToSuggestions(); // filters are what initial populate the suggestions
        updateUiForFilename();
        resetFilter();
    }

    private void updateUiForFilename() {
        boolean logFileMode = mCurrentlyOpenLog != null;

        String hint = logFileMode ? getString(R.string.matlog_search_hint_file, mCurrentlyOpenLog)
                : getString(mKernelMode ? R.string.matlog_search_hint_kernel : R.string.matlog_search_hint_log);
        mSearchBar.setHint(hint);
        mFindText.setHint(hint);
        boolean showBack = logFileMode || mKernelMode;
        mSearchBar.setNavigationIcon(showBack ? R.drawable.ic_arrow_back : R.drawable.ic_search);
        mSearchBar.setNavigationContentDescription(showBack ? R.string.matlog_back : R.string.search);
        mSearchBar.setNavigationOnClickListener(v -> {
            if (showBack) {
                onBackPressed();
            } else {
                openSearch(true);
            }
        });

        Menu bottomMenu = ((BottomAppBar) findViewById(R.id.bottomAppBar)).getMenu();
        // recordings only cover logcat
        bottomMenu.findItem(R.id.menu_record_log).setVisible(!mKernelMode);
        supportInvalidateOptionsMenu();
    }

    private void resetFilter() {
        String defaultLogLevel = Character.toString(PreferenceHelper.getDefaultLogLevelPreference(this));
        CharSequence[] logLevels = getResources().getStringArray(R.array.log_levels_values);
        int logLevelLimit = ArrayUtil.indexOf(logLevels, defaultLogLevel);
        mLogListAdapter.setLogLevelLimit(logLevelLimit);
        logLevelChanged();
    }

    private void showLogLevelDialog() {
        String[] logLevels = DialogHelper.logLevelLabels(this);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        builder.setTitle(R.string.log_level)
                .setNegativeButton(android.R.string.cancel, null)
                .setSingleChoiceItems(logLevels, mLogListAdapter.getLogLevelLimit(), new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLogListAdapter.setLogLevelLimit(which);
                        logLevelChanged();
                        dialog.dismiss();

                    }
                });

        builder.show();
    }

    private void setUpAdapter() {

        mLogListAdapter = new LogLineAdapter();
        mLogListAdapter.setClickListener(this);
        mLogListAdapter.setSelection(mSelection);
        mFilterOnSearch = LogSearch.isFilterOnSearch(this);
        mLogSearch = new LogSearch(
                getAttrColor(R.attr.colorTertiaryContainer), getAttrColor(R.attr.colorOnTertiaryContainer),
                getAttrColor(R.attr.colorPrimary), getAttrColor(R.attr.colorOnPrimary));
        mLogListAdapter.setSearch(mLogSearch);

        ((RecyclerView) findViewById(R.id.list)).setAdapter(mLogListAdapter);

        ((RecyclerView) findViewById(R.id.list)).addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);


                // update what the first viewable item is
                final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                // if the bottom of the list isn't visible anymore, then stop autoscrolling
                mAutoscrollToBottom = (layoutManager.findLastCompletelyVisibleItemPosition() == recyclerView.getAdapter().getItemCount() - 1);

                // only hide the fast scroll if we're unpaused and at the bottom of the list
                // TODO:
                //mListView.setFastScrollEnabled(enableFastScroll);

            }
        });

        ((RecyclerView) findViewById(R.id.list)).setHasFixedSize(true);

        new ScrollButtons(findViewById(R.id.list), findViewById(R.id.scroll_top), findViewById(R.id.scroll_bottom),
                () -> {
                    mAutoscrollToBottom = false;
                    ((RecyclerView) findViewById(R.id.list)).scrollToPosition(0);
                },
                () -> {
                    mAutoscrollToBottom = true;
                    scrollToBottom();
                });
    }

    private void setSearchText(String text) {
        // sets the search text without invoking autosuggestions, which are really only useful when typing
        search(text);
        openSearch(false);
        mDynamicallyEnteringSearchText = true;
        mFindText.setText(text, false);
        mFindText.setSelection(mFindText.length());
    }

    private void search(String filterText) {
        mSearchingString = filterText;
        mLogSearch.setQuery(filterText, mSearchRegex);
        mLogListAdapter.setSearchRegex(mSearchRegex);
        Filter filter = mLogListAdapter.getFilter();
        filter.filter(filterQuery(), this);
        updateSearchUi();
    }

    // in highlight mode the search text only marks matches
    private String filterQuery() {
        return mFilterOnSearch ? mSearchingString : "";
    }

    private void updateFabStatus() {
        if (mLogcatSession.isRunning()) {
            if (!mLogcatSession.isPaused()) {
                mFab.setImageResource(R.drawable.ic_pause_white_24dp);
            } else {
                mFab.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            }
        }
    }

    @Override
    public void onFilterComplete(int count) {
        // always scroll to the bottom when searching
        ((RecyclerView) findViewById(R.id.list)).scrollToPosition(count - 1);
        refreshSearchHits();
    }


    private void logLevelChanged() {
        search(mSearchingString);
    }


    private void addToAutocompleteSuggestions(LogLine logLine) {
        // add the tags to the autocompletetextview

        if (!StringUtil.isEmptyOrWhitespaceOnly(logLine.getTag())) {
            String trimmed = logLine.getTag().trim();
            addToAutocompleteSuggestions(trimmed);
        }
    }

    private void addToAutocompleteSuggestions(String trimmed) {
        if (mSearchSuggestionsSet.size() < MAX_NUM_SUGGESTIONS
                && !mSearchSuggestionsSet.contains(trimmed)) {
            mSearchSuggestionsSet.add(trimmed);
        }
    }

    private void scrollToBottom() {
        ((RecyclerView) findViewById(R.id.list)).scrollToPosition(mLogListAdapter.getItemCount() - 1);
    }

    @Override
    public LogLine createLogLine(String line) {
        return LogLine.newLogLine(line, !mCollapsedMode);
    }

    @Override
    public void onLogLines(List<LogLine> lines) {
        if (!mFirstLinesReceived) {
            mFirstLinesReceived = true;
            hideProgressBar();
        }
        for (LogLine logLine : lines) {
            mLogListAdapter.addWithFilter(logLine, filterQuery(), false);
            addToAutocompleteSuggestions(logLine);
        }

        int maxNumLogLines = PreferenceHelper.getDisplayLimitPreference(this);
        int numItemsToRemove = mLogListAdapter.getTrueValues().size() - maxNumLogLines;
        if (numItemsToRemove > 0) {
            mLogListAdapter.removeFirst(numItemsToRemove);
            if (mSelection.isActive()) {
                mSelection.retainIn(mLogListAdapter.getTrueValues());
                onSelectionChanged();
            }
        } else {
            mLogListAdapter.notifyDataSetChanged();
        }

        refreshSearchHits();

        if (mAutoscrollToBottom) {
            scrollToBottom();
        }
    }

    private void clearLog() {
        if (mCurrentlyOpenLog != null) {
            startMainLog();
            return;
        }
        finishSelection();
        if (mLogListAdapter != null) {
            mLogListAdapter.clear();
            refreshSearchHits();
        }
    }

    private int getAttrColor(int attr) {
        TypedArray ta = obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }
}
