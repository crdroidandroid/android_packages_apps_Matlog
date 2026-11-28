/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.data

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

// pid:, tag: and the remaining text are ANDed; the text may be a regex
class SearchCriteria(inputQuery: CharSequence?, regex: Boolean = false) {

    private var pid = -1
    private var tag: String? = null
    private val searchText: String
    private val searchTextAsInt: Int
    private val pattern: Pattern?

    val isInvalid: Boolean

    val isEmpty: Boolean
        get() = pid == -1 && tag.isNullOrEmpty() && searchText.isEmpty()

    init {
        val query = StringBuilder(inputQuery?.toString().orEmpty())

        val pidMatcher = PID_PATTERN.matcher(query)
        if (pidMatcher.find()) {
            pidMatcher.group(1)!!.toIntOrNull()?.let {
                pid = it
                query.replace(pidMatcher.start(), pidMatcher.end(), "")
            }
        }

        val tagMatcher = TAG_PATTERN.matcher(query)
        if (tagMatcher.find()) {
            tag = tagMatcher.group(1)!!.removeSurrounding("\"")
            query.replace(tagMatcher.start(), tagMatcher.end(), "")
        }

        searchText = query.toString().trim()
        searchTextAsInt = if (regex) -1 else searchText.toIntOrNull() ?: -1

        var compiled: Pattern? = null
        var invalid = false
        if (regex && searchText.isNotEmpty()) {
            try {
                compiled = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE)
            } catch (e: PatternSyntaxException) {
                invalid = true
            }
        }
        pattern = compiled
        isInvalid = invalid
    }

    fun matches(logLine: LogLine): Boolean {
        if (isInvalid) return false
        if (pid != -1 && logLine.processId != pid) return false
        if (!tag.isNullOrEmpty() && !containsIgnoreCase(logLine.tag, tag!!)) return false
        return searchText.isEmpty()
                || (searchTextAsInt != -1 && searchTextAsInt == logLine.processId)
                || textFound(logLine.tag)
                || textFound(logLine.packageName)
                || textFound(logLine.logOutput)
    }

    // ranges in the message to highlight
    fun outputRanges(text: CharSequence?): List<IntRange> = textRanges(text)

    // ranges in the tag to highlight, from both tag: and the free text
    fun tagRanges(text: CharSequence?): List<IntRange> {
        val ranges = textRanges(text)
        val tagQuery = tag
        if (text == null || tagQuery.isNullOrEmpty()) return ranges
        return (ranges + plainRanges(text, tagQuery)).sortedBy { it.first }
    }

    private fun textFound(text: String?): Boolean {
        if (text == null) return false
        return pattern?.matcher(text)?.find() ?: containsIgnoreCase(text, searchText)
    }

    private fun textRanges(text: CharSequence?): List<IntRange> {
        if (text.isNullOrEmpty() || searchText.isEmpty() || isInvalid) return emptyList()
        val regex = pattern ?: return plainRanges(text, searchText)
        val ranges = ArrayList<IntRange>()
        val matcher = regex.matcher(text)
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) ranges.add(matcher.start() until matcher.end())
            if (ranges.size >= MAX_RANGES) break
        }
        return ranges
    }

    private fun plainRanges(text: CharSequence, query: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        val string = text.toString()
        var index = indexOfIgnoreCase(string, query, 0)
        while (index >= 0 && ranges.size < MAX_RANGES) {
            ranges.add(index until index + query.length)
            index = indexOfIgnoreCase(string, query, index + query.length)
        }
        return ranges
    }

    companion object {
        const val PID_KEYWORD = "pid:"
        const val TAG_KEYWORD = "tag:"

        private const val MAX_RANGES = 50

        private val PID_PATTERN = Pattern.compile("pid:(\\d+)", Pattern.CASE_INSENSITIVE)
        private val TAG_PATTERN = Pattern.compile("tag:(\"[^\"]+\"|\\S+)", Pattern.CASE_INSENSITIVE)

        private fun containsIgnoreCase(text: String?, query: String): Boolean =
            text != null && indexOfIgnoreCase(text, query, 0) >= 0

        private fun indexOfIgnoreCase(text: String, query: String, from: Int): Int {
            val limit = text.length - query.length
            for (i in from..limit) {
                if (text.regionMatches(i, query, 0, query.length, ignoreCase = true)) return i
            }
            return -1
        }
    }
}
