package com.tpms.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.domain.UsbFault
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.embedded.EmbeddedMainScreenFrame
import com.tpms.app.ui.embedded.LocalEmbeddedWindow
import com.tpms.app.ui.batteryLabel
import com.tpms.app.domain.toSeverity
import com.tpms.app.ui.cardLabel
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.theme.statusColor
import com.tpms.app.ui.wheelPositionLabel
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
    val embedded = LocalEmbeddedWindow.current
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
                targetKpa = uiState.targetPressureKpa,
                showTechnicalDetails = uiState.advancedMode,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    val alert = uiState.tpmsState as? TpmsState.Alert
    val (statusText, statusColor) = if (alert != null) {
        // The banner names the wheel and the problem; the header just flags it.
        stringResource(R.string.label_warning) to alert.type.toSeverity().statusColor()
    } else {
        mainStatusPresentation(uiState)
    }

    val screenContent: @Composable () -> Unit = {
        MainScreenScaffold(
            statusText = statusText,
            statusColor = statusColor,
            uiState = uiState,
            isRefreshing = isRefreshing,
            compactToolbar = embedded.isEmbedded,
            onRefresh = { viewModel.checkNow() },
            onStatusClick = if (uiState.usbFault == UsbFault.NO_PERMISSION) {
                { viewModel.requestUsbPermission() }
            } else {
                null
            },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDebug = onNavigateToDebug,
            showDebug = uiState.advancedMode && uiState.debugToolsEnabled,
            onWheelClick = { label, sensor -> selectedWheel = label to sensor }
        )
    }

    if (embedded.isEmbedded) {
        EmbeddedMainScreenFrame(content = screenContent)
    } else {
        screenContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenScaffold(
    statusText: String,
    statusColor: androidx.compose.ui.graphics.Color,
    uiState: MainUiState,
    isRefreshing: Boolean,
    compactToolbar: Boolean,
    onRefresh: () -> Unit,
    onStatusClick: (() -> Unit)?,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    showDebug: Boolean,
    onWheelClick: (String, TireSensor?) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    StatusHeaderRow(
                        statusText = statusText,
                        statusColor = statusColor,
                        compact = compactToolbar,
                        modifier = if (onStatusClick != null) Modifier.clickable(onClick = onStatusClick) else Modifier
                    )
                },
                actions = {
                    // The reference the readings are judged against, like an OEM cluster's TPMS page.
                    Text(
                        text = stringResource(
                            R.string.main_target_caption,
                            uiState.pressureUnit.formatFromKpa(uiState.targetPressureKpa),
                            uiState.pressureUnit.localizedLabel()
                        ),
                        style = if (compactToolbar) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    val iconSize = if (compactToolbar) 36.dp else 40.dp
                    if (showDebug) {
                        IconButton(onClick = onNavigateToDebug, modifier = Modifier.size(iconSize)) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.cd_debug_log),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(iconSize)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MainDashboardBody(
                uiState = uiState,
                onWheelClick = onWheelClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MainDashboardBody(
    uiState: MainUiState,
    onWheelClick: (String, TireSensor?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        (uiState.tpmsState as? TpmsState.Alert)?.let { alert ->
            val slot = MainUiHealth.wheelLabelOf(uiState, alert.sensor)
            AlertBanner(
                wheel = wheelPositionLabel(slot),
                type = alert.type,
                onClick = { onWheelClick(slot, alert.sensor) }
            )
        }
        CarTopDown(
            sensors = uiState.wheelSlots,
            wheelLabels = uiState.wheelSlotLabels,
            pressureUnit = uiState.pressureUnit,
            staleMinutes = uiState.wheelStaleMinutes,
            muted = uiState.wheelMuted,
            onWheelClick = onWheelClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/** Prominent alert under the header, like a cluster warning: wheel, problem and what to do. */
@Composable
private fun AlertBanner(wheel: String, type: AlertType, onClick: () -> Unit) {
    val color = type.toSeverity().statusColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.55f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = stringResource(R.string.status_alert_format, wheel, type.cardLabel()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = stringResource(type.adviceRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

private fun AlertType.adviceRes(): Int = when (this) {
    AlertType.LOW_PRESSURE -> R.string.alert_advice_low
    AlertType.HIGH_PRESSURE -> R.string.alert_advice_high
    AlertType.SENSOR_LOST -> R.string.alert_advice_lost
    AlertType.HIGH_TEMP -> R.string.alert_advice_high_temp
    AlertType.BATTERY_LOW -> R.string.alert_advice_low_battery
}

@Composable
private fun StatusHeaderRow(
    statusText: String,
    statusColor: androidx.compose.ui.graphics.Color,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 8.dp else 10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = statusText,
            color = statusColor,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (compact) 6.dp else 10.dp)
        )
    }
}

@Composable
private fun WheelDetailSheet(
    label: String,
    sensor: TireSensor?,
    pressureUnit: PressureUnit,
    targetKpa: Float,
    showTechnicalDetails: Boolean = false,
    modifier: Modifier = Modifier
) {
    val emDash = stringResource(R.string.value_em_dash)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = wheelPositionLabel(label),
            style = MaterialTheme.typography.headlineSmall
        )
        sensor?.alertType?.let { alert ->
            Text(
                text = alert.cardLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = alert.toSeverity().statusColor(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (sensor == null) {
            Text(
                text = stringResource(R.string.widget_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        DetailRow(stringResource(R.string.detail_pressure), wheelDetailPressure(sensor, pressureUnit))
        DetailRow(stringResource(R.string.detail_target), formatPressureWithUnit(targetKpa, pressureUnit))
        DetailRow(stringResource(R.string.detail_temperature), wheelDetailTemperature(sensor))
        DetailRow(stringResource(R.string.detail_battery), sensor.batteryLabel())
        if (showTechnicalDetails) {
            DetailRow(stringResource(R.string.detail_sensor_id), sensor.id.ifBlank { emDash })
            DetailRow(stringResource(R.string.detail_last_update), formatTimestamp(sensor.timestamp, emDash))
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
    val emDash = stringResource(R.string.value_em_dash)
    return when {
        sensor.alertType == AlertType.SENSOR_LOST -> emDash
        sensor.pressureKpa.isFinite() -> formatPressureWithUnit(sensor.pressureKpa, unit)
        else -> emDash
    }
}

@Composable
private fun formatPressureWithUnit(kpa: Float, unit: PressureUnit): String =
    "${unit.formatFromKpa(kpa)} ${unit.localizedLabel()}"

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
