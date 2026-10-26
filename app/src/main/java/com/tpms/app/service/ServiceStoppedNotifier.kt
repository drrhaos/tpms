package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.ui.main.MainActivity

/**
 * "Monitoring stopped" notice after an unexpected service stop. A status message, not a tire alert:
 * it goes to [TpmsApplication.CHANNEL_STATUS], so the alert channel (whose sound "Configure alert
 * sound" in Settings opens) carries only per-wheel tire alerts (wayfinder 15).
 */
object ServiceStoppedNotifier {

    private const val NOTIF_ID = 1002

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val restartIntent = PendingIntent.getActivity(
            context,
            0,
            MainActivity.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_service_stopped_title))
            .setContentText(context.getString(R.string.notification_service_stopped_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(restartIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.notification_action_restart),
                restartIntent
            )
            .build()
        manager.notify(NOTIF_ID, notification)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
