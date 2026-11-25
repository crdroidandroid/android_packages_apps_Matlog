/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

data class Crash(
    val id: Long = 0,
    val type: CrashType,
    val packageName: String,
    val timestamp: String,
    val detectedAt: Long,
    val log: String = "",
)
