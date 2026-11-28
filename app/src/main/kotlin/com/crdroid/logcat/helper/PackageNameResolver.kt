/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.helper

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache

object PackageNameResolver {

    private val APP_UID_PATTERN = Regex("u(\\d+)_a(\\d+)")

    private val WELL_KNOWN_UIDS = mapOf(
        "root" to 0,
        "system" to 1000,
        "radio" to 1001,
        "bluetooth" to 1002,
        "nfc" to 1027,
        "shell" to 2000,
    )

    private const val PER_USER_RANGE = 100000
    private const val FIRST_APPLICATION_UID = 10000

    private val cache = LruCache<String, String>(512)

    @Volatile
    private var packageManager: PackageManager? = null

    fun init(context: Context) {
        packageManager = context.applicationContext.packageManager
    }

    fun resolve(uid: String?): String? {
        if (uid.isNullOrEmpty()) return null
        cache.get(uid)?.let { return it.ifEmpty { null } }
        val pm = packageManager ?: return null
        val name = toNumericUid(uid)?.let { numericUid ->
            runCatching { pm.getPackagesForUid(numericUid)?.firstOrNull() }.getOrNull()
        }.orEmpty()
        cache.put(uid, name)
        return name.ifEmpty { null }
    }

    private fun toNumericUid(uid: String): Int? {
        uid.toIntOrNull()?.let { return it }
        WELL_KNOWN_UIDS[uid]?.let { return it }
        val match = APP_UID_PATTERN.matchEntire(uid) ?: return null
        val userId = match.groupValues[1].toIntOrNull() ?: return null
        val appId = match.groupValues[2].toIntOrNull() ?: return null
        return userId * PER_USER_RANGE + FIRST_APPLICATION_UID + appId
    }
}
