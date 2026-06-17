package com.tpms.app.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.domain.MonitoringHealthPolicy
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoringHealthNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var blindNotified = false
    private var offlineNotified = false

    fun evaluate(
        disconnectedSinceMs: Long?,
        dongleOpenedAtMs: Long,
        lastValidFrameAtMs: Long,
        now: Long = System.currentTimeMillis()
    ) {
        if (!NotificationHelper.canPostAlerts(context)) return

        val offline = MonitoringHealthPolicy.shouldAlertMonitoringOffline(disconnectedSinceMs, now)
        val blind = !offline && MonitoringHealthPolicy.shouldAlertMonitoringBlind(
            dongleOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs,
            now = now
        )

        if (offline) {
            if (!offlineNotified) {
                post(
                    NOTIF_OFFLINE,
                    context.getString(R.string.notification_monitoring_offline_title),
                    context.getString(R.string.notification_monitoring_offline_body)
                )
                offlineNotified = true
            }
            blindNotified = false
            return
        }
        if (offlineNotified) {
            offlineNotified = false
            notificationManager.cancel(NOTIF_OFFLINE)
        }

        if (blind) {
            if (!blindNotified) {
                post(
                    NOTIF_BLIND,
                    context.getString(R.string.notification_monitoring_blind_title),
                    context.getString(R.string.notification_monitoring_blind_body)
                )
                blindNotified = true
            }
        } else {
            blindNotified = false
            notificationManager.cancel(NOTIF_BLIND)
        }
    }

    fun clear() {
        blindNotified = false
        offlineNotified = false
        notificationManager.cancel(NOTIF_BLIND)
        notificationManager.cancel(NOTIF_OFFLINE)
    }

    private fun post(id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        notificationManager.notify(id, notification)
    }

    companion object {
        private const val NOTIF_BLIND = 1101
        private const val NOTIF_OFFLINE = 1102
    }
}

object NotificationHelper {
    fun canPostAlerts(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }
}

object ServiceRunningChecker {
    fun isRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}

object ServiceLivenessEvaluator {
    fun shouldRestart(
        heartbeatStore: ServiceHeartbeatStore,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val lastBeat = heartbeatStore.lastBeatMsBlocking()
        if (lastBeat <= 0L) return false
        return now - lastBeat > MonitoringHealthPolicy.SERVICE_HEARTBEAT_STALE_MS
    }
}
