package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.ui.main.MainActivity

/**
 * "USB permission required" while a dongle is present without permission (wayfinder 14): quiet
 * status channel, ongoing until [UsbPermissionRequester] dismisses it (granted / dongle gone), with
 * a "Request permission" action. Shown and dismissed only by [UsbPermissionRequester]. A swipe
 * (Android 14+) reports back through the delete intent so the next sync posts it again.
 */
object UsbPermissionNotifier {

    private const val NOTIF_ID = 1103
    private const val REQUEST_OPEN = 0
    private const val REQUEST_PERMISSION = 1
    private const val REQUEST_SWIPED = 2

    /** False when nothing was posted (notifications not allowed). */
    fun show(context: Context): Boolean {
        if (!NotificationHelper.canPostAlerts(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        val openIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            MainActivity.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Ours and explicit, so immutable; the USB permission PendingIntent itself stays mutable (UsbPermissionHelper).
        val requestIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_PERMISSION,
            Intent(context, UsbPermissionReceiver::class.java).apply {
                action = UsbPermissionReceiver.ACTION_REQUEST_USB_PERMISSION
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_SWIPED,
            Intent(context, UsbPermissionReceiver::class.java).apply {
                action = UsbPermissionReceiver.ACTION_PERMISSION_NOTIFICATION_SWIPED
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_usb_permission_title))
            .setContentText(context.getString(R.string.notification_usb_permission_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .addAction(
                android.R.drawable.ic_menu_manage,
                context.getString(R.string.notification_action_request_usb_permission),
                requestIntent
            )
            .build()
        manager.notify(NOTIF_ID, notification)
        return true
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
