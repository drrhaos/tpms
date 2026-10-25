package com.tpms.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.tpms.app.R
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.label
import com.tpms.app.ui.shortLabel
import com.tpms.app.ui.statusLabel
import com.tpms.app.ui.theme.StatusColors

@Composable
fun mainStatusPresentation(uiState: MainUiState): Pair<String, Color> {
    uiState.lastError?.let { message ->
        return stringResource(R.string.ui_error_logged, message) to StatusColors.alert
    }

    when (val state = uiState.tpmsState) {
        is TpmsState.Disconnected -> {
            uiState.usbFault?.let { return usbFaultPresentation(it, uiState) }
            val text = if (uiState.monitoringOffline) {
                stringResource(R.string.notification_monitoring_offline_title)
            } else {
                state.statusLabel()
            }
            return withStaleSuffix(text, uiState) to StatusColors.disconnected
        }
        is TpmsState.Connecting -> {
            uiState.usbFault?.let { return usbFaultPresentation(it, uiState) }
            return withStaleSuffix(state.statusLabel(), uiState) to StatusColors.warning
        }
        is TpmsState.Alert -> {
            val wheel = MainUiHealth.wheelLabelOf(uiState, state.sensor)
            val alertLabel = state.type.shortLabel()
            val base = stringResource(R.string.status_alert_format, wheel, alertLabel)
            return withStaleSuffix(base, uiState) to StatusColors.alert
        }
        is TpmsState.Connected -> {
            MainUiHealth.firstMissingWheelLabel(uiState)?.let { label ->
                val text = stringResource(R.string.status_no_sensor_format, label)
                return withStaleSuffix(text, uiState) to StatusColors.alert
            }
            // Data flows again, but a stuck reader is still leaked until the head unit reboots.
            if (uiState.usbFault == UsbFault.USB_STUCK) return usbFaultPresentation(UsbFault.USB_STUCK, uiState)
            if (uiState.protocolUnhealthy) {
                val text = stringResource(R.string.notification_monitoring_blind_title)
                return withStaleSuffix(text, uiState) to StatusColors.warning
            }
            val color = if (uiState.dataStale) StatusColors.warning else StatusColors.ok
            return withStaleSuffix(state.statusLabel(), uiState) to color
        }
    }
}

/**
 * Appends "data outdated" to [base] in every state (wayfinder 10); without a known age the minutes
 * are left out rather than guessed. [MainUiState.dataStale] is never set when there is no data at
 * all (rule in [com.tpms.app.domain.SensorFreshness.isDataStale]).
 */
@Composable
private fun withStaleSuffix(base: String, uiState: MainUiState): String {
    if (!uiState.dataStale) return base
    return uiState.dataAgeMinutes
        ?.let { stringResource(R.string.main_status_stale_suffix_format, base, it) }
        ?: stringResource(R.string.main_status_stale_suffix, base)
}

/** The fault stays the primary text and colour; outdated data is still flagged after it. */
@Composable
private fun usbFaultPresentation(fault: UsbFault, uiState: MainUiState): Pair<String, Color> =
    withStaleSuffix(fault.label(), uiState) to when (fault) {
        UsbFault.DONGLE_SILENT -> StatusColors.warning
        UsbFault.USB_STUCK -> StatusColors.alert
        UsbFault.NO_PERMISSION -> StatusColors.warning
    }
