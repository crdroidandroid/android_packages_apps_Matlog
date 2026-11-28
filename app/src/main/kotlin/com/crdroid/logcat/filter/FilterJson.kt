/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.filter

import android.util.Log
import com.crdroid.logcat.util.LogLineAdapterUtil
import org.json.JSONArray
import org.json.JSONObject

// same layout as LogFox exports so filters can be shared between both apps;
// the regex and time range keys are Matlog extras that LogFox ignores
object FilterJson {

    private val LEVEL_NAMES = mapOf(
        Log.VERBOSE to "VERBOSE",
        Log.DEBUG to "DEBUG",
        Log.INFO to "INFO",
        Log.WARN to "WARNING",
        Log.ERROR to "ERROR",
        LogLineAdapterUtil.LOG_WTF to "FATAL",
    )

    fun encode(filters: List<UserFilter>): String {
        val array = JSONArray()
        filters.forEach { filter ->
            val json = JSONObject()
            filter.name?.let { json.put("name", it) }
            json.put("including", filter.including)
            json.put(
                "allowedLevels",
                JSONArray(UserFilter.LEVELS.filter { it in filter.levels }.mapNotNull { LEVEL_NAMES[it] })
            )
            filter.uid?.let { json.put("uid", it) }
            filter.pid?.let { json.put("pid", it) }
            filter.packageName?.let { json.put("packageName", it) }
            filter.tag?.let { json.put("tag", it) }
            filter.content?.let { json.put("content", it) }
            if (filter.tagRegex) json.put("tagRegex", true)
            if (filter.contentRegex) json.put("contentRegex", true)
            if (filter.packageRegex) json.put("packageNameRegex", true)
            filter.since?.let { json.put("since", it) }
            filter.until?.let { json.put("until", it) }
            array.put(json)
        }
        return array.toString(2)
    }

    fun decode(text: String): List<UserFilter> {
        val array = JSONArray(text)
        val levelsByName = LEVEL_NAMES.entries.associate { (level, name) -> name to level }
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val levels = json.optJSONArray("allowedLevels")?.let { levelArray ->
                (0 until levelArray.length()).mapNotNull { levelsByName[levelArray.optString(it)] }.toSet()
            }.orEmpty()
            UserFilter(
                name = json.optStringOrNull("name"),
                including = json.optBoolean("including", true),
                levels = levels,
                uid = json.optStringOrNull("uid"),
                pid = json.optStringOrNull("pid"),
                packageName = json.optStringOrNull("packageName"),
                tag = json.optStringOrNull("tag"),
                content = json.optStringOrNull("content"),
                tagRegex = json.optBoolean("tagRegex", false),
                contentRegex = json.optBoolean("contentRegex", false),
                packageRegex = json.optBoolean("packageNameRegex", false),
                since = json.optLongOrNull("since"),
                until = json.optLongOrNull("until"),
            )
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}
