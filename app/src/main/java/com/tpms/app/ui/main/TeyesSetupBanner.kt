package com.tpms.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpms.app.R
import com.tpms.app.startup.TeyesSetupStatus
import com.tpms.app.ui.components.TpmsCard
import com.tpms.app.ui.theme.StatusColors

@Composable
fun TeyesSetupBanner(
    status: TeyesSetupStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!status.needsAttention && !status.showWidgetHint) return

    val message = when {
        !status.serviceRunning -> stringResource(R.string.main_teyes_service_stopped)
        !status.notificationsEnabled -> stringResource(R.string.settings_teyes_runtime_notifications_denied)
        !status.batteryUnrestricted -> stringResource(R.string.settings_teyes_runtime_battery_restricted)
        !status.checklistComplete -> stringResource(R.string.main_teyes_checklist_incomplete)
        status.showWidgetHint -> stringResource(R.string.main_teyes_widget_hint)
        else -> return
    }

    TpmsCard(
        title = stringResource(R.string.settings_teyes_permissions),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = StatusColors.warning
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.main_open_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
