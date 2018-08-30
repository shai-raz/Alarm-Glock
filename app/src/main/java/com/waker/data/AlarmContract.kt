package com.waker.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.BaseColumns

object AlarmContract {
    const val CONTENT_AUTHORITY = "com.waker"
    val BASE_CONTENT_URI: Uri = Uri.parse("content://$CONTENT_AUTHORITY")
    const val PATH_ALARM_GROUP = "alarm_group"
    const val PATH_ALARM_TIME = "alarm_time"
    const val PATH_ALARM = "alarm"

    class AlarmGroupEntry: BaseColumns {

        companion object {
            val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALARM_GROUP)
            const val CONTENT_LIST_TYPE =
                    ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM_GROUP
            const val CONTENT_ITEM_TYPE =
                    ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM_GROUP

            const val TABLE_NAME = "alarm_groups"
            const val COLUMN_ID = BaseColumns._ID
            const val COLUMN_NAME = "name"
            const val COLUMN_ACTIVE = "active"
            const val COLUMN_SOUND = "sound"
            const val COLUMN_RINGTONE_URI = "ringtone_uri"
            const val COLUMN_DAYS_IN_WEEK = "days_in_week"

            // Advanced Settings
            const val COLUMN_VIBRATE = "vibrate"
            const val COLUMN_VOLUME = "volume"
            const val COLUMN_SNOOZE_DURATION = "snooze_duration"
        }

    }

    class AlarmTimeEntry: BaseColumns {

        companion object {
            val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALARM_TIME)
            const val CONTENT_LIST_TYPE =
                    ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM_TIME
            const val CONTENT_ITEM_TYPE =
                    ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM_TIME

            const val TABLE_NAME = "alarm_times"
            const val COLUMN_ID = BaseColumns._ID
            const val COLUMN_TIME = "time"
            const val COLUMN_GROUP_ID = "group_id"
        }

    }

    class AlarmEntry: BaseColumns {

        companion object {
            val CONTENT_URI: Uri = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALARM)
            const val CONTENT_LIST_TYPE =
                    ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM
            const val CONTENT_ITEM_TYPE =
                    ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALARM

            const val TABLE_NAME = "alarms"
            const val COLUMN_ID = BaseColumns._ID
            const val COLUMN_UNIX_TIME = "unix_time"
            const val COLUMN_ALARM_ID = "alarm_id"
            const val COLUMN_TIME_ID = "time_id"
            const val COLUMN_GROUP_ID = "group_id"
            const val COLUMN_IS_SNOOZE = "is_snooze"
        }
    }
}