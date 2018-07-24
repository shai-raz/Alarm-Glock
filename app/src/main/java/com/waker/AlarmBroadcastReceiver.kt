package com.waker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat.startActivity

class AlarmBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val groupId = intent!!.getIntExtra("groupId", 0)
        val timeId = intent.getIntExtra("timeId", 0)

        val alarmIntent = Intent(context, AlarmActivity::class.java)
        // Add flags to make the AlarmActivity be independent
        alarmIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        alarmIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        alarmIntent.putExtra("groupId", groupId)
        alarmIntent.putExtra("timeId", timeId)
        startActivity(context!!, alarmIntent, null)
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