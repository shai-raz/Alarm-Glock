package com.waker

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.support.v4.app.NotificationCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import com.waker.AlarmUtils.getMinutesInDay
import com.waker.data.AlarmContract.AlarmEntry
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.alarm_layout.*
import java.util.*


private const val SWIPE_MAX_OFF_PATH = 250
private const val SWIPE_MIN_DISTANCE = 200
private const val SWIPE_THRESHOLD_VELOCITY = 200

private const val SWIPE_TOO_SLOW = -1
private const val DIRECTION_LEFT_TO_RIGHT = 0
private const val DIRECTION_RIGHT_TO_LEFT = 1
private const val DIRECTION_UPWARDS = 2
private const val DIRECTION_DOWNWARDS = 3

class AlarmActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName

    private lateinit var mAlarmLayout: RelativeLayout
    private lateinit var mAlarmClock: View
    private lateinit var mAlarmNameTextView: TextView
    private lateinit var mDismissGroupButton: Button
    private lateinit var mSnoozeButton: Button
    private lateinit var mDismissLayout: LinearLayout

    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mAudioManager: AudioManager
    private lateinit var mCalendar: Calendar
    private lateinit var mGestureDetector: GestureDetector
    private var mMediaPlayer: MediaPlayer? = null
    private var mVibrator: Vibrator? = null


    private lateinit var mDaysOfWeek: List<Int>
    private var mAlarmId = 0
    private var mGroupId = 0
    private var mTimeId = 0
    private var mIsRepeating = false
    private var mSnoozeDuration = 1
    private var mUserRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var mUserVolume: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN) // Make activity fullscreen
        setContentView(R.layout.alarm_layout)

        mAlarmId = intent!!.getIntExtra("alarmId", 0)
        mGroupId = intent!!.getIntExtra("groupId", 0)
        mTimeId = intent!!.getIntExtra("timeId", 0)
        Log.i(LOG_TAG, "groupId: $mGroupId, timeId: $mTimeId, alarmId: $mAlarmId")

        // Widgets
        mAlarmLayout = alarm_layout
        mAlarmNameTextView = alarm_name_text_view
        mDismissGroupButton = alarm_dismiss_group_button
        mSnoozeButton = alarm_snooze_button
        mDismissLayout = alarm_dismiss_layout
        if (Build.VERSION.SDK_INT >= 17) {
            mAlarmClock = alarm_clock as TextClock
        } else if (Build.VERSION.SDK_INT == 16) {
            mAlarmClock = alarm_clock_16 as DigitalClock
        }


        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mCalendar = Calendar.getInstance()
        mGestureDetector = GestureDetector(this, DismissGesture())

        val onTouchListener = View.OnTouchListener { _, motionEvent ->
            mGestureDetector.onTouchEvent(motionEvent)
        }

        mAlarmLayout.setOnTouchListener(onTouchListener)

        // Set animation for swiping screen up to dismiss
        val swipeToDismissAnimation = AnimationUtils.loadAnimation(this, R.anim.dismiss_arrows_blink)
        swipeToDismissAnimation.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation) {
                // Make the animation repeat infinitely
                animation.cancel()
                mDismissLayout.startAnimation(swipeToDismissAnimation)
            }
        })
        mDismissLayout.startAnimation(swipeToDismissAnimation)

        mUserRingerMode = mAudioManager.ringerMode
        mUserVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        alarm() // Alarm logic

        if (isMoreAlarmsToday()) { // Disable Snooze Button if this is not the last in group
            mSnoozeButton.visibility = View.GONE
        } else {
            mDismissGroupButton.visibility = View.GONE
        }

        addNotification()

        // Click listeners
        mDismissGroupButton.setOnClickListener {
            dismissGroup()
        }

        mSnoozeButton.setOnClickListener {
            snoozeAlarm()
        }

        mAlarmLayout
        /*mDismissButton.setOnClickListener {
            dismissAlarm()
        }*/
    }

    // Make back button do nothing (so the user won't leave the activity unintentionally)
    override fun onBackPressed() { }

    // Make volume buttons do nothing[or mute the alarm?] (so the user won't be able to change the alarm's volume)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> return true
            KeyEvent.KEYCODE_VOLUME_DOWN -> return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onAttachedToWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.i(LOG_TAG, "onNewIntent()")
    }

    private fun alarm() {
        // Is this a snooze alarm? (check before deleting the entry)
        val alarmCursor = contentResolver.query(AlarmEntry.CONTENT_URI,
                arrayOf(AlarmEntry.COLUMN_IS_SNOOZE),
                "${AlarmEntry.COLUMN_ALARM_ID}=?",
                arrayOf(mAlarmId.toString()),
                null)
        Log.i(LOG_TAG, "alarmCursorCount: ${alarmCursor?.count} alarmId")

        var isSnooze = false
        if (alarmCursor?.moveToFirst() == true) {
            isSnooze = alarmCursor.getInt(alarmCursor.getColumnIndex(AlarmEntry.COLUMN_IS_SNOOZE)) == 1
            Log.i(LOG_TAG, "isSnooze: $isSnooze")
        }
        alarmCursor?.close()

        // Delete the alarm from the alarms table
        contentResolver.delete(AlarmEntry.CONTENT_URI,
                "${AlarmEntry.COLUMN_ALARM_ID}=?",
                arrayOf(mAlarmId.toString()))

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

        if (groupCursor?.moveToFirst() == true) {
            // Get the alarm info
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
                mMediaPlayer!!.isLooping = true
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
            if (AlarmUtils.isRepeating(mDaysOfWeek) && !isSnooze) { // If the alarm is set to repeat, set the next week's Alarm
                Log.i(LOG_TAG, "mIsRepeating = true")
                mIsRepeating = true

                val timeProjection = arrayOf(AlarmTimeEntry.COLUMN_ID,
                        AlarmTimeEntry.COLUMN_TIME)

                val timeCursor = contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                        timeProjection,
                        "${AlarmTimeEntry.COLUMN_ID}=?",
                        arrayOf(mTimeId.toString()),
                        null)

                if (timeCursor?.moveToFirst() == true) {
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

                timeCursor?.close()
            }
        }

        groupCursor?.close()

        // Delete the alarm from the alarms table
        /*contentResolver.delete(AlarmEntry.CONTENT_URI,
                "${AlarmEntry.COLUMN_TIME_ID}=?",
                arrayOf(mTimeId.toString()))*/
    }

    /**
     * Creates a persistent notification, that once clicked, takes the user back to this activity.
     */
    private fun addNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("default", "Alarm Activity", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Description"
            mNotificationManager.createNotificationChannel(channel)
        }
        val notificationIntent = this.packageManager.getLaunchIntentForPackage(this.packageName)
                .setPackage(null)
                //.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        //val notificationPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationPendingIntent = PendingIntent.getActivity(this, 0, Intent(this, AlarmActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "default")
                .setSmallIcon(R.drawable.ic_alarm_black)
                .setOngoing(true)
                .setContentTitle("Alarm is on")
                .setContentText("Click to go back to the alarm screen")
                .setContentIntent(notificationPendingIntent)
                .build()
        mNotificationManager.notify(0, notification)
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
                alarmManager,
                isSnooze = true)

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

        animateViewOut()
    }

    private fun finishAlarm() {
        // Remove the notification
        mNotificationManager.cancel(0)

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

    private fun animateViewOut() {
        val x = AnimationUtils.loadAnimation(this, R.anim.dismiss_alarm)
        x.setAnimationListener(object: Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?){}
            override fun onAnimationStart(animation: Animation?){}

            override fun onAnimationEnd(animation: Animation?) {
                mAlarmLayout.visibility = View.GONE
                finishAlarm()
            }
        })

        mAlarmLayout.startAnimation(x)
    }

    private fun isMoreAlarmsToday(): Boolean {
        val alarmCursor = contentResolver.query(AlarmEntry.CONTENT_URI,
                arrayOf(AlarmEntry.COLUMN_UNIX_TIME),
                "${AlarmEntry.COLUMN_GROUP_ID}=? AND ${AlarmEntry.COLUMN_TIME_ID}!=$mTimeId",
                arrayOf(mGroupId.toString()),
                "${AlarmEntry.COLUMN_GROUP_ID} ASC")

        while (alarmCursor?.moveToNext() == true) {
            val unixTime = alarmCursor.getLong(alarmCursor.getColumnIndex(AlarmEntry.COLUMN_UNIX_TIME))
            val currentDayOfWeek = mCalendar.get(Calendar.DAY_OF_WEEK)
            val alarmDayOfWeek = Calendar.getInstance().apply{timeInMillis = unixTime}.get(Calendar.DAY_OF_WEEK)

            if (currentDayOfWeek == alarmDayOfWeek) {
                return true
            }
        }

        alarmCursor?.close()
        return false
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

        while(timesCursor?.moveToNext() == true) {
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

        timesCursor?.close()
        Log.i(LOG_TAG, "isMoreAlarms(): false")
        return false
    }

    inner class DismissGesture : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (Math.abs(e1.x - e2.x) > SWIPE_MAX_OFF_PATH)
                return false
            when (getDirection(e1, e2, velocityX, velocityY)) {
                DIRECTION_UPWARDS ->
                    dismissAlarm()

                else -> return false
            }

            return true
        }

        /**
         * Returns the direction of a fling
         * @return  Int One of the following constants:
         *              *DIRECTION_LEFT_TO_RIGHT*,
         *              *DIRECTION_RIGHT_TO_LEFT*,
         *              *DIRECTION_UPWARDS*,
         *              *DIRECTION_DOWNWARDS*,
         *              *SWIPE_TOO_SLOW*
         */
        private fun getDirection(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Int {
            return when {
                (Math.abs(e1.x - e2.x) > SWIPE_MAX_OFF_PATH) ->
                    SWIPE_TOO_SLOW
                (e2.y - e1.y > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) ->
                    DIRECTION_DOWNWARDS
                (e1.y - e2.y > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) ->
                    DIRECTION_UPWARDS
                (e1.x - e2.x > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) ->
                    DIRECTION_RIGHT_TO_LEFT
                (e2.x - e1.x > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) ->
                    DIRECTION_LEFT_TO_RIGHT
                else -> -1
            }
        }
    }
}