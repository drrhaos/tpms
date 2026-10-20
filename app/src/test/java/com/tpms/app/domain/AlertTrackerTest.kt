package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertTrackerTest {

    private val thresholds = AlertThresholds(lowPressureKpa = 150f)
    private val tracker = AlertTracker()

    private fun reading(id: String, pressure: Float, at: Long) = TireSensor(
        id = id,
        label = id,
        pressureKpa = pressure,
        temperatureCelsius = 30f,
        batteryPercent = 80,
        alertType = null,
        timestamp = at
    )

    @Test
    fun lowPressureIsHeldByHysteresisOnNextReading() {
        assertEquals(AlertType.LOW_PRESSURE, tracker.apply(reading("FL", 140f, 0L), thresholds).alertType)
        // Back above the threshold but within the release margin: still LOW.
        assertEquals(AlertType.LOW_PRESSURE, tracker.apply(reading("FL", 155f, 1_000L), thresholds).alertType)
    }

    @Test
    fun sensorsAreTrackedIndependently() {
        tracker.apply(reading("FL", 140f, 0L), thresholds)

        assertNull(tracker.apply(reading("FR", 155f, 1_000L), thresholds).alertType)
    }

    @Test
    fun retainDropsHistoryOfOtherSensors() {
        tracker.apply(reading("FL", 140f, 0L), thresholds)
        tracker.apply(reading("RR", 140f, 0L), thresholds)
        tracker.retain(setOf("FL"))

        assertEquals(AlertType.LOW_PRESSURE, tracker.apply(reading("FL", 155f, 1_000L), thresholds).alertType)
        assertNull(tracker.apply(reading("RR", 155f, 1_000L), thresholds).alertType)
    }

    @Test
    fun clearDropsAllHistory() {
        tracker.apply(reading("FL", 140f, 0L), thresholds)
        tracker.clear()

        assertNull(tracker.apply(reading("FL", 155f, 1_000L), thresholds).alertType)
    }

    @Test
    fun returnsReadingWithOnlyAlertChanged() {
        val input = reading("RL", 220f, 5_000L)

        assertEquals(input, tracker.apply(input, thresholds))
    }
}
