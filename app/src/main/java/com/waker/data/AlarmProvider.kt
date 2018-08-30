package com.waker.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.waker.data.AlarmContract.AlarmEntry
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry


private const val ALARM_GROUP = 100
private const val ALARM_GROUP_ID = 101
private const val ALARM_TIME = 200
private const val ALARM_TIME_ID = 201
private const val ALARM = 300
private const val ALARM_ID = 301


class AlarmProvider: ContentProvider() {

    private lateinit var mDbHelper: DbHelper
    private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    init {
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM_GROUP, ALARM_GROUP)
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM_GROUP + "/#", ALARM_GROUP_ID)
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM_TIME, ALARM_TIME)
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM_TIME + "/#", ALARM_TIME_ID)
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM, ALARM)
        sUriMatcher.addURI(AlarmContract.CONTENT_AUTHORITY, AlarmContract.PATH_ALARM + "/#", ALARM_ID)
    }

    override fun onCreate(): Boolean {
        mDbHelper = DbHelper(context!!)
        return true
    }

    override fun query(uri: Uri?, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val db = mDbHelper.readableDatabase

        val match = sUriMatcher.match(uri)
        val cursor = when (match) {
            ALARM_GROUP -> db.query(AlarmGroupEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
            ALARM_GROUP_ID -> {
                db.query(AlarmGroupEntry.TABLE_NAME,
                        projection,
                        AlarmGroupEntry.COLUMN_ID + "=?",
                        arrayOf(ContentUris.parseId(uri).toString()),
                        null, null, sortOrder)
            }

            ALARM_TIME -> db.query(AlarmTimeEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
            ALARM_TIME_ID -> {
                db.query(AlarmTimeEntry.TABLE_NAME,
                        projection,
                        AlarmTimeEntry.COLUMN_ID + "=?",
                        arrayOf(ContentUris.parseId(uri).toString()),
                        null, null, sortOrder)
            }

            ALARM -> db.query(AlarmEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder)
            ALARM_ID -> {
                db.query(AlarmEntry.TABLE_NAME,
                        projection,
                        AlarmEntry.COLUMN_ID + "=?",
                        arrayOf(ContentUris.parseId(uri).toString()),
                        null, null, sortOrder)
            }

            else -> throw IllegalArgumentException("Cannot query, unknown URI: $uri")
        }

        cursor.setNotificationUri(context!!.contentResolver, uri)

        return cursor
    }


    override fun insert(uri: Uri, contentValues: ContentValues): Uri? {
        val match = sUriMatcher.match(uri)
        return when (match) {
            ALARM_GROUP -> insertToDb(uri, contentValues, AlarmGroupEntry.TABLE_NAME)
            ALARM_TIME -> insertToDb(uri, contentValues, AlarmTimeEntry.TABLE_NAME)
            ALARM -> insertToDb(uri, contentValues, AlarmEntry.TABLE_NAME)
            else -> throw IllegalArgumentException("Cannot insert, unknown URI: $uri")
        }
    }

    private fun insertToDb(uri: Uri, contentValues: ContentValues, tableName: String): Uri? {
        val db = mDbHelper.readableDatabase

        val id = db.insert(tableName, null, contentValues)
        if (id == -1L) return null

        context!!.contentResolver.notifyChange(uri, null)

        return ContentUris.withAppendedId(uri, id)
    }

    override fun update(uri: Uri, contentValues: ContentValues, selection: String?, selectionArgs: Array<out String>?): Int {
        val match = sUriMatcher.match(uri)
        return when (match) {
            ALARM_GROUP -> updateDb(uri, contentValues, selection, selectionArgs, AlarmGroupEntry.TABLE_NAME)
            ALARM_GROUP_ID -> updateDb(uri,
                    contentValues,
                    AlarmGroupEntry.COLUMN_ID + "=?",
                    arrayOf(ContentUris.parseId(uri).toString()),
                    AlarmGroupEntry.TABLE_NAME)

            ALARM_TIME -> updateDb(uri, contentValues, selection, selectionArgs, AlarmTimeEntry.TABLE_NAME)
            ALARM_TIME_ID -> updateDb(uri,
                    contentValues,
                    AlarmTimeEntry.COLUMN_ID + "=?",
                    arrayOf(ContentUris.parseId(uri).toString()),
                    AlarmTimeEntry.TABLE_NAME)

            ALARM -> updateDb(uri, contentValues, selection, selectionArgs, AlarmEntry.TABLE_NAME)
            ALARM_ID -> updateDb(uri,
                    contentValues,
                    AlarmEntry.COLUMN_ID + "=?",
                    arrayOf(ContentUris.parseId(uri).toString()),
                    AlarmEntry.TABLE_NAME)

            else -> throw IllegalArgumentException("Cannot insert, unknown URI: $uri")
        }
    }

    private fun updateDb(uri: Uri, contentValues: ContentValues, selection: String?, selectionArgs: Array<out String>?, tableName: String): Int {
        if (contentValues.size() == 0) return 0

        val db = mDbHelper.writableDatabase

        val rowsUpdated = db.update(tableName, contentValues, selection, selectionArgs)
        if (rowsUpdated != 0) context!!.contentResolver.notifyChange(uri, null)

        return rowsUpdated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = mDbHelper.writableDatabase

        val match = sUriMatcher.match(uri)
        val rowsDeleted = when (match) {
            ALARM_GROUP -> db.delete(AlarmGroupEntry.TABLE_NAME, selection, selectionArgs)
            ALARM_GROUP_ID -> db.delete(AlarmGroupEntry.TABLE_NAME, AlarmGroupEntry.COLUMN_ID + "=?", arrayOf(ContentUris.parseId(uri).toString()))
            ALARM_TIME -> db.delete(AlarmTimeEntry.TABLE_NAME, selection, selectionArgs)
            ALARM_TIME_ID -> db.delete(AlarmTimeEntry.TABLE_NAME, AlarmTimeEntry.COLUMN_ID + "=?", arrayOf(ContentUris.parseId(uri).toString()))
            ALARM -> db.delete(AlarmEntry.TABLE_NAME, selection, selectionArgs)
            ALARM_ID -> db.delete(AlarmEntry.TABLE_NAME, AlarmEntry.COLUMN_ID + "=?", arrayOf(ContentUris.parseId(uri).toString()))
            else -> throw IllegalArgumentException("Cannot insert, unknown URI: $uri")
        }

        if (rowsDeleted != 0) context!!.contentResolver.notifyChange(uri, null)

        return rowsDeleted
    }

    override fun getType(uri: Uri): String {
        val match = sUriMatcher.match(uri)
        return when (match) {
            ALARM_GROUP -> AlarmGroupEntry.CONTENT_LIST_TYPE
            ALARM_GROUP_ID -> AlarmGroupEntry.CONTENT_ITEM_TYPE
            ALARM_TIME -> AlarmTimeEntry.CONTENT_LIST_TYPE
            ALARM_TIME_ID -> AlarmTimeEntry.CONTENT_ITEM_TYPE
            ALARM -> AlarmEntry.CONTENT_LIST_TYPE
            ALARM_ID -> AlarmEntry.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("Cannot insert, unknown URI: $uri")
        }
    }

    fun getGroupTimes(groupId: Int): Cursor {
        return query(AlarmTimeEntry.CONTENT_URI,
                arrayOf(AlarmTimeEntry.COLUMN_TIME),
                "=?",
                arrayOf(groupId.toString()),
                AlarmTimeEntry.COLUMN_TIME)
    }

}