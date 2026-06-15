package com.tpms.app.data.diagnostics

import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
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
    private val debugLog: UsbDebugLog
) {

    fun exportFullReport(
        tpmsState: TpmsState,
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String>,
        pressureUnit: PressureUnit,
        usbScan: String,
        serviceStatusLine: String
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
        appendLine("--- Wheel mapping ---")
        if (wheelMapping.values.any { it.isNotBlank() }) {
            WheelLayout.ORDER.forEach { slot ->
                appendLine("  $slot → ${wheelMapping[slot].orEmpty().ifBlank { "(auto)" }}")
            }
        } else {
            appendLine("  (automatic)")
        }
        appendLine()
        appendLine("--- Sensors (${sensors.size}) ---")
        if (sensors.isEmpty()) {
            appendLine("  No sensor data")
        } else {
            WheelLayout.orderedSlots(sensors, wheelMapping).forEachIndexed { index, sensor ->
                val slot = WheelLayout.ORDER[index]
                appendLine("  $slot: ${formatSensor(sensor, pressureUnit)}")
            }
            sensors.values.sortedBy { it.id }.forEach { sensor ->
                if (WheelLayout.orderedSlots(sensors, wheelMapping).none { it?.id == sensor.id }) {
                    appendLine("  unmapped ${sensor.id}: ${formatSensor(sensor, pressureUnit)}")
                }
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

    private fun formatState(state: TpmsState): String = when (state) {
        is TpmsState.Disconnected -> "Disconnected"
        is TpmsState.Connecting -> "Connecting (attempt ${state.attempt})"
        is TpmsState.Connected -> "Connected (${state.sensors.size} sensors)"
        is TpmsState.Alert -> "Alert ${state.type} on ${state.sensor.id}"
    }

    private fun formatSensor(sensor: TireSensor?, unit: PressureUnit): String {
        if (sensor == null) return "—"
        val pressure = if (sensor.pressureKpa.isFinite()) {
            "%.1f %s".format(unit.fromKpa(sensor.pressureKpa), unit.label)
        } else {
            "—"
        }
        val temp = if (sensor.temperatureCelsius.isFinite()) {
            "%.0f°C".format(sensor.temperatureCelsius)
        } else {
            "—"
        }
        val alert = sensor.alertType?.name ?: "OK"
        return "${sensor.id} label=${sensor.label} $pressure $temp batt=${sensor.batteryPercent}% $alert"
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
