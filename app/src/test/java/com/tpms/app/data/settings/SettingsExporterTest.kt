package com.tpms.app.data.settings

import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsExporterTest {

    @Test
    fun exportAndImport_roundTrip() {
        val exported = SettingsExporter.export(
            pressureUnit = PressureUnit.BAR,
            thresholds = AlertThresholds(lowPressureKpa = 170f, highPressureKpa = 280f, highTempCelsius = 70f),
            dongleProtocolMode = DongleProtocolMode.SERIAL_AA55,
            sensorTimeoutMs = 90_000L,
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = mapOf("FL" to "ABC"),
            wheelNames = mapOf("FL" to "Front Left"),
            showSpareWheel = true,
            minLiveWheelPressureKpa = 110f,
            alertNotificationPrefs = AlertNotificationPrefs(soundEnabled = false, vibrationEnabled = true),
            teyesChecklist = TeyesChecklist(autoStart = true, batteryUnrestricted = true, lockInRecents = false, bootCompleted = true)
        )

        val imported = SettingsExporter.import(exported)

        assertEquals(PressureUnit.BAR, imported.pressureUnit)
        assertEquals(170f, imported.thresholds.lowPressureKpa)
        assertEquals(DongleProtocolMode.SERIAL_AA55, imported.dongleProtocolMode)
        assertEquals(90_000L, imported.sensorTimeoutMs)
        assertEquals(120_000L, imported.staleFrameTimeoutMs)
        assertEquals("ABC", imported.wheelMapping["FL"])
        assertEquals("Front Left", imported.wheelNames["FL"])
        assertEquals(true, imported.showSpareWheel)
        assertEquals(110f, imported.minLiveWheelPressureKpa, 0.01f)
    }
}
