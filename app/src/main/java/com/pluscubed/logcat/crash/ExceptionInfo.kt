/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

data class ExceptionInfo(
    val id: Long,
    val packageName: String,
    val tag: String,
    val exception: String,
    val message: String,
    val topFrame: String,
    val count: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val muted: Boolean,
    val log: String = "",
) {
    val shortName: String
        get() = exception.substringAfterLast('.')
}
