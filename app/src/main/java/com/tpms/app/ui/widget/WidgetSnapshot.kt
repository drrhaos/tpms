package com.tpms.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import com.tpms.app.R
import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.toSeverity
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.domain.model.WidgetThemeMode
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.widgetStatusLabel

enum class WidgetTireStatus {
    OK, WARNING, ALERT, EMPTY
}

data class WidgetTireSlot(
    val label: String,
    val pressureText: String,
    val temperatureText: String = "--°C",
    val batteryText: String = "--%",
    val batteryPercent: Int? = null,
    val isLowBattery: Boolean = false,
    val status: WidgetTireStatus
)

data class WidgetSnapshot(
    val connectionStatus: String,
    val unitLabel: String,
    val tires: List<WidgetTireSlot>,
    val dataAgeSec: Long? = null,
    val dataStale: Boolean = false,
    val useLightTheme: Boolean = false
) {
    companion object {
        fun from(
            context: Context,
            state: TpmsState,
            sensors: Map<String, TireSensor>,
            unit: PressureUnit,
            wheelMapping: Map<String, String> = emptyMap(),
            showSpareWheel: Boolean = false,
            dataAgeSec: Long? = null,
            dataStale: Boolean = false,
            useLightTheme: Boolean = false
        ): WidgetSnapshot {
            val slots = WheelLayout.allSlots(showSpareWheel)
            val ordered = WheelLayout.orderedSlots(sensors, wheelMapping, showSpareWheel)
            val tires = slots.mapIndexed { index, slot ->
                val sensor = ordered.getOrNull(index)
                formatTireSlot(context, sensor, slot, unit)
            }

            val baseStatus = state.widgetStatusLabel(context)
            val status = when {
                dataStale && dataAgeSec != null -> {
                    val minutes = (dataAgeSec / 60).coerceAtLeast(1)
                    context.getString(R.string.widget_status_stale_format, baseStatus, minutes)
                }
                dataStale ->
                    context.getString(R.string.widget_status_stale_short, baseStatus)
                else -> baseStatus
            }

            return WidgetSnapshot(status, unit.localizedLabel(context), tires, dataAgeSec, dataStale, useLightTheme)
        }

        fun resolveLightTheme(context: Context, mode: WidgetThemeMode): Boolean = when (mode) {
            WidgetThemeMode.LIGHT -> true
            WidgetThemeMode.DARK -> false
            WidgetThemeMode.AUTO -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode != Configuration.UI_MODE_NIGHT_YES
            }
        }

        fun empty(
            context: Context,
            unit: PressureUnit = PressureUnit.PSI,
            showSpareWheel: Boolean = false,
            useLightTheme: Boolean = false
        ): WidgetSnapshot {
            val tires = WheelLayout.allSlots(showSpareWheel).map { formatTireSlot(context, null, it, unit) }
            return WidgetSnapshot(
                context.getString(R.string.widget_status_offline),
                unit.localizedLabel(context),
                tires,
                useLightTheme = useLightTheme
            )
        }

        private fun formatTireSlot(
            context: Context,
            sensor: TireSensor?,
            label: String,
            unit: PressureUnit
        ): WidgetTireSlot {
            if (sensor == null) {
                return WidgetTireSlot(
                    label = label,
                    pressureText = context.getString(R.string.value_no_data_pressure, unit.localizedLabel(context)),
                    temperatureText = context.getString(R.string.value_no_data_temp),
                    batteryText = context.getString(R.string.value_no_data_battery),
                    status = WidgetTireStatus.EMPTY
                )
            }
            if (sensor.alertType == AlertType.SENSOR_LOST) {
                return WidgetTireSlot(
                    label = label,
                    pressureText = context.getString(R.string.label_lost),
                    temperatureText = context.getString(R.string.value_no_data_temp),
                    batteryText = context.getString(R.string.value_no_data_battery),
                    status = WidgetTireStatus.ALERT
                )
            }
            val pressureText = if (sensor.pressureKpa.isFinite()) {
                unit.formatFromKpa(sensor.pressureKpa)
            } else {
                context.getString(R.string.value_no_data_pressure, unit.localizedLabel(context))
            }
            val temperatureText = if (sensor.temperatureCelsius.isFinite()) {
                "%.0f°C".format(sensor.temperatureCelsius)
            } else {
                context.getString(R.string.value_no_data_temp)
            }
            return WidgetTireSlot(
                label = label,
                pressureText = pressureText,
                temperatureText = temperatureText,
                batteryText = "${sensor.batteryPercent}%",
                batteryPercent = sensor.batteryPercent,
                isLowBattery = sensor.alertType == AlertType.BATTERY_LOW,
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
