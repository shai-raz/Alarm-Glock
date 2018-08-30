package com.waker


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.os.CountDownTimer
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.facebook.stetho.Stetho
import com.waker.data.AlarmContract.AlarmGroupEntry
import kotlinx.android.synthetic.main.activity_main.*


private const val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

class MainActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor> {

    private val LOG_TAG = this.javaClass.simpleName

    private lateinit var mNextAlarmTextView: TextView
    private lateinit var mGroupsRecyclerView: RecyclerView
    private lateinit var mEmptyView: TextView
    private lateinit var mFab: FloatingActionButton
    private lateinit var mGroupsAdapter: GroupsAdapter

    private var mIsFirst = true
    var mCountDownTimer: CountDownTimer? = null
    fun cancelCountDownTimer() = mCountDownTimer?.cancel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Stetho.initializeWithDefaults(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            val dialog = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                    .setTitleText(getString(R.string.dialog_permission_required))
                    .setContentText(getString(R.string.dialog_request_permission))
            dialog.setOnDismissListener {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            }
            dialog.show()
        }

        //@PendingIntent TEST
        /*val content = mutableListOf<String>()
        var isExist: Boolean
        for (i in 0 .. 5) {
            for (x in 0 .. 7) {
                isExist = (PendingIntent.getBroadcast(applicationContext,
                        "$i$x".toInt(),
                        Intent(applicationContext, AlarmBroadcastReceiver::class.java),
                        PendingIntent.FLAG_NO_CREATE) != null)
                content.add(i, "[$i$x]:$isExist")
            }
        }
        Log.i(LOG_TAG, content.toString())*/
        /*SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                .setContentText(content.toString())
                .show()*/

        mNextAlarmTextView = next_alarm_textview
        mGroupsRecyclerView = groups_recyclerview
        mEmptyView = groups_empty_view
        mFab = add_group_fab

        mGroupsRecyclerView.addItemDecoration(DividerItemDecoration(this, 1))
        mGroupsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        mGroupsAdapter = GroupsAdapter(this, null)
        mGroupsRecyclerView.adapter = mGroupsAdapter

        LoaderManager.getInstance(this).initLoader(1, null, this)

        mFab.setOnClickListener {
            startActivity(Intent(this, AlarmEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        mFab.startAnimation(AnimationUtils.loadAnimation(this, R.anim.add_group_fab_show))
    }

    override fun onPause() {
        super.onPause()
        cancelCountDownTimer()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
                AlarmGroupEntry.COLUMN_ID,
                AlarmGroupEntry.COLUMN_NAME,
                AlarmGroupEntry.COLUMN_ACTIVE,
                AlarmGroupEntry.COLUMN_SOUND,
                AlarmGroupEntry.COLUMN_DAYS_IN_WEEK,
                AlarmGroupEntry.COLUMN_SOUND)

        Log.i(LOG_TAG, "LOADER: created")

        return CursorLoader(
                this,
                AlarmGroupEntry.CONTENT_URI,
                projection,
                null,
                null,
                null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        updateTimeTillNextAlarm(cursor)
        mGroupsAdapter.swapCursor(cursor)?.close()
        Log.i(LOG_TAG, "LOADER: finished")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mGroupsAdapter.swapCursor(null)?.close()
        Log.i(LOG_TAG, "LOADER: reset")
    }

    /**
     * If there are no alarms set, sets the empty view as VISIBLE,
     * Otherwise, sets it to GONE
     * @param cursor the loaded cursor
     * @return       `true` if the empty view has been set, `false` otherwise
     */
    private fun setEmptyView(cursor: Cursor?): Boolean {
        if (cursor == null || cursor.count == 0) {
            mEmptyView.visibility = View.VISIBLE
            return true
        }
        mEmptyView.visibility = View.GONE
        return false

    }

    /**
     * Updating time left until the next alarm.
     * If no alarms are set - sets an empty view.
     * @param cursor the loaded cursor
     */
    private fun updateTimeTillNextAlarm(cursor: Cursor?) {
        if (!setEmptyView(cursor)) {
            mNextAlarmTextView.visibility = View.VISIBLE

            val nextAlarmDiff = AlarmUtils.getNextAlarmDiff(this)
            if (nextAlarmDiff != null) {
                mCountDownTimer = object : CountDownTimer(nextAlarmDiff, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        mNextAlarmTextView.text = AlarmUtils.getNextAlarmString(this@MainActivity, millisUntilFinished)
                    }

                    override fun onFinish() {
                        mNextAlarmTextView.text = "The alarm will go off any second!"
                    }

                }
                mCountDownTimer!!.start()
                /*val nextAlarm = AlarmUtils.getNextAlarmString(this)

            mNextAlarmTextView.text = nextAlarm*/
            } else {
                mNextAlarmTextView.text = getString(R.string.main_no_active_alarms)
            }
        } else {
            mNextAlarmTextView.visibility = View.GONE
        }
    }

}
