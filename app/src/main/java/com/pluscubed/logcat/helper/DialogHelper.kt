/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.pluscubed.logcat.data.FilterQueryWithLevel
import com.pluscubed.logcat.data.SortedFilterArrayAdapter
import com.pluscubed.logcat.util.Callback
import org.omnirom.logcat.R

object DialogHelper {

    fun interface InputTextCallback {
        fun setInputText(text: String)
    }

    @JvmStatic
    fun startRecording(
        filename: String,
        filterQuery: String,
        logLevel: String,
        onPostExecute: Runnable?,
        context: Context,
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            ServiceHelper.startBackgroundServiceIfNotAlreadyRunning(context, filename, filterQuery, logLevel)
            onPostExecute?.let { handler.post(it) }
        }.start()
    }

    @JvmStatic
    fun stopRecordingLog(context: Context) {
        ServiceHelper.stopBackgroundServiceIfRunning(context)
    }

    @JvmStatic
    fun createLogFilename(): String = SaveLogHelper.createLogFilename()

    @JvmStatic
    fun isInvalidFilename(filename: CharSequence?): Boolean {
        val name = filename?.toString().orEmpty()
        return name.isEmpty() || '/' in name || ':' in name || ' ' in name || !name.endsWith(".txt")
    }

    // log level names with the default one marked
    @JvmStatic
    fun logLevelLabels(context: Context): Array<String> {
        val labels = context.resources.getStringArray(R.array.log_levels)
        val default = PreferenceHelper.getDefaultLogLevelPreference(context).toString()
        val index = context.resources.getStringArray(R.array.log_levels_values).indexOf(default)
        if (index >= 0) labels[index] = "${labels[index]} ${context.getString(R.string.default_in_parens)}"
        return labels
    }

    @JvmStatic
    fun showFilterDialogForRecording(
        context: Context,
        queryFilterText: String,
        logLevelText: String,
        filterQuerySuggestions: List<String>,
        callback: Callback<FilterQueryWithLevel>,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_recording_filter, null, false)
        val filterText = view.findViewById<AutoCompleteTextView>(R.id.filter_text)
        filterText.setText(queryFilterText)
        filterText.setAdapter(SortedFilterArrayAdapter(context, R.layout.list_item_dropdown, filterQuerySuggestions))

        val values = context.resources.getStringArray(R.array.log_levels_values)
        val labels = logLevelLabels(context)
        var selected = values.indexOf(logLevelText).coerceAtLeast(0)
        view.findViewById<MaterialAutoCompleteTextView>(R.id.log_level).apply {
            setSimpleItems(labels)
            setText(labels[selected], false)
            setOnItemClickListener { _, _, position, _ -> selected = position }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_filter)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callback.onCallback(FilterQueryWithLevel(filterText.text.toString(), values[selected]))
            }
            .show()
    }

    @JvmStatic
    @JvmOverloads
    fun showFilenameSuggestingDialog(
        context: Context,
        negativeCallback: Runnable?,
        inputCallback: InputTextCallback,
        titleResId: Int,
        filename: String = createLogFilename(),
    ) {
        filenameDialog(
            context, titleResId, filename,
            configure = { setNegativeButton(android.R.string.cancel) { _, _ -> negativeCallback?.run() } },
            onFilename = inputCallback::setInputText,
        ).show()
    }

    // stays open with an error until the filename is valid
    fun filenameDialog(
        context: Context,
        titleResId: Int,
        filename: String,
        configure: MaterialAlertDialogBuilder.() -> Unit = {},
        onShow: (AlertDialog) -> Unit = {},
        onFilename: (String) -> Unit,
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_input_text, null, false)
        val layout = view.findViewById<TextInputLayout>(R.id.input_layout)
        val input = view.findViewById<EditText>(R.id.edit_text)
        input.setText(filename)
        input.setSelection(0, (filename.length - ".txt".length).coerceAtLeast(0))

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleResId)
            .setMessage(R.string.enter_filename)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .apply(configure)
            .create()

        fun submit() {
            val text = input.text.toString()
            if (isInvalidFilename(text)) {
                layout.error = context.getString(R.string.enter_good_filename)
            } else {
                onFilename(text)
                dialog.dismiss()
            }
        }

        input.doAfterTextChanged { layout.error = null }
        input.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            input.requestFocus()
            onShow(dialog)
        }
        return dialog
    }
}
