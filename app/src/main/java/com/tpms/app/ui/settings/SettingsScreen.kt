package com.tpms.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tpms.app.R
import com.tpms.app.data.settings.AppLanguage
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.WidgetThemeMode
import com.tpms.app.startup.SetupStatus
import com.tpms.app.ui.localizedLabel

/**
 * What a driver may want to change: the recommended pressure, units, sensors, alerts and display.
 * Head unit permissions live in the system check, engineering knobs in the hidden service menu.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenSystemCheck: () -> Unit,
    onOpenServiceMenu: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val targetKpa by viewModel.targetPressureKpa.collectAsState()
    val wheelAssignment by viewModel.wheelAssignment.collectAsState()
    val criticalAlertsFullscreen by viewModel.criticalAlertsFullscreen.collectAsState()
    val floatingOverlayEnabled by viewModel.floatingOverlayEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current
    val setupStatus by viewModel.setupStatus.collectAsState()
    val serviceMenuUnlocked by viewModel.serviceMenuUnlocked.collectAsState()
    val message by viewModel.message.collectAsState()

    RefreshSetupStatusOnResume(viewModel)

    val problems = setupStatus.problemCount()

    SettingsPage(
        title = stringResource(R.string.title_settings),
        onBack = onBack,
        message = message,
        onMessageShown = viewModel::clearMessage
    ) {
        // Something to fix comes first; otherwise the check sits quietly near the end.
        if (problems > 0) systemSection(problems, onOpenSystemCheck)

        settingsSection(
            title = R.string.settings_section_tyres,
            footer = R.string.settings_target_footer
        ) {
            SettingsStepperRow(
                label = stringResource(R.string.settings_target_pressure),
                value = "${pressureUnit.formatFromKpa(targetKpa)} ${pressureUnit.localizedLabel()}",
                onDecrease = { viewModel.stepTargetPressure(up = false) },
                onIncrease = { viewModel.stepTargetPressure(up = true) }
            )
            SettingsGroupDivider()
            SettingsActionRow(
                label = stringResource(R.string.settings_calibrate),
                description = stringResource(R.string.settings_calibrate_hint),
                onClick = viewModel::acceptCurrentPressures
            )
            SettingsGroupDivider()
            SettingsSegmentedRow(
                label = stringResource(R.string.settings_pressure_unit),
                options = listOf(PressureUnit.BAR, PressureUnit.PSI, PressureUnit.KPA),
                selected = pressureUnit,
                optionLabel = { it.localizedLabel() },
                onSelect = viewModel::setPressureUnit
            )
        }

        settingsSection(title = null) {
            val placed = WheelLayout.ORDER.count { it in wheelAssignment }
            SettingsNavigationRow(
                label = stringResource(R.string.settings_sensors),
                value = stringResource(R.string.settings_sensors_value, placed, WheelLayout.ORDER.size),
                onClick = onOpenSensors
            )
        }

        settingsSection(title = R.string.settings_section_alerts) {
            SettingsNavigationRow(
                label = stringResource(R.string.settings_alert_sound_configure),
                description = stringResource(R.string.settings_alert_sound_configure_hint),
                onClick = viewModel::openAlertChannelSettings
            )
            SettingsGroupDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_critical_alerts_fullscreen),
                description = stringResource(R.string.settings_critical_alerts_fullscreen_hint),
                checked = criticalAlertsFullscreen,
                onCheckedChange = viewModel::setCriticalAlertsFullscreen
            )
        }

        settingsSection(title = R.string.settings_section_display) {
            SettingsSegmentedRow(
                label = stringResource(R.string.settings_appearance),
                options = listOf(WidgetThemeMode.AUTO, WidgetThemeMode.DARK, WidgetThemeMode.LIGHT),
                selected = themeMode,
                optionLabel = { it.appearanceLabel() },
                onSelect = viewModel::setThemeMode
            )
            SettingsGroupDivider()
            SettingsSegmentedRow(
                label = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = language,
                optionLabel = { it.label() },
                onSelect = { viewModel.setLanguage(it, context) }
            )
            SettingsGroupDivider()
            SettingsNavigationRow(
                label = stringResource(R.string.settings_widget_add_simple),
                description = stringResource(R.string.settings_section_widget_hint_simple),
                onClick = { viewModel.pinWidgetToHome() }
            )
            SettingsGroupDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_floating_overlay),
                description = stringResource(R.string.settings_floating_overlay_hint),
                checked = floatingOverlayEnabled,
                onCheckedChange = viewModel::setFloatingOverlayEnabled
            )
        }

        if (problems == 0) systemSection(problems, onOpenSystemCheck)

        settingsSection(
            title = R.string.settings_section_about,
            footer = R.string.settings_about_credits
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::onVersionTapped)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = viewModel.versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (serviceMenuUnlocked) {
                SettingsGroupDivider()
                SettingsNavigationRow(
                    label = stringResource(R.string.settings_service_menu),
                    onClick = onOpenServiceMenu
                )
            }
        }
    }
}

private fun LazyListScope.systemSection(problems: Int, onOpenSystemCheck: () -> Unit) {
    settingsSection(title = null) {
        SettingsStatusRow(
            label = stringResource(R.string.settings_system_check),
            description = if (problems == 0) {
                stringResource(R.string.settings_system_ok)
            } else {
                pluralStringResource(R.plurals.settings_system_problems, problems, problems)
            },
            ok = problems == 0,
            onClick = onOpenSystemCheck
        )
    }
}

/** What the system check flags; the main settings page shows the count. */
fun SetupStatus.problemCount(): Int = listOf(
    !serviceRunning,
    !batteryUnrestricted,
    !notificationsEnabled,
    isTeyesDevice && !checklistComplete
).count { it }

@Composable
private fun WidgetThemeMode.appearanceLabel(): String = when (this) {
    WidgetThemeMode.AUTO -> stringResource(R.string.settings_appearance_auto)
    WidgetThemeMode.DARK -> stringResource(R.string.settings_appearance_dark)
    WidgetThemeMode.LIGHT -> stringResource(R.string.settings_appearance_light)
}

/** Each language by its own name, so it can be found whatever the current one. */
@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.RUSSIAN -> "Русский"
}

/** Permissions can change in system settings while we are away: re-read them on every resume. */
@Composable
fun RefreshSetupStatusOnResume(viewModel: SettingsViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshRuntimeSetupStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
