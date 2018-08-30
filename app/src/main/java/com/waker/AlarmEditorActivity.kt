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
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.support.v7.widget.helper.ItemTouchHelper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import cn.pedant.SweetAlert.SweetAlertDialog
import com.waker.data.AlarmContract
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.activity_alarm_editor.*
import kotlinx.android.synthetic.main.editor_times_entry.view.*
import java.util.*


const val SELECT_RINGTONE_RESULTS = 1
private const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2
private const val ADVANCED_SETTINGS_RESULTS = 3

class AlarmEditorActivity: AppCompatActivity() {

    private val LOG_TAG = this.javaClass.simpleName

    private lateinit var mCancelButton: ImageButton
    private lateinit var mSaveButton: ImageButton
    //private lateinit var mScrollView: NestedScrollView
    private lateinit var mAlarmNameEditText: EditText
    private lateinit var mSoundSwitch: SwitchCompat
    private lateinit var mPickRingtoneButton: LinearLayout
    private lateinit var mPickRingtoneTextView: TextView
    private lateinit var mDaysOfWeekToggle: Array<ToggleButton>
    private lateinit var mAdvancedButton: Button
    private lateinit var mTimesRecyclerView: RecyclerView
    private lateinit var mFABCoordinator: CoordinatorLayout
    private lateinit var mAddTimeButton: FloatingActionButton
    //private lateinit var mAddGroupTimeButton: FloatingActionButton

    private lateinit var mAlarmManager: AlarmManager
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var mTimesAdapter: TimesAdapter

    private var mEditMode = false
    private var mTimesList = mutableListOf<Int>()
    private var mGroupId: Int = 0

    private var ringtoneUri: Uri? = Uri.EMPTY
    private var isVibrate = false
    private var volume = 50
    private var snoozeDuration = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_editor)

        mAlarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Widgets
        mCancelButton = editor_toolbar_cancel
        mSaveButton = editor_toolbar_done
        mAlarmNameEditText = editor_name_edit_text
        mSoundSwitch = editor_sound_switch
        mPickRingtoneButton = editor_pick_ringtone
        mPickRingtoneTextView = editor_ringtone_text_view
        mAdvancedButton = editor_advanced_settings_button
        mTimesRecyclerView = editor_times_recycler_view
        mFABCoordinator = editor_fab_coordinator
        mAddTimeButton = editor_add_time_fab
        //mAddGroupTimeButton = editor_add_group_time_fab
        //mAddGroupTimeButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.add_time_fab_show)) // Use when clicking add time fab to add group times
        mAddTimeButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.add_time_fab_show))

        //Log.i(LOG_TAG, "getRingtone: ${getRingtone()}")

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
                val intent = Intent(this, ChooseRingtoneActivity::class.java)
                startActivityForResult(intent, SELECT_RINGTONE_RESULTS)
                /*val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select ringtone for notifications:")
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                startActivityForResult(intent, SELECT_RINGTONE_RESULTS)*/
                /*val ringtoneIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(ringtoneIntent, SELECT_RINGTONE_RESULTS)*/
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

        mLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val itemDecoration = DividerItemDecoration(this, mLayoutManager.orientation).apply {
            setDrawable(ContextCompat.getDrawable(this@AlarmEditorActivity, R.drawable.times_divider)!!)
        }
        mTimesRecyclerView.addItemDecoration(itemDecoration)

        mTimesRecyclerView.layoutManager = mLayoutManager

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
                Snackbar.make(mFABCoordinator, getString(R.string.editor_time_removed), Snackbar.LENGTH_SHORT)
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
                        .setActionTextColor(ContextCompat.getColor(this@AlarmEditorActivity, R.color.snackbarAction))
                        .setAction("Undo") {}
                        .show()

                // Update the positions (of items after affected items) in the adapter so that the onClickListener will be called for the correct positions
                mTimesAdapter.notifyItemRangeChanged(pos, mTimesAdapter.itemCount)
            }
            /*override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                /*if (mTimesAdapter.itemCount == 1) { // disable swipe if there is only 1 time
                    return ItemTouchHelper.ACTION_STATE_IDLE
                }*/

                return super.getSwipeDirs(recyclerView, viewHolder)
            }*/

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
            addTime()
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
                    Log.i(LOG_TAG, "ringtoneUri: $ringtoneUri")

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
                Toast.makeText(this, getString(R.string.editor_no_ringtone_was_chosen), Toast.LENGTH_SHORT).show()
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
        val now = Calendar.getInstance()
        val hours = now.get(Calendar.HOUR_OF_DAY)
        val minutes = now.get(Calendar.MINUTE) + 1

        val onTimeSetListener = TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMin ->
            mTimesList.add(AlarmUtils.getMinutesInDay(selectedHour, selectedMin))
            mTimesAdapter.notifyItemInserted(mTimesList.size-1)
            // If the new time is not the "biggest", notify the adapter to use the new sorted list
            val oldList = mTimesList.toList()
            mTimesList.sort()
            if (mTimesList != oldList) {
                mTimesAdapter.notifyDataSetChanged()
            }
        }

        val timePickerDialog = MyTimePickerDialog(this,
                onTimeSetListener,
                hours,
                minutes,
                true)

        timePickerDialog.setCanceledOnTouchOutside(true)
        /*timePickerDialog.setOnDismissListener {
            mTimesList.removeAt(mTimesList.size-1)
        }*/

        timePickerDialog.show()
    }

    /**
     * Saves the Alarm into the Database
     */
    private fun saveAlarm() {
        val alarmName = mAlarmNameEditText.text.toString().trim()
        val isSound = mSoundSwitch.isChecked.toInt()

        when {
            hasDuplicates() -> // 2 alarms at the same time
                Toast.makeText(this, getString(R.string.editor_you_cant_set_2_alarms_to_the_same_time), Toast.LENGTH_SHORT).show()
            mTimesList.isEmpty() -> // no times are set
                Toast.makeText(this, getString(R.string.editor_you_must_have_at_least_one_time_set), Toast.LENGTH_SHORT).show()
            else -> { // everything is fine, set the alarm
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
                        Snackbar.make(mFABCoordinator, "Error adding Alarm", Snackbar.LENGTH_SHORT).show()
                    } else {
                        // Otherwise, the insertion was successful and we can display a toast.
                        Snackbar.make(mFABCoordinator, "Alarm Saved", Snackbar.LENGTH_SHORT).show()
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
                            Snackbar.make(mFABCoordinator, "Error adding Time", Snackbar.LENGTH_SHORT).show()
                        } else {
                            // Otherwise, the insertion was successful and we can display a toast.
                            Snackbar.make(mFABCoordinator, "Time Saved", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                } else { // Editing existing Alarm (Update)
                    AlarmUtils.cancelGroupAlarms(this, mGroupId)

                    val rowsAffected = contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                            groupValues,
                            "${AlarmGroupEntry.COLUMN_ID}=?",
                            arrayOf(mGroupId.toString()))

                    if (rowsAffected == 0) {
                        Snackbar.make(mAlarmNameEditText, "Error adding Time", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(mAlarmNameEditText, "Time Saved", Snackbar.LENGTH_SHORT).show()
                    }

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
            }
        }
    }

    private inner class TimesAdapter(private val mContext: AppCompatActivity, private val list: MutableList<Int>) :
            RecyclerView.Adapter<TimesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val timeTextView: TextView = view.editor_times_list_view_text_view

            fun update(position: Int) {
                timeTextView.text = AlarmUtils.minutesInDayTo24(list[position])

                itemView.setOnClickListener { _ ->
                    val onTimeSetListener = TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMin ->
                        mTimesList[position] = AlarmUtils.getMinutesInDay(selectedHour, selectedMin)
                        mTimesAdapter.notifyItemChanged(position)
                    }

                    val timePickerDialog = TimePickerDialog(mContext,
                            onTimeSetListener,
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

    /**
     * @return true if mTimesList has duplicates
     */
    private fun hasDuplicates(): Boolean {
        val duplicateList = mutableListOf<Int>()
        for (time in mTimesList) {
            if (duplicateList.contains(time)) {
                return true
            }
            else {
                duplicateList.add(time)
            }
        }
        return false
    }

    private fun getRingtone(): List<Uri> {
        val ringtoneManager = RingtoneManager(this)
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = ringtoneManager.cursor

        val ringtoneMap = mutableMapOf<String,String>()
        val list = mutableListOf<Uri>()
        while (cursor.moveToNext()) {
            val pos = cursor.position
            list.add(pos, ringtoneManager.getRingtoneUri(pos))
            Log.i(LOG_TAG, "title : ${ringtoneManager.getRingtone(pos).getTitle(this)}")
            /*val notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
            ringtoneMap[notificationTitle] = notificationUri*/
        }

        return list
    }
}

/**
 * A workaround to fix a call to OnTimeSetListener when not actually setting the time (on older devices using JellyBean)
 */
class MyTimePickerDialog(context: Context, listener: TimePickerDialog.OnTimeSetListener, hourOfDay: Int, minute: Int, is24HourView: Boolean):
        TimePickerDialog(context, listener, hourOfDay, minute, is24HourView) {
    override fun onStop() {
    }
}