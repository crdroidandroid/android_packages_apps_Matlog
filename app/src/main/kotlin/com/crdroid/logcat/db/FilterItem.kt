/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.db

class FilterItem(val id: Int, val text: String) : Comparable<FilterItem> {
    override fun compareTo(other: FilterItem): Int = text.compareTo(other.text, ignoreCase = true)
}
