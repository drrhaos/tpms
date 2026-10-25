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
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = mapOf("FL" to "ABC"),
            showSpareWheel = true,
            minLiveWheelPressureKpa = 110f,
            alertNotificationPrefs = AlertNotificationPrefs(soundEnabled = false),
            teyesChecklist = TeyesChecklist(autoStart = true, batteryUnrestricted = true, lockInRecents = false, bootCompleted = true, autoRunAwake = true),
            diagnosticLogEnabled = true,
            debugToolsEnabled = true
        )

        val imported = SettingsExporter.import(exported)

        assertEquals(PressureUnit.BAR, imported.pressureUnit)
        assertEquals(170f, imported.thresholds.lowPressureKpa)
        assertEquals(DongleProtocolMode.SERIAL_AA55, imported.dongleProtocolMode)
        assertEquals(120_000L, imported.staleFrameTimeoutMs)
        assertEquals("ABC", imported.wheelMapping["FL"])
        assertEquals(true, imported.showSpareWheel)
        assertEquals(110f, imported.minLiveWheelPressureKpa, 0.01f)
        assertEquals(true, imported.diagnosticLogEnabled)
        assertEquals(true, imported.debugToolsEnabled)
    }

    @Test
    fun spareMapping_roundTripsWhileSpareIsHidden() {
        val exported = SettingsExporter.export(
            pressureUnit = PressureUnit.KPA,
            thresholds = AlertThresholds(),
            dongleProtocolMode = DongleProtocolMode.AUTO,
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = mapOf("FL" to "ABC", "SP" to "XYZ"),
            showSpareWheel = false,
            minLiveWheelPressureKpa = 100f,
            alertNotificationPrefs = AlertNotificationPrefs(),
            teyesChecklist = TeyesChecklist()
        )

        val imported = SettingsExporter.import(exported)

        assertEquals(false, imported.showSpareWheel)
        assertEquals(
            mapOf("FL" to "ABC", "FR" to "", "RL" to "", "RR" to "", "SP" to "XYZ"),
            imported.wheelMapping
        )
    }

    @Test
    fun import_slotMissingFromOldFile_isBlank() {
        val old = """{"version":2,"show_spare_wheel":false,"wheel_mapping":{"FL":"ABC","FR":"","RL":"","RR":""}}"""

        val imported = SettingsExporter.import(old)

        assertEquals(setOf("FL", "FR", "RL", "RR", "SP"), imported.wheelMapping.keys)
        assertEquals("", imported.wheelMapping["SP"])
        assertEquals("ABC", imported.wheelMapping["FL"])
    }
}
