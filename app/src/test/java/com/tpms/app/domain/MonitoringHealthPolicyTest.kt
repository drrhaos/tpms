package com.tpms.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringHealthPolicyTest {

    @Test
    fun shouldAlertMonitoringOffline_afterThreshold() {
        val since = 1_000L
        val now = since + MonitoringHealthPolicy.DISCONNECT_ALERT_MS
        assertTrue(MonitoringHealthPolicy.shouldAlertMonitoringOffline(since, now))
    }

    @Test
    fun shouldAlertMonitoringOffline_falseWhenRecent() {
        val since = 10_000L
        assertFalse(MonitoringHealthPolicy.shouldAlertMonitoringOffline(since, since + 30_000L))
    }

    // --- Blind monitoring notification ---

    private val delay = MonitoringHealthPolicy.DISCONNECT_ALERT_MS

    @Test
    fun isBlind_onlyWithWheels() {
        assertFalse(MonitoringHealthPolicy.isBlind(hasWheels = false, hasLiveSession = false, streamingWithoutFrames = false))
        assertTrue(MonitoringHealthPolicy.isBlind(hasWheels = true, hasLiveSession = false, streamingWithoutFrames = false))
        assertTrue(MonitoringHealthPolicy.isBlind(hasWheels = true, hasLiveSession = true, streamingWithoutFrames = true))
        assertFalse(MonitoringHealthPolicy.isBlind(hasWheels = true, hasLiveSession = true, streamingWithoutFrames = false))
    }

    @Test
    fun nextBlind_postsOnceAfterDelay_thenCancelsWhenLive() {
        var state = MonitoringHealthPolicy.BlindState()
        val t0 = 1_000_000L
        var step = MonitoringHealthPolicy.nextBlind(state, blind = true, screenOn = true, screenOnAtMs = 0L, now = t0)
        assertEquals(MonitoringHealthPolicy.BlindAction.NONE, step.first)
        state = step.second
        step = MonitoringHealthPolicy.nextBlind(state, true, true, 0L, t0 + delay)
        assertEquals(MonitoringHealthPolicy.BlindAction.POST, step.first)
        state = step.second
        step = MonitoringHealthPolicy.nextBlind(state, true, true, 0L, t0 + 2 * delay)
        assertEquals(MonitoringHealthPolicy.BlindAction.NONE, step.first)
        state = step.second
        step = MonitoringHealthPolicy.nextBlind(state, false, true, 0L, t0 + 3 * delay)
        assertEquals(MonitoringHealthPolicy.BlindAction.CANCEL, step.first)
        assertEquals(MonitoringHealthPolicy.BlindState(), step.second)
    }

    @Test
    fun nextBlind_silentWithScreenOff_andCountsFromScreenOn() {
        val t0 = 1_000_000L
        var state = MonitoringHealthPolicy.nextBlind(MonitoringHealthPolicy.BlindState(), true, false, 0L, t0).second
        assertEquals(MonitoringHealthPolicy.BlindAction.NONE, MonitoringHealthPolicy.nextBlind(state, true, false, 0L, t0 + 10 * delay).first)
        val screenOn = t0 + 10 * delay
        state = MonitoringHealthPolicy.nextBlind(state, true, true, screenOn, screenOn + 1).second
        assertEquals(MonitoringHealthPolicy.BlindAction.NONE, MonitoringHealthPolicy.nextBlind(state, true, true, screenOn, screenOn + delay - 1).first)
        assertEquals(MonitoringHealthPolicy.BlindAction.POST, MonitoringHealthPolicy.nextBlind(state, true, true, screenOn, screenOn + delay).first)
    }
}
