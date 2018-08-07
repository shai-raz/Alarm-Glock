package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator.MAX_VOLUME
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.waker.AlarmUtils.getMinutesInDay
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.alarm_layout.*
import java.util.*


class AlarmActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mAlarmNameTextView: TextView
    private lateinit var mDismissGroupButton: Button
    private lateinit var mSnoozeButton: Button
    private lateinit var mDismissButton: Button
    private lateinit var mAudioManager: AudioManager
    private var mMediaPlayer: MediaPlayer? = null
    private var mVibrator: Vibrator? = null
    private lateinit var mDaysOfWeek: List<Int>

    private lateinit var mCalendar: Calendar

    private var mGroupId = 0
    private var mTimeId = 0
    private var mIsRepeating = false
    private var mSnoozeDuration = 1
    private var mUserRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var mUserVolume: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.alarm_layout)

        mGroupId = intent!!.getIntExtra("groupId", 0)
        mTimeId = intent!!.getIntExtra("timeId", 0)
        Log.i(LOG_TAG, "groupId: $mGroupId, timeId: $mTimeId")

        mAlarmNameTextView = alarm_name_text_view
        mDismissGroupButton = alarm_dismiss_group_button
        mSnoozeButton = alarm_snooze_button
        mDismissButton = alarm_dismiss_button

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mCalendar = Calendar.getInstance()

        mUserRingerMode = mAudioManager.ringerMode
        mUserVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        alarm()

        if (isMoreAlarms(false)) { // Disable Snooze Button (GONE) if this Alarm is repeating, or not the last in group
            mSnoozeButton.visibility = View.GONE
        }

        if (!isMoreAlarms(false)) {
            mDismissGroupButton.visibility = View.GONE
        }

        mDismissGroupButton.setOnClickListener {
            dismissGroup()
        }

        mSnoozeButton.setOnClickListener {
            snoozeAlarm()
        }

        mDismissButton.setOnClickListener {
            dismissAlarm()
        }

        /*val player = MediaPlayer.create(this, RingtoneManager.getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE))
        player.prepare()
        player.start()*/

        /*val defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE)
        Log.i("AlarmActivity", "RingtoneUri: $defaultRingtoneUri")

        val notificationId = intent.getIntExtra("notificationId", 0)
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mediaPlayer.setDataSource(applicationContext, Uri.parse("/system/media/audio/ringtones/MI.ogg"))
        mediaPlayer.prepare()
        mediaPlayer.start()*/
    }

    override fun onBackPressed() {
    }

    override fun onAttachedToWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    private fun alarm() {
        val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_NAME,
                AlarmGroupEntry.COLUMN_SOUND,
                AlarmGroupEntry.COLUMN_RINGTONE_URI,
                AlarmGroupEntry.COLUMN_DAYS_IN_WEEK,
                AlarmGroupEntry.COLUMN_VIBRATE,
                AlarmGroupEntry.COLUMN_VOLUME,
                AlarmGroupEntry.COLUMN_SNOOZE_DURATION)

        val groupCursor = contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                groupProjection,
                "${AlarmGroupEntry.COLUMN_ID}=?",
                arrayOf(mGroupId.toString()),
                null)

        if (groupCursor.moveToFirst()) {
            val name: String = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_NAME))
            val ringtoneUri: String = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI))
            val isSound: Boolean = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_SOUND)) == 1
            val isVibrate: Boolean = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_VIBRATE)) == 1
            val volume: Int = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_VOLUME))
            mSnoozeDuration = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_SNOOZE_DURATION))

            mAlarmNameTextView.text = name

            if (isSound) {
                val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) // max volume = 15

                //mAudioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                mMediaPlayer = MediaPlayer.create(this, Uri.parse(ringtoneUri))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mMediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build())
                } else {
                    mMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                val logVolume: Float = (1 - (Math.log((MAX_VOLUME - volume).toDouble()) / Math.log(MAX_VOLUME.toDouble()))).toFloat()
                mMediaPlayer!!.setVolume(logVolume, logVolume)
                mMediaPlayer!!.start()
            }

            if (isVibrate) {
                mVibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mVibrator!!.vibrate(VibrationEffect.createWaveform(longArrayOf(1000, 800, 150, 0), 0))
                } else {
                    mVibrator!!.vibrate(longArrayOf(1000, 800, 150, 0), 0)
                }
            }

            val daysOfWeekString = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK))
            mDaysOfWeek = AlarmUtils.getDOWArray(daysOfWeekString)
            if (AlarmUtils.isRepeating(mDaysOfWeek)) { // If the alarm is set to repeat, set the next week's Alarm
                Log.i(LOG_TAG, "mIsRepeating = true")
                mIsRepeating = true

                val timeProjection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                        AlarmTimeEntry.COLUMN_TIME)

                val timeCursor = contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                        timeProjection,
                        "${AlarmTimeEntry.COLUMN_ID}=?",
                        arrayOf(mTimeId.toString()),
                        null)

                if (timeCursor.moveToFirst()) {
                    val time = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                    val timeId = timeCursor.getInt(timeCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    val nextAlarmDate = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, AlarmUtils.minutesInDayToHours(time).toInt())
                        set(Calendar.MINUTE, AlarmUtils.minutesInDayToMinutes(time).toInt())
                        set(Calendar.SECOND, 0)
                        add(Calendar.DAY_OF_YEAR, 7)
                    }

                    AlarmUtils.setAlarm(applicationContext,
                            time,
                            timeId,
                            mGroupId,
                            AlarmUtils.listOfSpecificDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)),
                            alarmManager,
                            nextAlarmDate)
                }

                timeCursor.close()
            }
        }

        groupCursor.close()
    }

    private fun dismissGroup() {
        if (!mIsRepeating) {
            AlarmUtils.cancelGroupAlarms(this, mGroupId, setInactive = true)
        } else {
            val currentDayOfWeek = mCalendar.get(Calendar.DAY_OF_WEEK)
            AlarmUtils.cancelGroupAlarms(this, mGroupId, dayOfWeek = currentDayOfWeek)
            AlarmUtils.setGroupAlarms(this, mGroupId, dayOfWeek = currentDayOfWeek, nextWeek = true)
        }
        finishAlarm()
    }

    private fun snoozeAlarm() {
        val currentTime = Calendar.getInstance()
        currentTime.add(Calendar.MINUTE, mSnoozeDuration)
        Log.i(LOG_TAG, "mSnoozeDuration: $mSnoozeDuration")

        val snoozeTime = AlarmUtils.getMinutesInDay(currentTime.get(Calendar.HOUR_OF_DAY),
                currentTime.get(Calendar.MINUTE))

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        AlarmUtils.setAlarm(applicationContext,
                snoozeTime,
                mTimeId,
                mGroupId,
                AlarmUtils.listOfSpecificDay(-1),
                alarmManager)

        finishAlarm()
    }

    private fun dismissAlarm() {
        if (!isMoreAlarms()) { // if there are no more alarms set for the group, set the group as not-active
            Log.i(LOG_TAG, "isMoreAlarms() = false")
            val values = ContentValues()
            values.put(AlarmGroupEntry.COLUMN_ACTIVE, 0)

            contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                    values,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(mGroupId.toString()))
        }

        finishAlarm()
    }

    private fun finishAlarm() {
        // Return volume & ringer mode to user's settings
        //mAudioManager.ringerMode = mUserRingerMode
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mUserVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

        // Stop the music
        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.stop()
            mMediaPlayer!!.release()
        }

        // Stop the Vibration
        if (mVibrator != null) {
            mVibrator!!.cancel()
        }

        finish()
    }

    /**
     * Checks if there are more set alarms for the current group
     */
    private fun isMoreAlarms(checkRepeating: Boolean = true): Boolean {
        if (mIsRepeating && checkRepeating) {
            return true
        }

        val projection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                AlarmTimeEntry.COLUMN_TIME)
        val timesCursor = contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                projection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=? AND ${AlarmTimeEntry.COLUMN_ID}!=?",
                arrayOf(mGroupId.toString(), mTimeId.toString()),
                null)

        val currentTime = getMinutesInDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),Calendar.getInstance().get(Calendar.MINUTE))
        var timeId: Int
        var time: Int
        var isAlarmExist: Boolean
        var dayOfWeek = 0
        if (!checkRepeating) {
            dayOfWeek = mCalendar.get(Calendar.DAY_OF_WEEK)
            Log.i(LOG_TAG, "checkRepeating: false")
        }
        Log.i(LOG_TAG, "dayOfWeek: $dayOfWeek")

        while(timesCursor.moveToNext()) {
            timeId = timesCursor.getInt(timesCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            time = timesCursor.getInt(timesCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            Log.i(LOG_TAG, "currentTime: $currentTime, time: $time")
            if (time > currentTime) {
                Log.i(LOG_TAG, "timeId: $timeId")
                isAlarmExist = (PendingIntent.getBroadcast(applicationContext,
                        "$timeId$dayOfWeek".toInt(),
                        Intent(applicationContext, AlarmBroadcastReceiver::class.java),
                        PendingIntent.FLAG_NO_CREATE) != null)

                if (isAlarmExist) {
                    Log.i(LOG_TAG, "isMoreAlarms(): true")
                    return true
                }
            }
        }

        timesCursor.close()
        Log.i(LOG_TAG, "isMoreAlarms(): false")
        return false
    }
}