package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.waker.data.AlarmContract.AlarmEntry
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import java.text.SimpleDateFormat
import java.util.*

/* Extension Functions */
fun Boolean.toInt() = if (this) 1 else 0
//fun <T> List<T>.hasDuplicates() =

object AlarmUtils {

    private val LOG_TAG = this.javaClass.simpleName

    /**
     * Set all Alarms of a group
     * @param context   Activity Context
     * @param groupId   The ID of the group
     */
    fun setGroupAlarms(context: Context, groupId: Int, dayOfWeek: Int? = null, nextWeek: Boolean = false) {
        val alarmProjection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                AlarmTimeEntry.COLUMN_TIME)
        val alarmCursor = context.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                alarmProjection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                null)

        var daysOfWeek: List<Int> = listOfSpecificDay(-1)
        if (dayOfWeek == null) {
            val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
            val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                    groupProjection,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(groupId.toString()),
                    null)

            if (groupCursor?.moveToFirst() == true) {
                val daysOfWeekString = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK))
                daysOfWeek = getDOWArray(daysOfWeekString)
            }
            groupCursor?.close()
        } else {
            daysOfWeek = listOfSpecificDay(dayOfWeek)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var time: Int
        var timeId: Int
        while (alarmCursor?.moveToNext() == true) {
            time = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            timeId = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            setAlarm(context.applicationContext, time, timeId, groupId, daysOfWeek, alarmManager, nextWeek = nextWeek)
        }

        alarmCursor?.close()
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
    fun setAlarm(appContext: Context, time: Int, timeId: Int, groupId: Int, daysOfWeek: List<Int>,
                 alarmManager: AlarmManager, specificAlarmTime: Calendar? = null, nextWeek: Boolean = false,
                 isSnooze: Boolean = false) {
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
            val pendingIntent = PendingIntent.getBroadcast(appContext,
                    "${timeId}0".toInt(),
                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                            .putExtra("groupId", groupId)
                            .putExtra("timeId", timeId)
                            .putExtra("alarmId", "${timeId}0".toInt()),
                    PendingIntent.FLAG_CANCEL_CURRENT)
            when { // Check the current Android Version, and set the alarm to work at an exact time, even in Doze
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> // For API >= 23, allow the alarm to start when in Doze & be Exact
                    /*alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            PendingIntent.getBroadcast(appContext,
                                    "${timeId}0".toInt(),
                                    Intent(appContext, AlarmBroadcastReceiver::class.java)
                                            .putExtra("groupId", groupId)
                                            .putExtra("timeId", timeId),
                                    PendingIntent.FLAG_CANCEL_CURRENT))*/
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarmTime.timeInMillis, pendingIntent),
                            pendingIntent)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            pendingIntent)

                else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                    alarmManager.set(AlarmManager.RTC_WAKEUP,
                            alarmTime.timeInMillis,
                            pendingIntent)
            }

            val values = ContentValues()
            values.put(AlarmEntry.COLUMN_UNIX_TIME, alarmTime.timeInMillis)
            values.put(AlarmEntry.COLUMN_ALARM_ID, "${timeId}0".toInt())
            values.put(AlarmEntry.COLUMN_TIME_ID, timeId)
            values.put(AlarmEntry.COLUMN_GROUP_ID, groupId)
            values.put(AlarmEntry.COLUMN_IS_SNOOZE, isSnooze.toInt())
            appContext.contentResolver.insert(AlarmEntry.CONTENT_URI, values)

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
                    val pendingIntent = PendingIntent.getBroadcast(appContext,
                            "$timeId${i+1}".toInt(),
                            Intent(appContext, AlarmBroadcastReceiver::class.java)
                                    .putExtra("groupId", groupId)
                                    .putExtra("timeId", timeId)
                                    .putExtra("alarmId", "$timeId${i+1}".toInt()),
                            PendingIntent.FLAG_CANCEL_CURRENT)
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->// For API >= 23, allow the alarm to start when in Doze & be Exact
                            /*alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    pendingIntent)*/
                            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarmTime.timeInMillis, pendingIntent),
                                    pendingIntent)

                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> // For API >= 19, make the alarm Exact
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    pendingIntent)

                        else -> // For API < 19, the alarm will already be exact (& no Doze mode)
                            alarmManager.set(AlarmManager.RTC_WAKEUP,
                                    alarmTime.timeInMillis,
                                    pendingIntent)
                    }

                    val values = ContentValues()
                    values.put(AlarmEntry.COLUMN_UNIX_TIME, alarmTime.timeInMillis)
                    values.put(AlarmEntry.COLUMN_ALARM_ID, "$timeId${i+1}".toInt())
                    values.put(AlarmEntry.COLUMN_TIME_ID, timeId)
                    values.put(AlarmEntry.COLUMN_GROUP_ID, groupId)
                    values.put(AlarmEntry.COLUMN_IS_SNOOZE, isSnooze.toInt())
                    appContext.contentResolver.insert(AlarmEntry.CONTENT_URI, values)

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

        var daysOfWeek: List<Int> = listOfSpecificDay(-1)

        if (dayOfWeek == null) {
            val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)
            val groupCursor = context.contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                    groupProjection,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(groupId.toString()),
                    null)

            if (groupCursor?.moveToFirst() == true) {
                daysOfWeek = getDOWArray(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)))
            }
            groupCursor?.close()
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

        while(timeCursor?.moveToNext() == true) {
            val timeId = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            cancelAlarm(context, timeId, daysOfWeek)
        }

        timeCursor?.close()

    }

    /**
     * Cancel a single Alarm (one "Time")
     * @param appContext From getApplicationContext() / (applicationContext) - used for setting the alarms
     * @param timeId     The ID of the time in the DB
     */
    private fun cancelAlarm(appContext: Context, timeId: Int, daysOfWeek: List<Int>? = null) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        var pendingIntent: PendingIntent

        if (daysOfWeek != null && isRepeating(daysOfWeek)) { // If the alarm repeats
            for (i in 0 until daysOfWeek.size-1) {
                if (daysOfWeek[i] == 1) {
                    pendingIntent = PendingIntent.getBroadcast(appContext,
                            "$timeId${i+1}".toInt(),
                            Intent(appContext, AlarmBroadcastReceiver::class.java),
                            PendingIntent.FLAG_CANCEL_CURRENT)

                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.i(LOG_TAG, "Alarm canceled [$timeId${i+1}]")

                    appContext.contentResolver.delete(AlarmEntry.CONTENT_URI,
                            "${AlarmEntry.COLUMN_ALARM_ID}=?",
                            arrayOf("$timeId${i+1}"))
                }
            }
        } else {
            pendingIntent = PendingIntent.getBroadcast(appContext,
                    "${timeId}0".toInt(),
                    Intent(appContext, AlarmBroadcastReceiver::class.java),
                    PendingIntent.FLAG_CANCEL_CURRENT)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            appContext.contentResolver.delete(AlarmEntry.CONTENT_URI,
                    "${AlarmEntry.COLUMN_ALARM_ID}=?",
                    arrayOf("${timeId}0"))
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

    fun getNextAlarmDiff(context: Context): Long? {
        val nextAlarmInMillis: Long = getNextAlarmInMillis(context) ?: return null
        val nowInMillis = System.currentTimeMillis()

        return nextAlarmInMillis - nowInMillis
    }

    fun getNextAlarmString(context: Context, diffInMillis: Long): String {
        var diffString = context.getString(R.string.main_next_alarm_in)

        val days = diffInMillis / (1000 * 60 * 60 * 24)
        val hours = (diffInMillis / (1000 * 60 * 60)) % 24
        val minutes = (diffInMillis / (1000 * 60)) % 60
        val seconds = (diffInMillis / 1000) % 60

        if (days > 0) {
            diffString += context.getString(R.string.main_in_days, days)
        }
        if (hours > 0) {
            diffString += context.getString(R.string.main_in_hours, hours)
        }
        if (minutes > 0) {
            diffString += context.getString(R.string.main_in_minutes, minutes)
        }
        if (minutes == 0L && seconds > 0) {
            diffString += context.getString(R.string.main_in_seconds, seconds)
        }

        return diffString
    }

    private fun getNextAlarmInMillis(context: Context): Long? {
        val projection = arrayOf(AlarmEntry.COLUMN_UNIX_TIME)
        val alarmCursor = context.contentResolver.query(AlarmEntry.CONTENT_URI,
                projection,
                null,
                null,
                "${AlarmEntry.COLUMN_UNIX_TIME} ASC")

        if (alarmCursor != null) {
            if (alarmCursor.moveToFirst()) {
                val nextAlarm = alarmCursor.getLong(alarmCursor.getColumnIndex(AlarmEntry.COLUMN_UNIX_TIME))
                alarmCursor.close()

                return nextAlarm
            }
        }

        return null
    }

    private fun isAlarmExist(appContext: Context, alarmId: Int): Boolean {
        return (PendingIntent.getBroadcast(appContext,
                alarmId,
                Intent(appContext, AlarmBroadcastReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE) != null)
    }
}