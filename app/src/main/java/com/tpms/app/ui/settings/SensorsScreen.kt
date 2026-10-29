package com.tpms.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.wheelPositionLabel

/**
 * Which sensor sits on which wheel. On top, the car with double arrows: one tap swaps two wheels'
 * sensors, as after a tyre rotation. Below, per wheel: "Auto" keeps the receiver's own placement; a
 * sensor is picked from a list showing its live pressure, so the user can find it by letting air
 * out of one tyre.
 */
@Composable
fun SensorsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val wheelMapping by viewModel.wheelMapping.collectAsState()
    val mappableSensors by viewModel.mappableSensors.collectAsState()
    val wheelAssignment by viewModel.wheelAssignment.collectAsState()
    val showSpareWheel by viewModel.showSpareWheel.collectAsState()
    val message by viewModel.message.collectAsState()

    SettingsPage(
        title = stringResource(R.string.settings_sensors),
        onBack = onBack,
        message = message,
        onMessageShown = viewModel::clearMessage
    ) {
        settingsSection(title = null, footer = R.string.settings_wheels_swap_hint) {
            WheelSwapDiagram(
                wheels = wheelAssignment,
                unit = pressureUnit,
                onSwap = viewModel::swapWheels,
                onSwapCross = viewModel::swapWheelsCross
            )
        }
        settingsSection(
            title = null,
            footer = R.string.settings_sensors_footer
        ) {
            if (mappableSensors.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_sensors),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
                SettingsGroupDivider()
            }
            WheelLayout.allSlots(showSpareWheel).forEachIndexed { index, slot ->
                if (index > 0) SettingsGroupDivider()
                WheelMappingRow(
                    slot = slot,
                    mappedId = wheelMapping[slot].orEmpty(),
                    placed = wheelAssignment[slot],
                    sensors = mappableSensors,
                    unit = pressureUnit,
                    onSelect = { viewModel.setWheelMapping(slot, it) }
                )
            }
            SettingsGroupDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_show_spare_wheel),
                checked = showSpareWheel,
                onCheckedChange = viewModel::setShowSpareWheel
            )
        }
    }
}

@Composable
private fun WheelMappingRow(
    slot: String,
    mappedId: String,
    placed: TireSensor?,
    sensors: Map<String, TireSensor>,
    unit: PressureUnit,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val auto = stringResource(R.string.settings_auto)
    val value = if (mappedId.isBlank()) {
        placed?.let { "$auto · ${it.id}" } ?: auto
    } else {
        mappedId
    }
    val description = (sensors[mappedId] ?: placed.takeIf { mappedId.isBlank() })?.let { sensorSummary(it, unit) }

    Box {
        SettingsNavigationRow(
            label = wheelPositionLabel(slot),
            description = description,
            value = value,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(auto) },
                onClick = {
                    expanded = false
                    onSelect("")
                }
            )
            sensors.values.forEach { sensor ->
                DropdownMenuItem(
                    text = { Text("${sensor.id} · ${sensorSummary(sensor, unit)}") },
                    onClick = {
                        expanded = false
                        onSelect(sensor.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun sensorSummary(sensor: TireSensor, unit: PressureUnit): String {
    val pressure = if (sensor.pressureKpa.isFinite()) {
        "${unit.formatFromKpa(sensor.pressureKpa)} ${unit.localizedLabel()}"
    } else {
        stringResource(R.string.value_em_dash)
    }
    return if (sensor.temperatureCelsius.isFinite()) {
        "$pressure · %.0f°C".format(sensor.temperatureCelsius)
    } else {
        pressure
    }
}
