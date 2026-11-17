package com.tpms.app.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.domain.MonitoringHealthPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Monitoring not working" (wayfinder alerts 01 / 05): tracks blind spells with
 * [MonitoringHealthPolicy.nextBlind] — known wheels without live data for
 * [MonitoringHealthPolicy.DISCONNECT_ALERT_MS] with the screen on, counted from the later of the
 * spell's start and the last SCREEN_ON — and reports whether it is due. [AlertCoordinator] presents
 * it as a fault. Called from the service's UI refresh (IO thread) and SCREEN_ON.
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

    /** [blind] per [MonitoringHealthPolicy.isBlind]; returns whether "monitoring not working" is due. */
    @Synchronized
    fun evaluate(blind: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val screenOn = powerManager?.isInteractive ?: true
        state = MonitoringHealthPolicy.nextBlind(state, blind, screenOn, screenOnAtMs, now).second
        return state.notified
    }

    /** Monitoring stopped: forget the spell; removes notifications of older versions. */
    @Synchronized
    fun clear() {
        state = MonitoringHealthPolicy.BlindState()
        LEGACY_IDS.forEach { notificationManager.cancel(it) }
    }

    companion object {
        /** Ids older versions posted "monitoring" notifications under; only cancelled. */
        private val LEGACY_IDS = listOf(1101, 1102)
    }
}

object NotificationHelper {
    fun canPostAlerts(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    /**
     * Tyre alert notifications can pop up: notifications allowed and the alert channel not lowered
     * below HIGH by the user (a channel that does not exist yet counts as fine: it is created HIGH).
     * The signal itself is played by [AlertSoundPlayer], not by the channel.
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
