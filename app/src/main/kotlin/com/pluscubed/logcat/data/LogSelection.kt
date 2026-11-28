/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import java.util.Collections
import java.util.IdentityHashMap

class LogSelection {

    private val selected: MutableSet<LogLine> =
        Collections.newSetFromMap(IdentityHashMap())

    val count: Int
        get() = selected.size

    val isActive: Boolean
        get() = selected.isNotEmpty()

    fun isSelected(line: LogLine): Boolean = line in selected

    fun toggle(line: LogLine) {
        if (!selected.remove(line)) selected.add(line)
    }

    // selects every visible line, or clears the selection if they already are
    fun toggleAll(visible: List<LogLine>) {
        if (visible.isNotEmpty() && visible.all { it in selected }) {
            selected.clear()
        } else {
            selected.addAll(visible)
        }
    }

    fun clear() {
        selected.clear()
    }

    fun retainIn(lines: List<LogLine>) {
        if (selected.isEmpty()) return
        val present: MutableSet<LogLine> = Collections.newSetFromMap(IdentityHashMap())
        present.addAll(lines)
        selected.retainAll(present)
    }

    fun selectedIn(lines: List<LogLine>): List<LogLine> = lines.filter { it in selected }

    companion object {
        fun toText(lines: List<LogLine>): String =
            lines.joinToString("\n") { it.originalLine.orEmpty() }
    }
}
