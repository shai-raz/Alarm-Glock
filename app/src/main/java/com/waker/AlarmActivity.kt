package com.waker

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import com.waker.data.AlarmContract.AlarmGroupEntry
import kotlinx.android.synthetic.main.alarm_layout.*


class AlarmActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mDismissButton: Button
    private var mMediaPlayer: MediaPlayer? = null
    private lateinit var mAudioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.alarm_layout)

        val groupId = intent!!.getIntExtra("groupId", 0)
        Log.i(LOG_TAG, "groupId: $groupId")

        mDismissButton = alarm_dismiss_button

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val projection = arrayOf(AlarmGroupEntry.COLUMN_NAME,
                AlarmGroupEntry.COLUMN_SOUND,
                AlarmGroupEntry.COLUMN_RINGTONE_URI)

        val cursor = contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                projection,
                "${AlarmGroupEntry.COLUMN_ID}=?",
                arrayOf(groupId.toString()),
                null)

        Log.i(LOG_TAG, "Cursor count: ${cursor.count}")

        if(cursor.moveToFirst()) {
            Log.i(LOG_TAG, "Ringtone URI: ${cursor.getString(cursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI))}")
            val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i(LOG_TAG, "maxVolume: $maxVolume")
            //mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
            mMediaPlayer = MediaPlayer.create(this, Uri.parse(cursor.getString(cursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI))))
            mMediaPlayer!!.start()
        }

        cursor.close()

        mDismissButton.setOnClickListener { view ->
            if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
                mMediaPlayer!!.stop()
                mMediaPlayer!!.release()
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
            }
            finish()
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

    override fun onAttachedToWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }
}