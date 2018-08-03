package com.waker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.support.constraint.ConstraintLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.group_entry.view.*
import kotlinx.android.synthetic.main.group_time_entry.view.*

class GroupsAdapter(private val mContext: AppCompatActivity, private var mCursor: Cursor?):
        RecyclerView.Adapter<GroupsAdapter.ViewHolder>() {

    private val LOG_TAG = this.javaClass.simpleName!!

    private val selectedItems = ArrayList<Int>()
    private var mMultiSelect = false
    private var mExpandedPosition = -1

    init {
        setHasStableIds(true)
    }

    private val actionModeCallbacks: ActionMode.Callback = object: ActionMode.Callback {
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mMultiSelect = true
            mContext.menuInflater.inflate(R.menu.menu_main_action, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.menu_main_delete -> {
                    var currentId: Int

                    for (i in selectedItems) {
                        mCursor!!.moveToPosition(i)
                        currentId = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
                        deleteGroup(currentId, i)
                    }

                    mode.finish()
                }
            }

            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            mMultiSelect = false
            clearSelection()
        }

    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Declare widgets
        //private val groupCardView: CardView = view.group_entry_card_view
        private val groupLayout: ConstraintLayout = view.group_entry_layout
        private val groupExpandButton: ImageButton = view.group_entry_expand
        private val groupNameTextView: TextView = view.group_entry_name
        //private val groupFirstTimeLayout: LinearLayout = view.group_entry_first_layout
        private val groupLastTimeLayout: LinearLayout = view.group_entry_last_layout
        private val groupFirstTextView: TextView = view.group_entry_text_view_first
        private val groupFirstTimeTextView: TextView = view.group_entry_start_time
        private val groupLastTimeTextView = view.group_entry_end_time
        private val groupSwitch: SwitchCompat = view.group_entry_switch
        private val groupSoundIndicatorImageView: ImageView = view.group_entry_sound_indicator
        private val groupDOWTextView: TextView = view.group_entry_days_of_week
        private val groupExpandableRecyclerView: RecyclerView = view.group_expandable_recycler_view

        fun update(position: Int) {
            val isExpanded = (position == mExpandedPosition)

            mCursor!!.moveToPosition(position)
            // Get values from the Cursor
            val groupId = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
            val groupName = mCursor!!.getString(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_NAME))
            val isActive: Boolean = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ACTIVE)) != 0
            val isSound: Boolean = mCursor!!.getInt(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_SOUND)) != 0
            val daysOfWeek = AlarmUtils.getDOWArray(mCursor!!.getString(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_DAYS_IN_WEEK)))

            val groupTimes = getGroupTimes(groupId)

            if (groupTimes.moveToFirst()) {
                val groupFirstTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                groupFirstTimeTextView.text = AlarmUtils.minutesInDayTo24(groupFirstTime)
            }
            if (groupTimes.count != 1) {
                if (groupTimes.moveToLast()) {
                    val groupEndTime = groupTimes.getInt(groupTimes.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))
                    groupLastTimeTextView.text = AlarmUtils.minutesInDayTo24(groupEndTime)
                    groupExpandButton.setImageResource(if(isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
                    groupExpandableRecyclerView.visibility = if(isExpanded) View.VISIBLE else View.GONE
                    groupExpandableRecyclerView.layoutManager = LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false)
                    groupExpandableRecyclerView.adapter = TimesAdapater(mContext, groupTimes)
                }
            } else {
                groupLastTimeLayout.visibility = View.GONE
                groupFirstTextView.visibility = View.GONE
                //groupExpandButton.visibility = View.INVISIBLE
                groupExpandButton.setImageResource(R.drawable.ic_alarm_black)
                groupExpandButton.setBackgroundResource(0)
                groupExpandableRecyclerView.visibility = View.GONE
            }

            // Populate widgets with values
            //groupLayout.setBackgroundResource(if(isExpanded) R.drawable.group_shadow else 0)

            itemView.isActivated = isExpanded
            groupNameTextView.text = groupName
            groupSwitch.isChecked = isActive
            groupSoundIndicatorImageView.setImageResource(if(isSound) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
            groupSoundIndicatorImageView.contentDescription = mContext.getString(if(isSound) R.string.description_sound_is_on else R.string.description_sound_is_off)
            groupDOWTextView.text = if (daysOfWeek.contains(1)) daysOfWeekToString(daysOfWeek) else ""


            if (selectedItems.contains(position)) { // Item is selected
                groupLayout.setBackgroundColor(Color.LTGRAY)
            } else { // Item isn't selected
                groupLayout.setBackgroundColor(Color.parseColor("#FAFAFA"))
            }

            if (groupTimes.count != 1) {
                groupExpandButton.setOnClickListener {
                    mExpandedPosition = if (isExpanded) -1 else position
                    notifyItemChanged(position)
                }
            }

            groupSwitch.setOnCheckedChangeListener { button, isChecked ->
                if (button.isPressed) {
                    //isTouched = false
                    val values = ContentValues()
                    values.put(AlarmGroupEntry.COLUMN_ACTIVE, isChecked)
                    mContext.contentResolver.update(AlarmGroupEntry.CONTENT_URI,
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

            itemView.setOnLongClickListener { view ->
                (view.context as AppCompatActivity).startSupportActionMode(actionModeCallbacks)
                selectItem(position)
                true
            }

            itemView.setOnClickListener { view ->
                if (!mMultiSelect) { // If ActionMode is off
                    val intent = Intent(mContext, AlarmEditorActivity::class.java)
                    intent.putExtra("groupId", groupId)

                    mContext.startActivity(intent)
                } else { // Selecting items
                    selectItem(position)
                }
            }
        }

        private fun selectItem(position: Int) {
            if (mMultiSelect) {
                if (selectedItems.contains(position)) {
                    selectedItems.remove(position)
                    groupLayout.setBackgroundColor(Color.WHITE)
                } else {
                    selectedItems.add(position)
                    groupLayout.setBackgroundColor(Color.LTGRAY)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupsAdapter.ViewHolder{
        val view = LayoutInflater.from(mContext).inflate(R.layout.group_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.update(position)
    }

    override fun getItemCount(): Int {
        return mCursor?.count ?: 0
    }

    override fun getItemId(position: Int): Long {
        if (mCursor!!.moveToPosition(position)) {
            return mCursor!!.getLong(mCursor!!.getColumnIndex(AlarmGroupEntry.COLUMN_ID))
        }

        return 0
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

    /**
     * Returns a cursor with all times of a group, sorted by time
     */
    private fun getGroupTimes(groupId: Int): Cursor {
        val projection = arrayOf(AlarmTimeEntry.COLUMN_TIME)
        return mContext.contentResolver.query(AlarmTimeEntry.CONTENT_URI,
                projection,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()),
                AlarmTimeEntry.COLUMN_TIME)
    }

    // ActionMode functions

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun deleteGroup(groupId: Int, position: Int) {
        // Cancel active alarms
        AlarmUtils.cancelGroupAlarms(mContext, groupId)

        // Delete all times of the group
        val deleteAlarmsQuery = mContext.contentResolver.delete(AlarmTimeEntry.CONTENT_URI,
                "${AlarmTimeEntry.COLUMN_GROUP_ID}=?",
                arrayOf(groupId.toString()))

        // Delete the group
        val deleteGroupQuery = mContext.contentResolver.delete(AlarmGroupEntry.CONTENT_URI,
                "${AlarmGroupEntry.COLUMN_ID}=?",
                arrayOf(groupId.toString()))

        notifyItemRemoved(position)
    }

    private inner class TimesAdapater(private val mContext: Context, private var mCursor: Cursor):
            RecyclerView.Adapter<TimesAdapater.ViewHolder>() {

        inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val groupTimeTextView: TextView = view.group_time

            fun update(position: Int) {
                mCursor.moveToPosition(position)
                val groupTime = mCursor.getInt(mCursor.getColumnIndex(AlarmTimeEntry.COLUMN_TIME))

                groupTimeTextView.text = AlarmUtils.minutesInDayTo24(groupTime)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimesAdapater.ViewHolder {
            val view = LayoutInflater.from(mContext).inflate(R.layout.group_time_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.update(position)
        }

        override fun getItemCount(): Int {
            return mCursor.count
        }

    }

    private fun daysOfWeekToString(daysOfWeek: List<Int>): String {
        val x: Map<Int, String> = mapOf(0 to "Sun",
                1 to "Mon",
                2 to "Tue",
                3 to "Wed",
                4 to "Thu",
                5 to "Fri",
                6 to "Sat")

        var string = ""
        if (daysOfWeek.contains(0)) {
            daysOfWeek.forEachIndexed { i, day ->
                if (day == 1) {
                    string += "${x[i]}, "
                }
            }
            string = string.removeSuffix(", ")
        } else {
            string = mContext.getString(R.string.group_entry_everyday)
        }

        return string
    }
}