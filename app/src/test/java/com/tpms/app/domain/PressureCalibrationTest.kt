package com.tpms.app.domain

import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureCalibrationTest {

    private fun sensor(kpa: Float, offset: Float = 0f) = TireSensor(
        id = "ABC",
        pressureKpa = kpa,
        temperatureCelsius = 20f,
        batteryPercent = null,
        alertType = null,
        timestamp = 1L,
        pressureOffsetKpa = offset
    )

    @Test
    fun apply_addsOffset_andKeepsRaw() {
        val corrected = PressureCalibration.apply(sensor(230f), 5f)

        assertEquals(235f, corrected.pressureKpa, 0.001f)
        assertEquals(230f, corrected.rawPressureKpa, 0.001f)
    }

    @Test
    fun apply_toAlreadyCorrected_replacesTheOffset() {
        val again = PressureCalibration.apply(sensor(235f, offset = 5f), -3f)

        assertEquals(227f, again.pressureKpa, 0.001f)
        assertEquals(-3f, again.pressureOffsetKpa, 0.001f)
    }

    @Test
    fun apply_unknownPressure_staysUnknown() {
        assertTrue(PressureCalibration.apply(sensor(Float.NaN), 5f).pressureKpa.isNaN())
    }

    @Test
    fun stepped_movesByUnitStep_andClamps() {
        assertEquals(1f, PressureCalibration.stepped(0f, PressureUnit.BAR, up = true), 0.001f)
        assertEquals(-1f, PressureCalibration.stepped(0f, PressureUnit.KPA, up = false), 0.001f)
        assertEquals(0.689f, PressureCalibration.stepped(0f, PressureUnit.PSI, up = true), 0.001f)
        assertEquals(PressureCalibration.MAX_OFFSET_KPA, PressureCalibration.stepped(100f, PressureUnit.BAR, up = true), 0.001f)
        assertEquals(-PressureCalibration.MAX_OFFSET_KPA, PressureCalibration.stepped(-100f, PressureUnit.KPA, up = false), 0.001f)
    }

    @Test
    fun stepped_backToZero_isExactlyZero() {
        val up = PressureCalibration.stepped(0f, PressureUnit.PSI, up = true)

        assertEquals(0f, PressureCalibration.stepped(up, PressureUnit.PSI, up = false))
    }

    @Test
    fun formatOffset_hasSign() {
        assertEquals("+0.05", PressureCalibration.formatOffset(5f, PressureUnit.BAR))
        assertEquals("−12", PressureCalibration.formatOffset(-12f, PressureUnit.KPA))
        assertEquals("0.00", PressureCalibration.formatOffset(0f, PressureUnit.BAR))
    }

    @Test
    fun encodeDecode_roundTrip_dropsZeroAndClamps() {
        val decoded = PressureCalibration.decode(
            PressureCalibration.encode(mapOf("A" to 5f, "B" to 0f, "C" to -250f))
        )

        assertEquals(mapOf("A" to 5f, "C" to -PressureCalibration.MAX_OFFSET_KPA), decoded)
    }

    @Test
    fun decode_garbage_isNoCorrection() {
        assertEquals(emptyMap<String, Float>(), PressureCalibration.decode("not json"))
        assertEquals(emptyMap<String, Float>(), PressureCalibration.decode(null))
    }
}
