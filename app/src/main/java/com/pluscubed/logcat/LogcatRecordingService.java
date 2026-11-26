package com.pluscubed.logcat;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.pluscubed.logcat.data.LogLine;
import com.pluscubed.logcat.data.SearchCriteria;
import com.pluscubed.logcat.filter.FilterRepository;
import com.pluscubed.logcat.filter.FilterSet;
import com.pluscubed.logcat.helper.PreferenceHelper;
import com.pluscubed.logcat.helper.SaveLogHelper;
import com.pluscubed.logcat.helper.ServiceHelper;
import com.pluscubed.logcat.helper.WidgetHelper;
import com.pluscubed.logcat.helper.LogcatHelper;
import com.pluscubed.logcat.reader.LogcatReader;
import com.pluscubed.logcat.ui.LogcatActivity;
import com.pluscubed.logcat.util.ArrayUtil;
import com.pluscubed.logcat.util.LogLineAdapterUtil;
import com.pluscubed.logcat.util.UtilLogger;

import org.omnirom.logcat.OmniApp;
import org.omnirom.logcat.R;

import java.io.IOException;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Reads logs.
 *
 * @author nolan
 */
public class LogcatRecordingService extends IntentService {

    public static final String URI_SCHEME = "catlog_recording_service";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_SINCE = "since";
    public static final String EXTRA_QUERY_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";
    private static final String ACTION_STOP_RECORDING = "com.pluscubed.catlog.action.STOP_RECORDING";
    private static final String ACTION_PAUSE_RECORDING = "org.omnirom.logcat.action.PAUSE_RECORDING";
    private static final String ACTION_RESUME_RECORDING = "org.omnirom.logcat.action.RESUME_RECORDING";

    private static UtilLogger log = new UtilLogger(LogcatRecordingService.class);
    private final Object lock = new Object();
    private LogcatReader mReader;
    private boolean mKilled;
    // paused recordings keep logcat running but drop incoming lines
    private volatile boolean mPaused;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.d("onReceive() %s", intent.getAction());

            if (ACTION_PAUSE_RECORDING.equals(intent.getAction())) {
                setPaused(true);
                return;
            }
            if (ACTION_RESUME_RECORDING.equals(intent.getAction())) {
                setPaused(false);
                return;
            }

            // received broadcast to kill service
            killProcess();
            ServiceHelper.stopBackgroundServiceIfRunning(context);
        }
    };

    private Handler handler;

    public LogcatRecordingService() {
        super("AppTrackerService");
    }


    @Override
    public void onCreate() {
        super.onCreate();
        log.d("onCreate()");

        IntentFilter intentFilter = new IntentFilter(ACTION_STOP_RECORDING);
        intentFilter.addAction(ACTION_PAUSE_RECORDING);
        intentFilter.addAction(ACTION_RESUME_RECORDING);
        intentFilter.addDataScheme(URI_SCHEME);

        ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        handler = new Handler(Looper.getMainLooper());
    }


    private void initializeReader(Intent intent) {
        try {
            String since = intent.getStringExtra(EXTRA_SINCE);
            if (since == null) {
                since = LogcatHelper.currentTimestamp();
            }
            mReader = LogcatReader.open(this, since);
            if (!mKilled) {
                makeToast(R.string.log_recording_started, Toast.LENGTH_SHORT);
            }
        } catch (IOException e) {
            log.d(e, "");
        }

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        log.d("onDestroy()");
        killProcess();

        unregisterReceiver(receiver);

        stopForeground(true);

        WidgetHelper.updateWidgets(getApplicationContext(), false);
    }

    // This is the old onStart method that will be called on the pre-2.0
    // platform.
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        log.d("onStart()");
        handleCommand();
    }

    private void handleCommand() {

        // notify the widgets that we're running
        WidgetHelper.updateWidgets(getApplicationContext());

        startForeground(R.string.notification_title, buildNotification());
    }

    private void setPaused(boolean paused) {
        mPaused = paused;
        getSystemService(NotificationManager.class).notify(R.string.notification_title, buildNotification());
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(getApplicationContext(), LogcatActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), OmniApp.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_record)
                .setTicker(getText(R.string.notification_ticker))
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(getString(mPaused ? R.string.matlog_recording_paused : R.string.notification_title))
                .setContentText(getString(mPaused ? R.string.matlog_recording_paused_summary : R.string.matlog_recording_summary))
                .setContentIntent(contentIntent);

        if (mPaused) {
            builder.addAction(R.drawable.ic_play_arrow_white_24dp, getString(R.string.matlog_resume),
                    controlIntent(ACTION_RESUME_RECORDING, 2));
        } else {
            builder.addAction(R.drawable.ic_pause_white_24dp, getString(R.string.matlog_pause),
                    controlIntent(ACTION_PAUSE_RECORDING, 1));
        }
        builder.addAction(R.drawable.ic_stop, getString(R.string.matlog_stop),
                controlIntent(ACTION_STOP_RECORDING, 3));
        return builder.build();
    }

    private PendingIntent controlIntent(String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        intent.setData(Uri.parse(URI_SCHEME + "://" + action));
        return PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    protected void onHandleIntent(Intent intent) {
        log.d("onHandleIntent()");
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {

        log.d("Starting up %s now with intent: %s", LogcatRecordingService.class.getSimpleName(), intent);

        String filename = intent.getStringExtra(EXTRA_FILENAME);
        String queryText = intent.getStringExtra(EXTRA_QUERY_FILTER);
        String logLevel = intent.getStringExtra(EXTRA_LEVEL);

        SearchCriteria searchCriteria = new SearchCriteria(queryText);

        CharSequence[] logLevels = getResources().getStringArray(R.array.log_levels_values);
        int logLevelLimit = ArrayUtil.indexOf(logLevels, logLevel);

        boolean searchCriteriaWillAlwaysMatch = searchCriteria.isEmpty();
        boolean logLevelAcceptsEverything = logLevelLimit == 0;

        FilterRepository filterRepository = FilterRepository.get(this);
        long filtersVersion = filterRepository.getVersion();
        FilterSet userFilters = filterRepository.activeFilterSet();

        SaveLogHelper.deleteLogIfExists(filename);

        initializeReader(intent);

        StringBuilder stringBuilder = new StringBuilder();

        try {

            String line;
            int lineCount = 0;
            int logLinePeriod = PreferenceHelper.getLogLinePeriodPreference(getApplicationContext());
            while (mReader != null && (line = mReader.readLine()) != null && !mKilled) {

                if (mPaused) {
                    continue;
                }

                if (filterRepository.getVersion() != filtersVersion) {
                    filtersVersion = filterRepository.getVersion();
                    userFilters = filterRepository.activeFilterSet();
                }

                // filter
                if (!searchCriteriaWillAlwaysMatch || !logLevelAcceptsEverything || !userFilters.isEmpty()) {
                    if (!checkLogLine(line, searchCriteria, logLevelLimit, userFilters)) {
                        continue;
                    }
                }

                stringBuilder.append(line).append("\n");

                if (++lineCount % logLinePeriod == 0) {
                    // avoid OutOfMemoryErrors; flush now
                    SaveLogHelper.saveLog(stringBuilder, filename);
                    stringBuilder.delete(0, stringBuilder.length()); // clear
                }
            }
        } catch (IOException e) {
            // ignore
        } finally {
            killProcess();
            log.d("CatlogService ended");

            boolean logSaved = SaveLogHelper.saveLog(stringBuilder, filename);

            if (logSaved) {
                makeToast(R.string.log_saved, Toast.LENGTH_SHORT);
                startLogcatActivityToViewSavedFile(filename);
            } else {
                makeToast(R.string.unable_to_save_log, Toast.LENGTH_LONG);
            }
        }
    }

    private boolean checkLogLine(String line, SearchCriteria searchCriteria, int logLevelLimit, FilterSet userFilters) {
        LogLine logLine = LogLine.newLogLine(line, false);
        return searchCriteria.matches(logLine)
                && LogLineAdapterUtil.logLevelIsAcceptableGivenLogLevelLimit(logLine.getLogLevel(), logLevelLimit)
                && userFilters.accepts(logLine);
    }


    private void startLogcatActivityToViewSavedFile(String filename) {

        // start up the logcat activity if necessary and show the saved file

        Intent targetIntent = new Intent(getApplicationContext(), LogcatActivity.class);
        targetIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.setAction(Intent.ACTION_MAIN);
        targetIntent.putExtra("filename", filename);

        startActivity(targetIntent);

    }


    private void makeToast(final int stringResId, final int toastLength) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LogcatRecordingService.this, stringResId, toastLength).show();
            }
        });

    }

    private void killProcess() {
        if (!mKilled) {
            synchronized (lock) {
                if (!mKilled && mReader != null) {
                    // kill the logcat process
                    mReader.killQuietly();
                    mKilled = true;
                }
            }
        }
    }
}
