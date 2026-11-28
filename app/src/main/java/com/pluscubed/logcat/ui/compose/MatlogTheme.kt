/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.omnirom.logcat.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MatlogTheme(surface: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive()) {
        if (surface) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        } else {
            content()
        }
    }
}

// draws one of the app's Material Symbols drawables
@Composable
fun MatlogIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(id), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        MatlogIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.matlog_back))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MatlogLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier)
}
