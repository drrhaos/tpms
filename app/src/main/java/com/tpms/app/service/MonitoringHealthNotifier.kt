package com.tpms.app.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.domain.MonitoringHealthPolicy
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Monitoring not working" (wayfinder 15 follow-up): when known wheels get no live data for
 * [MonitoringHealthPolicy.DISCONNECT_ALERT_MS] with the screen on (dongle gone, no permission, stuck
 * reader, silent receiver), one audible notification on the alert channel per blind spell
 * ([MonitoringHealthPolicy.nextBlind]); removed when live data is back. Without it the driver would
 * trust a header nobody looks at. Called from the service's UI refresh (IO thread) and SCREEN_ON.
 */
@Singleton
class MonitoringHealthNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var state = MonitoringHealthPolicy.BlindState()

    @Volatile private var screenOnAtMs = 0L

    /** SCREEN_ON: reconnecting after a wake does not count towards the blind delay. */
    fun onScreenOn(now: Long = System.currentTimeMillis()) {
        screenOnAtMs = now
    }

    /** [blind] per [MonitoringHealthPolicy.isBlind]; [reason] is shown as the notification text. */
    @Synchronized
    fun evaluate(blind: Boolean, reason: String?, now: Long = System.currentTimeMillis()) {
        val screenOn = powerManager?.isInteractive ?: true
        val (action, next) = MonitoringHealthPolicy.nextBlind(state, blind, screenOn, screenOnAtMs, now)
        when (action) {
            MonitoringHealthPolicy.BlindAction.POST -> {
                // A refused post is retried on the next evaluation.
                if (!post(reason)) {
                    state = next.copy(notified = false)
                    return
                }
            }
            MonitoringHealthPolicy.BlindAction.CANCEL -> notificationManager.cancel(NOTIF_OFFLINE)
            MonitoringHealthPolicy.BlindAction.NONE -> Unit
        }
        state = next
    }

    /** Monitoring stopped or nothing to monitor: forget the spell and remove the notification. */
    @Synchronized
    fun clear() {
        state = MonitoringHealthPolicy.BlindState()
        notificationManager.cancel(NOTIF_BLIND)
        notificationManager.cancel(NOTIF_OFFLINE)
    }

    private fun post(reason: String?): Boolean {
        if (!NotificationHelper.canPostAlerts(context)) return false
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tpms)
            .setContentTitle(context.getString(R.string.notification_monitoring_blind_title))
            .setContentText(reason ?: context.getString(R.string.notification_monitoring_blind_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        return runCatching { notificationManager.notify(NOTIF_OFFLINE, notification) }.isSuccess
    }

    companion object {
        /** Legacy id, only cancelled. */
        private const val NOTIF_BLIND = 1101
        private const val NOTIF_OFFLINE = 1102
        private const val REQUEST_OPEN = 1102
    }
}

object NotificationHelper {
    fun canPostAlerts(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    /**
     * Tyre alerts can pop up with sound: notifications allowed and the alert channel not lowered
     * below HIGH by the user (a channel that does not exist yet counts as fine: it is created HIGH).
     */
    fun isAlertChannelLoud(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(TpmsApplication.CHANNEL_ALERT) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
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
