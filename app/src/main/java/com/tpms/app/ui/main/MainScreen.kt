package com.tpms.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.shortLabel
import com.tpms.app.ui.statusLabel
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedWheel by remember { mutableStateOf<Pair<String, TireSensor?>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    selectedWheel?.let { (label, sensor) ->
        ModalBottomSheet(
            onDismissRequest = { selectedWheel = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WheelDetailSheet(
                label = label,
                sensor = sensor,
                pressureUnit = uiState.pressureUnit,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.title_main),
                            modifier = Modifier.padding(start = 10.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.checkNow() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusHeader(
                    state = uiState.tpmsState,
                    wheelMapping = uiState.wheelMapping,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDebug = onNavigateToDebug
                )

                CarTopDown(
                    sensors = uiState.wheelSlots,
                    wheelLabels = uiState.wheelSlotLabels,
                    pressureUnit = uiState.pressureUnit,
                    onWheelClick = { label, sensor -> selectedWheel = label to sensor },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                uiState.lastError?.let { message ->
                    UiErrorBanner(message = message)
                }

                if (uiState.dataStale) {
                    val minutes = uiState.dataAgeMinutes ?: 1
                    DataStaleBanner(minutes = minutes)
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(
    state: TpmsState,
    wheelMapping: Map<String, String>,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit
) {
    val (text, color) = when (state) {
        is TpmsState.Disconnected -> state.statusLabel() to StatusColors.disconnected
        is TpmsState.Connecting -> state.statusLabel() to StatusColors.warning
        is TpmsState.Connected -> state.statusLabel() to StatusColors.ok
        is TpmsState.Alert -> {
            val wheel = WheelLayout.resolveWheelLabel(state.sensor, wheelMapping)
            val alertLabel = state.type.shortLabel()
            stringResource(R.string.status_alert_format, wheel, alertLabel) to StatusColors.alert
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(TpmsColors.surfaceElevated)
            .border(1.dp, TpmsColors.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        IconButton(onClick = onNavigateToDebug, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = stringResource(R.string.cd_debug_log),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WheelDetailSheet(
    label: String,
    sensor: TireSensor?,
    pressureUnit: PressureUnit,
    modifier: Modifier = Modifier
) {
    val emDash = stringResource(R.string.value_em_dash)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (sensor == null) {
            Text(
                text = stringResource(R.string.widget_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        DetailRow(stringResource(R.string.detail_sensor_id), sensor.id.ifBlank { emDash })
        DetailRow(stringResource(R.string.detail_pressure), wheelDetailPressure(sensor, pressureUnit))
        DetailRow(stringResource(R.string.detail_temperature), wheelDetailTemperature(sensor))
        DetailRow(stringResource(R.string.detail_battery), "${sensor.batteryPercent}%")
        DetailRow(stringResource(R.string.detail_last_update), formatTimestamp(sensor.timestamp, emDash))
        sensor.alertType?.let { alert ->
            DetailRow(
                label = stringResource(R.string.detail_alert),
                value = alert.shortLabel(),
                valueColor = StatusColors.alert
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun wheelDetailPressure(sensor: TireSensor, unit: PressureUnit): String {
    val lost = stringResource(R.string.label_lost)
    val emDash = stringResource(R.string.value_em_dash)
    return when {
        sensor.alertType == AlertType.SENSOR_LOST -> lost
        sensor.pressureKpa.isFinite() -> unit.formatPressure(sensor.pressureKpa)
        else -> emDash
    }
}

@Composable
private fun wheelDetailTemperature(sensor: TireSensor): String {
    val emDash = stringResource(R.string.value_em_dash)
    return if (sensor.temperatureCelsius.isFinite()) {
        "%.0f°C".format(sensor.temperatureCelsius)
    } else {
        emDash
    }
}

private fun formatTimestamp(timestamp: Long, emDash: String): String {
    if (timestamp <= 0L) return emDash
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun DataStaleBanner(minutes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(StatusColors.warning.copy(alpha = 0.12f))
            .border(1.dp, StatusColors.warning.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.main_data_stale_warning, minutes),
            style = MaterialTheme.typography.bodySmall,
            color = StatusColors.warning
        )
    }
}

@Composable
private fun UiErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(StatusColors.alert.copy(alpha = 0.12f))
            .border(1.dp, StatusColors.alert.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.ui_error_logged, message),
            style = MaterialTheme.typography.bodySmall,
            color = StatusColors.alert
        )
    }
}
