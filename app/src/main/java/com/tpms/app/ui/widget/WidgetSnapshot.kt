package com.tpms.app.ui.widget

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
    val status: WidgetTireStatus
)

data class WidgetSnapshot(
    val connectionStatus: String,
    val unitLabel: String,
    val tires: List<WidgetTireSlot>
) {
    companion object {
        private val WHEEL_ORDER = listOf("FL", "FR", "RL", "RR")
        private val WHEEL_FALLBACK_IDS = listOf("SENSOR_01", "SENSOR_02", "SENSOR_03", "SENSOR_04")

        fun from(
            state: TpmsState,
            sensors: Map<String, TireSensor>,
            unit: PressureUnit
        ): WidgetSnapshot {
            val statusText = when (state) {
                is TpmsState.Disconnected -> "Offline"
                is TpmsState.Connecting -> "Connecting"
                is TpmsState.Connected -> "Live"
                is TpmsState.Alert -> "Alert"
            }

            val tires = WHEEL_ORDER.mapIndexed { index, label ->
                val sensor = sensors[label]
                    ?: sensors[WHEEL_FALLBACK_IDS[index]]
                    ?: sensors.values.elementAtOrNull(index)

                if (sensor == null) {
                    WidgetTireSlot(label, "--", WidgetTireStatus.EMPTY)
                } else {
                    WidgetTireSlot(
                        label = label,
                        pressureText = "%.0f".format(unit.fromKpa(sensor.pressureKpa)),
                        status = tireStatus(sensor)
                    )
                }
            }

            return WidgetSnapshot(statusText, unit.label, tires)
        }

        fun empty(unit: PressureUnit = PressureUnit.PSI): WidgetSnapshot {
            val tires = WHEEL_ORDER.map { WidgetTireSlot(it, "--", WidgetTireStatus.EMPTY) }
            return WidgetSnapshot("Offline", unit.label, tires)
        }

        private fun tireStatus(sensor: TireSensor): WidgetTireStatus = when {
            sensor.alertType == AlertType.LOW_PRESSURE ||
                sensor.alertType == AlertType.HIGH_PRESSURE ||
                sensor.alertType == AlertType.SENSOR_LOST -> WidgetTireStatus.ALERT
            sensor.alertType == AlertType.HIGH_TEMP ||
                sensor.alertType == AlertType.BATTERY_LOW -> WidgetTireStatus.WARNING
            else -> WidgetTireStatus.OK
        }
    }
}
