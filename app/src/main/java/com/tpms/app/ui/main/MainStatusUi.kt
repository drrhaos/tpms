package com.tpms.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.TpmsState
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
            val text = if (uiState.monitoringOffline) {
                stringResource(R.string.notification_monitoring_offline_title)
            } else {
                state.statusLabel()
            }
            return text to StatusColors.disconnected
        }
        is TpmsState.Connecting -> {
            return state.statusLabel() to StatusColors.warning
        }
        is TpmsState.Alert -> {
            val wheel = WheelLayout.resolveWheelLabel(state.sensor, uiState.wheelMapping)
            val alertLabel = state.type.shortLabel()
            val base = stringResource(R.string.status_alert_format, wheel, alertLabel)
            val text = if (uiState.dataStale) {
                val minutes = uiState.dataAgeMinutes ?: 1
                stringResource(R.string.widget_status_stale_format, base, minutes)
            } else {
                base
            }
            return text to StatusColors.alert
        }
        is TpmsState.Connected -> {
            MainUiHealth.firstMissingWheelLabel(uiState)?.let { label ->
                return stringResource(R.string.status_no_sensor_format, label) to StatusColors.alert
            }
            if (uiState.protocolUnhealthy) {
                return stringResource(R.string.notification_monitoring_blind_title) to StatusColors.warning
            }
            if (uiState.dataStale) {
                val minutes = uiState.dataAgeMinutes ?: 1
                return stringResource(R.string.main_status_stale_format, minutes) to StatusColors.warning
            }
            return state.statusLabel() to StatusColors.ok
        }
    }
}
