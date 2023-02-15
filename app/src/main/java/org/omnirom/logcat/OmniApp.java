package org.omnirom.logcat;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public class OmniApp extends Application {
    public static final String NOTIFICATION_CHANNEL_ID = "com.pluscubed.logcat.notification";

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(
                this,
                new DynamicColorsOptions.Builder().setThemeOverlay(R.style.Theme_MatLog_Overlay).build()
        );
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            CharSequence name = getString(R.string.channel_name);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
