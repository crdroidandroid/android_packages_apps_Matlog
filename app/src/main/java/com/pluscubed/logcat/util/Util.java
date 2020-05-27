package com.pluscubed.logcat.util;

import android.content.Context;
import android.content.res.TypedArray;

/**
 * Util
 */
public class Util {
    public static int convertDpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density
                + 0.5f);
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }
}
