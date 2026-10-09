package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertEvaluatorTest {

    private val thresholds = AlertThresholds(
        lowPressureKpa = 150f,
        highPressureKpa = 350f,
        highTempCelsius = 85f
    )

    private fun sensor(
        pressureKpa: Float = 240f,
        temperatureCelsius: Float = 25f,
        batteryPercent: Int? = 80,
        id: String = "FL",
        flags: SensorFlags = SensorFlags.NONE
    ) = TireSensor(
        id = id,
        label = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        alertType = null,
        timestamp = 1L,
        flags = flags
    )

    @Test
    fun evaluate_returnsNull_whenWithinThresholds() {
        assertNull(AlertEvaluator.evaluate(sensor(), thresholds))
    }

    @Test
    fun evaluate_detectsLowPressure() {
        assertEquals(
            AlertType.LOW_PRESSURE,
            AlertEvaluator.evaluate(sensor(pressureKpa = 120f), thresholds)
        )
    }

    @Test
    fun evaluate_detectsHighPressure() {
        assertEquals(
            AlertType.HIGH_PRESSURE,
            AlertEvaluator.evaluate(sensor(pressureKpa = 400f), thresholds)
        )
    }

    @Test
    fun evaluate_detectsHighTemperature() {
        assertEquals(
            AlertType.HIGH_TEMP,
            AlertEvaluator.evaluate(sensor(temperatureCelsius = 90f), thresholds)
        )
    }

    @Test
    fun evaluate_detectsLowBattery() {
        assertEquals(
            AlertType.BATTERY_LOW,
            AlertEvaluator.evaluate(sensor(batteryPercent = 5), thresholds)
        )
    }

    @Test
    fun evaluate_mapsHardwareFlagsBeforeThresholds() {
        assertEquals(
            AlertType.SENSOR_LOST,
            AlertEvaluator.evaluate(sensor(pressureKpa = 100f, flags = SensorFlags(noSignal = true)), thresholds)
        )
        assertEquals(
            AlertType.LOW_PRESSURE,
            AlertEvaluator.evaluate(sensor(flags = SensorFlags(leak = true)), thresholds)
        )
        assertEquals(
            AlertType.BATTERY_LOW,
            AlertEvaluator.evaluate(sensor(batteryPercent = null, flags = SensorFlags(lowBattery = true)), thresholds)
        )
    }

    @Test
    fun evaluate_thresholdAlertOutranksLowBatteryWarning() {
        assertEquals(
            AlertType.HIGH_TEMP,
            AlertEvaluator.evaluate(
                sensor(temperatureCelsius = 120f, flags = SensorFlags(lowBattery = true)),
                thresholds
            )
        )
    }

    @Test
    fun evaluate_unknownBatteryIsNotLow() {
        assertNull(AlertEvaluator.evaluate(sensor(batteryPercent = null), thresholds))
    }

    @Test
    fun evaluate_spareIgnoresThresholdsButNotFlags() {
        // Without a fifth sensor the receiver reports a dummy value for the spare slot.
        assertNull(AlertEvaluator.evaluate(sensor(pressureKpa = 20f, id = "SP"), thresholds))
        assertEquals(
            AlertType.LOW_PRESSURE,
            AlertEvaluator.evaluate(sensor(id = "SP", flags = SensorFlags(leak = true)), thresholds)
        )
    }
}
