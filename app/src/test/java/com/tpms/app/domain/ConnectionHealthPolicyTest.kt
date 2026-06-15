package com.tpms.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionHealthPolicyTest {

    @Test
    fun shouldReconnectStaleFrame_whenConnectedAndOldValidFrame() {
        val now = 100_000L
        assertTrue(
            ConnectionHealthPolicy.shouldReconnectStaleFrame(
                isUsbConnected = true,
                lastValidFrameAtMs = now - 120_000L,
                staleThresholdMs = 90_000L,
                now = now
            )
        )
    }

    @Test
    fun shouldNotReconnectStaleFrame_whenRecentFrame() {
        val now = 100_000L
        assertFalse(
            ConnectionHealthPolicy.shouldReconnectStaleFrame(
                isUsbConnected = true,
                lastValidFrameAtMs = now - 30_000L,
                staleThresholdMs = 90_000L,
                now = now
            )
        )
    }

    @Test
    fun isProtocolUnhealthy_afterGraceWithoutValidFrames() {
        val now = 300_000L
        assertTrue(
            ConnectionHealthPolicy.isProtocolUnhealthy(
                dongleOpenedAtMs = now - 180_000L,
                lastValidFrameAtMs = 0L,
                unhealthyThresholdMs = 120_000L,
                now = now
            )
        )
    }

    @Test
    fun isProtocolUnhealthy_falseDuringInitialGrace() {
        val now = 100_000L
        assertFalse(
            ConnectionHealthPolicy.isProtocolUnhealthy(
                dongleOpenedAtMs = now - 60_000L,
                lastValidFrameAtMs = 0L,
                unhealthyThresholdMs = 120_000L,
                now = now
            )
        )
    }
}
