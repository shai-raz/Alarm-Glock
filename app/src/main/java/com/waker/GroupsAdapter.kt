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
                groupFirstTimeTextView.text = minutesInDayTo24(groupFirstTime)
            }
            if (groupTimes.moveToLast()) {
                val groupEndTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                groupEndTimeTextView.text = minutesInDayTo24(groupEndTime)
            }

            // Populate widgets with values
            groupNameTextView.text = groupName
            groupSwitch.isChecked = isActive

            groupSwitch.setOnTouchListener { view, event ->
                isTouched = true
                /*if (event!!.action == MotionEvent.ACTION_DOWN) {
                    groupSwitch.parent.requestDisallowInterceptTouchEvent(true)
                }*/

                false
            }

            groupSwitch.setOnCheckedChangeListener { button, isChecked ->
                if (isTouched) {
                    isTouched = false
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

    /**
     * Converts minutes in day to HH:mm format
     */
    private fun minutesInDayTo24(time: Int): String {
        var hours = (time / 60).toString()
        if (hours.length == 1) {
            hours = "0$hours"
        }

        var minutes = (time % 60).toString()
        if (minutes.length == 1) {
            minutes = "0$minutes"
        }

        return "$hours:$minutes"
    }
}

/*class GroupsAdapter(private val mContext: Context, private var mCursor: Cursor?): CursorAdapter(mContext, mCursor, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER) {


    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        return LayoutInflater.from(context).inflate(R.layout.main_group_entry, parent, false)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = super.getView(position, convertView, parent)//let the adapter handle setting up the row views
        view.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.background_light)) //default color

        /*if (mSelection.get(position) != null) {
            view.setBackgroundColor(ContextCompat.getColor(mContext, android.R.color.holo_blue_light))// this is a selected position so make it red
        }*/

        return view
    }

    override fun bindView(view: View?, context: Context, cursor: Cursor?) {
        // Declare Widgets
        val groupNameTV: TextView = view!!.findViewById(R.id.group_entry_name)
        val groupFirstTimeTv: TextView = view.findViewById(R.id.group_entry_start_time)
        val groupEndTimeTv: TextView = view.findViewById(R.id.group_entry_end_time)
        val groupSwitch: SwitchCompat = view.findViewById(R.id.group_entry_switch)

        // Get values from Cursors
        val groupId = cursor!!.getInt(cursor.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
        val groupName = cursor.getString(cursor.getColumnIndex(AlarmGroupEntry.COLUMN_NAME))
        val isActive = cursor.getInt(cursor.getColumnIndex(AlarmGroupEntry.COLUMN_ACTIVE)) != 0

        val groupTimes = getGroupTimes(groupId)
        //Toast.makeText(mContext, "Number of times: ${groupTimes.count}", Toast.LENGTH_SHORT).show()
        if (groupTimes.moveToFirst()) {
            val groupFirstTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            groupFirstTimeTv.text = minutesInDayTo24(groupFirstTime)
        }
        if (groupTimes.moveToLast()) {
            val groupEndTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
            groupEndTimeTv.text = minutesInDayTo24(groupEndTime)
        }

        // Populate widgets with values
        groupNameTV.text = groupName
        groupSwitch.isChecked = isActive

        groupSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(button: CompoundButton?, isChecked: Boolean) {
                val values = ContentValues()
                values.put(AlarmGroupEntry.COLUMN_ACTIVE, isChecked)
                context.contentResolver.update(AlarmGroupEntry.CONTENT_URI,
                        values,
                        "${AlarmGroupEntry.COLUMN_ID}=?",
                        arrayOf(groupId.toString()))

                /*if (rowsAffected == 0) {
                    // If the new content URI is null, then there was an error with insertion.
                    Snackbar.make(view, "Error", Snackbar.LENGTH_SHORT).show()
                } else {
                    // Otherwise, the insertion was successful and we can display a toast.
                    Snackbar.make(view, "Saved", Snackbar.LENGTH_SHORT).show()
                }*/

            }

        })

    }

    private fun getGroupTimes(groupId: Int): Cursor {
        return mContext.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                arrayOf(AlarmTimeEntry.COLUMN_TIME),
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                AlarmTimeEntry.COLUMN_TIME)
    }

    /**
     * Converts minutes in day to HH:mm format
     */
    fun minutesInDayTo24(time: Int): String {
        var hours = (time / 60).toString()
        if (hours.length == 1) {
            hours = "0$hours"
        }

        var minutes = (time % 60).toString()
        if (minutes.length == 1) {
            minutes = "0$minutes"
        }

        return "$hours:$minutes"
    }

}*/