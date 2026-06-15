package com.tpms.app.data.settings

import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import org.json.JSONObject

object SettingsExporter {

    const val EXPORT_VERSION = 1

    fun export(
        pressureUnit: PressureUnit,
        thresholds: AlertThresholds,
        dongleProtocolMode: DongleProtocolMode,
        sensorTimeoutMs: Long,
        staleFrameTimeoutMs: Long,
        wheelMapping: Map<String, String>,
        showSpareWheel: Boolean,
        minLiveWheelPressureKpa: Float,
        alertNotificationPrefs: AlertNotificationPrefs,
        teyesChecklist: TeyesChecklist
    ): String = JSONObject().apply {
        put("version", EXPORT_VERSION)
        put("pressure_unit", pressureUnit.name)
        put("low_pressure_kpa", thresholds.lowPressureKpa.toDouble())
        put("high_pressure_kpa", thresholds.highPressureKpa.toDouble())
        put("high_temp_celsius", thresholds.highTempCelsius.toDouble())
        put("dongle_protocol", dongleProtocolMode.name)
        put("sensor_timeout_ms", sensorTimeoutMs)
        put("stale_frame_timeout_ms", staleFrameTimeoutMs)
        put("show_spare_wheel", showSpareWheel)
        put("min_live_wheel_pressure_kpa", minLiveWheelPressureKpa.toDouble())
        put("alert_sound", alertNotificationPrefs.soundEnabled)
        put("alert_vibration", alertNotificationPrefs.vibrationEnabled)
        put("teyes_auto_start", teyesChecklist.autoStart)
        put("teyes_battery", teyesChecklist.batteryUnrestricted)
        put("teyes_lock", teyesChecklist.lockInRecents)
        put("teyes_boot", teyesChecklist.bootCompleted)
        put("teyes_auto_run_awake", teyesChecklist.autoRunAwake)
        put("wheel_mapping", JSONObject().apply {
            WheelLayout.allSlots(showSpareWheel).forEach { slot ->
                put(slot, wheelMapping[slot].orEmpty())
            }
        })
    }.toString(2)

    fun import(json: String): ImportedSettings {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        if (version > EXPORT_VERSION) {
            throw IllegalArgumentException("Unsupported settings version: $version")
        }
        val unitName = root.optString("pressure_unit", PressureUnit.KPA.name)
        val protocolName = root.optString("dongle_protocol", DongleProtocolMode.AUTO.name)
        val wheelMappingJson = root.optJSONObject("wheel_mapping")
        val showSpare = root.optBoolean("show_spare_wheel", false)
        val slots = WheelLayout.allSlots(showSpare)

        return ImportedSettings(
            pressureUnit = PressureUnit.entries.find { it.name == unitName } ?: PressureUnit.KPA,
            thresholds = AlertThresholds(
                lowPressureKpa = root.optDouble("low_pressure_kpa", AlertThresholds().lowPressureKpa.toDouble()).toFloat(),
                highPressureKpa = root.optDouble("high_pressure_kpa", AlertThresholds().highPressureKpa.toDouble()).toFloat(),
                highTempCelsius = root.optDouble("high_temp_celsius", AlertThresholds().highTempCelsius.toDouble()).toFloat()
            ),
            dongleProtocolMode = DongleProtocolMode.entries.find { it.name == protocolName }
                ?: DongleProtocolMode.AUTO,
            sensorTimeoutMs = root.optLong("sensor_timeout_ms", SettingsStore.DEFAULT_SENSOR_TIMEOUT_MS),
            staleFrameTimeoutMs = root.optLong(
                "stale_frame_timeout_ms",
                ConnectionHealthPolicy.DEFAULT_STALE_FRAME_MS
            ),
            showSpareWheel = showSpare,
            minLiveWheelPressureKpa = root.optDouble(
                "min_live_wheel_pressure_kpa",
                SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA.toDouble()
            ).toFloat(),
            alertNotificationPrefs = AlertNotificationPrefs(
                soundEnabled = root.optBoolean("alert_sound", true),
                vibrationEnabled = root.optBoolean("alert_vibration", true)
            ),
            teyesChecklist = TeyesChecklist(
                autoStart = root.optBoolean("teyes_auto_start", false),
                batteryUnrestricted = root.optBoolean("teyes_battery", false),
                lockInRecents = root.optBoolean("teyes_lock", false),
                bootCompleted = root.optBoolean("teyes_boot", false),
                autoRunAwake = root.optBoolean("teyes_auto_run_awake", false)
            ),
            wheelMapping = slots.associateWith { slot ->
                wheelMappingJson?.optString(slot, "").orEmpty()
            }
        )
    }
}

data class ImportedSettings(
    val pressureUnit: PressureUnit,
    val thresholds: AlertThresholds,
    val dongleProtocolMode: DongleProtocolMode,
    val sensorTimeoutMs: Long,
    val staleFrameTimeoutMs: Long,
    val showSpareWheel: Boolean,
    val minLiveWheelPressureKpa: Float,
    val alertNotificationPrefs: AlertNotificationPrefs,
    val teyesChecklist: TeyesChecklist,
    val wheelMapping: Map<String, String>
)

object SensorValidatorDefaults {
    const val MIN_LIVE_WHEEL_PRESSURE_KPA = 100f
}
