package com.waker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SwitchCompat
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.travijuu.numberpicker.library.NumberPicker
import kotlinx.android.synthetic.main.activity_advanced_settings.*


class AdvancedSettingsActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mRelativeLayout: RelativeLayout
    private lateinit var mVibrateSwitch: SwitchCompat
    private lateinit var mVolumeIndicator: TextView
    private lateinit var mVolumeImage: ImageView
    private lateinit var mVolumeSeekBar: SeekBar
    private lateinit var mSnoozeDurationNumberPicker: NumberPicker
    private lateinit var mCancelButton: ImageButton
    private lateinit var mSaveButton: ImageButton

    private lateinit var mAudioManager: AudioManager
    private var mMediaPlayer: MediaPlayer? = null

    private var mUserVolume: Int = 0

    private var ringtoneUri: Uri? = Uri.EMPTY
    private var isVibrate = false
    private var volume = 100
    private var snoozeDuration = 1
    private var logVolume: Float = 1.0F

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        mRelativeLayout = advanced_editor_layout
        mVibrateSwitch = advanced_editor_vibrate_switch
        mVolumeIndicator = advanced_editor_volume_indicator
        mVolumeImage = advanced_editor_alarm_volume_image
        mVolumeSeekBar = advanced_editor_volume_seek_bar
        mSnoozeDurationNumberPicker = advanced_editor_snooze_number_picker
        mCancelButton = advanced_editor_toolbar_cancel
        mSaveButton = advanced_editor_toolbar_save

        mVolumeSeekBar.max = 100
        mVolumeSeekBar.progress = volume

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mUserVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        mRelativeLayout.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                pauseTheMusic()
            }
            true
        }

        mVolumeSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                volume = progress
                mVolumeIndicator.text = progress.toString()
                mVolumeIndicator.x = seekBar.thumb.bounds.centerX().toFloat() + mVolumeImage.width
                Log.i(LOG_TAG, "width: ${mVolumeImage.width}")
                when {
                    progress < 10 -> mVolumeIndicator.x += (seekBar.width / 100)
                    progress == 100 -> mVolumeIndicator.x -= (seekBar.width / 100)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mVolumeIndicator.visibility = View.VISIBLE
                if (ringtoneUri != Uri.EMPTY) {
                    pauseTheMusic()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mVolumeIndicator.visibility = View.INVISIBLE

                if (ringtoneUri != Uri.EMPTY) {
                    mUserVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mMediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build())
                    } else {
                        mMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                    }

                    logVolume = (1 - (Math.log((ToneGenerator.MAX_VOLUME - volume).toDouble()) / Math.log(ToneGenerator.MAX_VOLUME.toDouble()))).toFloat()
                    mMediaPlayer!!.setVolume(logVolume, logVolume)
                    mMediaPlayer!!.seekTo(0)
                    mMediaPlayer!!.start()
                }
            }

        })

        mCancelButton.setOnClickListener {
            stopTheMusic()
            finish()
        }

        mSaveButton.setOnClickListener {
            stopTheMusic()
            sendResults()
        }

        if (intent.extras != null) {
            ringtoneUri = intent.getParcelableExtra("ringtoneUri")
            isVibrate = intent.getBooleanExtra("vibrate", false)
            volume = intent.getIntExtra("volume", 100)
            snoozeDuration = intent.getIntExtra("snooze_duration", 1)
            populateExistingAlarm()
        }

        if (ringtoneUri != Uri.EMPTY) {
            mMediaPlayer = MediaPlayer.create(this@AdvancedSettingsActivity, ringtoneUri)
        }
    }

    override fun onBackPressed() {
        stopTheMusic()
        super.onBackPressed()
    }

    private fun populateExistingAlarm() {
        mVibrateSwitch.isChecked = isVibrate
        mVolumeSeekBar.progress = volume
        mSnoozeDurationNumberPicker.value = snoozeDuration
    }

    private fun sendResults() {
        val data = Intent()
        data.putExtra("vibrate", mVibrateSwitch.isChecked)
        data.putExtra("volume", volume)
        data.putExtra("snooze_duration", mSnoozeDurationNumberPicker.value)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun pauseTheMusic() {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mUserVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.pause()
        }
    }

    private fun stopTheMusic() {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mUserVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.stop()
            mMediaPlayer!!.release()
        }
    }
}