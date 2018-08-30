package com.waker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat.startActivity
import android.util.Log
import com.waker.data.AlarmContract.AlarmEntry

class AlarmBroadcastReceiver: BroadcastReceiver() {

    private val LOG_TAG = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getIntExtra("groupId", 0)
        val timeId = intent.getIntExtra("timeId", 0)
        val alarmId = intent.getIntExtra("alarmId", 0)

        Log.i(LOG_TAG, "onReceive(): groupId $groupId timeId $timeId, alarmId $alarmId")

        val cursor = context.contentResolver.query(AlarmEntry.CONTENT_URI,
                arrayOf(AlarmEntry.COLUMN_ALARM_ID),
                "${AlarmEntry.COLUMN_ALARM_ID}=?",
                arrayOf(alarmId.toString()),
                null)

        if (cursor?.count != 0) {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                // Add flags to make the AlarmActivity be independent
                //addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                /*addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)*/
                /*flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                flags = Intent.FLAG_ACTIVITY_NEW_TASK*/

                putExtra("groupId", groupId)
                putExtra("timeId", timeId)
                putExtra("alarmId", alarmId)
            }
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            /*alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)*/

            startActivity(context, alarmIntent, null)
        }

        cursor?.close()


        //val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        /*notificationManager.notify(intent!!.getIntExtra("notificationId", 0),
                Notification.Builder(context).apply {
                    setSmallIcon(android.R.drawable.ic_dialog_info)
                    setContentTitle("contentTitle")
                    setContentText("contentText")
                    setWhen(System.currentTimeMillis())
                    setPriority(Notification.PRIORITY_DEFAULT)
                    setAutoCancel(true)
                    setDefaults(Notification.DEFAULT_SOUND)
                    setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
                }.build())*/
    }
}