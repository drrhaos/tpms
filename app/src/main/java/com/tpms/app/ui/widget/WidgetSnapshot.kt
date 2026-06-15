package com.tpms.app.ui.widget

import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.toSeverity
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState

enum class WidgetTireStatus {
    OK, WARNING, ALERT, EMPTY
}

data class WidgetTireSlot(
    val label: String,
    val pressureText: String,
    val temperatureText: String = "--°C",
    val batteryText: String = "--%",
    val status: WidgetTireStatus
)

data class WidgetSnapshot(
    val connectionStatus: String,
    val unitLabel: String,
    val tires: List<WidgetTireSlot>
) {
    companion object {
        fun from(
            state: TpmsState,
            sensors: Map<String, TireSensor>,
            unit: PressureUnit,
            wheelMapping: Map<String, String> = emptyMap()
        ): WidgetSnapshot {
            val statusText = when (state) {
                is TpmsState.Disconnected -> "Offline"
                is TpmsState.Connecting -> "Connecting"
                is TpmsState.Connected -> "Monitoring"
                is TpmsState.Alert -> "Alert"
            }

            val tires = WheelLayout.ORDER.mapIndexed { index, label ->
                val sensor = WheelLayout.orderedSlots(sensors, wheelMapping).getOrNull(index)
                formatTireSlot(sensor, label, unit)
            }

            return WidgetSnapshot(statusText, unit.label, tires)
        }

        fun empty(unit: PressureUnit = PressureUnit.PSI): WidgetSnapshot {
            val tires = WheelLayout.ORDER.map { formatTireSlot(null, it, unit) }
            return WidgetSnapshot("Offline", unit.label, tires)
        }

        private fun formatTireSlot(sensor: TireSensor?, label: String, unit: PressureUnit): WidgetTireSlot {
            if (sensor == null) {
                return WidgetTireSlot(
                    label = label,
                    pressureText = "-- ${unit.label}",
                    status = WidgetTireStatus.EMPTY
                )
            }
            if (sensor.alertType == AlertType.SENSOR_LOST) {
                return WidgetTireSlot(
                    label = label,
                    pressureText = "LOST",
                    status = WidgetTireStatus.ALERT
                )
            }
            val pressureText = if (sensor.pressureKpa.isFinite()) {
                unit.formatPressure(sensor.pressureKpa)
            } else {
                "-- ${unit.label}"
            }
            val temperatureText = if (sensor.temperatureCelsius.isFinite()) {
                "%.0f°C".format(sensor.temperatureCelsius)
            } else {
                "--°C"
            }
            return WidgetTireSlot(
                label = label,
                pressureText = pressureText,
                temperatureText = temperatureText,
                batteryText = "${sensor.batteryPercent}%",
                status = sensor.toSeverity().toWidgetStatus()
            )
        }

        private fun AlertSeverity.toWidgetStatus(): WidgetTireStatus = when (this) {
            AlertSeverity.OK -> WidgetTireStatus.OK
            AlertSeverity.WARNING -> WidgetTireStatus.WARNING
            AlertSeverity.ALERT -> WidgetTireStatus.ALERT
            AlertSeverity.DISCONNECTED -> WidgetTireStatus.EMPTY
        }
    }
}
