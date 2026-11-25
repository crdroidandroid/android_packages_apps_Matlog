/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import androidx.annotation.StringRes
import org.omnirom.logcat.R

enum class CrashType(@StringRes val labelRes: Int) {
    JAVA(R.string.matlog_crash_type_java),
    NATIVE(R.string.matlog_crash_type_native),
    ANR(R.string.matlog_crash_type_anr),
}
