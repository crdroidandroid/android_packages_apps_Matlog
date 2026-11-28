/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.helper

import android.os.Build
import java.util.Locale

object BuildHelper {

    private val BUILD_FIELDS = listOf(
        "BOARD", "BOOTLOADER", "BRAND", "CPU_ABI", "CPU_ABI2",
        "DEVICE", "DISPLAY", "FINGERPRINT", "HARDWARE", "HOST",
        "ID", "MANUFACTURER", "MODEL", "PRODUCT", "RADIO",
        "SERIAL", "TAGS", "TIME", "TYPE", "USER",
    )
    private val BUILD_VERSION_FIELDS = listOf("CODENAME", "INCREMENTAL", "RELEASE", "SDK_INT")

    // the public constants of android.os.Build and Build.VERSION, one per line
    fun buildInformation(): String {
        val values = sortedMapOf<String, String>()
        fun collect(clazz: Class<*>, fields: List<String>) {
            for (name in fields) {
                runCatching {
                    val key = "${clazz.simpleName}.$name".lowercase(Locale.ROOT)
                    values[key] = "${clazz.getField(name).get(null)}"
                }
            }
        }
        collect(Build::class.java, BUILD_FIELDS)
        collect(Build.VERSION::class.java, BUILD_VERSION_FIELDS)
        return buildString { values.forEach { (key, value) -> append(key).append(": ").append(value).append('\n') } }
    }
}
