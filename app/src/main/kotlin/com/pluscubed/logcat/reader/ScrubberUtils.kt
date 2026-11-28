/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pluscubed.logcat.reader

object ScrubberUtils {

    private val EMAIL_PATTERN = Regex("[a-zA-Z0-9_]+(?:\\.[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+)*(@|%40)(?!([a-zA-Z0-9]*\\.[a-zA-Z0-9]*\\.[a-zA-Z0-9]*\\.))(?:[A-Za-z0-9](?:[a-zA-Z0-9-]*[A-Za-z0-9])?\\.)+[a-zA-Z](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?")
    private val PHONE_NUMBER_PATTERN = Regex("^(?:(?:\\+?1\\s*(?:[.-]\\s*)?)?(?:\\(\\s*([2-9]1[02-9]|[2-9][02-8]1|[2-9][02-8][02-9])\\s*\\)|([2-9]1[02-9]|[2-9][02-8]1|[2-9][02-8][02-9]))\\s*(?:[.-]\\s*)?)?([2-9]1[02-9]|[2-9][02-9]1|[2-9][02-9]{2})\\s*(?:[.-]\\s*)?([0-9]{4})(?:\\s*(?:#|x\\.?|ext\\.?|extension)\\s*(\\d+))?\$")
    private val WEB_URL_PATTERN = Regex("\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
    private val IP_ADDRESS_PATTERN = Regex("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\$")
    private val PHONE_INFO_PATTERN = Regex("(msisdn=|mMsisdn=|iccid=|iccid: |mImsi=)[a-zA-Z0-9]*", RegexOption.IGNORE_CASE)
    private val USER_INFO_PATTERN = Regex("(UserInfo\\{\\d:)[a-zA-Z0-9\\s]*", RegexOption.IGNORE_CASE)
    private val ACCOUNT_INFO_PATTERN = Regex("(Account \\{name=)[a-zA-Z0-9]*", RegexOption.IGNORE_CASE)

    private val IGNORED_PATHS = listOf("/data/resource-cache", "/data/dalvik-cache", "/cache/dalvik-cache")

    fun scrubLine(line: String): String {
        if (IGNORED_PATHS.any { it in line }) return line
        return line
            .replace(IP_ADDRESS_PATTERN, "<IP address omitted>")
            .replace(EMAIL_PATTERN, "<email omitted>")
            .replace(PHONE_NUMBER_PATTERN, "<phone number omitted>")
            .replace(WEB_URL_PATTERN, "<web url omitted>")
            .replace(PHONE_INFO_PATTERN, "<omitted>")
            .replace(USER_INFO_PATTERN, "<omitted>")
            .replace(ACCOUNT_INFO_PATTERN, "<omitted>")
    }
}
