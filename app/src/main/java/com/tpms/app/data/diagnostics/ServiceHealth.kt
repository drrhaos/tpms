package com.tpms.app.data.diagnostics

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceHealth @Inject constructor() {
    val serviceStartedAtMs: Long = System.currentTimeMillis()

    @Volatile var pollCount: Long = 0
        private set
    @Volatile var reconnectCount: Long = 0
        private set
    @Volatile var staleReconnectCount: Long = 0
        private set
    @Volatile var lastPollCompletedAtMs: Long = 0
        private set
    @Volatile var lastError: String? = null
        private set

    fun recordPollStart() {
        pollCount++
    }

    fun recordPollCompleted() {
        lastPollCompletedAtMs = System.currentTimeMillis()
    }

    fun recordReconnect() {
        reconnectCount++
    }

    fun recordStaleReconnect() {
        staleReconnectCount++
        reconnectCount++
    }

    fun recordError(message: String) {
        lastError = message
    }

    fun uptimeSeconds(now: Long = System.currentTimeMillis()): Long =
        (now - serviceStartedAtMs).coerceAtLeast(0) / 1000

    fun healthLine(
        frameAgeSec: Long?,
        isReconnecting: Boolean,
        protocolUnhealthy: Boolean
    ): String {
        val health = when {
            isReconnecting -> "reconnecting"
            protocolUnhealthy -> "protocol_warn"
            frameAgeSec == null -> "no_data"
            frameAgeSec <= 30 -> "ok"
            frameAgeSec <= 90 -> "stale"
            else -> "critical"
        }
        val err = lastError?.let { " err=$it" }.orEmpty()
        return "health=$health uptime=${uptimeSeconds()}s polls=$pollCount reconn=$reconnectCount stale=$staleReconnectCount$err"
    }

    fun reportBlock(): String = buildString {
        appendLine("--- Service health ---")
        appendLine("uptime_sec: ${uptimeSeconds()}")
        appendLine("poll_count: $pollCount")
        appendLine("reconnect_count: $reconnectCount")
        appendLine("stale_reconnect_count: $staleReconnectCount")
        appendLine("last_poll_completed_ms: $lastPollCompletedAtMs")
        lastError?.let { appendLine("last_error: $it") }
    }
}
