package com.tpms.app.data.settings

import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.PressureTarget
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.WidgetThemeMode
import org.json.JSONObject

object SettingsExporter {

    const val EXPORT_VERSION = 4

    fun export(
        pressureUnit: PressureUnit,
        targetPressureKpa: Float,
        highTempCelsius: Float = AlertThresholds().highTempCelsius,
        dongleProtocolMode: DongleProtocolMode,
        staleFrameTimeoutMs: Long,
        wheelMapping: Map<String, String>,
        showSpareWheel: Boolean,
        minLiveWheelPressureKpa: Float,
        teyesChecklist: TeyesChecklist,
        onboardingComplete: Boolean = false,
        silentStartup: Boolean = false,
        floatingOverlayEnabled: Boolean = false,
        criticalAlertsFullscreen: Boolean = true,
        preferredUsbVidPid: String? = null,
        widgetThemeMode: WidgetThemeMode = WidgetThemeMode.AUTO,
        diagnosticLogEnabled: Boolean = false,
        debugToolsEnabled: Boolean = false
    ): String = JSONObject().apply {
        put("version", EXPORT_VERSION)
        put("pressure_unit", pressureUnit.name)
        put("target_pressure_kpa", targetPressureKpa.toDouble())
        put("high_temp_celsius", highTempCelsius.toDouble())
        put("dongle_protocol", dongleProtocolMode.name)
        put("stale_frame_timeout_ms", staleFrameTimeoutMs)
        put("show_spare_wheel", showSpareWheel)
        put("min_live_wheel_pressure_kpa", minLiveWheelPressureKpa.toDouble())
        put("teyes_auto_start", teyesChecklist.autoStart)
        put("teyes_battery", teyesChecklist.batteryUnrestricted)
        put("teyes_lock", teyesChecklist.lockInRecents)
        put("teyes_boot", teyesChecklist.bootCompleted)
        put("teyes_auto_run_awake", teyesChecklist.autoRunAwake)
        put("teyes_frontapp_home", teyesChecklist.frontAppHome)
        put("onboarding_complete", onboardingComplete)
        put("silent_startup", silentStartup)
        put("floating_overlay", floatingOverlayEnabled)
        put("critical_alerts_fullscreen", criticalAlertsFullscreen)
        preferredUsbVidPid?.let { put("preferred_usb_vid_pid", it) }
        put("widget_theme_mode", widgetThemeMode.name)
        put("diagnostic_log_enabled", diagnosticLogEnabled)
        put("debug_tools_enabled", debugToolsEnabled)
        // All five slots: an SP mapping applies even with the spare hidden (wayfinder 11).
        put("wheel_mapping", JSONObject().apply {
            WheelLayout.allSlots(showSpareWheel = true).forEach { slot ->
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
        // All five slots regardless of the spare toggle; a slot missing from an old file is blank ("Авто").
        val slots = WheelLayout.allSlots(showSpareWheel = true)

        return ImportedSettings(
            pressureUnit = PressureUnit.entries.find { it.name == unitName } ?: PressureUnit.KPA,
            // Files before version 4 carry absolute limits only: the target is derived from the low one.
            targetPressureKpa = if (root.has("target_pressure_kpa")) {
                PressureTarget.clamp(root.getDouble("target_pressure_kpa").toFloat())
            } else {
                PressureTarget.fromLegacyLow(
                    root.optDouble("low_pressure_kpa").takeUnless { it.isNaN() }?.toFloat()
                )
            },
            highTempCelsius = root.optDouble("high_temp_celsius", AlertThresholds().highTempCelsius.toDouble()).toFloat(),
            dongleProtocolMode = DongleProtocolMode.entries.find { it.name == protocolName }
                ?: DongleProtocolMode.AUTO,
            staleFrameTimeoutMs = root.optLong(
                "stale_frame_timeout_ms",
                ConnectionHealthPolicy.DEFAULT_STALE_FRAME_MS
            ),
            showSpareWheel = showSpare,
            minLiveWheelPressureKpa = root.optDouble(
                "min_live_wheel_pressure_kpa",
                SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA.toDouble()
            ).toFloat(),
            // "alert_sound" from older files is ignored: the sound lives in the system channel settings.
            teyesChecklist = TeyesChecklist(
                autoStart = root.optBoolean("teyes_auto_start", false),
                batteryUnrestricted = root.optBoolean("teyes_battery", false),
                lockInRecents = root.optBoolean("teyes_lock", false),
                bootCompleted = root.optBoolean("teyes_boot", false),
                autoRunAwake = root.optBoolean("teyes_auto_run_awake", false),
                frontAppHome = root.optBoolean("teyes_frontapp_home", false)
            ),
            onboardingComplete = root.optBoolean("onboarding_complete", false),
            silentStartup = root.optBoolean("silent_startup", false),
            floatingOverlayEnabled = root.optBoolean("floating_overlay", false),
            criticalAlertsFullscreen = root.optBoolean("critical_alerts_fullscreen", true),
            preferredUsbVidPid = root.optString("preferred_usb_vid_pid", "").takeIf { it.isNotBlank() },
            widgetThemeMode = WidgetThemeMode.fromName(root.optString("widget_theme_mode", WidgetThemeMode.AUTO.name)),
            diagnosticLogEnabled = root.optBoolean("diagnostic_log_enabled", false),
            debugToolsEnabled = root.optBoolean("debug_tools_enabled", false),
            wheelMapping = slots.associateWith { slot ->
                wheelMappingJson?.optString(slot, "").orEmpty()
            }
        )
    }
}

data class ImportedSettings(
    val pressureUnit: PressureUnit,
    val targetPressureKpa: Float,
    val highTempCelsius: Float,
    val dongleProtocolMode: DongleProtocolMode,
    val staleFrameTimeoutMs: Long,
    val showSpareWheel: Boolean,
    val minLiveWheelPressureKpa: Float,
    val teyesChecklist: TeyesChecklist,
    val onboardingComplete: Boolean = false,
    val silentStartup: Boolean = false,
    val floatingOverlayEnabled: Boolean = false,
    val criticalAlertsFullscreen: Boolean = true,
    val preferredUsbVidPid: String? = null,
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.AUTO,
    val diagnosticLogEnabled: Boolean = false,
    val debugToolsEnabled: Boolean = false,
    val wheelMapping: Map<String, String>
)

object SensorValidatorDefaults {
    const val MIN_LIVE_WHEEL_PRESSURE_KPA = 100f
}
