package com.pluscubed.logcat.helper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.pluscubed.logcat.LogcatRecordingService;
import com.pluscubed.logcat.RecordingWidgetProvider;
import com.pluscubed.logcat.ui.RecordLogDialogActivity;
import com.pluscubed.logcat.util.UtilLogger;

import org.omnirom.logcat.R;

public class WidgetHelper {

    private static UtilLogger log = new UtilLogger(WidgetHelper.class);

    public static void updateWidgets(Context context) {
        int[] appWidgetIds = findAppWidgetIds(context);
        updateWidgets(context, appWidgetIds);
    }

    /**
     * manually tell us if the service is running or not
     */
    public static void updateWidgets(Context context, boolean serviceRunning) {
        int[] appWidgetIds = findAppWidgetIds(context);
        updateWidgets(context, appWidgetIds, serviceRunning);
    }

    public static void updateWidgets(Context context, int[] appWidgetIds) {
        boolean serviceRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
        updateWidgets(context, appWidgetIds, serviceRunning);
    }

    public static void updateWidgets(Context context, int[] appWidgetIds, boolean serviceRunning) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int appWidgetId : appWidgetIds) {
            if (!PreferenceHelper.getWidgetExistsPreference(context, appWidgetId)) {
                // android has a bug that sometimes keeps stale app widget ids around
                log.d("Found stale app widget id %d; skipping...", appWidgetId);
                continue;
            }
            updateWidget(context, manager, appWidgetId, serviceRunning);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, boolean serviceRunning) {
        RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_recording);
        updateViews.setImageViewResource(R.id.record_badge_image_view, serviceRunning ? R.drawable.ic_widget_stop_record : R.drawable.ic_widget_start_record);
        updateViews.setTextViewText(R.id.record_badge_image_text, serviceRunning ?
                context.getResources().getString(R.string.widget_status_recording) :
                context.getResources().getString(R.string.widget_status_stopped));

        PendingIntent pendingIntent = getPendingIntent(context, appWidgetId);
        updateViews.setOnClickPendingIntent(R.id.record_badge_view, pendingIntent);
        manager.updateAppWidget(appWidgetId, updateViews);
    }

    private static PendingIntent getPendingIntent(Context context, int appWidgetId) {
        Intent intent = new Intent();
        intent.setClass(context, RecordLogDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.setAction(RecordingWidgetProvider.ACTION_RECORD_OR_STOP);

        return PendingIntent.getActivity(context,
                0 /* no requestCode */, intent, PendingIntent.FLAG_ONE_SHOT);
    }

    private static int[] findAppWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, RecordingWidgetProvider.class);
        return manager.getAppWidgetIds(widget);
    }

}
