package com.tpms.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tpms.app.R
import com.tpms.app.startup.SetupStatus
import com.tpms.app.ui.theme.StatusColors

@Composable
fun SetupWarnings(
    status: SetupStatus,
    modifier: Modifier = Modifier
) {
    val messages = buildList {
        if (!status.serviceRunning) add(R.string.main_setup_service_stopped)
        if (!status.notificationsEnabled) add(R.string.settings_runtime_notifications_denied)
        if (!status.batteryUnrestricted) add(R.string.settings_runtime_battery_restricted)
        if (status.isTeyesDevice && !status.checklistComplete) {
            add(R.string.main_teyes_checklist_incomplete)
        }
        if (status.showFrontAppHint) add(R.string.main_teyes_frontapp_hint)
    }
    if (messages.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        messages.forEach { messageRes ->
            SettingsWarningBanner(text = stringResource(messageRes))
        }
    }
}

@Composable
fun SettingsWarningBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(StatusColors.warning.copy(alpha = 0.12f))
            .border(1.dp, StatusColors.warning.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = StatusColors.warning
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
