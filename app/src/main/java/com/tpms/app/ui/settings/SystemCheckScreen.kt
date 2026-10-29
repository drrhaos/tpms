package com.tpms.app.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R

/**
 * Everything monitoring needs from the system: checks the app can read (service, battery,
 * notifications) and, on Teyes, the head unit steps the user confirms by hand.
 */
@Composable
fun SystemCheckScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val setupStatus by viewModel.setupStatus.collectAsState()
    val checklist by viewModel.teyesChecklist.collectAsState()
    val killedInSleep by viewModel.killedInSleep.collectAsState()

    RefreshSetupStatusOnResume(viewModel)

    SettingsPage(title = stringResource(R.string.settings_system_check), onBack = onBack) {
        settingsSection(
            title = null,
            footer = R.string.settings_system_footer
        ) {
            SettingsStatusRow(
                label = stringResource(R.string.settings_check_service),
                description = stringResource(
                    if (setupStatus.serviceRunning) R.string.settings_check_service_ok
                    else R.string.settings_check_service_bad
                ),
                ok = setupStatus.serviceRunning,
                onClick = viewModel::openAppDetails
            )
            SettingsGroupDivider()
            SettingsStatusRow(
                label = stringResource(R.string.settings_check_battery),
                description = stringResource(
                    if (setupStatus.batteryUnrestricted) R.string.settings_check_battery_ok
                    else R.string.settings_check_battery_bad
                ),
                ok = setupStatus.batteryUnrestricted,
                onClick = viewModel::openBatterySettings
            )
            SettingsGroupDivider()
            SettingsStatusRow(
                label = stringResource(R.string.settings_check_notifications),
                description = stringResource(
                    if (setupStatus.notificationsEnabled) R.string.settings_check_notifications_ok
                    else R.string.settings_check_notifications_bad
                ),
                ok = setupStatus.notificationsEnabled,
                onClick = viewModel::openNotificationSettings
            )
        }

        if (setupStatus.isTeyesDevice) {
            settingsSection(
                title = R.string.settings_teyes_permissions,
                footer = R.string.settings_teyes_auto_run_hint
            ) {
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_open_teyes_settings),
                    description = stringResource(R.string.settings_teyes_checklist_hint),
                    onClick = viewModel::openTeyesSettings
                )
                if (killedInSleep) {
                    Text(
                        text = stringResource(
                            if (checklist.autoRunAwake) R.string.settings_teyes_killed_in_sleep
                            else R.string.settings_teyes_killed_in_sleep_auto_run
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                SettingsGroupDivider()
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_auto_start),
                    description = stringResource(R.string.settings_teyes_auto_start_hint),
                    checked = checklist.autoStart,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("auto_start", it) }
                )
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_auto_run_awake),
                    checked = checklist.autoRunAwake,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("auto_run_awake", it) }
                )
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_battery),
                    checked = checklist.batteryUnrestricted,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("battery", it) }
                )
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_lock),
                    checked = checklist.lockInRecents,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("lock", it) }
                )
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_boot),
                    checked = checklist.bootCompleted,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("boot", it) }
                )
            }

            settingsSection(
                title = R.string.settings_teyes_home_title,
                footer = R.string.settings_teyes_frontapp_hint
            ) {
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_open_frontapp),
                    onClick = viewModel::openFrontAppStore
                )
                SettingsGroupDivider()
                SettingsCheckRow(
                    label = stringResource(R.string.settings_teyes_frontapp_done),
                    checked = checklist.frontAppHome,
                    onCheckedChange = { viewModel.setTeyesChecklistItem("frontapp_home", it) }
                )
            }
        }
    }
}
