package com.tpms.app.data.diagnostics

import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.WheelAssignment
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsExporter @Inject constructor(
    private val systemDiagnostics: SystemDiagnostics,
    private val uiBreadcrumbs: UiBreadcrumbs,
    private val crashLogStore: CrashLogStore,
    private val debugLog: UsbDebugLog,
    private val heartbeatStore: ServiceHeartbeatStore
) {

    fun exportFullReport(
        tpmsState: TpmsState,
        wheels: Map<String, TireSensor>,
        seenSensors: Map<String, TireSensor>,
        aa55EmptySlots: Set<String>,
        wheelMapping: Map<String, String>,
        showSpareWheel: Boolean,
        settingsUiMode: SettingsUiMode,
        pressureUnit: PressureUnit,
        usbScan: String,
        serviceStatusLine: String,
        serviceHealth: ServiceHealth,
        pressureOffsets: Map<String, Float> = emptyMap()
    ): String = buildString {
        appendLine("=== TPMS Full Diagnostic Report ===")
        appendLine("Generated: ${timestamp()}")
        append(systemDiagnostics.systemInfoBlock())
        appendLine(uiBreadcrumbs.describe())
        appendLine()
        appendLine("--- Service status ---")
        appendLine(serviceStatusLine)
        appendLine("TPMS state: ${formatState(tpmsState)}")
        appendLine()
        append(serviceHealth.reportBlock())
        appendLine()
        appendLine("--- USB permission ---")
        appendLine(usbPermissionLine())
        appendLine()
        appendLine("--- Settings ---")
        appendLine("show_spare_wheel: $showSpareWheel")
        appendLine("settings_ui_mode: $settingsUiMode")
        appendLine("sensor_ids: ${seenSensors.keys.sorted().joinToString().ifEmpty { "none" }}")
        appendLine("aa55_empty_slots: ${aa55EmptySlots.sorted().joinToString().ifEmpty { "none" }}")
        appendLine()
        appendLine("--- Wheel mapping ---")
        val slots = WheelLayout.allSlots(showSpareWheel)
        if (wheelMapping.values.any { it.isNotBlank() }) {
            // All five slots: an SP mapping applies even with the spare hidden (wayfinder 11).
            WheelLayout.allSlots(showSpareWheel = true).forEach { slot ->
                val hidden = if (slot !in slots) " (spare off)" else ""
                appendLine("  $slot$hidden → ${wheelMapping[slot].orEmpty().ifBlank { "(auto)" }}")
            }
        } else {
            appendLine("  (automatic)")
        }
        appendLine()
        appendLine("--- Pressure calibration ---")
        if (pressureOffsets.isEmpty()) {
            appendLine("  (none)")
        } else {
            pressureOffsets.toSortedMap().forEach { (id, kpa) -> appendLine("  $id: %+.1f kPa".format(kpa)) }
        }
        appendLine()
        appendLine("--- Sensors (${seenSensors.size} seen) ---")
        if (seenSensors.isEmpty()) {
            appendLine("  No sensor data")
        } else {
            // The wheels the repository publishes (slot → sensor), not a re-derivation.
            slots.forEach { slot ->
                appendLine("  $slot: ${formatSensor(wheels[slot], pressureUnit)}")
            }
            WheelAssignment.unassigned(seenSensors, wheels).forEach { sensor ->
                val emptySlot = if (sensor.label in aa55EmptySlots) " (empty AA55 slot)" else ""
                val mappedSlot = WheelLayout.allSlots(showSpareWheel = true)
                    .firstOrNull { wheelMapping[it] == sensor.id }
                val status = when {
                    mappedSlot == null -> "unmapped"
                    mappedSlot !in slots -> "mapped to $mappedSlot (spare off)"
                    else -> "mapped to $mappedSlot, not placed"
                }
                appendLine("  $status ${sensor.id}$emptySlot: ${formatSensor(sensor, pressureUnit)}")
            }
        }
        appendLine()
        crashLogStore.read()?.let { crash ->
            appendLine("--- Last persisted crash ---")
            append(crash)
            if (!crash.endsWith("\n")) appendLine()
            appendLine()
        }
        appendLine(usbScan)
        appendLine("--- Event log ---")
        append(debugLog.eventsText())
    }

    /** Many requests on a firmware that forgets "use by default" show up here (wayfinder 14). */
    private fun usbPermissionLine(): String {
        val stats = heartbeatStore.usbPermissionStatsBlocking() ?: return "usb_permission_requests: unavailable"
        val last = stats.lastRequestMs?.let { formatTime(it) } ?: "never"
        return "usb_permission_requests: ${stats.requests} (explicit ${stats.explicitRequests}, " +
            "auto ${stats.requests - stats.explicitRequests}) denials: ${stats.denials} last: $last"
    }

    private fun formatState(state: TpmsState): String = when (state) {
        is TpmsState.Disconnected -> "Disconnected"
        is TpmsState.Connecting -> "Connecting (attempt ${state.attempt})"
        is TpmsState.Connected -> "Connected (${state.sensors.size} sensors)"
        is TpmsState.Alert -> "Alert ${state.type} on ${state.sensor.id}"
    }

    private fun formatSensor(sensor: TireSensor?, unit: PressureUnit): String {
        if (sensor == null) return "—"
        val pressure = if (sensor.pressureKpa.isFinite()) {
            unit.formatPressure(sensor.pressureKpa)
        } else {
            "—"
        }
        val temp = if (sensor.temperatureCelsius.isFinite()) {
            "%.0f°C".format(sensor.temperatureCelsius)
        } else {
            "—"
        }
        val alert = sensor.alertType?.name ?: "OK"
        return "${sensor.id} label=${sensor.label} $pressure $temp batt=${sensor.batteryPercent?.let { "$it%" } ?: "?"} flags=${sensor.flags} $alert"
    }

    private fun timestamp(): String = formatTime(System.currentTimeMillis())

    private fun formatTime(atMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(atMs))
}
