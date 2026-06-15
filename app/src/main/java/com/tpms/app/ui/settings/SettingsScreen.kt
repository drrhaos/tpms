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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.ui.components.TpmsCard
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
                title = { Text("Settings", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            TpmsCard(title = "USB Debug Log") {
                Text(
                    text = "If your dongle (e.g. 100-a1-xl-v01) is not detected, open the debug log, tap Scan USB, then Copy/Share the output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open USB debug log")
                }
            }

            TpmsCard(title = "Teyes Panel Widget") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (hasWidget) "Widget active on home screen" else "Add widget to main panel",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "On Teyes CC3/CC2: long-press the home screen → Widgets → TPMS Monitor. " +
                        "Or use the button below to pin the widget directly.",
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
                        Text("Pin widget to Teyes panel")
                    }
                } else {
                    Text(
                        text = "Widget pinning is not supported on this device. Add manually from the launcher widget list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TpmsCard(title = "Pressure Unit") {
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
                            Text(unit.label)
                        }
                    }
                }
            }

            TpmsCard(title = "Dongle Protocol") {
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
                            text = mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "For 100-a1-xl-v01 / CH340 dongles use Auto or Serial 0x55AA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auto: HID for HID dongles, Serial for CH340. Use Deelife for MU7J/MU9F modules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TpmsCard(title = "Wheel Mapping") {
                Text(
                    text = "Assign sensor IDs to wheel positions. Tap to cycle: Auto → sensor IDs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (knownSensorIds.isEmpty()) {
                    Text(
                        text = "No sensors detected yet — connect dongle and wait for data.",
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
                            Text(selected.ifBlank { "Auto" })
                        }
                    }
                }
            }

            TpmsCard(title = "Alert Thresholds") {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = TpmsColors.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = pressureUnit.formatThresholdValue(lowPressure),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setLowPressure(v) } },
                    label = { Text("Low Pressure (${pressureUnit.label})") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pressureUnit.formatThresholdValue(highPressure),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setHighPressure(v) } },
                    label = { Text("High Pressure (${pressureUnit.label})") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = highTemp.toString(),
                    onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setHighTemp(v) } },
                    label = { Text("High Temp (°C)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sensorTimeoutSec.toString(),
                    onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setSensorTimeoutSec(v) } },
                    label = { Text("Sensor lost timeout (sec)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }

            TpmsCard(title = "Alert Notifications") {
                TeyesChecklistItem(
                    label = "Alert sound",
                    checked = alertSoundEnabled,
                    onCheckedChange = { viewModel.setAlertSoundEnabled(it) }
                )
                TeyesChecklistItem(
                    label = "Alert vibration",
                    checked = alertVibrationEnabled,
                    onCheckedChange = { viewModel.setAlertVibrationEnabled(it) }
                )
            }

            TpmsCard(title = "Teyes Permissions") {
                TeyesChecklistItem(
                    label = "Auto start enabled (Settings → Apps → TPMS)",
                    checked = teyesChecklist.autoStart,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("auto_start", it) }
                )
                TeyesChecklistItem(
                    label = "Battery → No restrictions",
                    checked = teyesChecklist.batteryUnrestricted,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("battery", it) }
                )
                TeyesChecklistItem(
                    label = "Locked in recent apps",
                    checked = teyesChecklist.lockInRecents,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("lock", it) }
                )
                TeyesChecklistItem(
                    label = "Boot completed allowed",
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
                Text("Save")
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
