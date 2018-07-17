package com.waker

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SwitchCompat
import android.view.*
import android.widget.TextView
import com.waker.data.AlarmContract.AlarmGroupEntry
import com.waker.data.AlarmContract.AlarmTimeEntry
import kotlinx.android.synthetic.main.main_group_entry.view.*

class GroupsAdapter(private val mContext: AppCompatActivity, private var mCursor: Cursor?):
        RecyclerView.Adapter<GroupsAdapter.ViewHolder>() {

    private val selectedItems = ArrayList<Int>()
    private var mMultiSelect = false

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
        private val groupCardView: CardView = view.group_entry_card_view
        private val groupNameTextView: TextView = view.group_entry_name
        private val groupFirstTimeTextView: TextView = view.group_entry_start_time
        private val groupEndTimeTextView = view.group_entry_end_time
        private val groupSwitch: SwitchCompat = view.group_entry_switch

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

            if (selectedItems.contains(position)) { // Item is selected
                groupCardView.setCardBackgroundColor(Color.LTGRAY)
            } else { // Item isn't selected
                groupCardView.setCardBackgroundColor(Color.WHITE)
            }

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

            itemView.setOnLongClickListener { view ->
                (view.context as AppCompatActivity).startSupportActionMode(actionModeCallbacks)
                selectItem(position)
                true
            }

            itemView.setOnClickListener { view ->
                if (!mMultiSelect) { // If ActionMode is off
                    val intent = Intent(mContext, AlarmEditor::class.java)
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
                    groupCardView.setCardBackgroundColor(Color.WHITE)
                } else {
                    selectedItems.add(position)
                    groupCardView.setCardBackgroundColor(Color.LTGRAY)
                }
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
}