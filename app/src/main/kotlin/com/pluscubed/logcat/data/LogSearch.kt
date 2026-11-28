/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.preference.PreferenceManager
import org.omnirom.logcat.R

// tracks search hits in the displayed list; main thread only
class LogSearch(
    private val hitBackground: Int,
    private val hitForeground: Int,
    private val currentBackground: Int,
    private val currentForeground: Int,
) {

    var criteria: SearchCriteria? = null
        private set

    private val hits = ArrayList<Int>()
    private var firstLine: LogLine? = null
    private var scannedCount = 0
    private var currentLine: LogLine? = null

    var currentIndex = -1
        private set

    val hitCount: Int
        get() = hits.size

    val isActive: Boolean
        get() = criteria != null

    val isInvalid: Boolean
        get() = criteria?.isInvalid == true

    fun setQuery(query: String?, regex: Boolean) {
        val newCriteria = SearchCriteria(query, regex).takeUnless { it.isEmpty }
        criteria = newCriteria
        currentLine = null
        currentIndex = -1
        hits.clear()
        firstLine = null
        scannedCount = 0
    }

    // rescans only appended lines when the head of the list is unchanged
    fun update(lines: List<LogLine>) {
        val search = criteria
        if (search == null || lines.isEmpty()) {
            hits.clear()
            firstLine = null
            scannedCount = 0
            currentIndex = -1
            return
        }
        val from = if (lines[0] === firstLine && lines.size >= scannedCount) {
            scannedCount
        } else {
            hits.clear()
            0
        }
        for (i in from until lines.size) {
            if (search.matches(lines[i])) hits.add(i)
        }
        firstLine = lines[0]
        scannedCount = lines.size
        currentIndex = currentLine?.let { line -> hits.indexOfFirst { lines[it] === line } } ?: -1
        if (currentIndex == -1) currentLine = null
    }

    fun currentPosition(): Int = if (currentIndex >= 0) hits[currentIndex] else -1

    // with no current hit, starts from the edge of what is on screen
    fun next(lines: List<LogLine>, firstVisible: Int): Int {
        if (hits.isEmpty()) return -1
        currentIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % hits.size
        } else {
            hits.indexOfFirst { it >= firstVisible }.takeIf { it >= 0 } ?: 0
        }
        return select(lines)
    }

    fun previous(lines: List<LogLine>, lastVisible: Int): Int {
        if (hits.isEmpty()) return -1
        currentIndex = if (currentIndex >= 0) {
            (currentIndex - 1 + hits.size) % hits.size
        } else {
            hits.indexOfLast { it <= lastVisible }.takeIf { it >= 0 } ?: hits.size - 1
        }
        return select(lines)
    }

    private fun select(lines: List<LogLine>): Int {
        val position = hits[currentIndex]
        currentLine = lines[position]
        return position
    }

    fun highlightOutput(text: String?, position: Int): CharSequence? =
        highlight(text, criteria?.outputRanges(text), position)

    fun highlightTag(text: String?, position: Int): CharSequence? =
        highlight(text, criteria?.tagRanges(text), position)

    private fun highlight(text: String?, ranges: List<IntRange>?, position: Int): CharSequence? {
        if (text == null || ranges.isNullOrEmpty()) return text
        val current = position == currentPosition()
        val spannable = SpannableString(text)
        for (range in ranges) {
            val end = range.last + 1
            spannable.setSpan(
                BackgroundColorSpan(if (current) currentBackground else hitBackground),
                range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(if (current) currentForeground else hitForeground),
                range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    companion object {
        fun isFilterOnSearch(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.matlog_pref_filter_on_search), true)
    }
}
