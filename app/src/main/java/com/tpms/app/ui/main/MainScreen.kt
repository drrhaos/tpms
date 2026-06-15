package com.tpms.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

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
                            text = "TPMS Monitor",
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
                    wheelMapping = uiState.wheelMapping
                )

                CarTopDown(
                    sensors = uiState.wheelSlots,
                    pressureUnit = uiState.pressureUnit,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDebug = onNavigateToDebug,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                uiState.lastError?.let { message ->
                    UiErrorBanner(message = message)
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(state: TpmsState, wheelMapping: Map<String, String>) {
    val (text, color) = when (state) {
        is TpmsState.Disconnected -> "Disconnected" to StatusColors.disconnected
        is TpmsState.Connecting -> "Connecting…" to StatusColors.warning
        is TpmsState.Connected -> "Monitoring" to StatusColors.ok
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun AlertType.shortLabel(): String = when (this) {
    AlertType.LOW_PRESSURE -> "LOW"
    AlertType.HIGH_PRESSURE -> "HIGH"
    AlertType.SENSOR_LOST -> "LOST"
    AlertType.HIGH_TEMP -> "HIGH TEMP"
    AlertType.BATTERY_LOW -> "LOW BATT"
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
            text = "UI error logged: $message",
            style = MaterialTheme.typography.bodySmall,
            color = StatusColors.alert
        )
    }
}
