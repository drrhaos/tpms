package com.tpms.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tpms.app.R
import kotlinx.coroutines.launch
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.WidgetThemeMode
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.theme.TpmsColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsUiMode by viewModel.settingsUiMode.collectAsState()
    val pressureUnit by viewModel.pressureUnit.collectAsState()
    val lowPressure by viewModel.lowPressure.collectAsState()
    val highPressure by viewModel.highPressure.collectAsState()
    val highTemp by viewModel.highTemp.collectAsState()
    val dongleProtocolMode by viewModel.dongleProtocolMode.collectAsState()
    val sensorTimeoutSec by viewModel.sensorTimeoutSec.collectAsState()
    val wheelMapping by viewModel.wheelMapping.collectAsState()
    val knownSensorIds by viewModel.knownSensorIds.collectAsState()
    val alertSoundEnabled by viewModel.alertSoundEnabled.collectAsState()
    val showSpareWheel by viewModel.showSpareWheel.collectAsState()
    val teyesChecklist by viewModel.teyesChecklist.collectAsState()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val widgetActive by viewModel.widgetActive.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val silentStartup by viewModel.silentStartup.collectAsState()
    val floatingOverlayEnabled by viewModel.floatingOverlayEnabled.collectAsState()
    val criticalAlertsFullscreen by viewModel.criticalAlertsFullscreen.collectAsState()
    val widgetThemeMode by viewModel.widgetThemeMode.collectAsState()
    val preferredUsbVidPid by viewModel.preferredUsbVidPid.collectAsState()
    val setupStatus by viewModel.setupStatus.collectAsState()
    val isTeyesDevice = viewModel.isTeyesDevice
    val staleFrameTimeoutSec by viewModel.staleFrameTimeoutSec.collectAsState()
    val minLiveWheelPressure by viewModel.minLiveWheelPressure.collectAsState()
    val importExportMessage by viewModel.importExportMessage.collectAsState()
    var importJson by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.refreshRuntimeSetupStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshRuntimeSetupStatus()
                Lifecycle.Event.ON_STOP -> viewModel.savePendingThresholds()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(importExportMessage) {
        importExportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportExportMessage()
        }
    }

    val wheelSlots = viewModel.wheelSlots()
    val advanced = settingsUiMode == SettingsUiMode.ADVANCED
    val sensorsAvailable = knownSensorIds.isNotEmpty()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = TpmsColors.outline,
        focusedContainerColor = TpmsColors.surfaceVariant.copy(alpha = 0.35f),
        unfocusedContainerColor = TpmsColors.surfaceVariant.copy(alpha = 0.2f),
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
    )
    val density = LocalDensity.current
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val savedMessage = stringResource(R.string.settings_saved)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.title_settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.saveSettings()
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val extraBottom = if (navBottomPx == 0) 16.dp else 0.dp
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = extraBottom),
                color = TpmsColors.surfaceElevated,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.saveSettings()
                            snackbarHostState.showSnackbar(savedMessage)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_save_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SetupWarnings(status = setupStatus)
            }

            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SettingsUiMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settingsUiMode == mode,
                                    onClick = { viewModel.setSettingsUiMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index,
                                        SettingsUiMode.entries.size
                                    ),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = TpmsColors.primaryDim,
                                        activeContentColor = TpmsColors.accent
                                    )
                                ) {
                                    Text(
                                        when (mode) {
                                            SettingsUiMode.USER ->
                                                stringResource(R.string.settings_mode_user)
                                            SettingsUiMode.ADVANCED ->
                                                stringResource(R.string.settings_mode_advanced)
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsInfoBanner(
                            text = if (advanced) {
                                stringResource(R.string.settings_mode_advanced_hint)
                            } else {
                                stringResource(R.string.settings_mode_user_hint)
                            }
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_general),
                    subtitle = stringResource(R.string.settings_section_general_hint)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_pressure_unit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PressureUnit.entries.forEachIndexed { index, unit ->
                                SegmentedButton(
                                    selected = pressureUnit == unit,
                                    onClick = { viewModel.setPressureUnit(unit) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index,
                                        PressureUnit.entries.size
                                    ),
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
                    SettingsGroupDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = pressureUnit.formatThresholdValue(lowPressure),
                                onValueChange = {
                                    it.toFloatOrNull()?.let { v -> viewModel.setLowPressure(v) }
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.settings_low_pressure_short,
                                            pressureUnit.localizedLabel()
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = pressureUnit.formatThresholdValue(highPressure),
                                onValueChange = {
                                    it.toFloatOrNull()?.let { v -> viewModel.setHighPressure(v) }
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.settings_high_pressure_short,
                                            pressureUnit.localizedLabel()
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        OutlinedTextField(
                            value = highTemp.toString(),
                            onValueChange = { it.toFloatOrNull()?.let { v -> viewModel.setHighTemp(v) } },
                            label = { Text(stringResource(R.string.settings_high_temp)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        if (advanced) {
                            OutlinedTextField(
                                value = sensorTimeoutSec.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v -> viewModel.setSensorTimeoutSec(v) }
                                },
                                label = { Text(stringResource(R.string.settings_sensor_timeout)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_wheels),
                    subtitle = stringResource(R.string.settings_section_wheels_hint)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    if (!sensorsAvailable) {
                        Text(
                            text = stringResource(R.string.settings_no_sensors),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                        SettingsGroupDivider()
                    }
                    wheelSlots.forEachIndexed { index, slot ->
                        if (index > 0) SettingsGroupDivider()
                        val selected = wheelMapping[slot].orEmpty()
                        SettingsValueRow(
                            label = slot,
                            value = selected.ifBlank { stringResource(R.string.settings_auto) },
                            onClick = { viewModel.cycleWheelMapping(slot, knownSensorIds) },
                            enabled = sensorsAvailable || selected.isNotBlank()
                        )
                    }
                    if (advanced) {
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_show_spare_wheel),
                            checked = showSpareWheel,
                            onCheckedChange = { viewModel.setShowSpareWheel(it) }
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_background),
                    subtitle = stringResource(R.string.settings_section_background_hint)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    SettingsNavigationRow(
                        label = if (serviceRunning) {
                            stringResource(R.string.settings_teyes_service_running)
                        } else {
                            stringResource(R.string.settings_teyes_service_stopped)
                        },
                        onClick = { viewModel.openAppDetails() }
                    )
                    SettingsGroupDivider()
                    SettingsNavigationRow(
                        label = if (batteryUnrestricted) {
                            stringResource(R.string.settings_runtime_battery_ok)
                        } else {
                            stringResource(R.string.settings_runtime_battery_restricted)
                        },
                        onClick = { viewModel.openBatterySettings() }
                    )
                    SettingsGroupDivider()
                    SettingsNavigationRow(
                        label = if (notificationsEnabled) {
                            stringResource(R.string.settings_runtime_notifications_ok)
                        } else {
                            stringResource(R.string.settings_runtime_notifications_denied)
                        },
                        onClick = { viewModel.openNotificationSettings() }
                    )
                    SettingsGroupDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_silent_startup),
                        checked = silentStartup,
                        onCheckedChange = { viewModel.setSilentStartup(it) }
                    )
                    SettingsGroupDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_floating_overlay),
                        checked = floatingOverlayEnabled,
                        onCheckedChange = { viewModel.setFloatingOverlayEnabled(it) }
                    )
                    SettingsGroupDivider()
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_critical_alerts_fullscreen),
                        checked = criticalAlertsFullscreen,
                        onCheckedChange = { viewModel.setCriticalAlertsFullscreen(it) }
                    )
                }
            }

            item {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_widget),
                    subtitle = stringResource(R.string.settings_section_widget_hint)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    SettingsNavigationRow(
                        label = stringResource(R.string.widget_pin_to_panel),
                        onClick = { viewModel.pinWidgetToHome() }
                    )
                    SettingsGroupDivider()
                    SettingsNavigationRow(
                        label = stringResource(R.string.widget_pin_compact),
                        onClick = { viewModel.pinCompactWidget() }
                    )
                    if (widgetActive) {
                        SettingsGroupDivider()
                        WidgetThemeModePicker(
                            selected = widgetThemeMode,
                            onSelect = { viewModel.setWidgetThemeMode(it) }
                        )
                    }
                }
            }

            if (isTeyesDevice) {
                item {
                    SettingsSectionHeader(
                        title = stringResource(R.string.settings_teyes_permissions),
                        subtitle = stringResource(R.string.settings_teyes_checklist_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsGroup {
                        SettingsNavigationRow(
                            label = stringResource(R.string.settings_open_teyes_settings),
                            onClick = { viewModel.openTeyesSettings() }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_auto_start),
                            checked = teyesChecklist.autoStart,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("auto_start", it) }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_battery),
                            checked = teyesChecklist.batteryUnrestricted,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("battery", it) }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_lock),
                            checked = teyesChecklist.lockInRecents,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("lock", it) }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_boot),
                            checked = teyesChecklist.bootCompleted,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("boot", it) }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_auto_run_awake),
                            checked = teyesChecklist.autoRunAwake,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("auto_run_awake", it) }
                        )
                        SettingsGroupDivider()
                        Text(
                            text = stringResource(R.string.settings_teyes_auto_run_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                item {
                    SettingsSectionHeader(
                        title = stringResource(R.string.settings_teyes_home_title),
                        subtitle = stringResource(R.string.settings_teyes_frontapp_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsGroup {
                        SettingsNavigationRow(
                            label = stringResource(R.string.settings_open_frontapp),
                            onClick = { viewModel.openFrontAppStore() }
                        )
                        SettingsGroupDivider()
                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_teyes_frontapp_done),
                            checked = teyesChecklist.frontAppHome,
                            onCheckedChange = { viewModel.setTeyesChecklistItem("frontapp_home", it) }
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_alerts))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_alert_sound),
                        checked = alertSoundEnabled,
                        onCheckedChange = { viewModel.setAlertSoundEnabled(it) }
                    )
                }
            }

            if (advanced) {
                item {
                    SettingsSectionHeader(
                        title = stringResource(R.string.settings_section_advanced),
                        subtitle = stringResource(R.string.settings_section_advanced_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsGroup {
                        SettingsNavigationRow(
                            label = stringResource(R.string.settings_usb_debug_title),
                            description = stringResource(R.string.settings_usb_debug_hint),
                            onClick = onNavigateToDebug
                        )
                        SettingsGroupDivider()
                        SettingsValueRow(
                            label = stringResource(R.string.settings_preferred_usb_dongle),
                            value = preferredUsbVidPid?.ifBlank { null }
                                ?: stringResource(R.string.settings_auto),
                            onClick = { viewModel.cyclePreferredUsbDevice() }
                        )
                        SettingsGroupDivider()
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.settings_dongle_protocol),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            DongleProtocolMode.entries.forEachIndexed { index, mode ->
                                if (index > 0) SettingsGroupDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setDongleProtocolMode(mode) }
                                        .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
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
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            SettingsInfoBanner(
                                text = stringResource(R.string.settings_protocol_hint_modes),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_stability),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = staleFrameTimeoutSec.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { v -> viewModel.setStaleFrameTimeoutSec(v) }
                                },
                                label = { Text(stringResource(R.string.settings_stale_frame_timeout)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = minLiveWheelPressure.toString(),
                                onValueChange = {
                                    it.toFloatOrNull()?.let { v -> viewModel.setMinLiveWheelPressure(v) }
                                },
                                label = { Text(stringResource(R.string.settings_min_live_wheel_pressure)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }

                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_import_export),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { viewModel.copySettingsToClipboard() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_export_copy))
                            }
                            OutlinedTextField(
                                value = importJson,
                                onValueChange = { importJson = it },
                                label = { Text(stringResource(R.string.settings_import_json)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors,
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
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }
}
