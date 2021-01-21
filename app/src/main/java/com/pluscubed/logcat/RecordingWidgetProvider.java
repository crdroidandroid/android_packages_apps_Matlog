package com.pluscubed.logcat;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pluscubed.logcat.helper.DialogHelper;
import com.pluscubed.logcat.helper.PreferenceHelper;
import com.pluscubed.logcat.helper.ServiceHelper;
import com.pluscubed.logcat.helper.WidgetHelper;
import com.pluscubed.logcat.ui.RecordLogDialogActivity;
import com.pluscubed.logcat.util.UtilLogger;

import java.util.Arrays;

import androidx.annotation.NonNull;

public class RecordingWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_RECORD_OR_STOP = "com.pluscubed.logcat.action.RECORD_OR_STOP";
    public static UtilLogger log = new UtilLogger(RecordingWidgetProvider.class);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        log.i("onUpdate() for appWidgetIds %s", Arrays.toString(appWidgetIds));
        log.i("appWidgetIds are %s", Arrays.toString(appWidgetIds));

        // track which widgets were created, since there's a bug in the android system that lets
        // stale app widget ids stick around
        PreferenceHelper.setWidgetExistsPreference(context, appWidgetIds);
        WidgetHelper.updateWidgets(context, appWidgetIds);
    }

    @Override
    public void onReceive(@NonNull final Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        log.i("onReceive(); intent is: %s", intent);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            PreferenceHelper.clearWidgetExistsPreference(context, id);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            PreferenceHelper.remapWidgetExistsPreference(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }
}
