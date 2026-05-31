package com.tpms.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.theme.StatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sensors by viewModel.sensors.collectAsState()
    val unit by viewModel.pressureUnit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TPMS Monitor") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusHeader(state = state)

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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        sensors.values.sortedBy { it.id }.forEach { sensor ->
                            SensorDetailCard(sensor = sensor, unit = unit)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(state: TpmsState) {
    val (text, color) = when (state) {
        is TpmsState.Disconnected -> "Disconnected" to StatusColors.disconnected
        is TpmsState.Connecting -> "Connecting..." to StatusColors.warning
        is TpmsState.Connected -> "Monitoring" to StatusColors.ok
        is TpmsState.Alert -> "Alert!" to StatusColors.alert
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SensorDetailCard(
    sensor: com.tpms.app.domain.model.TireSensor,
    unit: com.tpms.app.domain.model.PressureUnit
) {
    val statusColor = when {
        sensor.isAlert -> StatusColors.alert
        sensor.alertType != null -> StatusColors.warning
        else -> StatusColors.ok
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sensor.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.1f %s".format(unit.fromKpa(sensor.pressureKpa), unit.label),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SensorStat(label = "Temp", value = "%.0f°C".format(sensor.temperatureCelsius))
                SensorStat(label = "Battery", value = "${sensor.batteryPercent}%")
            }
            if (sensor.isAlert) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sensor.alertType?.name?.replace("_", " ") ?: "",
                    color = StatusColors.alert,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SensorStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
