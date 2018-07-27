package com.waker


import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import cn.pedant.SweetAlert.SweetAlertDialog
import com.facebook.stetho.Stetho
import com.waker.data.AlarmContract.AlarmGroupEntry
import kotlinx.android.synthetic.main.activity_main.*


private const val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1

class MainActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor> {

    private val LOG_TAG = this.javaClass.simpleName!!

    private lateinit var mGroupsRecyclerView: RecyclerView
    private lateinit var mFab: FloatingActionButton
    private lateinit var mGroupsAdapter: GroupsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Stetho.initializeWithDefaults(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            val dialog = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                    .setTitleText(getString(R.string.dialog_permission_required))
                    .setContentText("In order to be able to choose a ringtone, we need some permissions, please accept in the next dialog.")
            dialog.setOnDismissListener {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            }
            dialog.show()
        }


        //@PendingIntent TEST
        val content = mutableListOf<String>()
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
        Log.i(LOG_TAG, content.toString())
        /*SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                .setContentText(content.toString())
                .show()*/


        mGroupsRecyclerView = groups_recycler_view
        mFab = add_group_fab

        mGroupsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        mGroupsAdapter = GroupsAdapter(this, null)
        mGroupsRecyclerView.adapter = mGroupsAdapter

        LoaderManager.getInstance(this).initLoader(1, null, this)

        mFab.setOnClickListener {
            startActivity(Intent(this, AlarmEditor::class.java))
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
                AlarmGroupEntry.COLUMN_ID,
                AlarmGroupEntry.COLUMN_NAME,
                AlarmGroupEntry.COLUMN_ACTIVE,
                AlarmGroupEntry.COLUMN_SOUND)

        return CursorLoader(
                this,
                AlarmGroupEntry.CONTENT_URI,
                projection,
                null,
                null,
                null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mGroupsAdapter.swapCursor(cursor)?.close()
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mGroupsAdapter.swapCursor(null)?.close()
    }


}
