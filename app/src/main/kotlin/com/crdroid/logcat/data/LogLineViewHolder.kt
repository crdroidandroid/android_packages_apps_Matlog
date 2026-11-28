/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.data

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.crdroid.logcat.R
import com.crdroid.logcat.ui.LogcatOverflowMenu

class LogLineViewHolder(itemView: View, private val clickListener: OnClickListener) :
    RecyclerView.ViewHolder(itemView) {

    var logLine: LogLine? = null

    val levelText: TextView = itemView.findViewById(R.id.log_level_text)
    val outputText: TextView = itemView.findViewById(R.id.log_output_text)
    val tagText: TextView = itemView.findViewById(R.id.tag_text)
    val pidText: TextView = itemView.findViewById(R.id.pid_text)
    val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)

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
