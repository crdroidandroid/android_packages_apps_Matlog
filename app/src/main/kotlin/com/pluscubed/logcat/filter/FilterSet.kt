/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.filter

import com.pluscubed.logcat.data.LogLine

class FilterSet(filters: List<UserFilter>) {

    private val including = filters.filter { it.enabled && it.including && it.hasConditions }
    private val excluding = filters.filter { it.enabled && !it.including && it.hasConditions }

    val isEmpty: Boolean
        get() = including.isEmpty() && excluding.isEmpty()

    // excludes win; with any include filter, a line must match one of them
    fun accepts(line: LogLine): Boolean {
        if (excluding.any { it.matches(line) }) return false
        return including.isEmpty() || including.any { it.matches(line) }
    }

    companion object {
        val EMPTY = FilterSet(emptyList())
    }
}
