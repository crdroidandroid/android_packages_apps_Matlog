/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.reader

import java.io.Closeable
import java.io.IOException

interface LineReader : Closeable {

    @Throws(IOException::class)
    fun readLine(): String?

    fun killQuietly()

    override fun close() = killQuietly()
}
