/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// the saved searches
class CatlogDBHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val db: SQLiteDatabase = writableDatabase

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "create table if not exists $TABLE_NAME ($COLUMN_ID integer not null primary key autoincrement, " +
                "$COLUMN_TEXT text);"
        )
        db.execSQL("create unique index if not exists index_game_id on $TABLE_NAME ($COLUMN_TEXT);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun findFilterItems(): List<FilterItem> = synchronized(LOCK) {
        db.query(TABLE_NAME, arrayOf(COLUMN_ID, COLUMN_TEXT), null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(FilterItem(cursor.getInt(0), cursor.getString(1).orEmpty()))
            }
        }
    }

    fun deleteFilter(id: Int) {
        synchronized(LOCK) {
            db.delete(TABLE_NAME, "$COLUMN_ID=?", arrayOf(id.toString()))
        }
    }

    // null when the text is already saved
    fun addFilter(text: String): FilterItem? = synchronized(LOCK) {
        val rowId = db.insert(TABLE_NAME, null, ContentValues().apply { put(COLUMN_TEXT, text) })
        if (rowId == -1L) null else FilterItem(rowId.toInt(), text)
    }

    private companion object {
        const val DB_NAME = "catlog.db"
        const val DB_VERSION = 1
        const val TABLE_NAME = "Filters"
        const val COLUMN_ID = "_id"
        const val COLUMN_TEXT = "filterText"
        val LOCK = Any()
    }
}
