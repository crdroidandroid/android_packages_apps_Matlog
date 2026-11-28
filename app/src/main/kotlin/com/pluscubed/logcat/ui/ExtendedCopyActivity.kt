/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.omnirom.logcat.R
import com.pluscubed.logcat.helper.LogTextActions
import com.pluscubed.logcat.helper.PreferenceHelper
import com.pluscubed.logcat.ui.compose.BackButton
import com.pluscubed.logcat.ui.compose.MatlogIcon
import com.pluscubed.logcat.ui.compose.MatlogIconButton
import com.pluscubed.logcat.ui.compose.MatlogTheme

class ExtendedCopyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the text is held in memory only, so there is nothing to show after process death
        val text = pendingText ?: run {
            finish()
            return
        }
        val textSize = PreferenceHelper.getTextSizePreference(this)
        setContent {
            MatlogTheme {
                ExtendedCopyScreen(
                    text = text,
                    textSize = textSize,
                    onCopyAll = { LogTextActions.copy(this, text) },
                    onShare = { LogTextActions.share(this, text) },
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) pendingText = null
    }

    companion object {
        @Volatile
        private var pendingText: String? = null

        fun start(context: Context, text: String) {
            pendingText = text
            context.startActivity(Intent(context, ExtendedCopyActivity::class.java))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtendedCopyScreen(
    text: String,
    textSize: Float,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matlog_extended_copy)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    MatlogIconButton(onClick = onCopyAll) {
                        MatlogIcon(R.drawable.ic_copy, contentDescription = stringResource(R.string.matlog_copy_all))
                    }
                    MatlogIconButton(onClick = onShare) {
                        MatlogIcon(R.drawable.ic_share_white_24dp, contentDescription = stringResource(R.string.matlog_share))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // a selectable TextView handles large logs far better than a Compose Text
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    setTextIsSelectable(true)
                    typeface = Typeface.MONOSPACE
                }
            },
            update = { view ->
                view.text = text
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                view.setTextColor(textColor)
                view.highlightColor = highlightColor
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
