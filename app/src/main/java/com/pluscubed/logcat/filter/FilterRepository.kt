/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.filter

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FilterRepository private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val _changes = MutableStateFlow(0L)

    // bumped on every write so screens and the log list can refresh
    val changes: StateFlow<Long> = _changes.asStateFlow()

    val version: Long
        get() = _changes.value

    @Volatile
    private var cachedSet: Pair<Long, FilterSet>? = null

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT,
                $COL_INCLUDING INTEGER NOT NULL,
                $COL_LEVELS TEXT NOT NULL,
                $COL_UID TEXT,
                $COL_PID TEXT,
                $COL_PACKAGE TEXT,
                $COL_TAG TEXT,
                $COL_CONTENT TEXT,
                $COL_ENABLED INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getAll(): List<UserFilter> = readableDatabase.query(
        TABLE, null, null, null, null, null, "$COL_ID ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toFilter())
        }
    }

    fun get(id: Long): UserFilter? = readableDatabase.query(
        TABLE, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toFilter() else null
    }

    fun save(filter: UserFilter): Long {
        val values = filter.toValues()
        val id = if (filter.id == 0L) {
            writableDatabase.insert(TABLE, null, values)
        } else {
            writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(filter.id.toString()))
            filter.id
        }
        notifyChanged()
        return id
    }

    fun addAll(filters: List<UserFilter>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            filters.forEach { db.insert(TABLE, null, it.copy(id = 0).toValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put(COL_ENABLED, if (enabled) 1 else 0) }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE, null, null)
        notifyChanged()
    }

    /** The enabled filters, cached until the next change. */
    fun activeFilterSet(): FilterSet {
        val current = version
        cachedSet?.let { (cachedVersion, set) -> if (cachedVersion == current) return set }
        val set = FilterSet(getAll())
        cachedSet = current to set
        return set
    }

    private fun notifyChanged() = _changes.update { it + 1 }

    private fun UserFilter.toValues() = ContentValues().apply {
        put(COL_NAME, name)
        put(COL_INCLUDING, if (including) 1 else 0)
        put(COL_LEVELS, levels.sorted().joinToString(","))
        put(COL_UID, uid)
        put(COL_PID, pid)
        put(COL_PACKAGE, packageName)
        put(COL_TAG, tag)
        put(COL_CONTENT, content)
        put(COL_ENABLED, if (enabled) 1 else 0)
    }

    private fun Cursor.toFilter() = UserFilter(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        name = stringOrNull(COL_NAME),
        including = getInt(getColumnIndexOrThrow(COL_INCLUDING)) != 0,
        levels = getString(getColumnIndexOrThrow(COL_LEVELS))
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .toSet(),
        uid = stringOrNull(COL_UID),
        pid = stringOrNull(COL_PID),
        packageName = stringOrNull(COL_PACKAGE),
        tag = stringOrNull(COL_TAG),
        content = stringOrNull(COL_CONTENT),
        enabled = getInt(getColumnIndexOrThrow(COL_ENABLED)) != 0,
    )

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    companion object {
        private const val DB_NAME = "user_filters.db"
        private const val DB_VERSION = 1
        private const val TABLE = "filters"
        private const val COL_ID = "_id"
        private const val COL_NAME = "name"
        private const val COL_INCLUDING = "including"
        private const val COL_LEVELS = "levels"
        private const val COL_UID = "uid"
        private const val COL_PID = "pid"
        private const val COL_PACKAGE = "package"
        private const val COL_TAG = "tag"
        private const val COL_CONTENT = "content"
        private const val COL_ENABLED = "enabled"

        @Volatile
        private var instance: FilterRepository? = null

        @JvmStatic
        fun get(context: Context): FilterRepository =
            instance ?: synchronized(this) {
                instance ?: FilterRepository(context.applicationContext).also { instance = it }
            }
    }
}
