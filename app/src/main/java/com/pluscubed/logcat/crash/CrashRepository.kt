/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pluscubed.logcat.crash

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

class CrashRepository private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val _changes = MutableStateFlow(0L)

    // bumped on every write so screens can reload
    val changes: StateFlow<Long> = _changes.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TYPE TEXT NOT NULL,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_DETECTED_AT INTEGER NOT NULL,
                $COL_LOG TEXT NOT NULL,
                $COL_DISMISSED INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_TYPE, $COL_PACKAGE, $COL_TIMESTAMP)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DISMISSED_RETENTION_DAYS)
            db.delete(TABLE, "$COL_DISMISSED = 1 AND $COL_DETECTED_AT < ?", arrayOf(cutoff.toString()))
        }
    }

    /** Returns the new row id, or -1 when this crash was already recorded. */
    fun insert(crash: Crash): Long {
        val values = ContentValues().apply {
            put(COL_TYPE, crash.type.name)
            put(COL_PACKAGE, crash.packageName)
            put(COL_TIMESTAMP, crash.timestamp)
            put(COL_DETECTED_AT, crash.detectedAt)
            put(COL_LOG, crash.log)
        }
        val id = writableDatabase.insertWithOnConflict(
            TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        if (id != -1L) notifyChanged()
        return id
    }

    fun getAll(): List<Crash> = readableDatabase.query(
        TABLE,
        arrayOf(COL_ID, COL_TYPE, COL_PACKAGE, COL_TIMESTAMP, COL_DETECTED_AT),
        "$COL_DISMISSED = 0", null, null, null,
        "$COL_DETECTED_AT DESC, $COL_ID DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toCrash(withLog = false))
        }
    }

    fun get(id: Long): Crash? = readableDatabase.query(
        TABLE,
        arrayOf(COL_ID, COL_TYPE, COL_PACKAGE, COL_TIMESTAMP, COL_DETECTED_AT, COL_LOG),
        "$COL_ID = ? AND $COL_DISMISSED = 0", arrayOf(id.toString()), null, null, null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toCrash(withLog = true) else null
    }

    // rows are kept as dismissed so crashes still in the logcat buffer are not re-added
    fun dismiss(id: Long) = dismissWhere("$COL_ID = ?", arrayOf(id.toString()))

    fun dismissAll() = dismissWhere("$COL_DISMISSED = 0", null)

    private fun dismissWhere(selection: String, args: Array<String>?) {
        val values = ContentValues().apply {
            put(COL_DISMISSED, 1)
            put(COL_LOG, "")
        }
        if (writableDatabase.update(TABLE, values, selection, args) > 0) notifyChanged()
    }

    private fun notifyChanged() = _changes.update { it + 1 }

    private fun Cursor.toCrash(withLog: Boolean) = Crash(
        id = getLong(0),
        type = runCatching { CrashType.valueOf(getString(1)) }.getOrDefault(CrashType.JAVA),
        packageName = getString(2),
        timestamp = getString(3),
        detectedAt = getLong(4),
        log = if (withLog) getString(5) else "",
    )

    companion object {
        private const val DB_NAME = "crashes.db"
        private const val DB_VERSION = 1
        private const val TABLE = "crashes"
        private const val COL_ID = "_id"
        private const val COL_TYPE = "type"
        private const val COL_PACKAGE = "package"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_DETECTED_AT = "detected_at"
        private const val COL_LOG = "log"
        private const val COL_DISMISSED = "dismissed"
        private const val DISMISSED_RETENTION_DAYS = 30L

        @Volatile
        private var instance: CrashRepository? = null

        @JvmStatic
        fun get(context: Context): CrashRepository =
            instance ?: synchronized(this) {
                instance ?: CrashRepository(context.applicationContext).also { instance = it }
            }
    }
}
