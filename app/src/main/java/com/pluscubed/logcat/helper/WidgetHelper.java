package com.pluscubed.logcat.helper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.ArrayMap;
import android.util.SizeF;
import android.widget.RemoteViews;

import com.pluscubed.logcat.LogcatRecordingService;
import com.pluscubed.logcat.RecordingWidgetProvider;
import com.pluscubed.logcat.ui.RecordLogDialogActivity;
import com.pluscubed.logcat.util.UtilLogger;

import java.util.Map;

import org.omnirom.logcat.R;

public class WidgetHelper {

    private static UtilLogger log = new UtilLogger(WidgetHelper.class);
    private static final float WIDE_WIDGET_WIDTH = 120f;

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
        PendingIntent pendingIntent = getPendingIntent(context, appWidgetId);
        Map<SizeF, RemoteViews> layouts = new ArrayMap<>();
        layouts.put(new SizeF(48f, 48f), widgetViews(context, R.layout.widget_recording, serviceRunning, pendingIntent));
        layouts.put(new SizeF(WIDE_WIDGET_WIDTH, 48f),
                widgetViews(context, R.layout.widget_recording_wide, serviceRunning, pendingIntent));
        manager.updateAppWidget(appWidgetId, new RemoteViews(layouts));
    }

    private static RemoteViews widgetViews(Context context, int layout, boolean serviceRunning,
            PendingIntent pendingIntent) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setImageViewResource(R.id.record_badge_image_view,
                serviceRunning ? R.drawable.ic_widget_stop : R.drawable.ic_widget_record);
        views.setInt(R.id.record_badge_image_view, "setBackgroundResource",
                serviceRunning ? R.drawable.bg_widget_button_recording : R.drawable.bg_widget_button_idle);
        views.setContentDescription(R.id.record_badge_image_view,
                context.getString(serviceRunning ? R.string.matlog_stop : R.string.record_log));
        if (layout == R.layout.widget_recording_wide) {
            views.setTextViewText(R.id.record_label, context.getString(
                    serviceRunning ? R.string.widget_status_recording : R.string.start_recording_log));
        }
        views.setOnClickPendingIntent(android.R.id.background, pendingIntent);
        return views;
    }

    private static PendingIntent getPendingIntent(Context context, int appWidgetId) {
        Intent intent = new Intent();
        intent.setClass(context, RecordLogDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.setAction(RecordingWidgetProvider.ACTION_RECORD_OR_STOP);

        return PendingIntent.getActivity(context,
                0 /* no requestCode */, intent,
                PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static int[] findAppWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, RecordingWidgetProvider.class);
        return manager.getAppWidgetIds(widget);
    }

}
