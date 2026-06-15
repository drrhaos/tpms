package com.tpms.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PressureUnitTest {

    @Test
    fun formatFromKpa_barUsesOneDecimal() {
        assertEquals("2.2", PressureUnit.BAR.formatFromKpa(220f))
        assertEquals("2.0", PressureUnit.BAR.formatFromKpa(200f))
    }

    @Test
    fun formatFromKpa_psiUsesInteger() {
        assertEquals("32", PressureUnit.PSI.formatFromKpa(220.64f))
    }

    @Test
    fun formatPressure_includesUnitLabel() {
        assertEquals("2.2 Bar", PressureUnit.BAR.formatPressure(220f))
        assertEquals("150 kPa", PressureUnit.KPA.formatPressure(150f))
    }

    @Test
    fun toKpaAndFromKpa_roundTrip() {
        val psi = 32f
        val kpa = PressureUnit.PSI.toKpa(psi)
        assertEquals(psi, PressureUnit.PSI.fromKpa(kpa), 0.01f)
    }

    @Test
    fun formatThresholdValue_barUsesOneDecimal() {
        assertEquals("1.5", PressureUnit.BAR.formatThresholdValue(1.5f))
        assertEquals("32", PressureUnit.PSI.formatThresholdValue(32f))
    }
}
