package com.tpms.app.ui.widget

import android.content.Context
import android.content.res.Configuration
import com.tpms.app.R
import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.SensorFreshness
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.toSeverity
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.domain.model.WidgetThemeMode
import com.tpms.app.ui.batteryLabel
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
    val status: WidgetTireStatus,
    /** Minutes for the "N min" caption of a stale wheel (wayfinder 10), null when fresh. */
    val staleMinutes: Long? = null,
    /** Stale wheel without an active alert: drawn grey; alert colours win over muting. */
    val muted: Boolean = false
) {
    /** Status that drives colours: a muted wheel looks like an empty slot, values stay visible. */
    val displayStatus: WidgetTireStatus get() = if (muted) WidgetTireStatus.EMPTY else status
}

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
            /** Slot label → placed sensor, as published by the repository ([com.tpms.app.data.repository.TpmsRepository.wheels]). */
            wheels: Map<String, TireSensor>,
            unit: PressureUnit,
            showSpareWheel: Boolean = false,
            dataAgeSec: Long? = null,
            dataStale: Boolean = false,
            useLightTheme: Boolean = false,
            wheelProtocol: DongleProtocol? = null,
            now: Long = System.currentTimeMillis()
        ): WidgetSnapshot {
            val tires = WheelLayout.allSlots(showSpareWheel).map { slot ->
                val sensor = wheels[slot]
                formatTireSlot(context, sensor, slot, unit).copy(
                    staleMinutes = SensorFreshness.staleWheelMinutes(sensor, wheelProtocol, now),
                    muted = SensorFreshness.isWheelMuted(sensor, wheelProtocol, now)
                )
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
                batteryText = sensor.batteryLabel(context),
                batteryPercent = sensor.batteryPercent,
                isLowBattery = sensor.isLowBattery,
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
