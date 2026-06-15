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
fun mainStatusPresentation(
    state: TpmsState,
    wheelMapping: Map<String, String>
): Pair<String, Color> {
    return when (state) {
        is TpmsState.Disconnected -> state.statusLabel() to StatusColors.disconnected
        is TpmsState.Connecting -> state.statusLabel() to StatusColors.warning
        is TpmsState.Connected -> state.statusLabel() to StatusColors.ok
        is TpmsState.Alert -> {
            val wheel = WheelLayout.resolveWheelLabel(state.sensor, wheelMapping)
            val alertLabel = state.type.shortLabel()
            stringResource(R.string.status_alert_format, wheel, alertLabel) to StatusColors.alert
        }
    }
}
