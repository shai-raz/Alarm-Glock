package com.waker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.alarm_layout.*
import java.util.*


class AlarmActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mDismissButton: Button
    private lateinit var mAudioManager: AudioManager
    private var mMediaPlayer: MediaPlayer? = null
    private lateinit var mDaysOfWeek: List<Int>

    private var mGroupId = 0
    private var mTimeId = 0
    private var mIsRepeating = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.alarm_layout)

        mGroupId = intent!!.getIntExtra("groupId", 0)
        mTimeId = intent!!.getIntExtra("timeId", 0)
        Log.i(LOG_TAG, "groupId: $mGroupId, timeId: $mTimeId")

        mDismissButton = alarm_dismiss_button

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val groupProjection = arrayOf(AlarmGroupEntry.COLUMN_NAME,
                AlarmGroupEntry.COLUMN_SOUND,
                AlarmGroupEntry.COLUMN_RINGTONE_URI,
                AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)

        val groupCursor = contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                groupProjection,
                "${AlarmGroupEntry.COLUMN_ID}=?",
                arrayOf(mGroupId.toString()),
                null)

        if(groupCursor.moveToFirst()) {
            Log.i(LOG_TAG, "Ringtone URI: ${groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI))}")
            val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i(LOG_TAG, "maxVolume: $maxVolume")
            //mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
            mMediaPlayer = MediaPlayer.create(this, Uri.parse(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI))))
            mMediaPlayer!!.start()

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

        if (!isMoreAlarms()) { // if there are no more alarms set for the group, set the group as not-active
            Log.i(LOG_TAG, "isMoreAlarms() = false")
            val values = ContentValues()
            values.put(AlarmGroupEntry.COLUMN_ACTIVE, 0)

            contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                    values,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(mGroupId.toString()))
        }

        mDismissButton.setOnClickListener { view ->
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
        super.onBackPressed()
        dismissAlarm()
    }

    override fun onAttachedToWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(LOG_TAG, "onDestroy()")
        //AlarmUtils.cancelAlarm(applicationContext, mTimeId, mDaysOfWeek)
    }

    private fun dismissAlarm() {
        // Stop the music
        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.stop()
            mMediaPlayer!!.release()
        }

        finish()
    }

    /**
     * Checks if there are more set alarms for the current group
     */
    private fun isMoreAlarms(): Boolean {
        if (mIsRepeating) { // If the alarm is repeating, leave it as active until the alarm has been set to inactive manually
            return true
        }

        val projection = arrayOf(AlarmTimeEntry.COLUMN_ID)
        val timesCursor = contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                projection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=? AND ${AlarmTimeEntry.COLUMN_ID}!=?",
                arrayOf(mGroupId.toString(), mTimeId.toString()),
                null)


        var timeId: Int
        var isAlarmExist: Boolean

        while(timesCursor.moveToNext()) {
            timeId = timesCursor.getInt(timesCursor.getColumnIndex(AlarmTimeEntry.COLUMN_ID))
            Log.i(LOG_TAG, "timeId: $timeId")
            isAlarmExist = (PendingIntent.getBroadcast(applicationContext,
                    "${timeId}0".toInt(),
                    Intent(applicationContext, AlarmBroadcastReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE) != null)

            if (isAlarmExist) {
                Log.i(LOG_TAG, "isMoreAlarms(): true")
                return true
            }
        }

        timesCursor.close()
        Log.i(LOG_TAG, "isMoreAlarms(): false")
        return false
    }
}