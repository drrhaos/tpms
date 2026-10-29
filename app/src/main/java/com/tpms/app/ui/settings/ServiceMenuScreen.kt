package com.tpms.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.ui.localizedLabel

/**
 * Engineering settings for installers and support: receiver protocol, timeouts, diagnostics and
 * backup. Hidden until the version is tapped seven times.
 */
@Composable
fun ServiceMenuScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val dongleProtocolMode by viewModel.dongleProtocolMode.collectAsState()
    val preferredUsbVidPid by viewModel.preferredUsbVidPid.collectAsState()
    val highTemp by viewModel.highTemp.collectAsState()
    val staleFrameTimeoutSec by viewModel.staleFrameTimeoutSec.collectAsState()
    val minLiveWheelPressure by viewModel.minLiveWheelPressure.collectAsState()
    val diagnosticLogEnabled by viewModel.diagnosticLogEnabled.collectAsState()
    val debugToolsEnabled by viewModel.debugToolsEnabled.collectAsState()
    val silentStartup by viewModel.silentStartup.collectAsState()
    val message by viewModel.message.collectAsState()
    var importJson by rememberSaveable { mutableStateOf("") }

    SettingsPage(
        title = stringResource(R.string.settings_service_menu),
        onBack = onBack,
        message = message,
        onMessageShown = viewModel::clearMessage
    ) {
        settingsSection(
            title = R.string.settings_section_receiver,
            footer = R.string.settings_protocol_hint_modes
        ) {
            SettingsNavigationRow(
                label = stringResource(R.string.settings_preferred_usb_dongle),
                value = preferredUsbVidPid?.ifBlank { null } ?: stringResource(R.string.settings_auto),
                onClick = viewModel::cyclePreferredUsbDevice
            )
            SettingsGroupDivider()
            Text(
                text = stringResource(R.string.settings_dongle_protocol),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
            DongleProtocolMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDongleProtocolMode(mode) }
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = dongleProtocolMode == mode,
                        onClick = { viewModel.setDongleProtocolMode(mode) }
                    )
                    Text(text = mode.localizedLabel(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        settingsSection(title = R.string.settings_section_limits) {
            SettingsStepperRow(
                label = stringResource(R.string.settings_high_temp),
                value = "%.0f °C".format(highTemp),
                onDecrease = { viewModel.stepHighTemp(up = false) },
                onIncrease = { viewModel.stepHighTemp(up = true) }
            )
            SettingsGroupDivider()
            SettingsStepperRow(
                label = stringResource(R.string.settings_stale_frame_timeout),
                value = stringResource(R.string.value_seconds_format, staleFrameTimeoutSec),
                onDecrease = { viewModel.stepStaleFrameTimeout(up = false) },
                onIncrease = { viewModel.stepStaleFrameTimeout(up = true) }
            )
            SettingsGroupDivider()
            SettingsStepperRow(
                label = stringResource(R.string.settings_min_live_wheel_pressure),
                value = "%.0f".format(minLiveWheelPressure),
                onDecrease = { viewModel.stepMinLiveWheelPressure(up = false) },
                onIncrease = { viewModel.stepMinLiveWheelPressure(up = true) }
            )
        }

        settingsSection(title = R.string.settings_section_diagnostics) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_diagnostic_log),
                description = stringResource(R.string.settings_diagnostic_log_hint),
                checked = diagnosticLogEnabled,
                onCheckedChange = viewModel::setDiagnosticLogEnabled
            )
            SettingsGroupDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_debug_tools),
                description = stringResource(R.string.settings_debug_tools_hint),
                checked = debugToolsEnabled,
                onCheckedChange = viewModel::setDebugToolsEnabled
            )
            if (debugToolsEnabled) {
                SettingsGroupDivider()
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_open_debug),
                    description = stringResource(R.string.settings_usb_debug_hint),
                    onClick = onOpenDebug
                )
            }
            SettingsGroupDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_silent_startup),
                description = stringResource(R.string.settings_silent_startup_hint),
                checked = silentStartup,
                onCheckedChange = viewModel::setSilentStartup
            )
        }

        settingsSection(title = R.string.settings_import_export) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::copySettingsToClipboard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_export_copy))
                }
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text(stringResource(R.string.settings_import_json)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedButton(
                    onClick = { viewModel.importSettingsJson(importJson) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = importJson.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_import_apply))
                }
            }
        }

        settingsSection(title = null) {
            SettingsActionRow(
                label = stringResource(R.string.settings_service_lock),
                onClick = {
                    viewModel.lockServiceMenu()
                    onBack()
                }
            )
        }
    }
}
