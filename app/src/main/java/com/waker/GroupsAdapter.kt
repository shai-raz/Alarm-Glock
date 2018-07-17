package com.waker

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.main_group_entry.view.*

class GroupsAdapter(private val mContext: AppCompatActivity, private var mCursor: Cursor?):
        RecyclerView.Adapter<GroupsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Declare widgets
        private val groupNameTextView: TextView = view.group_entry_name
        private val groupFirstTimeTextView: TextView = view.group_entry_start_time
        private val groupEndTimeTextView = view.group_entry_end_time
        private val groupSwitch: SwitchCompat = view.group_entry_switch
        private var isTouched = false

        fun update(position: Int) {
            mCursor!!.moveToPosition(position)
            // Get values from the Cursor
            val groupId = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
            val groupName = mCursor!!.getString(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_NAME))
            val isActive = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ACTIVE)) != 0

            val groupTimes = getGroupTimes(groupId)

            if (groupTimes.moveToFirst()) {
                val groupFirstTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                groupFirstTimeTextView.text = AlarmUtils.minutesInDayTo24(groupFirstTime)
            }
            if (groupTimes.moveToLast()) {
                val groupEndTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                groupEndTimeTextView.text = AlarmUtils.minutesInDayTo24(groupEndTime)
            }

            // Populate widgets with values
            groupNameTextView.text = groupName
            groupSwitch.isChecked = isActive

            /*groupSwitch.setOnTouchListener { view, event ->
                isTouched = true
                /*if (event!!.action == MotionEvent.ACTION_DOWN) {
                    groupSwitch.parent.requestDisallowInterceptTouchEvent(true)
                }*/

                false
            }*/

            groupSwitch.setOnCheckedChangeListener { button, isChecked ->
                if (button.isPressed) {
                    //isTouched = false
                    val values = ContentValues()
                    values.put(AlarmGroupEntry.COLUMN_ACTIVE, isChecked)
                    val rowsAffected = mContext.contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                            values,
                            "${AlarmGroupEntry.COLUMN_ID}=?",
                            arrayOf(groupId.toString()))
                    /*if (rowsAffected == 0) {
                        // If the new content URI is null, then there was an error with insertion.
                        Snackbar.make(itemView, "Error", Snackbar.LENGTH_SHORT).show()
                    } else {
                        // Otherwise, the insertion was successful and we can display a toast.
                        Snackbar.make(itemView, "Saved", Snackbar.LENGTH_SHORT).show()
                    }*/

                    if (isChecked) {
                        AlarmUtils.setGroupAlarms(mContext, groupId)
                    } else {
                        AlarmUtils.cancelGroupAlarms(mContext, groupId)
                    }
                }
            }

            itemView.setOnClickListener { view ->
                val intent = Intent(mContext, AlarmEditor::class.java)
                intent.putExtra("groupId", groupId)

                mContext.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupsAdapter.ViewHolder{
        val view = LayoutInflater.from(mContext).inflate(R.layout.main_group_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.update(position)
    }

    override fun getItemCount(): Int {
        return mCursor?.count ?: 0
    }

    fun swapCursor(cursor: Cursor?): Cursor? {
        if (cursor == mCursor) {
            return null
        }

        val oldCursor = mCursor

        mCursor = cursor
        if (cursor != null) {
            notifyDataSetChanged()
        }

        return oldCursor
    }

    private fun getGroupTimes(groupId: Int): Cursor {
        return mContext.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                arrayOf(AlarmTimeEntry.COLUMN_TIME),
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                AlarmTimeEntry.COLUMN_TIME)
    }
}