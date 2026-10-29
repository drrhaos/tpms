package com.tpms.app.data.settings

import com.tpms.app.domain.PressureTarget
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsExporterTest {

    @Test
    fun exportAndImport_roundTrip() {
        val exported = SettingsExporter.export(
            pressureUnit = PressureUnit.BAR,
            targetPressureKpa = 250f,
            highTempCelsius = 70f,
            dongleProtocolMode = DongleProtocolMode.SERIAL_AA55,
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = mapOf("FL" to "ABC"),
            showSpareWheel = true,
            minLiveWheelPressureKpa = 110f,
            teyesChecklist = TeyesChecklist(autoStart = true, batteryUnrestricted = true, lockInRecents = false, bootCompleted = true, autoRunAwake = true),
            diagnosticLogEnabled = true,
            debugToolsEnabled = true
        )

        val imported = SettingsExporter.import(exported)

        assertEquals(PressureUnit.BAR, imported.pressureUnit)
        assertEquals(250f, imported.targetPressureKpa, 0.01f)
        assertEquals(70f, imported.highTempCelsius, 0.01f)
        assertEquals(DongleProtocolMode.SERIAL_AA55, imported.dongleProtocolMode)
        assertEquals(120_000L, imported.staleFrameTimeoutMs)
        assertEquals("ABC", imported.wheelMapping["FL"])
        assertEquals(true, imported.showSpareWheel)
        assertEquals(110f, imported.minLiveWheelPressureKpa, 0.01f)
        assertEquals(true, imported.diagnosticLogEnabled)
        assertEquals(true, imported.debugToolsEnabled)
    }

    @Test
    fun pressureOffsets_roundTrip_andMissingInOldFiles() {
        val exported = SettingsExporter.export(
            pressureUnit = PressureUnit.BAR,
            targetPressureKpa = PressureTarget.DEFAULT_KPA,
            dongleProtocolMode = DongleProtocolMode.AUTO,
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = emptyMap(),
            showSpareWheel = false,
            minLiveWheelPressureKpa = 100f,
            teyesChecklist = TeyesChecklist(),
            pressureOffsets = mapOf("ABC" to 5f, "SENSOR_02" to -12f)
        )

        assertEquals(mapOf("ABC" to 5f, "SENSOR_02" to -12f), SettingsExporter.import(exported).pressureOffsets)
        assertEquals(emptyMap<String, Float>(), SettingsExporter.import("""{"version":4}""").pressureOffsets)
    }

    @Test
    fun spareMapping_roundTripsWhileSpareIsHidden() {
        val exported = SettingsExporter.export(
            pressureUnit = PressureUnit.KPA,
            targetPressureKpa = PressureTarget.DEFAULT_KPA,
            dongleProtocolMode = DongleProtocolMode.AUTO,
            staleFrameTimeoutMs = 120_000L,
            wheelMapping = mapOf("FL" to "ABC", "SP" to "XYZ"),
            showSpareWheel = false,
            minLiveWheelPressureKpa = 100f,
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

    @Test
    fun import_legacyAlertSoundKey_isIgnoredAndNotExported() {
        val old = """{"version":3,"pressure_unit":"BAR","alert_sound":false}"""

        val imported = SettingsExporter.import(old)

        assertEquals(PressureUnit.BAR, imported.pressureUnit)
        val reexported = SettingsExporter.export(
            pressureUnit = imported.pressureUnit,
            targetPressureKpa = imported.targetPressureKpa,
            highTempCelsius = imported.highTempCelsius,
            dongleProtocolMode = imported.dongleProtocolMode,
            staleFrameTimeoutMs = imported.staleFrameTimeoutMs,
            wheelMapping = imported.wheelMapping,
            showSpareWheel = imported.showSpareWheel,
            minLiveWheelPressureKpa = imported.minLiveWheelPressureKpa,
            teyesChecklist = imported.teyesChecklist
        )
        assertFalse(reexported.contains("alert_sound"))
    }

    @Test
    fun import_legacyAbsoluteLimits_deriveTheTarget() {
        val old = """{"version":3,"low_pressure_kpa":200.0,"high_pressure_kpa":350.0}"""

        assertEquals(250f, SettingsExporter.import(old).targetPressureKpa, 0.01f)
    }

    @Test
    fun import_noPressureAtAll_isDefaultTarget() {
        assertEquals(PressureTarget.DEFAULT_KPA, SettingsExporter.import("{}").targetPressureKpa, 0.01f)
    }
}
