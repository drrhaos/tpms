package com.tpms.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tpms.app.R
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
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

fun AlertType.shortLabel(context: Context): String = when (this) {
    AlertType.LOW_PRESSURE -> context.getString(R.string.alert_short_low)
    AlertType.HIGH_PRESSURE -> context.getString(R.string.alert_short_high)
    AlertType.SENSOR_LOST -> context.getString(R.string.alert_short_lost)
    AlertType.HIGH_TEMP -> context.getString(R.string.alert_short_high_temp)
    AlertType.BATTERY_LOW -> context.getString(R.string.alert_short_low_battery)
}

@Composable
fun AlertType.shortLabel(): String = when (this) {
    AlertType.LOW_PRESSURE -> stringResource(R.string.alert_short_low)
    AlertType.HIGH_PRESSURE -> stringResource(R.string.alert_short_high)
    AlertType.SENSOR_LOST -> stringResource(R.string.alert_short_lost)
    AlertType.HIGH_TEMP -> stringResource(R.string.alert_short_high_temp)
    AlertType.BATTERY_LOW -> stringResource(R.string.alert_short_low_battery)
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
