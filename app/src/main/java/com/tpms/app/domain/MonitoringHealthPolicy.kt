package com.tpms.app.domain

object MonitoringHealthPolicy {

    const val DISCONNECT_ALERT_MS = 120_000L
    const val SERVICE_HEARTBEAT_STALE_MS = 90_000L
    const val SERVICE_LIVENESS_INTERVAL_MS = 5 * 60 * 1000L

    fun shouldAlertMonitoringOffline(disconnectedSinceMs: Long?, now: Long = System.currentTimeMillis()): Boolean {
        val since = disconnectedSinceMs ?: return false
        return now - since >= DISCONNECT_ALERT_MS
    }

    fun shouldAlertMonitoringBlind(
        dongleOpenedAtMs: Long,
        lastValidFrameAtMs: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean = ConnectionHealthPolicy.isProtocolUnhealthy(
        dongleOpenedAtMs = dongleOpenedAtMs,
        lastValidFrameAtMs = lastValidFrameAtMs,
        now = now
    )
}
