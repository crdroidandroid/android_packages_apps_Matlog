/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.data

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.recyclerview.widget.RecyclerView
import org.omnirom.logcat.R

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
        PopupMenu(view.context, view).apply {
            menu.addItem(GROUP_LINE, CONTEXT_MENU_COPY_ID, R.string.copy_to_clipboard, R.drawable.ic_copy)
            menu.addItem(GROUP_LINE, CONTEXT_MENU_SELECT, R.string.matlog_select, R.drawable.ic_select_all)
            if (line != null && line.logLevel != -1) {
                menu.addItem(GROUP_FILTER, CONTEXT_MENU_FILTER_ID, R.string.filter_choice, R.drawable.ic_search)
                menu.addItem(GROUP_FILTER, CONTEXT_MENU_INCLUDE_FILTER, R.string.matlog_filter_from_line_include,
                    R.drawable.ic_filter)
                menu.addItem(GROUP_FILTER, CONTEXT_MENU_EXCLUDE_FILTER, R.string.matlog_filter_from_line_exclude,
                    R.drawable.ic_filter_off)
            }
            menu.addItem(GROUP_LIST, CONTEXT_MENU_EXPAND_ALL, R.string.expand_or_collapse_all, R.drawable.ic_unfold_more)
            menu.addItem(GROUP_LIST, CONTEXT_MENU_CLEAR, R.string.clear, R.drawable.ic_clear_all)
            MenuCompat.setGroupDividerEnabled(menu, true)
            setForceShowIcon(true)
            setOnMenuItemClickListener { clickListener.onMenuItemClick(it, line) }
            show()
        }
    }

    private fun Menu.addItem(group: Int, id: Int, title: Int, icon: Int) {
        add(group, id, Menu.NONE, title).setIcon(icon)
    }

    interface OnClickListener {
        fun onClick(itemView: View, logLine: LogLine?)
        fun onLongClick(itemView: View, logLine: LogLine?): Boolean
        fun onMenuItemClick(item: MenuItem, logLine: LogLine?): Boolean
    }

    companion object {
        const val CONTEXT_MENU_FILTER_ID = 0
        const val CONTEXT_MENU_COPY_ID = 1
        const val CONTEXT_MENU_EXPAND_ALL = 2
        const val CONTEXT_MENU_CLEAR = 3
        const val CONTEXT_MENU_SELECT = 4
        const val CONTEXT_MENU_INCLUDE_FILTER = 5
        const val CONTEXT_MENU_EXCLUDE_FILTER = 6

        private const val GROUP_LINE = 0
        private const val GROUP_FILTER = 1
        private const val GROUP_LIST = 2
    }
}
