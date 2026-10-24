package com.tpms.app.domain

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
}
