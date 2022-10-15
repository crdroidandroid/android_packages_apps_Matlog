package com.pluscubed.logcat.data;

import android.content.Context;

import org.omnirom.logcat.R;

import androidx.core.content.ContextCompat;

public enum ColorScheme {
    Default(R.array.light_theme_colors);

    private int tagColorsResource;

    ColorScheme(int tagColorsResource) {
        this.tagColorsResource = tagColorsResource;
    }
    public int[] getTagColors(Context context) {
        return context.getResources().getIntArray(tagColorsResource);
    }
}
