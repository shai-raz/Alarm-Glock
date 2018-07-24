package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import java.util.*


object AlarmUtils {

    /**
     * Set all Alarms of a group
     * @param context Activity Context
     * @param groupId The ID of the group
     */
    fun setGroupAlarms(context: Context, groupId: Int) {
        val alarmProjection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                AlarmTimeEntry.COLUMN_TIME)
        val alarmCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                alarmProjection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
        val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                groupProjection,
                "${AlarmGroupEntry.COLUMN_ID}=?",
                arrayOf(groupId.toString()),
                null)

        groupCursor.moveToFirst()
        val daysOfWeekString = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK))
        val daysOfWeek = getDOWArray(daysOfWeekString)
        groupCursor.close()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var time: Int
        var timeId: Int
        while (alarmCursor.moveToNext()) {
            time = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            timeId = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            setAlarm(context.applicationContext, time, timeId, groupId, daysOfWeek, alarmManager)
        }

        alarmCursor.close()
    }

    /**
     * Set an Alarm for a specific day in week (used for repeating Alarms to set themselves for the next week)
     * @param appContext Context: From getApplicationContext() / applicationContext - used for setting the alarms
     * @param time Int: The time in which the Alarm should go off
     * @param timeId Int: The ID of the time in the DB - used to make the Alarm Intent unique
     * @param groupId Int: The ID of the group - for basic settings
     * @param daysOfWeek The Days of Week in which the Alarm should repeat - no repeating days will result in a one-time alarm
     * @param alarmManager AlarmManager: An AlarmManager instance
     */
    fun setAlarm(appContext: Context, time: Int, timeId: Int, groupId: Int, daysOfWeek: List<Int>, alarmManager: AlarmManager) {
        val now = Calendar.getInstance()

        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesInDayToHours(time).toInt())
            set(Calendar.MINUTE, minutesInDayToMinutes(time).toInt())
            set(Calendar.SECOND, 0)
        }

        if (!isRepeating(daysOfWeek)) { // If the Alarm does not repeat (Closest occurrence will be applied as a one-time alarm)
            if (alarmTime.before(now)) { // If the chosen time is before the current time, have it run the next day
                alarmTime.add(Calendar.DAY_OF_MONTH, 1)
            }
            when { // Check the current Android Version, and set the alarm to work at an exact time, even in Doze
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> // For API >= 23, allow the alarm to start when in Doze & be Exact
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    timeId,
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    timeId,
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))

                else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                    alarmManager.set(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    timeId,
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))
            }
        } else { // If the Alarm does repeat for certain days
            for (i in daysOfWeek) {
                alarmTime.set(Calendar.DAY_OF_WEEK, i+1)
                if (i == 1) { // Set Repeating for a certain day
                    if (alarmTime.before(now)) { // If the chosen time is before the current time, make it run the next week
                        alarmTime.add(Calendar.DAY_OF_YEAR, 7)
                    }
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> // For API >= 23, allow the alarm to start when in Doze & be Exact
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            timeId,
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))

                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            timeId,
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))

                        else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                            alarmManager.set(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            timeId,
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))
                    }
                }
            }
        }
    }

    /**
     * Set an Alarm for a specific day in week (used for repeating Alarms to set themselves for the next week)
     * @param appContext Context: From getApplicationContext() / applicationContext - used for setting the alarms
     * @param time Int: The time in which the Alarm should go off
     * @param timeId Int: The ID of the time in the DB - used to make the Alarm Intent unique
     * @param groupId Int: The ID of the group - for basic settings
     * @param dayOfWeek Int: The day of week in which the alarm should be set to
     * @param alarmManager AlarmManager: An AlarmManager instance
     */
    fun setAlarmForDayInWeek(appContext: Context, time: Int, timeId: Int, groupId: Int, dayOfWeek: Int, alarmManager: AlarmManager) {
        val daysOfWeek = mutableListOf(0, 0, 0, 0, 0, 0, 0)
        daysOfWeek[dayOfWeek-1] = 1
        setAlarm(appContext, time, timeId, groupId, daysOfWeek, alarmManager)
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
    fun cancelAlarm(appContext: Context, timeId: Int) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = PendingIntent.getBroadcast(appContext,
                timeId,
                Intent(appContext, AlarmBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.cancel(pendingIntent)

        pendingIntent.cancel()
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

    fun getDOWArray(daysOfWeek: String): List<Int> {
        var daysOfWeekString = daysOfWeek
        daysOfWeekString = daysOfWeekString.replace("[","")
        daysOfWeekString = daysOfWeekString.replace("]","")
        daysOfWeekString = daysOfWeekString.replace("\\s+","")
        val daysOfWeekStringArray = daysOfWeekString.split(",")
        return daysOfWeekStringArray.map { it.toInt() }
    }

    fun isRepeating(daysOfWeek: List<Int>) = daysOfWeek.contains(1)
}