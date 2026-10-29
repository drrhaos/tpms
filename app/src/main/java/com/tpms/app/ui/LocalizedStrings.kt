package com.tpms.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpms.app.R
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState

fun PressureUnit.localizedLabel(context: Context): String = when (this) {
    PressureUnit.PSI -> context.getString(R.string.unit_psi)
    PressureUnit.KPA -> context.getString(R.string.unit_kpa)
    PressureUnit.BAR -> context.getString(R.string.unit_bar)
}

@Composable
fun PressureUnit.localizedLabel(): String = when (this) {
    PressureUnit.PSI -> stringResource(R.string.unit_psi)
    PressureUnit.KPA -> stringResource(R.string.unit_kpa)
    PressureUnit.BAR -> stringResource(R.string.unit_bar)
}

fun UsbFault.label(context: Context): String = context.getString(labelRes())

@Composable
fun UsbFault.label(): String = stringResource(labelRes())

private fun UsbFault.labelRes(): Int = when (this) {
    UsbFault.DONGLE_SILENT -> R.string.usb_fault_dongle_silent
    UsbFault.USB_STUCK -> R.string.usb_fault_stuck
    UsbFault.NO_PERMISSION -> R.string.usb_fault_no_permission
}

fun AlertType.shortLabel(context: Context): String = when (this) {
    AlertType.LOW_PRESSURE -> context.getString(R.string.alert_short_low)
    AlertType.HIGH_PRESSURE -> context.getString(R.string.alert_short_high)
    AlertType.SENSOR_LOST -> context.getString(R.string.alert_short_lost)
    AlertType.HIGH_TEMP -> context.getString(R.string.alert_short_high_temp)
    AlertType.BATTERY_LOW -> context.getString(R.string.alert_short_low_battery)
}

/** Full sentence for the per-wheel alert notification (wayfinder 15). */
fun AlertType.notificationText(context: Context): String = when (this) {
    AlertType.LOW_PRESSURE -> context.getString(R.string.notification_alert_low_pressure)
    AlertType.HIGH_PRESSURE -> context.getString(R.string.notification_alert_high_pressure)
    AlertType.SENSOR_LOST -> context.getString(R.string.notification_alert_sensor_lost)
    AlertType.HIGH_TEMP -> context.getString(R.string.notification_alert_high_temp)
    AlertType.BATTERY_LOW -> context.getString(R.string.notification_alert_battery_low)
}

@Composable
fun AlertType.shortLabel(): String = when (this) {
    AlertType.LOW_PRESSURE -> stringResource(R.string.alert_short_low)
    AlertType.HIGH_PRESSURE -> stringResource(R.string.alert_short_high)
    AlertType.SENSOR_LOST -> stringResource(R.string.alert_short_lost)
    AlertType.HIGH_TEMP -> stringResource(R.string.alert_short_high_temp)
    AlertType.BATTERY_LOW -> stringResource(R.string.alert_short_low_battery)
}

fun TireSensor.batteryLabel(context: Context): String = when {
    batteryPercent != null -> "$batteryPercent%"
    isLowBattery -> context.getString(R.string.alert_short_low_battery)
    else -> context.getString(R.string.value_no_data_battery)
}

@Composable
fun TireSensor.batteryLabel(): String = when {
    batteryPercent != null -> "$batteryPercent%"
    isLowBattery -> stringResource(R.string.alert_short_low_battery)
    else -> stringResource(R.string.value_no_data_battery)
}

fun TpmsState.statusLabel(context: Context): String = when (this) {
    is TpmsState.Disconnected -> context.getString(R.string.label_disconnected)
    is TpmsState.Connecting -> context.getString(R.string.label_connecting)
    is TpmsState.Connected -> context.getString(R.string.label_monitoring)
    is TpmsState.Alert -> context.getString(R.string.label_alert)
}

@Composable
fun TpmsState.statusLabel(): String = when (this) {
    is TpmsState.Disconnected -> stringResource(R.string.label_disconnected)
    is TpmsState.Connecting -> stringResource(R.string.label_connecting)
    is TpmsState.Connected -> stringResource(R.string.label_monitoring)
    is TpmsState.Alert -> stringResource(R.string.label_alert)
}

fun TpmsState.widgetStatusLabel(context: Context): String = when (this) {
    is TpmsState.Disconnected -> context.getString(R.string.widget_status_offline)
    is TpmsState.Connecting -> context.getString(R.string.widget_status_connecting)
    is TpmsState.Connected -> context.getString(R.string.widget_status_monitoring)
    is TpmsState.Alert -> context.getString(R.string.widget_status_alert)
}

fun DongleProtocolMode.localizedLabel(context: Context): String = when (this) {
    DongleProtocolMode.AUTO -> context.getString(R.string.protocol_auto)
    DongleProtocolMode.HID_GENERIC -> context.getString(R.string.protocol_hid)
    DongleProtocolMode.SERIAL_AA55 -> context.getString(R.string.protocol_serial)
    DongleProtocolMode.DEELIFE -> context.getString(R.string.protocol_deelife)
}

@Composable
fun DongleProtocolMode.localizedLabel(): String = when (this) {
    DongleProtocolMode.AUTO -> stringResource(R.string.protocol_auto)
    DongleProtocolMode.HID_GENERIC -> stringResource(R.string.protocol_hid)
    DongleProtocolMode.SERIAL_AA55 -> stringResource(R.string.protocol_serial)
    DongleProtocolMode.DEELIFE -> stringResource(R.string.protocol_deelife)
}

/** Human name of a wheel slot ("Front left"); an unknown slot is shown as is. */
fun wheelPositionLabel(context: Context, slot: String): String =
    wheelPositionRes(slot)?.let { context.getString(it) } ?: slot

@Composable
fun wheelPositionLabel(slot: String): String =
    wheelPositionRes(slot)?.let { stringResource(it) } ?: slot

private fun wheelPositionRes(slot: String): Int? = when (slot) {
    "FL" -> R.string.wheel_position_fl
    "FR" -> R.string.wheel_position_fr
    "RL" -> R.string.wheel_position_rl
    "RR" -> R.string.wheel_position_rr
    WheelLayout.SPARE_SLOT -> R.string.wheel_position_spare
    else -> null
}

/** Sentence-case alert name for the main screen and the widget ("Low pressure"). */
@Composable
fun AlertType.cardLabel(): String = stringResource(cardLabelRes())

fun AlertType.cardLabel(context: Context): String = context.getString(cardLabelRes())

private fun AlertType.cardLabelRes(): Int = when (this) {
    AlertType.LOW_PRESSURE -> R.string.alert_card_low
    AlertType.HIGH_PRESSURE -> R.string.alert_card_high
    AlertType.SENSOR_LOST -> R.string.alert_card_lost
    AlertType.HIGH_TEMP -> R.string.alert_card_high_temp
    AlertType.BATTERY_LOW -> R.string.alert_card_low_battery
}
