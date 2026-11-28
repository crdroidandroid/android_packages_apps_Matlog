/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.logcat.crash

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        createExceptionsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createExceptionsTable(db)
    }

    private fun createExceptionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $EX_TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $EX_FINGERPRINT TEXT NOT NULL UNIQUE,
                $COL_PACKAGE TEXT NOT NULL,
                $EX_TAG TEXT NOT NULL,
                $EX_EXCEPTION TEXT NOT NULL,
                $EX_MESSAGE TEXT NOT NULL,
                $EX_TOP_FRAME TEXT NOT NULL,
                $EX_COUNT INTEGER NOT NULL,
                $EX_FIRST_SEEN INTEGER NOT NULL,
                $EX_LAST_SEEN INTEGER NOT NULL,
                $EX_LAST_TIMESTAMP TEXT NOT NULL,
                $COL_LOG TEXT NOT NULL,
                $EX_MUTED INTEGER NOT NULL DEFAULT 0,
                $COL_DISMISSED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DISMISSED_RETENTION_DAYS)
            db.delete(TABLE, "$COL_DISMISSED = 1 AND $COL_DETECTED_AT < ?", arrayOf(cutoff.toString()))
            pruneExceptions(db)
        }
    }

    // muted rows are kept so the exception stays muted
    private fun pruneExceptions(db: SQLiteDatabase) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(EXCEPTION_RETENTION_DAYS)
        db.delete(EX_TABLE, "$EX_MUTED = 0 AND $EX_LAST_SEEN < ?", arrayOf(cutoff.toString()))
        db.execSQL(
            "DELETE FROM $EX_TABLE WHERE $EX_MUTED = 0 AND $COL_ID NOT IN " +
                "(SELECT $COL_ID FROM $EX_TABLE WHERE $EX_MUTED = 0 ORDER BY $EX_LAST_SEEN DESC LIMIT $MAX_EXCEPTIONS)"
        )
    }

    /** Last logcat timestamp counted for this fingerprint and whether it is muted, or null when unknown. */
    fun exceptionState(fingerprint: String): Pair<String, Boolean>? = readableDatabase.query(
        EX_TABLE, arrayOf(EX_LAST_TIMESTAMP, EX_MUTED),
        "$EX_FINGERPRINT = ?", arrayOf(fingerprint), null, null, null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) to (cursor.getInt(1) != 0) else null
    }

    // a dismissed exception starts counting again when it shows up after being cleared
    fun recordException(event: ExceptionEvent, occurrences: Int, firstSeen: Long, lastSeen: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                """
                UPDATE $EX_TABLE SET
                    $EX_COUNT = CASE WHEN $COL_DISMISSED = 1 THEN ? ELSE $EX_COUNT + ? END,
                    $EX_FIRST_SEEN = CASE WHEN $COL_DISMISSED = 1 THEN ? ELSE $EX_FIRST_SEEN END,
                    $EX_LAST_SEEN = ?, $EX_LAST_TIMESTAMP = ?, $EX_MESSAGE = ?, $COL_LOG = ?, $COL_DISMISSED = 0
                WHERE $EX_FINGERPRINT = ? AND $EX_MUTED = 0
                """.trimIndent(),
                arrayOf<Any>(
                    occurrences, occurrences, firstSeen, lastSeen, event.timestamp, event.message, event.log,
                    event.fingerprint,
                )
            )
            val values = ContentValues().apply {
                put(EX_FINGERPRINT, event.fingerprint)
                put(COL_PACKAGE, event.packageName)
                put(EX_TAG, event.tag)
                put(EX_EXCEPTION, event.exception)
                put(EX_MESSAGE, event.message)
                put(EX_TOP_FRAME, event.topFrame)
                put(EX_COUNT, occurrences)
                put(EX_FIRST_SEEN, firstSeen)
                put(EX_LAST_SEEN, lastSeen)
                put(EX_LAST_TIMESTAMP, event.timestamp)
                put(COL_LOG, event.log)
            }
            db.insertWithOnConflict(EX_TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun notifyExceptionsChanged() = notifyChanged()

    fun getExceptions(): List<ExceptionInfo> = readableDatabase.query(
        EX_TABLE, EXCEPTION_COLUMNS, "$COL_DISMISSED = 0", null, null, null, "$EX_LAST_SEEN DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toException(withLog = false))
        }
    }

    fun getException(id: Long): ExceptionInfo? = readableDatabase.query(
        EX_TABLE, EXCEPTION_COLUMNS + COL_LOG, "$COL_ID = ? AND $COL_DISMISSED = 0", arrayOf(id.toString()),
        null, null, null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toException(withLog = true) else null
    }

    fun setExceptionMuted(id: Long, muted: Boolean) {
        val values = ContentValues().apply { put(EX_MUTED, if (muted) 1 else 0) }
        if (writableDatabase.update(EX_TABLE, values, "$COL_ID = ?", arrayOf(id.toString())) > 0) notifyChanged()
    }

    fun dismissException(id: Long) = dismissExceptionsWhere("$COL_ID = ?", arrayOf(id.toString()))

    fun dismissAllExceptions() = dismissExceptionsWhere("$COL_DISMISSED = 0", null)

    private fun dismissExceptionsWhere(selection: String, args: Array<String>?) {
        val values = ContentValues().apply {
            put(COL_DISMISSED, 1)
            put(COL_LOG, "")
        }
        if (writableDatabase.update(EX_TABLE, values, selection, args) > 0) notifyChanged()
    }

    private fun Cursor.toException(withLog: Boolean) = ExceptionInfo(
        id = getLong(0),
        packageName = getString(1),
        tag = getString(2),
        exception = getString(3),
        message = getString(4),
        topFrame = getString(5),
        count = getInt(6),
        firstSeen = getLong(7),
        lastSeen = getLong(8),
        muted = getInt(9) != 0,
        log = if (withLog) getString(10) else "",
    )

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
        private const val DB_VERSION = 2
        private const val TABLE = "crashes"
        private const val COL_ID = "_id"
        private const val COL_TYPE = "type"
        private const val COL_PACKAGE = "package"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_DETECTED_AT = "detected_at"
        private const val COL_LOG = "log"
        private const val COL_DISMISSED = "dismissed"
        private const val DISMISSED_RETENTION_DAYS = 30L

        private const val EX_TABLE = "exceptions"
        private const val EX_FINGERPRINT = "fingerprint"
        private const val EX_TAG = "tag"
        private const val EX_EXCEPTION = "exception"
        private const val EX_MESSAGE = "message"
        private const val EX_TOP_FRAME = "top_frame"
        private const val EX_COUNT = "count"
        private const val EX_FIRST_SEEN = "first_seen"
        private const val EX_LAST_SEEN = "last_seen"
        private const val EX_LAST_TIMESTAMP = "last_timestamp"
        private const val EX_MUTED = "muted"
        private const val EXCEPTION_RETENTION_DAYS = 14L
        private const val MAX_EXCEPTIONS = 500
        private val EXCEPTION_COLUMNS = arrayOf(
            COL_ID, COL_PACKAGE, EX_TAG, EX_EXCEPTION, EX_MESSAGE, EX_TOP_FRAME,
            EX_COUNT, EX_FIRST_SEEN, EX_LAST_SEEN, EX_MUTED,
        )

        @Volatile
        private var instance: CrashRepository? = null

        fun get(context: Context): CrashRepository =
            instance ?: synchronized(this) {
                instance ?: CrashRepository(context.applicationContext).also { instance = it }
            }
    }
}
