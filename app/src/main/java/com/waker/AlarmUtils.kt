package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.waker.data.AlarmContract.AlarmTimeEntry
import java.util.*


object AlarmUtils {

    /**
     * Set all Alarms of a group
     * @param context Activity Context
     * @param groupId The ID of the group
     */
    fun setGroupAlarms(context: Context, groupId: Int) {
        val projection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                AlarmTimeEntry.COLUMN_TIME)
        val alarmCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                projection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var time: Int
        var timeId: Int
        while (alarmCursor.moveToNext()) {
            time = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            timeId = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            setAlarm(context.applicationContext, time, timeId, groupId, alarmManager)
        }

        alarmCursor.close()
    }

    /**
     * Set a single Alarm (one "Time")
     * @param appContext From getApplicationContext() / (applicationContext) - used for setting the alarms
     * @param time The time in which the Alarm should go off
     * @param timeId The ID of the time in the DB - used to make the Alarm Intent unique
     * @param groupId The ID of the group - for basic settings
     * @param alarmManager An instance of an AlarmManager
     */
    fun setAlarm(appContext: Context, time: Int, timeId: Int, groupId: Int, alarmManager: AlarmManager) {
        val now = Calendar.getInstance()

        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesInDayToHours(time).toInt())
            set(Calendar.MINUTE, minutesInDayToMinutes(time).toInt())
            set(Calendar.SECOND, 0)
        }

        if (alarmTime.before(now)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1)
        }

        alarmManager.set(AlarmManager.RTC_WAKEUP,
                alarmTime.timeInMillis,
                PendingIntent.getBroadcast(appContext,
                        timeId,
                        Intent(appContext, AlarmBroadcastReceiver::class.java).
                                putExtra("groupId", groupId),
                        PendingIntent.FLAG_CANCEL_CURRENT))
    }

    /**
     * Cancel all Alarms of a group
     * @param context Activity Context
     * @param groupId The ID of the group
     */
    fun cancelGroupAlarms(context: Context, groupId: Int) {
        val projection = arrayOf(AlarmTimeEntry.COLUMN_ID)
        val alarmCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                projection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        while(alarmCursor.moveToNext()) {
            cancelAlarm(context, alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID)))
        }

        alarmCursor.close()

    }

    /**
     * Cancel a single Alarm (one "Time")
     * @param appContext From getApplicationContext() / (applicationContext) - used for setting the alarms
     * @param timeId The ID of the time in the DB
     */
    private fun cancelAlarm(appContext: Context, timeId: Int) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(PendingIntent.getBroadcast(appContext,
                timeId,
                Intent(appContext, AlarmBroadcastReceiver::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT))
    }

    /**
     * Converts minutes in day to HH:mm format
     * @param time Minutes in day (e.g. 1 for 00:01)
     */
    fun minutesInDayTo24(time: Int): String {
        return "${minutesInDayToHours(time)}:${minutesInDayToMinutes(time)}"
    }

    fun minutesInDayToHours(time: Int): String {
        var hours = (time / 60).toString()
        if (hours.length == 1) {
            hours = "0$hours"
        }

        return hours
    }

    fun minutesInDayToMinutes(time: Int): String {
        var minutes = (time % 60).toString()
        if (minutes.length == 1) {
            minutes = "0$minutes"
        }

        return minutes
    }

    fun getMinutesInDay(hours: Int, minutes: Int): Int {
        return hours * 60 + minutes
    }
}