package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
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

    // Target 230 kPa: low 184, critical 149.5, high 310.5; temp 85 / 100 °C.
    private val thresholds = PressureTarget.thresholds(230f, highTempCelsius = 85f)
    private val minute = 60_000L

    private fun sensor(
        pressureKpa: Float = 230f,
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

    private fun AlertDecision.pair() = alertType to level

    // --- Thresholds and levels ---

    @Test
    fun normalReadingHasNoAlert() {
        val decision = eval(sensor())
        assertNull(decision.alertType)
        assertNull(decision.level)
    }

    @Test
    fun lowPressureIsAttentionAboveTheCriticalLimit() {
        assertEquals(AlertType.LOW_PRESSURE to AlertLevel.ATTENTION, eval(sensor(pressureKpa = 170f)).pair())
    }

    @Test
    fun criticallyLowPressureIsFault() {
        assertEquals(AlertType.LOW_PRESSURE to AlertLevel.FAULT, eval(sensor(pressureKpa = 140f)).pair())
    }

    @Test
    fun criticalLowClearsWithHysteresisIntoAttention() {
        val d = run(
            0L to sensor(pressureKpa = 140f),
            // A slow climb: never 30 kPa below the window's highest, so no PRESSURE_DROP.
            minute to sensor(pressureKpa = 155f),
            2 * minute to sensor(pressureKpa = 160f)
        )
        assertEquals(AlertLevel.FAULT, d[1].level) // 155 < 149.5 + 10
        assertEquals(AlertType.LOW_PRESSURE to AlertLevel.ATTENTION, d[2].pair())
    }

    @Test
    fun lowPressureAttentionClearsWithHysteresis() {
        val d = run(0L to sensor(pressureKpa = 180f), minute to sensor(pressureKpa = 190f), 2 * minute to sensor(pressureKpa = 195f))
        assertEquals(AlertType.LOW_PRESSURE, d[1].alertType) // 190 < 184 + 10
        assertNull(d[2].alertType)
    }

    @Test
    fun highPressureIsOnlyAttention() {
        assertEquals(AlertType.HIGH_PRESSURE to AlertLevel.ATTENTION, eval(sensor(pressureKpa = 400f)).pair())
    }

    @Test
    fun highTempIsAttentionThenFaultAtCriticalLimit() {
        assertEquals(AlertType.HIGH_TEMP to AlertLevel.ATTENTION, eval(sensor(temperatureCelsius = 90f)).pair())
        assertEquals(AlertType.HIGH_TEMP to AlertLevel.FAULT, eval(sensor(temperatureCelsius = 100f)).pair())
    }

    @Test
    fun criticalTempClearsWithHysteresis() {
        val d = run(0L to sensor(temperatureCelsius = 101f), 1_000L to sensor(temperatureCelsius = 98f), 2_000L to sensor(temperatureCelsius = 97f))
        assertEquals(AlertLevel.FAULT, d[1].level)
        assertEquals(AlertType.HIGH_TEMP to AlertLevel.ATTENTION, d[2].pair())
    }

    @Test
    fun spareSkipsThresholds() {
        assertNull(eval(sensor(pressureKpa = 50f, id = "SP")).alertType)
    }

    // --- Receiver flags ---

    @Test
    fun noSignalIsFaultAndKeepsMemory() {
        val memory = AlertMemory(batteryLatched = true)
        val decision = eval(sensor(flags = SensorFlags(noSignal = true)), memory)
        assertEquals(AlertType.SENSOR_LOST to AlertLevel.FAULT, decision.pair())
        assertEquals(memory, decision.memory)
    }

    @Test
    fun leakFlagBecomesPressureDropAfter15s() {
        val leak = sensor(flags = SensorFlags(leak = true))
        val d = run(0L to leak, 14_000L to leak, 15_000L to leak)
        assertNull(d[1].alertType)
        assertEquals(AlertType.PRESSURE_DROP to AlertLevel.FAULT, d[2].pair())
    }

    @Test
    fun brokenLeakRunRestartsTheHold() {
        val leak = sensor(flags = SensorFlags(leak = true))
        val d = run(0L to leak, 10_000L to sensor(), 12_000L to leak, 26_000L to leak, 27_000L to leak)
        assertNull(d[3].alertType)
        assertEquals(AlertType.PRESSURE_DROP, d[4].alertType)
    }

    // --- Rate of loss ---

    @Test
    fun fallOf30KpaWithinTenMinutesIsPressureDrop() {
        val d = run(0L to sensor(pressureKpa = 230f), 3 * minute to sensor(pressureKpa = 215f), 6 * minute to sensor(pressureKpa = 200f))
        assertNull(d[1].alertType)
        assertEquals(AlertType.PRESSURE_DROP to AlertLevel.FAULT, d[2].pair())
    }

    @Test
    fun slowFallIsNotPressureDrop() {
        val d = run(
            0L to sensor(pressureKpa = 230f),
            8 * minute to sensor(pressureKpa = 215f),
            16 * minute to sensor(pressureKpa = 200f)
        )
        assertNull(d[2].alertType)
    }

    @Test
    fun pressureDropClearsOnceThePressureHeldForTheWindow() {
        val d = run(
            0L to sensor(pressureKpa = 230f),
            5 * minute to sensor(pressureKpa = 195f),
            11 * minute to sensor(pressureKpa = 195f)
        )
        assertEquals(AlertType.PRESSURE_DROP, d[1].alertType)
        assertNull(d[2].alertType)
    }

    @Test
    fun samplesAreThinnedAndBoundedToTheWindow() {
        var memory = AlertMemory()
        for (i in 0..120) memory = eval(sensor(), memory, i * 10_000L).memory
        val samples = memory.pressureSamples
        assertTrue(samples.size <= (AlertEvaluator.DROP_WINDOW_MS / AlertEvaluator.SAMPLE_EVERY_MS).toInt() + 1)
        assertTrue(samples.all { 1_200_000L - it.atMs <= AlertEvaluator.DROP_WINDOW_MS })
    }

    // --- Priority ---

    @Test
    fun faultOutranksAttention() {
        // Critical temperature (FAULT) with an attention-level low pressure.
        val decision = eval(sensor(pressureKpa = 170f, temperatureCelsius = 105f))
        assertEquals(AlertType.HIGH_TEMP to AlertLevel.FAULT, decision.pair())
    }

    @Test
    fun withinALevelPressureDropOutranksLowPressure() {
        val d = run(0L to sensor(pressureKpa = 180f), minute to sensor(pressureKpa = 140f))
        assertEquals(AlertType.PRESSURE_DROP to AlertLevel.FAULT, d[1].pair())
    }

    @Test
    fun thresholdAlertOutranksLowBattery() {
        assertEquals(AlertType.LOW_PRESSURE, eval(sensor(pressureKpa = 170f, batteryPercent = 5)).alertType)
    }

    // --- Battery latch ---

    @Test
    fun lowBatteryLatchesAsAttention() {
        val d = run(0L to sensor(batteryPercent = 5), 1_000L to sensor(batteryPercent = 50))
        assertEquals(AlertType.BATTERY_LOW to AlertLevel.ATTENTION, d[0].pair())
        assertEquals(AlertType.BATTERY_LOW, d[1].alertType)
        assertTrue(d[1].memory.batteryLatched)
    }

    @Test
    fun batteryClearsAfterTenHealthyMinutes() {
        val d = run(0L to sensor(batteryPercent = 5), 1_000L to sensor(batteryPercent = 50), 1_000L + 10 * minute to sensor(batteryPercent = 50))
        assertFalse(d[2].memory.batteryLatched)
        assertNull(d[2].alertType)
    }
}
