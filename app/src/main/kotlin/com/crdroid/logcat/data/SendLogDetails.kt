/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.data

import java.io.File

class SendLogDetails(
    val subject: String,
    val attachmentType: AttachmentType,
    val attachment: File? = null,
) {
    enum class AttachmentType(val mimeType: String) {
        None("text/plain"),
        Zip("application/zip"),
        Text("text/plain"),
    }
}
