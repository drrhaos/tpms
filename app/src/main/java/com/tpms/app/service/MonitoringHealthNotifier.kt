package com.tpms.app.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.domain.MonitoringHealthPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates monitoring health and dismisses legacy shade notifications.
 * Status is surfaced in the main screen header instead of posting alerts.
 */
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
        val offline = MonitoringHealthPolicy.shouldAlertMonitoringOffline(disconnectedSinceMs, now)
        val blind = !offline && MonitoringHealthPolicy.shouldAlertMonitoringBlind(
            dongleOpenedAtMs = dongleOpenedAtMs,
            lastValidFrameAtMs = lastValidFrameAtMs,
            now = now
        )

        if (offline) {
            offlineNotified = true
            blindNotified = false
            return
        }
        if (offlineNotified) {
            offlineNotified = false
            notificationManager.cancel(NOTIF_OFFLINE)
        }

        if (blind) {
            blindNotified = true
        } else if (blindNotified) {
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
