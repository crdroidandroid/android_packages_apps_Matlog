/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.helper

import com.pluscubed.logcat.reader.DmesgReader

object DmesgHelper {

    @JvmStatic
    fun getDmsg(): List<CharSequence> = DmesgReader.dump()
}
