package com.tpms.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.tpms.app.domain.AlertSeverity

fun AlertSeverity.statusColor(): Color = when (this) {
    AlertSeverity.OK -> StatusColors.ok
    AlertSeverity.WARNING -> StatusColors.warning
    AlertSeverity.ALERT -> StatusColors.alert
    AlertSeverity.DISCONNECTED -> StatusColors.disconnected
}
