package com.tpms.app.domain

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun eval(sensor: TireSensor, memory: AlertMemory = AlertMemory(), now: Long = 0L) =
        AlertEvaluator.evaluate(sensor, thresholds, memory, now)

    /** Feeds readings in order, threading memory; returns the decision for each. */
    private fun run(vararg steps: Pair<Long, TireSensor>): List<AlertDecision> {
        var memory = AlertMemory()
        return steps.map { (now, s) -> eval(s, memory, now).also { memory = it.memory } }
    }

    // --- Immediate alerts ---

    @Test
    fun evaluate_returnsNull_whenWithinThresholds() {
        val decision = eval(sensor())
        assertNull(decision.alertType)
        assertEquals(AlertMemory(), decision.memory)
    }

    @Test
    fun evaluate_detectsLowPressure() {
        assertEquals(AlertType.LOW_PRESSURE, eval(sensor(pressureKpa = 120f)).alertType)
    }

    @Test
    fun evaluate_detectsHighPressure() {
        assertEquals(AlertType.HIGH_PRESSURE, eval(sensor(pressureKpa = 400f)).alertType)
    }

    @Test
    fun evaluate_detectsHighTemperature() {
        assertEquals(AlertType.HIGH_TEMP, eval(sensor(temperatureCelsius = 90f)).alertType)
    }

    @Test
    fun evaluate_detectsLowBattery() {
        assertEquals(AlertType.BATTERY_LOW, eval(sensor(batteryPercent = 5)).alertType)
        assertEquals(
            AlertType.BATTERY_LOW,
            eval(sensor(batteryPercent = null, flags = SensorFlags(lowBattery = true))).alertType
        )
    }

    @Test
    fun evaluate_unknownBatteryIsNotLow() {
        assertNull(eval(sensor(batteryPercent = null)).alertType)
    }

    @Test
    fun evaluate_thresholdAlertOutranksLowBatteryWarning() {
        assertEquals(
            AlertType.HIGH_TEMP,
            eval(sensor(temperatureCelsius = 120f, flags = SensorFlags(lowBattery = true))).alertType
        )
    }

    // --- SENSOR_LOST ---

    @Test
    fun noSignal_outranksThresholds_andClearsOnFirstReadingWithoutFlag() {
        val decisions = run(
            0L to sensor(pressureKpa = 100f, flags = SensorFlags(noSignal = true)),
            1_000L to sensor()
        )
        assertEquals(AlertType.SENSOR_LOST, decisions[0].alertType)
        assertNull(decisions[1].alertType)
    }

    @Test
    fun noSignal_leavesMemoryUntouched() {
        // A no-signal frame's payload is not a measurement (it can read 0 kPa), so it must not
        // raise threshold, leak or battery state that would outlive the signal loss.
        val decisions = run(
            0L to sensor(pressureKpa = 140f),
            1_000L to sensor(
                pressureKpa = 0f,
                batteryPercent = 5,
                flags = SensorFlags(noSignal = true, leak = true, lowBattery = true)
            ),
            2_000L to sensor(pressureKpa = 155f)
        )
        assertEquals(AlertType.SENSOR_LOST, decisions[1].alertType)
        assertEquals(decisions[0].memory, decisions[1].memory)
        // Held LOW from the reading before the loss, no battery latch from the dummy payload.
        assertEquals(AlertType.LOW_PRESSURE, decisions[2].alertType)
        assertEquals(false, decisions[2].memory.batteryLatched)
    }

    // --- Leak dwell ---

    @Test
    fun leak_underDwell_isNotShown() {
        val leak = sensor(flags = SensorFlags(leak = true))
        val decisions = run(0L to leak, 14_999L to leak)
        assertNull(decisions[0].alertType)
        assertNull(decisions[1].alertType)
        assertEquals(0L, decisions[1].memory.leakSinceMs)
    }

    @Test
    fun leak_atDwell_isShownAsLowPressure() {
        val leak = sensor(flags = SensorFlags(leak = true))
        val decisions = run(0L to leak, 15_000L to leak)
        assertEquals(AlertType.LOW_PRESSURE, decisions[1].alertType)
    }

    @Test
    fun leak_readingWithoutFlagResetsDwell() {
        val leak = sensor(flags = SensorFlags(leak = true))
        val decisions = run(
            0L to leak,
            10_000L to sensor(),
            12_000L to leak,
            26_999L to leak,
            27_000L to leak
        )
        assertNull(decisions[1].memory.leakSinceMs)
        assertEquals(12_000L, decisions[2].memory.leakSinceMs)
        assertNull(decisions[3].alertType)
        assertEquals(AlertType.LOW_PRESSURE, decisions[4].alertType)
    }

    @Test
    fun leak_outranksThresholds() {
        val leakHot = sensor(temperatureCelsius = 120f, flags = SensorFlags(leak = true))
        val decisions = run(0L to leakHot, 15_000L to leakHot)
        assertEquals(AlertType.HIGH_TEMP, decisions[0].alertType)
        assertEquals(AlertType.LOW_PRESSURE, decisions[1].alertType)
    }

    // --- Threshold hysteresis ---

    @Test
    fun lowPressure_heldInsideBand_clearsAtLowPlusMargin() {
        val decisions = run(
            0L to sensor(pressureKpa = 140f),
            1L to sensor(pressureKpa = 159.9f),
            2L to sensor(pressureKpa = 160f)
        )
        assertEquals(AlertType.LOW_PRESSURE, decisions[1].alertType)
        assertNull(decisions[2].alertType)
        assertNull(decisions[2].memory.thresholdAlert)
    }

    @Test
    fun lowPressure_bandDoesNotRaiseWithoutPriorAlert() {
        assertNull(eval(sensor(pressureKpa = 155f)).alertType)
    }

    @Test
    fun highPressure_heldInsideBand_clearsAtHighMinusMargin() {
        val decisions = run(
            0L to sensor(pressureKpa = 360f),
            1L to sensor(pressureKpa = 340.1f),
            2L to sensor(pressureKpa = 340f)
        )
        assertEquals(AlertType.HIGH_PRESSURE, decisions[1].alertType)
        assertNull(decisions[2].alertType)
    }

    @Test
    fun highTemp_heldInsideBand_clearsAtHighTempMinusMargin() {
        val decisions = run(
            0L to sensor(temperatureCelsius = 90f),
            1L to sensor(temperatureCelsius = 82.1f),
            2L to sensor(temperatureCelsius = 82f)
        )
        assertEquals(AlertType.HIGH_TEMP, decisions[1].alertType)
        assertNull(decisions[2].alertType)
    }

    @Test
    fun rawConditionReplacesHeldAlert() {
        val decisions = run(
            0L to sensor(temperatureCelsius = 90f),
            1L to sensor(pressureKpa = 140f, temperatureCelsius = 84f)
        )
        assertEquals(AlertType.LOW_PRESSURE, decisions[1].alertType)
        assertEquals(AlertType.LOW_PRESSURE, decisions[1].memory.thresholdAlert)
    }

    // --- Battery latch ---

    @Test
    fun battery_latchesAfterRecovery_untilOkForTenMinutes() {
        val tenMin = 10 * 60_000L
        val decisions = run(
            0L to sensor(batteryPercent = 5),
            1_000L to sensor(batteryPercent = 80),
            1_000L + tenMin - 1 to sensor(batteryPercent = 80),
            1_000L + tenMin to sensor(batteryPercent = 80)
        )
        assertEquals(AlertType.BATTERY_LOW, decisions[1].alertType)
        assertEquals(1_000L, decisions[1].memory.batteryOkSinceMs)
        assertEquals(AlertType.BATTERY_LOW, decisions[2].alertType)
        assertNull(decisions[3].alertType)
        assertFalse(decisions[3].memory.batteryLatched)
        assertNull(decisions[3].memory.batteryOkSinceMs)
    }

    @Test
    fun battery_betweenTenAndFifteenPercent_resetsOkClock() {
        val tenMin = 10 * 60_000L
        val decisions = run(
            0L to sensor(batteryPercent = 5),
            1_000L to sensor(batteryPercent = 80),
            2_000L to sensor(batteryPercent = 14),
            3_000L to sensor(batteryPercent = 15),
            1_000L + tenMin to sensor(batteryPercent = 80),
            3_000L + tenMin to sensor(batteryPercent = 80)
        )
        assertEquals(AlertType.BATTERY_LOW, decisions[2].alertType)
        assertTrue(decisions[2].memory.batteryLatched)
        assertNull(decisions[2].memory.batteryOkSinceMs)
        assertEquals(3_000L, decisions[3].memory.batteryOkSinceMs)
        assertEquals(AlertType.BATTERY_LOW, decisions[4].alertType)
        assertNull(decisions[5].alertType)
    }

    @Test
    fun battery_lowAgainWhileRecovering_restartsLatch() {
        val decisions = run(
            0L to sensor(batteryPercent = 5),
            1_000L to sensor(batteryPercent = 80),
            2_000L to sensor(batteryPercent = null, flags = SensorFlags(lowBattery = true))
        )
        assertTrue(decisions[2].memory.batteryLatched)
        assertNull(decisions[2].memory.batteryOkSinceMs)
    }

    @Test
    fun battery_unknownPercentWithoutFlagCountsAsOk() {
        val tenMin = 10 * 60_000L
        val decisions = run(
            0L to sensor(batteryPercent = null, flags = SensorFlags(lowBattery = true)),
            1_000L to sensor(batteryPercent = null),
            1_000L + tenMin to sensor(batteryPercent = null)
        )
        assertEquals(AlertType.BATTERY_LOW, decisions[1].alertType)
        assertEquals(1_000L, decisions[1].memory.batteryOkSinceMs)
        assertNull(decisions[2].alertType)
    }

    @Test
    fun battery_tenToFourteenPercentWithoutLatch_isNotLow() {
        assertNull(eval(sensor(batteryPercent = 12)).alertType)
        assertFalse(eval(sensor(batteryPercent = 12)).memory.batteryLatched)
    }

    // --- Spare ---

    @Test
    fun spare_ignoresThresholds() {
        // Without a fifth sensor the receiver reports a dummy value for the spare slot.
        val decision = eval(sensor(pressureKpa = 20f, id = "SP"))
        assertNull(decision.alertType)
        assertNull(decision.memory.thresholdAlert)
    }

    @Test
    fun spare_keepsFlagsAndBatteryLatch() {
        val leak = sensor(id = "SP", flags = SensorFlags(leak = true))
        assertEquals(AlertType.LOW_PRESSURE, run(0L to leak, 15_000L to leak)[1].alertType)

        val decisions = run(
            0L to sensor(pressureKpa = 20f, id = "SP", batteryPercent = 5),
            1_000L to sensor(pressureKpa = 20f, id = "SP", batteryPercent = 80)
        )
        assertEquals(AlertType.BATTERY_LOW, decisions[0].alertType)
        assertEquals(AlertType.BATTERY_LOW, decisions[1].alertType)
        assertTrue(decisions[1].memory.batteryLatched)
    }
}
