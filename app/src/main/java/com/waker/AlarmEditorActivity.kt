package com.waker

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import cn.pedant.SweetAlert.SweetAlertDialog
import com.waker.data.AlarmContract
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.activity_alarm_editor.*
import kotlinx.android.synthetic.main.editor_times_entry.view.*


private const val SELECT_RINGTONE_RESULTS = 1
private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2
private const val ADVANCED_SETTINGS_RESULTS = 3

class AlarmEditorActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mAlarmNameEditText: EditText
    private lateinit var mSoundSwitch: SwitchCompat
    private lateinit var mPickRingtoneButton: LinearLayout
    private lateinit var mPickRingtoneTextView: TextView
    private lateinit var mDaysOfWeekToggle: Array<ToggleButton>
    private lateinit var mAdvancedButton: Button
    private lateinit var mTimesRecyclerView: RecyclerView
    private lateinit var mAddTimeButton: Button
    private lateinit var mCancelButton: Button
    private lateinit var mSaveButton: Button

    private lateinit var mAlarmManager: AlarmManager
    private lateinit var mTimesAdapter: TimesAdapter

    private var mEditMode = false
    private val mTimesList = mutableListOf<Int>()
    private var mGroupId: Int = 0

    private var ringtoneUri: Uri? = Uri.EMPTY
    private var isVibrate = false
    private var volume = 100
    private var snoozeDuration = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_editor)

        mAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Widgets
        mAlarmNameEditText = editor_name_edit_text
        mSoundSwitch = editor_sound_switch
        mPickRingtoneButton = editor_pick_ringtone
        mPickRingtoneTextView = editor_ringtone_text_view
        mAdvancedButton = editor_advanced_settings_button
        mTimesRecyclerView = editor_times_recycler_view
        mAddTimeButton = editor_add_time_button
        mCancelButton = editor_cancel_button
        mSaveButton = editor_save_button

        mSoundSwitch.setOnClickListener {
            if (!mSoundSwitch.isEnabled) {
                Toast.makeText(this, "Please choose a ringtone first!", Toast.LENGTH_SHORT).show()
            }
        }

        mDaysOfWeekToggle = arrayOf(editor_toggle_sunday,
                editor_toggle_monday,
                editor_toggle_tuesday,
                editor_toggle_wednesday,
                editor_toggle_thursday,
                editor_toggle_friday,
                editor_toggle_saturday)

        mAddTimeButton.setOnClickListener {
            addTime()
        }

        mSaveButton.setOnClickListener {
            saveAlarm()
        }

        mCancelButton.setOnClickListener {
            finish()
        }

        mPickRingtoneButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) { // Check for storage permissions
                val dialog = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                        .setTitleText(getString(R.string.dialog_permission_required))
                        .setContentText(getString(R.string.dialog_request_permission))
                dialog.setOnDismissListener {
                    ActivityCompat.requestPermissions(this,
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                }
                dialog.show()
            } else {
                val ringtoneIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(ringtoneIntent, SELECT_RINGTONE_RESULTS)
            }
        }

        mAdvancedButton.setOnClickListener {
            val intent = Intent(this, AdvancedSettingsActivity::class.java)
            intent.putExtra("ringtoneUri", ringtoneUri)
            intent.putExtra("vibrate", isVibrate)
            intent.putExtra("volume", volume)
            intent.putExtra("snooze_duration", snoozeDuration)
            startActivityForResult(intent, ADVANCED_SETTINGS_RESULTS)
        }

        mTimesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        mTimesAdapter = TimesAdapter(this, mTimesList)
        mTimesRecyclerView.adapter = mTimesAdapter

        val itemTouchHelper = ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder): Boolean {
                Toast.makeText(this@AlarmEditorActivity, "Moved", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
                val pos = viewHolder.adapterPosition
                val old = mTimesList[pos]
                mTimesList.removeAt(pos)
                mTimesAdapter.notifyItemRemoved(pos)
                Snackbar.make(mTimesRecyclerView, "Removed", Snackbar.LENGTH_SHORT)
                        .addCallback(object: Snackbar.Callback() {
                            override fun onDismissed(snackbar: Snackbar, event: Int) {
                                when (event) {
                                    Snackbar.Callback.DISMISS_EVENT_ACTION -> {
                                        mTimesList.add(pos, old)
                                        mTimesAdapter.notifyItemInserted(pos)
                                    }
                                }
                            }
                        })
                        .setAction("Undo") {}
                        .show()

                // Update the positions (of items after affected items) in the adapter so that the onClickListener will be called for the correct positions
                mTimesAdapter.notifyItemRangeChanged(pos, mTimesAdapter.itemCount)
            }
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (mTimesAdapter.itemCount == 1) {
                    return ItemTouchHelper.ACTION_STATE_IDLE
                }

                return super.getSwipeDirs(recyclerView, viewHolder)
            }

        })

        itemTouchHelper.attachToRecyclerView(mTimesRecyclerView)

        if (intent.extras != null) {
            mEditMode = true
            mGroupId = intent.getIntExtra("groupId", 0)

            val projection = arrayOf(AlarmGroupEntry.COLUMN_NAME,
                    AlarmGroupEntry.COLUMN_SOUND,
                    AlarmGroupEntry.COLUMN_RINGTONE_URI,
                    AlarmGroupEntry.COLUMN_DAYS_IN_WEEK,
                    AlarmGroupEntry.COLUMN_VIBRATE,
                    AlarmGroupEntry.COLUMN_VOLUME,
                    AlarmGroupEntry.COLUMN_SNOOZE_DURATION)

            val groupCursor = contentResolver.query(AlarmGroupEntry.CONTENT_URI,
                    projection,
                    "${AlarmGroupEntry.COLUMN_ID}=?",
                    arrayOf(mGroupId.toString()),
                    null)

            val timesCursor = contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                    arrayOf(AlarmTimeEntry.COLUMN_TIME),
                    "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                    arrayOf(mGroupId.toString()),
                    AlarmTimeEntry.COLUMN_TIME)

            populateExistingAlarm(groupCursor, timesCursor)

            groupCursor.close()
            timesCursor.close()
        } else {
            mTimesList.add(0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) { // If permission is granted, let the user choose a ringtone
                val ringtoneIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(ringtoneIntent, SELECT_RINGTONE_RESULTS)
            } else {
                Toast.makeText(this, getString(R.string.dialog_permission_wasnt_granted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                SELECT_RINGTONE_RESULTS -> {
                    ringtoneUri = data!!.data

                    val ringtoneCursor = contentResolver.query(
                            ringtoneUri,
                            arrayOf(MediaStore.Audio.Media.TITLE),
                            null, null, null)
                    if (ringtoneCursor.moveToFirst()) {
                        mPickRingtoneTextView.text = ringtoneCursor.getString(ringtoneCursor.getColumnIndex(MediaStore.MediaColumns.TITLE))
                    }
                    ringtoneCursor.close()

                    mSoundSwitch.isEnabled = true
                    mSoundSwitch.isChecked = true
                }

                ADVANCED_SETTINGS_RESULTS -> {
                    isVibrate = data!!.getBooleanExtra("vibrate", false)
                    volume = data.getIntExtra("volume", 100)
                    snoozeDuration = data.getIntExtra("snooze_duration", 1)
                }
            }
        } else {
            if (requestCode == SELECT_RINGTONE_RESULTS) {
                Toast.makeText(this, getString(R.string.editor_no_ringtone_chosen), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateExistingAlarm(groupCursor: Cursor?, timesCursor: Cursor?) {
        if (groupCursor!!.moveToFirst()) {
            mAlarmNameEditText.setText(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_NAME)), TextView.BufferType.EDITABLE)
            mSoundSwitch.isChecked = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_SOUND)) == 1

            ringtoneUri = Uri.parse(groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_RINGTONE_URI)))
            val ringtoneCursor = contentResolver.query(
                    ringtoneUri,
                    arrayOf(MediaStore.Audio.Media.TITLE),
                    null, null, null)
            if (ringtoneCursor != null && ringtoneCursor.moveToFirst()) {
                mPickRingtoneTextView.text = ringtoneCursor.getString(ringtoneCursor.getColumnIndex(MediaStore.MediaColumns.TITLE))
                ringtoneCursor.close()
            }

            var daysOfWeek = groupCursor.getString(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK))
            daysOfWeek = daysOfWeek.replace("[","")
            daysOfWeek = daysOfWeek.replace("]","")
            daysOfWeek = daysOfWeek.replace("\\s+","")
            val daysOfWeekArray = daysOfWeek.split(",")

            for (i in 0 until mDaysOfWeekToggle.size) {
                mDaysOfWeekToggle[i].isChecked = daysOfWeekArray[i].trim().toInt() == 1
            }

            if (ringtoneUri != Uri.EMPTY) {
                mSoundSwitch.isEnabled = true
            }

            isVibrate = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_VIBRATE)) == 1
            volume = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_VOLUME))
            snoozeDuration = groupCursor.getInt(groupCursor.getColumnIndex(AlarmGroupEntry.COLUMN_SNOOZE_DURATION))

        }

        while(timesCursor!!.moveToNext()) {
            mTimesList.add(timesCursor.getInt(timesCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME)))
        }
        mTimesAdapter.notifyDataSetChanged()
    }

    private fun addTime() {
        mTimesList.add(0)
        mTimesAdapter.notifyItemInserted(mTimesList.size-1)
    }

    /**
     * Saves the Alarm into the Database
     */
    private fun saveAlarm() {
        val alarmName = mAlarmNameEditText.text.toString().trim()
        val isSound = mSoundSwitch.isChecked.toInt()

        if (!hasDupliactes()) {
            val groupValues = ContentValues()
            groupValues.put(AlarmGroupEntry.COLUMN_NAME, alarmName)
            groupValues.put(AlarmGroupEntry.COLUMN_ACTIVE, 1)
            groupValues.put(AlarmGroupEntry.COLUMN_SOUND, isSound)
            groupValues.put(AlarmGroupEntry.COLUMN_RINGTONE_URI, ringtoneUri.toString())

            val daysOfWeek: MutableList<Int> = mutableListOf()
            for (i in 0 until mDaysOfWeekToggle.size) {
                daysOfWeek.add(i, mDaysOfWeekToggle[i].isChecked.toInt())
            }
            groupValues.put(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK, daysOfWeek.toString())
            groupValues.put(AlarmGroupEntry.COLUMN_VIBRATE, isVibrate.toInt())
            groupValues.put(AlarmGroupEntry.COLUMN_VOLUME, volume)
            groupValues.put(AlarmGroupEntry.COLUMN_SNOOZE_DURATION, snoozeDuration)

            val timesValues = ContentValues()
            var timeUri: Uri
            var timeId: Int

            if (!mEditMode) { // Creating a new Alarm (Insert)
                val groupUri = contentResolver.insert(AlarmGroupEntry.CONTENT_URI, groupValues)

                if (groupUri == null) {
                    // If the new content URI is null, then there was an error with insertion.
                    Snackbar.make(mAlarmNameEditText, "Error adding Alarm", Snackbar.LENGTH_SHORT).show()
                } else {
                    // Otherwise, the insertion was successful and we can display a toast.
                    Snackbar.make(mAlarmNameEditText, "Alarm Saved", Snackbar.LENGTH_SHORT).show()
                }

                val groupId = ContentUris.parseId(groupUri).toInt()

                for (time in mTimesList) {
                    timesValues.put(AlarmTimeEntry.COLUMN_GROUP_ID, groupId)
                    timesValues.put(AlarmTimeEntry.COLUMN_TIME, time)
                    timeUri = contentResolver.insert(AlarmTimeEntry.CONTENT_URI, timesValues)
                    timeId = ContentUris.parseId(timeUri).toInt()
                    timesValues.clear()

                    AlarmUtils.setAlarm(this, time, timeId, groupId, daysOfWeek, mAlarmManager)

                    if (timeUri == null) {
                        // If the new content URI is null, then there was an error with insertion.
                        Snackbar.make(mAlarmNameEditText, "Error adding Time", Snackbar.LENGTH_SHORT).show()
                    } else {
                        // Otherwise, the insertion was successful and we can display a toast.
                        Snackbar.make(mAlarmNameEditText, "Time Saved", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } else { // Editing existing Alarm (Update)
                val rowsAffected = contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                        groupValues,
                        "${AlarmGroupEntry.COLUMN_ID}=?",
                        arrayOf(mGroupId.toString()))

                if (rowsAffected == 0) {
                    Snackbar.make(mAlarmNameEditText, "Error adding Time", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(mAlarmNameEditText, "Time Saved", Snackbar.LENGTH_SHORT).show()
                }

                AlarmUtils.cancelGroupAlarms(this, mGroupId)

                // Delete all existing times, and insert them anew
                contentResolver.delete(AlarmTimeEntry.CONTENT_URI,
                        "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                        arrayOf(mGroupId.toString()))

                for (time in mTimesList) {
                    timesValues.put(AlarmContract.AlarmTimeEntry.COLUMN_GROUP_ID, mGroupId)
                    timesValues.put(AlarmContract.AlarmTimeEntry.COLUMN_TIME, time)
                    timeUri = contentResolver.insert(AlarmContract.AlarmTimeEntry.CONTENT_URI, timesValues)
                    timeId = ContentUris.parseId(timeUri).toInt()
                    timesValues.clear()

                    AlarmUtils.setAlarm(this, time, timeId, mGroupId, daysOfWeek, mAlarmManager)

                    if (timeUri == null) {
                        // If the new content URI is null, then there was an error with insertion.
                        Snackbar.make(mAlarmNameEditText, "Error adding Time", Snackbar.LENGTH_SHORT).show()
                    } else {
                        // Otherwise, the insertion was successful and we can display a toast.
                        Snackbar.make(mAlarmNameEditText, "Time Saved", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            finish()
        } else { // 2 alarms at the same time
            Toast.makeText(this, "You can't have 2 alarms at the same time!", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class TimesAdapter(private val mContext: AppCompatActivity, private val list: MutableList<Int>) :
            RecyclerView.Adapter<TimesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val timeTextView: TextView = view.editor_times_list_view_text_view

            fun update(position: Int) {
                timeTextView.text = AlarmUtils.minutesInDayTo24(list[position])

                itemView.setOnClickListener { view ->
                    val timePickerDialog = TimePickerDialog(mContext,
                            TimePickerDialog.OnTimeSetListener { timePicker, selectedHour, selectedMin ->
                                mTimesList[position] = AlarmUtils.getMinutesInDay(selectedHour, selectedMin)
                                mTimesAdapter.notifyItemChanged(position) },
                            AlarmUtils.minutesInDayToHours(mTimesList[position]).toInt(),
                            AlarmUtils.minutesInDayToMinutes(mTimesList[position]).toInt(),
                            true)
                    timePickerDialog.show()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimesAdapter.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.editor_times_entry, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.update(position)
        }

    }

    private fun hasDupliactes(): Boolean {
        val duplicateList = mutableListOf<Int>()
        mTimesList.forEachIndexed { i, _ ->
            mTimesList.forEachIndexed { u, time ->
                if (i != u) {
                    if (duplicateList.contains(time)) {
                        return true
                    } else {
                        duplicateList.add(time)
                    }
                }
            }
        }
        return false
    }
}