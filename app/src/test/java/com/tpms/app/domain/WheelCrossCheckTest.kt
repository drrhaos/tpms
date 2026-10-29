package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WheelCrossCheckTest {

    private val now = 100_000_000L
    private val minute = 60_000L

    private fun sensor(
        slot: String,
        pressure: Float = 230f,
        at: Long = now,
        valueSince: Long = 0L,
        alert: AlertType? = null,
        flags: SensorFlags = SensorFlags.NONE
    ) = TireSensor(
        id = "S_$slot",
        label = slot,
        pressureKpa = pressure,
        temperatureCelsius = 20f,
        batteryPercent = null,
        alertType = alert,
        timestamp = at,
        flags = flags,
        valueSinceMs = valueSince
    )

    private fun check(
        target: TireSensor,
        others: List<TireSensor>,
        streaming: Boolean,
        previous: TireSensor? = target
    ): AlertType? {
        val wheels = (others + target).associateBy { it.label }
        return WheelCrossCheck.apply(target, target.label, wheels, previous, streaming, now).alertType
    }

    // --- HID: silent sensor ---

    @Test
    fun `hid sensor silent while others transmit is lost`() {
        val silent = sensor("FL", at = now - WheelCrossCheck.HID_LOST_AFTER_MS)
        val others = listOf(sensor("FR", at = now - minute), sensor("RL", at = now - minute), sensor("RR", at = now - 30 * minute))
        assertEquals(AlertType.SENSOR_LOST, check(silent, others, streaming = false))
    }

    @Test
    fun `hid sensors all quiet on a parked car are not lost`() {
        val old = now - 20 * minute
        val silent = sensor("FL", at = old)
        val others = listOf(sensor("FR", at = old), sensor("RL", at = old), sensor("RR", at = now - minute))
        assertNull(check(silent, others, streaming = false))
    }

    @Test
    fun `hid sensor silent for less than the limit is fine`() {
        val silent = sensor("FL", at = now - WheelCrossCheck.HID_LOST_AFTER_MS + 1)
        val others = listOf(sensor("FR"), sensor("RL"), sensor("RR"))
        assertNull(check(silent, others, streaming = false))
    }

    @Test
    fun `lost stays lost when the other sensors fall quiet`() {
        val old = now - 20 * minute
        val silent = sensor("FL", at = old, alert = AlertType.SENSOR_LOST)
        val others = listOf(sensor("FR", at = old), sensor("RL", at = old), sensor("RR", at = old))
        assertEquals(AlertType.SENSOR_LOST, check(silent, others, streaming = false))
    }

    @Test
    fun `a fresh reading of a lost sensor clears it`() {
        val previous = sensor("FL", at = now - 20 * minute, alert = AlertType.SENSOR_LOST)
        val fresh = sensor("FL", at = now)
        assertNull(check(fresh, listOf(sensor("FR"), sensor("RL"), sensor("RR")), streaming = false, previous = previous))
    }

    // --- AA55: frozen value ---

    @Test
    fun `aa55 value frozen while every other wheel changes is lost`() {
        val frozen = sensor("FL", valueSince = now - WheelCrossCheck.FROZEN_LOST_AFTER_MS)
        val others = listOf("FR", "RL", "RR").map { sensor(it, valueSince = now - minute) }
        assertEquals(AlertType.SENSOR_LOST, check(frozen, others, streaming = true))
    }

    @Test
    fun `aa55 frozen value is fine while another wheel is frozen too`() {
        val frozen = sensor("FL", valueSince = now - WheelCrossCheck.FROZEN_LOST_AFTER_MS)
        val others = listOf(sensor("FR", valueSince = now - minute), sensor("RL", valueSince = now - minute), sensor("RR", valueSince = now - 60 * minute))
        assertNull(check(frozen, others, streaming = true))
    }

    @Test
    fun `aa55 frame arrival time alone never makes a sensor look alive`() {
        // Repeated cache frames: timestamp is now, but the value has been the same for an hour.
        val frozen = sensor("FL", at = now, valueSince = now - 60 * minute)
        val others = listOf("FR", "RL", "RR").map { sensor(it, valueSince = now - minute) }
        assertEquals(AlertType.SENSOR_LOST, check(frozen, others, streaming = true))
    }

    // --- Axle gap ---

    @Test
    fun `wheel well below its axle mate is low`() {
        val low = sensor("RL", pressure = 230f - WheelCrossCheck.AXLE_LOW_RAISE_KPA)
        assertEquals(AlertType.LOW_PRESSURE, check(low, listOf(sensor("RR", pressure = 230f)), streaming = false))
    }

    @Test
    fun `small axle gap is fine`() {
        val wheel = sensor("RL", pressure = 230f - WheelCrossCheck.AXLE_LOW_RAISE_KPA + 1f)
        assertNull(check(wheel, listOf(sensor("RR", pressure = 230f)), streaming = false))
    }

    @Test
    fun `axle low clears with hysteresis`() {
        val previous = sensor("RL", alert = AlertType.LOW_PRESSURE)
        val stillLow = sensor("RL", pressure = 230f - WheelCrossCheck.AXLE_LOW_CLEAR_KPA)
        assertEquals(AlertType.LOW_PRESSURE, check(stillLow, listOf(sensor("RR")), streaming = false, previous = previous))
        val recovered = sensor("RL", pressure = 230f - WheelCrossCheck.AXLE_LOW_CLEAR_KPA + 1f)
        assertNull(check(recovered, listOf(sensor("RR")), streaming = false, previous = previous))
    }

    @Test
    fun `a lost axle mate is not compared`() {
        val wheel = sensor("RL", pressure = 150f)
        assertNull(check(wheel, listOf(sensor("RR", pressure = 230f, alert = AlertType.SENSOR_LOST)), streaming = false))
    }

    // --- Never over own alerts, never the spare ---

    @Test
    fun `own alert wins`() {
        val wheel = sensor("RL", pressure = 150f, alert = AlertType.HIGH_PRESSURE)
        assertEquals(AlertType.HIGH_PRESSURE, check(wheel, listOf(sensor("RR")), streaming = false))
    }

    @Test
    fun `the spare is never checked`() {
        val spare = sensor("SP", at = now - 60 * minute)
        val others = listOf("FL", "FR", "RL", "RR").map { sensor(it) }
        assertNull(check(spare, others, streaming = false))
    }
}
