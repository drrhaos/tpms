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

        assertEquals(1L, health.pollCount)
        assertEquals(2L, health.reconnectCount)
        assertEquals(1L, health.staleReconnectCount)
        assertTrue(health.lastPollCompletedAtMs > 0L)
    }

    @Test
    fun healthLine_reflectsStaleState() {
        val health = ServiceHealth()
        val line = health.healthLine(frameAgeSec = 45L, isReconnecting = false, protocolUnhealthy = false)
        assertTrue(line.contains("health=stale"))
    }
}
