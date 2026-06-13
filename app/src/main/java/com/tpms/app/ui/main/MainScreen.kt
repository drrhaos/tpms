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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.ui.components.TpmsCard
import com.tpms.app.ui.dashboard.MiniDashboard
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sensors by viewModel.sensors.collectAsState()
    val unit by viewModel.pressureUnit.collectAsState()

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
                ),
                actions = {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug log", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusHeader(state = state)

            if (sensors.isNotEmpty()) {
                MiniDashboard(sensors = sensors, pressureUnit = unit)
            }

            val sensorList = listOf(
                sensors["FL"] ?: sensors["SENSOR_01"] ?: sensors.values.firstOrNull(),
                sensors["FR"] ?: sensors["SENSOR_02"] ?: sensors.values.elementAtOrNull(1),
                sensors["RL"] ?: sensors["SENSOR_03"] ?: sensors.values.elementAtOrNull(2),
                sensors["RR"] ?: sensors["SENSOR_04"] ?: sensors.values.elementAtOrNull(3)
            )

            CarTopDown(
                sensors = sensorList,
                pressureUnit = unit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (sensors.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sensor Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    sensors.values.sortedBy { it.id }.forEach { sensor ->
                        SensorDetailCard(sensor = sensor, unit = unit)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(state: TpmsState) {
    val (text, color) = when (state) {
        is TpmsState.Disconnected -> "Disconnected" to StatusColors.disconnected
        is TpmsState.Connecting -> "Connecting…" to StatusColors.warning
        is TpmsState.Connected -> "Monitoring" to StatusColors.ok
        is TpmsState.Alert -> "Alert!" to StatusColors.alert
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

@Composable
private fun SensorDetailCard(sensor: TireSensor, unit: PressureUnit) {
    val statusColor = when {
        sensor.isAlert -> StatusColors.alert
        sensor.alertType != null -> StatusColors.warning
        else -> StatusColors.ok
    }

    TpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = sensor.label.ifEmpty { sensor.id },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (sensor.temperatureCelsius.isFinite())
                        "%.0f°C · ${sensor.batteryPercent}% batt".format(sensor.temperatureCelsius)
                    else "--°C · ${sensor.batteryPercent}% batt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (sensor.pressureKpa.isFinite())
                    "%.1f %s".format(unit.fromKpa(sensor.pressureKpa), unit.label)
                else "-- ${unit.label}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        if (sensor.isAlert) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sensor.alertType?.name?.replace("_", " ") ?: "",
                color = StatusColors.alert,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
