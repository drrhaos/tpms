package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Test

class PressureTargetTest {

    private fun sensor(slot: String, kpa: Float, alert: AlertType? = null) = TireSensor(
        id = slot,
        label = slot,
        pressureKpa = kpa,
        temperatureCelsius = 20f,
        batteryPercent = 80,
        alertType = alert,
        timestamp = 1L
    )

    private fun wheels(vararg kpa: Float) =
        WheelLayout.ORDER.zip(kpa.toList()).associate { (slot, p) -> slot to sensor(slot, p) }

    @Test
    fun thresholds_followTheTarget() {
        val t = PressureTarget.thresholds(250f, highTempCelsius = 80f)

        assertEquals(200f, t.lowPressureKpa, 0.01f)
        assertEquals(337.5f, t.highPressureKpa, 0.01f)
        assertEquals(80f, t.highTempCelsius, 0.01f)
    }

    @Test
    fun fromLegacyLow_defaultOrMissing_isDefaultTarget() {
        assertEquals(PressureTarget.DEFAULT_KPA, PressureTarget.fromLegacyLow(null))
        assertEquals(PressureTarget.DEFAULT_KPA, PressureTarget.fromLegacyLow(150f))
    }

    @Test
    fun fromLegacyLow_customLow_keepsTheSameLowLimit() {
        // 200 / 0.8 = 250, so the low limit the user chose survives the migration.
        assertEquals(250f, PressureTarget.fromLegacyLow(200f))
    }

    @Test
    fun stepped_movesOneUnitStepAndClamps() {
        assertEquals(240f, PressureTarget.stepped(230f, PressureUnit.BAR, up = true), 0.01f)
        assertEquals(225f, PressureTarget.stepped(230f, PressureUnit.KPA, up = false), 0.01f)
        assertEquals(PressureUnit.PSI.toKpa(34f), PressureTarget.stepped(230f, PressureUnit.PSI, up = true), 0.01f)
        assertEquals(PressureTarget.MAX_KPA, PressureTarget.stepped(450f, PressureUnit.BAR, up = true), 0.01f)
    }

    @Test
    fun calibrate_averagesAndRoundsToTenthOfBar() {
        // Mean 239 kPa → 240 (2.4 bar).
        val result = PressureTarget.calibrate(wheels(236f, 238f, 240f, 242f))

        assertEquals(PressureTarget.Calibration.Accepted(240f), result)
    }

    @Test
    fun calibrate_ignoresSpare() {
        val withSpare = wheels(230f, 230f, 230f, 230f) + (WheelLayout.SPARE_SLOT to sensor("SP", 100f))

        assertEquals(PressureTarget.Calibration.Accepted(230f), PressureTarget.calibrate(withSpare))
    }

    @Test
    fun calibrate_missingOrLostWheel_isRefused() {
        assertEquals(
            PressureTarget.Calibration.MissingWheel,
            PressureTarget.calibrate(wheels(230f, 230f, 230f))
        )
        val lost = wheels(230f, 230f, 230f, 230f) + ("RR" to sensor("RR", 230f, AlertType.SENSOR_LOST))
        assertEquals(PressureTarget.Calibration.MissingWheel, PressureTarget.calibrate(lost))
    }

    @Test
    fun calibrate_flatTyre_isRefused() {
        assertEquals(PressureTarget.Calibration.TooLow, PressureTarget.calibrate(wheels(230f, 230f, 140f, 230f)))
    }

    @Test
    fun calibrate_unevenWheels_isRefused() {
        assertEquals(PressureTarget.Calibration.Uneven, PressureTarget.calibrate(wheels(200f, 200f, 260f, 260f)))
    }
}
