package com.waker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlinx.android.synthetic.main.activity_choose_ringtone.*
import kotlinx.android.synthetic.main.ringtone_header_entry.view.*
import kotlinx.android.synthetic.main.ringtone_item_entry.view.*


private const val SELECT_LCOAL_RINGTONE_RESULTS = 2

class ChooseRingtoneActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<List<Pair<Uri,Ringtone>>> {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mCancelButton: ImageButton
    private lateinit var mSaveButton: ImageButton
    private lateinit var mRingtoneRecyclerView: RecyclerView
    private lateinit var mLoadingProgressBar: ProgressBar

    private lateinit var mRingtoneAdapter: RingtoneAdapter
    private var mRingtone: Ringtone? = null
    private var mSelectedRingtone: Int? = null
    private var mLocalRingtoneUri: Uri? = Uri.EMPTY
    private var mDefaultRingtoneUri: Uri? = Uri.EMPTY
    private var mRingtoneUri: Uri? = Uri.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_ringtone)

        mCancelButton = ringtone_chooser_toolbar_cancel
        mSaveButton = ringtone_chooser_toolbar_save
        mRingtoneRecyclerView = ringtone_chooser_recycler_view
        mLoadingProgressBar = ringtone_chooser_progress_bar

        mRingtoneRecyclerView.addItemDecoration(DividerItemDecoration(this, 1))
        mRingtoneRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        mRingtoneAdapter = RingtoneAdapter(this, null)
        mRingtoneRecyclerView.adapter = mRingtoneAdapter

        LoaderManager.getInstance(this).initLoader(1, null, this).forceLoad()

        mCancelButton.setOnClickListener {
            stopPlayingRingtone()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        mSaveButton.setOnClickListener {
            stopPlayingRingtone()
            when (mSelectedRingtone) {
                null -> Toast.makeText(this, getString(R.string.ringtone_chooser_please_choose_a_ringtone_first), Toast.LENGTH_SHORT).show()
                0 -> mRingtoneUri = mLocalRingtoneUri
                else -> {
                    if (mDefaultRingtoneUri != Uri.EMPTY) {
                        mRingtoneUri = mDefaultRingtoneUri
                    }
                }
            }

            if (mRingtoneUri != Uri.EMPTY) {
                val resultIntent = Intent()
                resultIntent.data = mRingtoneUri
                setResult(RESULT_OK, resultIntent)
            } else {
                setResult(Activity.RESULT_CANCELED)
            }

            finish()
        }
    }

    override fun onBackPressed() {
        if (mRingtone != null && mRingtone!!.isPlaying) {
            mRingtone!!.stop()
        } else {
            super.onBackPressed()
        }
    }

    inner class RingtoneAdapter(private val mContext: AppCompatActivity, private var mRingtoneList: List<Pair<Uri, Ringtone>>?):
            RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_ITEM = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_ITEM) {
                val view = LayoutInflater.from(mContext).inflate(R.layout.ringtone_item_entry, parent, false)
                return VHItem(view)
            } else {
                val view = LayoutInflater.from(mContext).inflate(R.layout.ringtone_header_entry, parent, false)
                return VHHeader(view)
            }

            /*val view = LayoutInflater.from(mContext).inflate(R.layout.ringtone_item_entry, parent, false)
            return ViewHolder(view)*/
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is VHHeader) {
                holder.update()
            } else if (holder is VHItem) {
                holder.update(position)
            }
        }

        override fun getItemCount(): Int {
            return if (mRingtoneList != null) mRingtoneList!!.size + 1 else 1
        }

        override fun getItemViewType(position: Int): Int {
            if (position == 0) {
                return TYPE_HEADER
            }
            return TYPE_ITEM
        }

        fun getItem(position: Int): Pair<Uri, Ringtone>? {
            return if (mRingtoneList != null) mRingtoneList!![position - 1] else null
        }

        inner class VHHeader(view: View): RecyclerView.ViewHolder(view) {
            private val localRingtoneNameTextView: TextView = view.ringtone_chooser_local_ringtone_name_text_view
            private val localRingtoneRadioButton: RadioButton = view.ringtone_chooser_local_ringtone_radio_button

            fun update() {
                if (mLocalRingtoneUri != Uri.EMPTY) {
                    val ringtoneCursor = contentResolver.query(
                            mLocalRingtoneUri,
                            arrayOf(MediaStore.Audio.Media.TITLE),
                            null, null, null)
                    if (ringtoneCursor != null && ringtoneCursor.moveToFirst()) {
                        localRingtoneNameTextView.text = ringtoneCursor.getString(ringtoneCursor.getColumnIndex(MediaStore.MediaColumns.TITLE))
                        ringtoneCursor.close()
                        localRingtoneRadioButton.isEnabled = true
                        localRingtoneRadioButton.isChecked = mSelectedRingtone == 0
                    } else {
                        localRingtoneNameTextView.text = getString(R.string.ringtone_chooser_no_local_ringtone_chosen)
                    }
                } else {
                    localRingtoneRadioButton.isEnabled = false
                }

                itemView.setOnClickListener {
                    stopPlayingRingtone()
                    val ringtoneIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(ringtoneIntent, SELECT_RINGTONE_RESULTS)
                }

                localRingtoneRadioButton.setOnClickListener {
                    stopPlayingRingtone()
                    selectLocalRingtone()
                }
            }

            private fun selectLocalRingtone() {
                mDefaultRingtoneUri = Uri.EMPTY
                /*if (mLocalRingtoneUri != Uri.EMPTY) { // Override local chosen ringtone (if one was chosen)
                    mLocalRingtoneUri = Uri.EMPTY
                    notifyItemChanged(0) // update header
                }*/
                var oldPosition: Int? = null
                if (mSelectedRingtone != null) {
                    oldPosition = mSelectedRingtone

                }
                mSelectedRingtone = 0
                if (oldPosition != null) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(mSelectedRingtone!!)
            }
        }

        inner class VHItem(view: View): RecyclerView.ViewHolder(view) {
            private val ringtoneNameTextView: TextView = view.ringtone_entry_name_text_view
            private val ringtoneRadioButton: RadioButton = view.ringtone_entry_radio_button

            fun update(position: Int) {
                val pos = position - 1
                ringtoneRadioButton.isChecked = mSelectedRingtone == position
                ringtoneNameTextView.text = mRingtoneList!![pos].second.getTitle(mContext)

                itemView.setOnClickListener {
                    stopPlayingRingtone()
                    mRingtone = mRingtoneList!![pos].second
                    mRingtone!!.play()
                    selectRingtone(position)
                }
            }

            private fun selectRingtone(position: Int) {
                mDefaultRingtoneUri = mRingtoneList!![position - 1].first
                /*if (mLocalRingtoneUri != Uri.EMPTY) { // Override local chosen ringtone (if one was chosen)
                    mLocalRingtoneUri = Uri.EMPTY
                    notifyItemChanged(0) // update header
                }*/
                var oldPosition: Int? = null
                if (mSelectedRingtone != null) {
                    oldPosition = mSelectedRingtone

                }
                mSelectedRingtone = position
                if (oldPosition != null) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(mSelectedRingtone!!)
            }
        }

        fun swapList(list: List<Pair<Uri, Ringtone>>?): List<Pair<Uri, Ringtone>>? {
            if (list == mRingtoneList) {
                return null
            }

            val oldList = mRingtoneList

            mRingtoneList = list
            if (list != null) {
                notifyDataSetChanged()

            }

            return oldList
        }
    }

    class RingtoneLoader(context: Context): AsyncTaskLoader<List<Pair<Uri,Ringtone>>>(context) {
        override fun loadInBackground(): List<Pair<Uri, Ringtone>> {
            return getDefaultRingtoneList()
        }

        private fun getDefaultRingtoneList(): List<Pair<Uri,Ringtone>> {
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = ringtoneManager.cursor

            val list = mutableListOf<Pair<Uri,Ringtone>>()
            while (cursor.moveToNext()) {
                val pos = cursor.position
                list.add(Pair(ringtoneManager.getRingtoneUri(pos), ringtoneManager.getRingtone(pos)))
            }

            return list
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<Pair<Uri,Ringtone>>> {
        return RingtoneLoader(this)
    }

    override fun onLoadFinished(loader: Loader<List<Pair<Uri,Ringtone>>>, list: List<Pair<Uri,Ringtone>>?) {
        mRingtoneAdapter.swapList(list)
        mLoadingProgressBar.visibility = View.GONE
        //mRingtoneRecyclerView.visibility = View.VISIBLE
    }

    override fun onLoaderReset(loader: Loader<List<Pair<Uri,Ringtone>>>) {
        mRingtoneAdapter.swapList(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                SELECT_RINGTONE_RESULTS -> {
                    mLocalRingtoneUri = data!!.data

                    mDefaultRingtoneUri = Uri.EMPTY
                    var oldPosition: Int? = null
                    if (mSelectedRingtone != null) {
                        oldPosition = mSelectedRingtone
                    }
                    mSelectedRingtone = 0
                    if (oldPosition != null) {
                        mRingtoneRecyclerView.adapter!!.notifyItemChanged(oldPosition)
                    }
                    mRingtoneRecyclerView.adapter!!.notifyItemChanged(mSelectedRingtone!!)
                }
            }
        } else {
            if (requestCode == SELECT_RINGTONE_RESULTS) {
                Toast.makeText(this, getString(R.string.editor_no_ringtone_was_chosen), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopPlayingRingtone() {
        if (mRingtone != null && mRingtone!!.isPlaying) { // Stop previously played ringtone
            mRingtone!!.stop()
        }
    }

    private fun getUserMediaCursor(): Cursor {
        val projection = arrayOf(MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME)

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null)
    }


}

