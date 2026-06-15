package com.tpms.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.ui.components.TpmsCard
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.theme.TpmsColors
import com.tpms.app.ui.widget.TpmsWidgetHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val lowPressure by viewModel.lowPressure.collectAsState()
    val highPressure by viewModel.highPressure.collectAsState()
    val highTemp by viewModel.highTemp.collectAsState()
    val dongleProtocolMode by viewModel.dongleProtocolMode.collectAsState()
    val sensorTimeoutSec by viewModel.sensorTimeoutSec.collectAsState()
    val wheelMapping by viewModel.wheelMapping.collectAsState()
    val knownSensorIds by viewModel.knownSensorIds.collectAsState()
    val teyesChecklist by viewModel.teyesChecklist.collectAsState()
    val alertSoundEnabled by viewModel.alertSoundEnabled.collectAsState()
    val alertVibrationEnabled by viewModel.alertVibrationEnabled.collectAsState()
    val pinSupported = TpmsWidgetHelper.isPinSupported(context)
    val hasWidget = TpmsWidgetHelper.hasActiveWidgets(context)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            TpmsCard(title = stringResource(R.string.settings_usb_debug_title)) {
                Text(
                    text = stringResource(R.string.settings_usb_debug_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_open_debug))
                }
            }

            TpmsCard(title = stringResource(R.string.settings_teyes_home_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (hasWidget) {
                            stringResource(R.string.widget_dashboard_active)
                        } else {
                            stringResource(R.string.widget_dashboard_inactive)
                        },
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.widget_teyes_panel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (pinSupported) {
                    OutlinedButton(
                        onClick = { TpmsWidgetHelper.requestPinToTeyesPanel(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.widget_pin_to_panel))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.widget_pin_manual_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TpmsCard(title = stringResource(R.string.settings_pressure_unit)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PressureUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = pressureUnit == unit,
                            onClick = { viewModel.setPressureUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, PressureUnit.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = TpmsColors.primaryDim,
                                activeContentColor = TpmsColors.accent
                            )
                        ) {
                            Text(unit.localizedLabel())
                        }
                    }
                }
            }

            TpmsCard(title = stringResource(R.string.settings_dongle_protocol)) {
                DongleProtocolMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDongleProtocolMode(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = dongleProtocolMode == mode,
                            onClick = { viewModel.setDongleProtocolMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = mode.localizedLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_protocol_hint_ch340),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_protocol_hint_modes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TpmsCard(title = stringResource(R.string.settings_wheel_mapping)) {
                Text(
                    text = stringResource(R.string.settings_wheel_mapping_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (knownSensorIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_sensors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                viewModel.wheelSlots.forEach { slot ->
                    val selected = wheelMapping[slot].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(slot, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { viewModel.cycleWheelMapping(slot, knownSensorIds) }
                        ) {
                            Text(selected.ifBlank { stringResource(R.string.settings_auto) })
                        }
                    }
                }
            }

            TpmsCard(title = stringResource(R.string.settings_alert_thresholds)) {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = TpmsColors.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = pressureUnit.formatThresholdValue(lowPressure),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setLowPressure(v) } },
                    label = { Text(stringResource(R.string.settings_low_pressure, pressureUnit.localizedLabel())) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pressureUnit.formatThresholdValue(highPressure),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setHighPressure(v) } },
                    label = { Text(stringResource(R.string.settings_high_pressure, pressureUnit.localizedLabel())) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = highTemp.toString(),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setHighTemp(v) } },
                    label = { Text(stringResource(R.string.settings_high_temp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sensorTimeoutSec.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setSensorTimeoutSec(v) } },
                    label = { Text(stringResource(R.string.settings_sensor_timeout)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }

            TpmsCard(title = stringResource(R.string.settings_alert_notifications)) {
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_alert_sound),
                    checked = alertSoundEnabled,
                    onCheckedChange = { viewModel.setAlertSoundEnabled(it) }
                )
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_alert_vibration),
                    checked = alertVibrationEnabled,
                    onCheckedChange = { viewModel.setAlertVibrationEnabled(it) }
                )
            }

            TpmsCard(title = stringResource(R.string.settings_teyes_permissions)) {
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_teyes_auto_start),
                    checked = teyesChecklist.autoStart,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("auto_start", it) }
                )
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_teyes_battery),
                    checked = teyesChecklist.batteryUnrestricted,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("battery", it) }
                )
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_teyes_lock),
                    checked = teyesChecklist.lockInRecents,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("lock", it) }
                )
                TeyesChecklistItem(
                    label = stringResource(R.string.settings_teyes_boot),
                    checked = teyesChecklist.bootCompleted,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("boot", it) }
                )
            }

            Button(
                onClick = {
                    viewModel.saveThresholds()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.action_save))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TeyesChecklistItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
