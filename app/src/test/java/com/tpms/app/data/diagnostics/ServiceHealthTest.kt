package com.tpms.app.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceHealthTest {

    @Test
    fun recordPollAndReconnect_incrementsCounters() {
        val health = ServiceHealth()
        health.recordPollStart()
        health.recordPollCompleted()
        health.recordReconnect()
        health.recordStaleReconnect()
        health.recordReadTimeout()
        health.recordWatchdogRestart()

        assertEquals(1L, health.pollCount)
        assertEquals(2L, health.reconnectCount)
        assertEquals(1L, health.staleReconnectCount)
        assertEquals(1L, health.readTimeoutCount)
        assertEquals(1L, health.watchdogRestartCount)
        assertTrue(health.lastPollCompletedAtMs > 0L)
    }

    @Test
    fun healthLine_reflectsStaleState() {
        val health = ServiceHealth()
        val line = health.healthLine(frameAgeSec = 45L, isReconnecting = false, protocolUnhealthy = false)
        assertTrue(line.contains("health=stale"))
    }
}
