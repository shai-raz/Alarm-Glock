package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import java.text.SimpleDateFormat
import java.util.*

/* Extension Functions */
fun Boolean.toInt() = if (this) 1 else 0
//fun <T> List<T>.hasDuplicates() =

object AlarmUtils {

    private val LOG_TAG = this.javaClass.simpleName!!

    /**
     * Set all Alarms of a group
     * @param context Activity Context
     * @param groupId The ID of the group
     */
    fun setGroupAlarms(context: Context, groupId: Int, dayOfWeek: Int? = null, nextWeek: Boolean = false) {
        val alarmProjection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                AlarmTimeEntry.COLUMN_TIME)
        val alarmCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                alarmProjection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        val daysOfWeek: List<Int>
        if (dayOfWeek == null) {
            val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
            val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                    groupProjection,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(groupId.toString()),
                    null)

            groupCursor.moveToFirst()
            val daysOfWeekString = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK))
            daysOfWeek = getDOWArray(daysOfWeekString)
            groupCursor.close()
        } else {
            daysOfWeek = listOfSpecificDay(dayOfWeek)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var time: Int
        var timeId: Int
        while (alarmCursor.moveToNext()) {
            time = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            timeId = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            setAlarm(context.applicationContext, time, timeId, groupId, daysOfWeek, alarmManager, nextWeek = nextWeek)
        }

        alarmCursor.close()
    }

    /**
     * Set an Alarm for a specific day in week (used for repeating Alarms to set themselves for the next week)
     * @param appContext    Context: From getApplicationContext() / applicationContext - used for setting the alarms
     * @param time          Int: The time in which the Alarm should go off
     * @param timeId        Int: The ID of the time in the DB - used to make the Alarm Intent unique
     * @param groupId       Int: The ID of the group - for basic settings
     * @param daysOfWeek    The Days of Week in which the Alarm should repeat - no repeating days will result in a one-time alarm
     * @param alarmManager  AlarmManager: An AlarmManager instance
     */
    fun setAlarm(appContext: Context, time: Int, timeId: Int, groupId: Int, daysOfWeek: List<Int>, alarmManager: AlarmManager, specificAlarmTime: Calendar? = null, nextWeek: Boolean = false) {
        val now = Calendar.getInstance()

        var alarmTime = specificAlarmTime ?: Calendar.getInstance().apply {
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
                                    "${timeId}0".toInt(),
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    "${timeId}0".toInt(),
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))

                else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                    alarmManager.set(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    "${timeId}0".toInt(),
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_UPDATE_CURRENT))
            }
            Log.i(LOG_TAG, "Next Alarm is set for ${SimpleDateFormat("dd.MM.yyyy HH:mm").format(alarmTime.time)}, [${timeId}0]")
        } else { // If the Alarm does repeat for certain days
            for (i in 0 until daysOfWeek.size-1) {
                alarmTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, minutesInDayToHours(time).toInt())
                    set(Calendar.MINUTE, minutesInDayToMinutes(time).toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.DAY_OF_WEEK, i+1)
                    //Log.i(LOG_TAG, daysOfWeek.toString())
                    if (nextWeek == true) {
                        add(Calendar.DAY_OF_YEAR, 7)
                    }
                }

                if (daysOfWeek[i] == 1) { // Set Repeating for a certain day
                    Log.i(LOG_TAG, "Day of week(i) = $i (${i+1})")
                    Log.i(LOG_TAG, "System.currentTimeMillis(): ${System.currentTimeMillis()}, alarmTime.timeInMillis: ${alarmTime.timeInMillis}")
                    if (System.currentTimeMillis() > alarmTime.timeInMillis) {
                    //if (alarmTime.before(now)) { // If the chosen time is before the current time, make it run the next week
                        Log.i(LOG_TAG, "System.currentTimeMillis() > alarmTime.timeInMillis = true")
                        alarmTime.add(Calendar.DAY_OF_YEAR, 7)
                    }
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> // For API >= 23, allow the alarm to start when in Doze & be Exact
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            "$timeId${i+1}".toInt(),
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))

                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            "$timeId${i+1}".toInt(),
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))

                        else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                            alarmManager.set(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    PendingIntent.getBroadcast(appContext,
                                            "$timeId${i+1}".toInt(),
                                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                                    .putExtra("groupId", groupId)
                                                    .putExtra("timeId", timeId),
                                            PendingIntent.FLAG_UPDATE_CURRENT))
                    }
                    Log.i(LOG_TAG, "(Repeating) Next Alarm is set for ${SimpleDateFormat("dd.MM.yyyy HH:mm").format(alarmTime.time)} [$timeId${i+1}]")
                }
            }
        }
    }

    /**
     * Set an Alarm for a specific day in week (used for repeating Alarms to set themselves for the next week)
     * @param appContext    Context: From getApplicationContext() / applicationContext - used for setting the alarms
     * @param time          Int: The time in which the Alarm should go off
     * @param timeId        Int: The ID of the time in the DB - used to make the Alarm Intent unique
     * @param groupId       Int: The ID of the group - for basic settings
     * @param dayOfWeek     Int: The day of week in which the alarm should be set to
     * @param alarmManager  AlarmManager: An AlarmManager instance
     */
    fun setAlarmForDayInWeek(appContext: Context, time: Int, timeId: Int, groupId: Int, dayOfWeek: Int, alarmManager: AlarmManager) {
        val daysOfWeek = mutableListOf(0, 0, 0, 0, 0, 0, 0)
        daysOfWeek[dayOfWeek-1] = 1
        setAlarm(appContext, time, timeId, groupId, daysOfWeek, alarmManager)
    }

    /**
     * Cancel all Alarms of a group
     * @param context       Context: Activity Context
     * @param groupId       Int: The ID of the group
     * @param dayOfWeek     Int?: A specific day to cancel alarms for
     * @param setInactive   Boolean: Default `false`. If set to `true`, set the group as inactive in the DB
     */
    fun cancelGroupAlarms(context: Context, groupId: Int, dayOfWeek: Int? = null, setInactive: Boolean = false) {
        if (setInactive) {
            val values = ContentValues()
            values.put(AlarmGroupEntry.COLUMN_ACTIVE, 0)
            context.contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                    values,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(groupId.toString()))
        }

        val daysOfWeek: List<Int>

        if (dayOfWeek == null) {
            val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
            val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                    groupProjection,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(groupId.toString()),
                    null)

            groupCursor.moveToFirst()
            daysOfWeek = getDOWArray(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)))
            groupCursor.close()
        } else {
            daysOfWeek = listOfSpecificDay(dayOfWeek)
            Log.i(LOG_TAG, "daysOfWeek: $daysOfWeek")
        }

        val timeProjection = arrayOf(AlarmTimeEntry.COLUMN_ID)
        val timeCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                timeProjection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        while(timeCursor.moveToNext()) {
            val timeId = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            cancelAlarm(context, timeId, daysOfWeek)
        }

        timeCursor.close()

    }

    /**
     * Cancel a single Alarm (one "Time")
     * @param appContext From getApplicationContext() / (applicationContext) - used for setting the alarms
     * @param timeId     The ID of the time in the DB
     */
    private fun cancelAlarm(appContext: Context, timeId: Int, daysOfWeek: List<Int>? = null) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        var pendingIntent: PendingIntent

        if (daysOfWeek != null && isRepeating(daysOfWeek)) {
            for (i in 0 until daysOfWeek.size-1) {
                if (daysOfWeek[i] == 1) {
                    pendingIntent = PendingIntent.getBroadcast(appContext,
                            "$timeId${i+1}".toInt(),
                            Intent(appContext, AlarmBroadcastReceiver::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT)

                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.i(LOG_TAG, "Alarm canceled [$timeId${i+1}]")
                }
            }
        } else {
            pendingIntent = PendingIntent.getBroadcast(appContext,
                    "${timeId}0".toInt(),
                    Intent(appContext, AlarmBroadcastReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }



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
        return daysOfWeekStringArray.map { it.trim().toInt() }
    }

    fun isRepeating(daysOfWeek: List<Int>) = daysOfWeek.contains(1)

    fun listOfSpecificDay(dayOfWeek: Int): List<Int> {
        val daysOfWeek = mutableListOf(0, 0, 0, 0, 0, 0, 0)
        if (dayOfWeek != -1) {
            daysOfWeek[dayOfWeek - 1] = 1
        }
        return daysOfWeek
    }

    /**
     * Returns a composed string of time till next alarm
     */
    fun getNextAlarmString(context: Context): String {
        val nextAlarm = getNextAlarm(context)
        val timeTillNextAlarm = getTimeTillNextAlarm(nextAlarm)

        if (timeTillNextAlarm != null) {
            var nextAlarmString = context.getString(R.string.main_next_alarm_in)
            val seconds = timeTillNextAlarm.get(Calendar.SECOND)
            val minutes = timeTillNextAlarm.get(Calendar.MINUTE)
            var hours = timeTillNextAlarm.get(Calendar.HOUR_OF_DAY) - 2
            var days = timeTillNextAlarm.get(Calendar.DAY_OF_YEAR) - 1

            if (days != 0) {
                if (days == 1) { // could be less than 1 day (e.g 23 hours)
                    if (hours <= 0) {
                        hours += 24
                        days -= 1
                    } else {
                        nextAlarmString += context.getString(R.string.main_in_days, days)
                    }
                } else if (days == 7) {
                    if (hours <= 0) {
                        hours += 24
                        days -= 1
                        nextAlarmString += context.getString(R.string.main_in_days, days)
                    }
                } else {
                    nextAlarmString += context.getString(R.string.main_in_days, days)
                }
            }
            if (hours != 0) {
                nextAlarmString += context.getString(R.string.main_in_hours, hours)
            }
            if (minutes != 1) {
                nextAlarmString += context.getString(R.string.main_in_minutes, minutes)
            }
            nextAlarmString += context.getString(R.string.main_in_seconds, seconds)

            return nextAlarmString
        } else {
            return context.getString(R.string.main_no_active_alarms)
        }
    }

    /**
     * Returns a Calendar instance of the closest active alarm
     */
    private fun getNextAlarm(context: Context): Calendar? {
        val projection = arrayOf(AlarmGroupEntry.COLUMN_ID,
                AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
        val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                projection,
                "${AlarmGroupEntry.COLUMN_ACTIVE}=?",
                arrayOf("1"),
                null)

        if (groupCursor != null) { // If there are any groups
            var calendar: Calendar
            var closestCalendar: Calendar? = null
            while (groupCursor.moveToNext()) { // Run through all the groups
                val daysOfWeek = getDOWArray(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)))
                val groupId = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
                if (daysOfWeek.contains(1)) { // If it is a repeating alarm
                    val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    daysOfWeek.forEachIndexed { i, active -> // Run through the days
                        if (active == 1) {
                           // if (i+1 >= currentDayOfWeek) {
                            val timeCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                                    arrayOf(AlarmTimeEntry.COLUMN_ID,
                                            AlarmTimeEntry.COLUMN_TIME),
                                    "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                                    arrayOf(groupId.toString()),
                                    AlarmTimeEntry.COLUMN_TIME)

                            while(timeCursor.moveToNext()) {
                                calendar = Calendar.getInstance()
                                val timeId = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
                                val cursorClosestTime = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                                calendar.apply {
                                    set(Calendar.HOUR_OF_DAY, minutesInDayToHours(cursorClosestTime).toInt())
                                    set(Calendar.MINUTE, minutesInDayToMinutes(cursorClosestTime).toInt())
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.DAY_OF_WEEK, i+1)
                                }

                                if (calendar.before(Calendar.getInstance()) || !isAlarmExist(context.applicationContext, "$timeId${i+1}".toInt())) {
                                    calendar.add(Calendar.DAY_OF_YEAR, 7)
                                }

                                if (closestCalendar == null || calendar.before(closestCalendar)) {

                                    closestCalendar = calendar
                                }
                            }
                            timeCursor.close()
                           // }
                        }
                    }
                } else { // If it is a non-repeating alarm
                    val timeCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                            arrayOf(AlarmTimeEntry.COLUMN_TIME),
                            "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                            arrayOf(groupId.toString()),
                            AlarmTimeEntry.COLUMN_TIME)

                    while(timeCursor.moveToNext()) {
                        calendar = Calendar.getInstance()
                        val cursorClosestTime = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                        calendar.apply {
                            set(Calendar.HOUR_OF_DAY, minutesInDayToHours(cursorClosestTime).toInt())
                            set(Calendar.MINUTE, minutesInDayToMinutes(cursorClosestTime).toInt())
                            set(Calendar.SECOND, 0)
                        }
                        if (calendar.before(Calendar.getInstance())) {
                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                        }
                        if (closestCalendar == null || calendar.before(closestCalendar)) {
                            closestCalendar = calendar
                        }
                    }

                    timeCursor.close()
                }
            }
            groupCursor.close()
            return closestCalendar
        } else {
            Log.i(LOG_TAG, "groupCursor == null")
            return null
        }
    }

    /**
     * Returns a Calendar instance with difference between now and the next alarm
     */
    private fun getTimeTillNextAlarm(nextAlarm: Calendar?): Calendar? {
        if (nextAlarm != null) {
            val nextAlarmMillis = nextAlarm.timeInMillis
            val now = Calendar.getInstance().timeInMillis
            val timeTillNextAlarmMillis = nextAlarmMillis - now
            val timeTillNextAlarmCalendar = Calendar.getInstance()
            timeTillNextAlarmCalendar.timeInMillis = timeTillNextAlarmMillis

            Log.i(LOG_TAG, "Time till next alarm: $timeTillNextAlarmCalendar")
            return timeTillNextAlarmCalendar
        } else {
            Log.i(LOG_TAG, "nextAlarm == null")
            return null
        }
    }


    private fun isAlarmExist(appContext: Context, alarmId: Int): Boolean {
        return (PendingIntent.getBroadcast(appContext,
                alarmId,
                Intent(appContext, AlarmBroadcastReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE) != null)
    }
}