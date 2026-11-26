/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.pluscubed.logcat.data.LogLine
import com.pluscubed.logcat.helper.LogTextActions
import org.omnirom.logcat.R

// lets the user copy the whole line or a single field of it
object CopyLogLineSheet {

    private const val DATE_LENGTH = 5

    @JvmStatic
    fun show(context: Context, line: LogLine) {
        val dialog = BottomSheetDialog(context)
        val inflater = LayoutInflater.from(context)
        val root = inflater.inflate(R.layout.sheet_copy_log_line, null)
        val container = root.findViewById<ViewGroup>(R.id.copy_fields)

        for ((label, value) in fields(line)) {
            val item = inflater.inflate(R.layout.item_copy_field, container, false)
            item.findViewById<TextView>(R.id.copy_field_label).setText(label)
            item.findViewById<TextView>(R.id.copy_field_value).text = value
            item.setOnClickListener {
                LogTextActions.copy(context, value)
                dialog.dismiss()
            }
            container.addView(item)
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun fields(line: LogLine): List<Pair<Int, String>> {
        val fields = ArrayList<Pair<Int, String>>()
        line.originalLine?.let { fields.add(R.string.matlog_copy_field_line to it) }
        if (line.logLevel == -1) return fields

        line.logOutput?.let { fields.add(R.string.matlog_copy_field_message to it) }
        line.tag?.trim()?.takeIf { it.isNotEmpty() }?.let { fields.add(R.string.matlog_column_tag to it) }
        line.packageName?.let { fields.add(R.string.matlog_column_package to it) }
        line.timestamp?.let { timestamp ->
            if (timestamp.length > DATE_LENGTH + 1) {
                fields.add(R.string.matlog_column_date to timestamp.substring(0, DATE_LENGTH))
                fields.add(R.string.matlog_column_time to timestamp.substring(DATE_LENGTH + 1))
            }
        }
        line.uid?.let { fields.add(R.string.matlog_column_uid to it) }
        if (line.processId != -1) fields.add(R.string.matlog_column_pid to line.processId.toString())
        return fields
    }
}
