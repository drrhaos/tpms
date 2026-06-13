package com.tpms.app.ui.widget

import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.toSeverity
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

            val tires = WheelLayout.ORDER.mapIndexed { index, label ->
                val sensor = WheelLayout.orderedSlots(sensors).getOrNull(index)

                if (sensor == null || !sensor.pressureKpa.isFinite()) {
                    WidgetTireSlot(label, "--", WidgetTireStatus.EMPTY)
                } else {
                    WidgetTireSlot(
                        label = label,
                        pressureText = "%.0f".format(unit.fromKpa(sensor.pressureKpa)),
                        status = sensor.toSeverity().toWidgetStatus()
                    )
                }
            }

            return WidgetSnapshot(statusText, unit.label, tires)
        }

        fun empty(unit: PressureUnit = PressureUnit.PSI): WidgetSnapshot {
            val tires = WheelLayout.ORDER.map { WidgetTireSlot(it, "--", WidgetTireStatus.EMPTY) }
            return WidgetSnapshot("Offline", unit.label, tires)
        }

        private fun AlertSeverity.toWidgetStatus(): WidgetTireStatus = when (this) {
            AlertSeverity.OK -> WidgetTireStatus.OK
            AlertSeverity.WARNING -> WidgetTireStatus.WARNING
            AlertSeverity.ALERT -> WidgetTireStatus.ALERT
            AlertSeverity.DISCONNECTED -> WidgetTireStatus.EMPTY
        }
    }
}
