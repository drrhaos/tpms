package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.ui.main.MainActivity

object UsbPermissionNotifier {

    private const val NOTIF_ID = 1103

    fun show(context: Context) {
        if (!NotificationHelper.canPostAlerts(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            MainActivity.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_usb_permission_title))
            .setContentText(context.getString(R.string.notification_usb_permission_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.notification_action_open),
                openIntent
            )
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
