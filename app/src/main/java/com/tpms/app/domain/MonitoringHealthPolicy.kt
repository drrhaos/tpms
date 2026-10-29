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

    /**
     * Monitoring is blind: wheels are known (live or restored) but no live data reaches them — no
     * open session (dongle gone, reconnecting, no permission, stuck), or a streaming session without
     * valid frames for [DISCONNECT_ALERT_MS]. HID may legitimately be quiet, so only a missing
     * session counts there.
     */
    fun isBlind(hasWheels: Boolean, hasLiveSession: Boolean, streamingWithoutFrames: Boolean): Boolean =
        hasWheels && (!hasLiveSession || streamingWithoutFrames)

    /** What the "monitoring not working" notification must do. */
    enum class BlindAction { POST, CANCEL, NONE }

    /** [blindSinceMs]: start of the current blind spell; [notified]: its notification was posted. */
    data class BlindState(val blindSinceMs: Long? = null, val notified: Boolean = false)

    /**
     * One audible "monitoring not working" per blind spell: posted once the spell lasted
     * [DISCONNECT_ALERT_MS] with the screen on, counted from the later of the spell's start and the
     * last SCREEN_ON ([screenOnAtMs]; reconnecting after a wake is not a fault); cancelled when live
     * data is back. With the screen off nothing is posted (the car is parked).
     */
    fun nextBlind(
        state: BlindState,
        blind: Boolean,
        screenOn: Boolean,
        screenOnAtMs: Long,
        now: Long
    ): Pair<BlindAction, BlindState> {
        if (!blind) {
            return (if (state.notified) BlindAction.CANCEL else BlindAction.NONE) to BlindState()
        }
        val since = state.blindSinceMs ?: now
        val next = state.copy(blindSinceMs = since)
        if (state.notified || !screenOn) return BlindAction.NONE to next
        val from = maxOf(since, screenOnAtMs)
        return if (now - from >= DISCONNECT_ALERT_MS) {
            BlindAction.POST to next.copy(notified = true)
        } else {
            BlindAction.NONE to next
        }
    }
}
