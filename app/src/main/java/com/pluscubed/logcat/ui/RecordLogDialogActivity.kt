/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.pluscubed.logcat.LogcatRecordingService
import com.pluscubed.logcat.RecordingWidgetProvider
import com.pluscubed.logcat.helper.DialogHelper
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.helper.ServiceHelper
import com.pluscubed.logcat.helper.WidgetHelper
import org.omnirom.logcat.R

class RecordLogDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        if (intent?.action == RecordingWidgetProvider.ACTION_RECORD_OR_STOP) {
            WidgetHelper.updateWidgets(applicationContext)
            if (ServiceHelper.checkIfServiceIsRunning(this, LogcatRecordingService::class.java)) {
                DialogHelper.stopRecordingLog(this)
                finish()
                return
            }
        }
        RecordLogDialog.newInstance(intent?.getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS) ?: emptyArray())
            .show(supportFragmentManager, TAG_DIALOG)
    }

    class RecordLogDialog : DialogFragment() {

        private var filterQuery = ""
        private var logLevel = ""
        private var starting = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val activity = requireActivity()
            val suggestions = requireArguments().getStringArray(ARG_SUGGESTIONS).orEmpty().toList()
            filterQuery = savedInstanceState?.getString(STATE_QUERY).orEmpty()
            logLevel = savedInstanceState?.getString(STATE_LEVEL)
                ?: PreferenceHelper.getDefaultLogLevelPreference(activity).toString()

            return DialogHelper.filenameDialog(
                activity, R.string.record_log, DialogHelper.createLogFilename(),
                configure = { setNeutralButton(R.string.text_filter_ellipsis, null) },
                onShow = { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        DialogHelper.showFilterDialogForRecording(activity, filterQuery, logLevel, suggestions) {
                            filterQuery = it.filterQuery
                            logLevel = it.logLevel
                        }
                    }
                },
            ) { filename ->
                starting = true
                DialogHelper.startRecording(filename, filterQuery, logLevel, { activity.finish() }, activity)
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putString(STATE_QUERY, filterQuery)
            outState.putString(STATE_LEVEL, logLevel)
        }

        // the activity only exists to host this dialog
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            val activity = activity ?: return
            if (!starting && !activity.isChangingConfigurations) activity.finish()
        }

        companion object {
            fun newInstance(suggestions: Array<String>) = RecordLogDialog().apply {
                arguments = bundleOf(ARG_SUGGESTIONS to suggestions)
            }
        }
    }

    companion object {
        const val EXTRA_QUERY_SUGGESTIONS = "suggestions"
        private const val TAG_DIALOG = "showRecordLogDialog"
        private const val ARG_SUGGESTIONS = "suggestions"
        private const val STATE_QUERY = "filter_query"
        private const val STATE_LEVEL = "log_level"
    }
}
