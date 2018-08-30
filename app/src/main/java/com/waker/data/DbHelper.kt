package com.waker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.waker.data.AlarmContract.AlarmEntry
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry


class DbHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        // Don't forget to increment the DB Version when DB schema is changed!
        const val DATABASE_VERSION = 9 // 26.08.18 20:11
        const val DATABASE_NAME = "Alarms.db"

        private const val SQL_CREATE_GROUP_ENTRIES =
                "CREATE TABLE ${AlarmGroupEntry.TABLE_NAME} " +
                        "(${AlarmGroupEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "${AlarmGroupEntry.COLUMN_NAME} TEXT," +
                        "${AlarmGroupEntry.COLUMN_ACTIVE} INTEGER," +
                        "${AlarmGroupEntry.COLUMN_SOUND} INTEGER," +
                        "${AlarmGroupEntry.COLUMN_RINGTONE_URI} TEXT," +
                        "${AlarmGroupEntry.COLUMN_DAYS_IN_WEEK} VARCHAR(15) DEFAULT [0,0,0,0,0,0,0]," +
                        "${AlarmGroupEntry.COLUMN_VIBRATE} INTEGER," +
                        "${AlarmGroupEntry.COLUMN_VOLUME} INTEGER," +
                        "${AlarmGroupEntry.COLUMN_SNOOZE_DURATION} INTEGER)"

        private const val SQL_CREATE_TIME_ENTRIES =
                "CREATE TABLE ${AlarmTimeEntry.TABLE_NAME} " +
                        "(${AlarmTimeEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "${AlarmTimeEntry.COLUMN_TIME} INTEGER," +
                        "${AlarmTimeEntry.COLUMN_GROUP_ID} INTEGER)"

        private const val SQL_CREATE_ALARM_ENTRIES =
                "CREATE TABLE ${AlarmEntry.TABLE_NAME} " +
                        "(${AlarmEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "${AlarmEntry.COLUMN_UNIX_TIME} INTEGER," +
                        "${AlarmEntry.COLUMN_ALARM_ID} INTEGER," +
                        "${AlarmEntry.COLUMN_TIME_ID} INTEGER," +
                        "${AlarmEntry.COLUMN_GROUP_ID} INTEGER," +
                        "${AlarmEntry.COLUMN_IS_SNOOZE} INTEGER)"


        private const val SQL_DELETE_GROUP_ENTRIES = "DROP TABLE IF EXISTS ${AlarmGroupEntry.TABLE_NAME}"
        private const val SQL_DELETE_TIME_ENTRIES = "DROP TABLE IF EXISTS ${AlarmTimeEntry.TABLE_NAME}"
        private const val SQL_DELETE_ALARM_ENTRIES = "DROP TABLE IF EXISTS ${AlarmEntry.TABLE_NAME}"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL(SQL_CREATE_GROUP_ENTRIES)
        db.execSQL(SQL_CREATE_TIME_ENTRIES)
        db.execSQL(SQL_CREATE_ALARM_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db!!.execSQL(SQL_DELETE_GROUP_ENTRIES)
        db.execSQL(SQL_DELETE_TIME_ENTRIES)
        db.execSQL(SQL_DELETE_ALARM_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

}