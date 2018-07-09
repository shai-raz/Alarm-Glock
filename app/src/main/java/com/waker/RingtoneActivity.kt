package com.waker

import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_ringtone.*
import kotlinx.android.synthetic.main.ringtone_entry.view.*


class RingtoneActivity: AppCompatActivity() {

    private lateinit var mRecyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ringtone)

        mRecyclerView = ringtone_recycler_view
        mRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"

        val projection = arrayOf(MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION)

        val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null, null)

        mRecyclerView.adapter = RingtoneAdapter(this, cursor)

        cursor.close()

    }

    inner class RingtoneAdapter(private val mContext: AppCompatActivity, private val mCursor: Cursor):
            RecyclerView.Adapter<RingtoneAdapter.ViewHolder>() {

        inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            private val ringtoneNameTextView = view.ringtone_name_text_view
            private val ringtoneRadioButton = view.ringtone_radio_button

            fun update(position: Int) {
                mCursor.moveToPosition(position)

                ringtoneNameTextView.text = mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RingtoneAdapter.ViewHolder{
            val view = LayoutInflater.from(mContext).inflate(R.layout.ringtone_entry, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return mCursor.count
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.update(position)
        }

    }
}