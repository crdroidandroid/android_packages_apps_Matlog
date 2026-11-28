/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.util

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.crdroid.logcat.R
import com.google.android.material.color.ColorRoles
import com.google.android.material.color.MaterialColors

object LogLineAdapterUtil {

    const val LOG_WTF = 100 // arbitrary int to signify 'wtf' log level

    private var cacheKey: Pair<Int, Boolean>? = null
    private val levelRoles = HashMap<Int, ColorRoles>()
    private var tagColors = IntArray(0)

    fun getBackgroundColorForLogLevel(context: Context, logLevel: Int): Int =
        levelRoles(context, logLevel).accent

    fun getForegroundColorForLogLevel(context: Context, logLevel: Int): Int =
        levelRoles(context, logLevel).onAccent

    fun getOrCreateTagColor(context: Context, tag: String?): Int {
        val colors = tagColors(context)
        return colors[Math.abs(tag?.hashCode() ?: 0) % colors.size]
    }

    fun logLevelIsAcceptableGivenLogLevelLimit(logLevel: Int, logLevelLimit: Int): Boolean {
        val minVal = when (logLevel) {
            Log.VERBOSE -> 0
            Log.DEBUG -> 1
            Log.INFO -> 2
            Log.WARN -> 3
            Log.ERROR -> 4
            LOG_WTF -> 5
            else -> return true // e.g. the starting line that says "output of log such-and-such"
        }
        return minVal >= logLevelLimit
    }

    @Synchronized
    private fun levelRoles(context: Context, logLevel: Int): ColorRoles {
        checkTheme(context)
        return levelRoles.getOrPut(logLevel) {
            val seed = ContextCompat.getColor(context, levelSeed(logLevel))
            MaterialColors.getColorRoles(context, MaterialColors.harmonizeWithPrimary(context, seed))
        }
    }

    @Synchronized
    private fun tagColors(context: Context): IntArray {
        checkTheme(context)
        if (tagColors.isEmpty()) {
            tagColors = context.resources.getIntArray(R.array.tag_colors)
                .map { MaterialColors.harmonizeWithPrimary(context, it) }.toIntArray()
        }
        return tagColors
    }

    // derived colors follow the wallpaper and dark mode
    private fun checkTheme(context: Context) {
        val key = MaterialColors.getColor(context, R.attr.colorPrimary, 0) to
            MaterialColors.isColorLight(MaterialColors.getColor(context, android.R.attr.colorBackground, 0))
        if (key != cacheKey) {
            cacheKey = key
            levelRoles.clear()
            tagColors = IntArray(0)
        }
    }

    private fun levelSeed(logLevel: Int) = when (logLevel) {
        Log.DEBUG -> R.color.background_debug
        Log.ERROR -> R.color.background_error
        Log.INFO -> R.color.background_info
        Log.VERBOSE -> R.color.background_verbose
        Log.WARN -> R.color.background_warn
        LOG_WTF -> R.color.background_wtf
        else -> R.color.background_verbose
    }
}
