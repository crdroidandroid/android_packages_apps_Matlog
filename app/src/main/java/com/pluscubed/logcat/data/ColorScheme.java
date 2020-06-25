package com.pluscubed.logcat.data;

import android.content.Context;

import org.omnirom.logcat.R;

import androidx.core.content.ContextCompat;

public enum ColorScheme {
    Default(R.color.main_background_light, R.array.light_theme_colors);

    private int backgroundColorResource;
    private int tagColorsResource;

    ColorScheme(int backgroundColorResource, int tagColorsResource) {
        this.backgroundColorResource = backgroundColorResource;
        this.tagColorsResource = tagColorsResource;
    }

    public int getBackgroundColor(Context context) {
        return ContextCompat.getColor(context,backgroundColorResource);
    }
    public int[] getTagColors(Context context) {
        return context.getResources().getIntArray(tagColorsResource);
    }
}
