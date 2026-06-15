package com.tpms.app.domain

object MonitoringHealthPolicy {

    const val DISCONNECT_ALERT_MS = 120_000L
    const val DATA_STALE_UI_SEC = 60L
    const val CRITICAL_ALERT_REPEAT_MS = 5 * 60 * 1000L
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

    fun newestSensorAgeSec(sensors: Collection<com.tpms.app.domain.model.TireSensor>, now: Long): Long? {
        if (sensors.isEmpty()) return null
        val newest = sensors.maxOfOrNull { it.timestamp } ?: return null
        if (newest <= 0L) return null
        return ((now - newest).coerceAtLeast(0)) / 1000
    }
}
