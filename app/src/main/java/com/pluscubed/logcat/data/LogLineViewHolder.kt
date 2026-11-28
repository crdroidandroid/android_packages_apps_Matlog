/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.pluscubed.logcat.ui.LogcatOverflowMenu

class LogLineViewHolder(itemView: View, private val clickListener: OnClickListener) :
    RecyclerView.ViewHolder(itemView) {

    @JvmField
    var logLine: LogLine? = null

    init {
        itemView.setOnClickListener { clickListener.onClick(it, logLine) }
        itemView.setOnLongClickListener { view ->
            if (!clickListener.onLongClick(view, logLine)) showMenu(view)
            true
        }
    }

    private fun showMenu(view: View) {
        val line = logLine
        LogcatOverflowMenu.showLogLine(view, line != null && line.logLevel != -1) {
            clickListener.onMenuItemClick(it, line)
        }
    }

    interface OnClickListener {
        fun onClick(itemView: View, logLine: LogLine?)
        fun onLongClick(itemView: View, logLine: LogLine?): Boolean
        fun onMenuItemClick(itemId: Int, logLine: LogLine?): Boolean
    }

    companion object {
        const val CONTEXT_MENU_FILTER_ID = 0
        const val CONTEXT_MENU_COPY_ID = 1
        const val CONTEXT_MENU_EXPAND_ALL = 2
        const val CONTEXT_MENU_CLEAR = 3
        const val CONTEXT_MENU_SELECT = 4
        const val CONTEXT_MENU_INCLUDE_FILTER = 5
        const val CONTEXT_MENU_EXCLUDE_FILTER = 6
    }
}
