package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor
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

    @Test
    fun newestSensorAgeSec_returnsOldestGapFromNewestTimestamp() {
        val now = 100_000L
        val sensors = listOf(
            sensor("1", now - 30_000L),
            sensor("2", now - 90_000L)
        )
        assertEquals(30L, MonitoringHealthPolicy.newestSensorAgeSec(sensors, now))
    }

    private fun sensor(id: String, timestamp: Long): TireSensor =
        TireSensor(
            id = id,
            pressureKpa = 220f,
            temperatureCelsius = 20f,
            batteryPercent = 90,
            alertType = null,
            timestamp = timestamp
        )
}
