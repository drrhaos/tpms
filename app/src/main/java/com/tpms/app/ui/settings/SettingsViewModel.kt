package com.tpms.app.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.AppLanguage
import com.tpms.app.data.settings.AppLocale
import com.tpms.app.data.settings.SettingsExporter
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.settings.TeyesChecklist
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.data.usb.UsbDebugLog
import com.tpms.app.data.usb.UsbDeviceInfo
import com.tpms.app.domain.PressureTarget
import com.tpms.app.domain.WheelAssignment
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.WidgetThemeMode
import com.tpms.app.service.FloatingOverlayController
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.startup.HeadUnitSupport
import com.tpms.app.startup.SetupStatus
import com.tpms.app.startup.SetupStatusProvider
import com.tpms.app.ui.wheelPositionLabel
import com.tpms.app.ui.widget.TpmsWidgetHelper
import com.tpms.app.ui.widget.WidgetPinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared by the settings screens (main, sensors, system check, service menu). Every change is
 * written at once, as in system settings; there is no "Save" step.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val repository: TpmsRepository,
    private val setupStatusProvider: SetupStatusProvider,
    private val headUnitSupport: HeadUnitSupport,
    private val usbDebugLog: UsbDebugLog,
    private val usbConnection: UsbConnection,
    private val floatingOverlayController: FloatingOverlayController,
    heartbeatStore: ServiceHeartbeatStore
) : ViewModel() {

    val isTeyesDevice: Boolean get() = headUnitSupport.isTeyesHeadUnit()

    val pressureUnit: StateFlow<PressureUnit> = settingsStore.pressureUnit

    /** Optimistic copy, so quick stepper taps build on each other before the store echoes back. */
    private val _targetPressureKpa = MutableStateFlow(settingsStore.targetPressureKpa.value)
    val targetPressureKpa: StateFlow<Float> = _targetPressureKpa.asStateFlow()

    private val _highTemp = MutableStateFlow(settingsStore.thresholds.value.highTempCelsius)
    val highTemp: StateFlow<Float> = _highTemp.asStateFlow()

    val dongleProtocolMode: StateFlow<DongleProtocolMode> = settingsStore.dongleProtocolMode

    val staleFrameTimeoutSec: StateFlow<Int> = settingsStore.staleFrameTimeoutMs
        .map { (it / 1000).toInt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (settingsStore.staleFrameTimeoutMs.value / 1000).toInt())

    val showSpareWheel: StateFlow<Boolean> = settingsStore.showSpareWheel
    val minLiveWheelPressure: StateFlow<Float> = settingsStore.minLiveWheelPressureKpa
    val wheelMapping: StateFlow<Map<String, String>> = settingsStore.wheelMapping
    val teyesChecklist: StateFlow<TeyesChecklist> = settingsStore.teyesChecklist
    val silentStartup: StateFlow<Boolean> = settingsStore.silentStartup
    val floatingOverlayEnabled: StateFlow<Boolean> = settingsStore.floatingOverlayEnabled
    val criticalAlertsFullscreen: StateFlow<Boolean> = settingsStore.criticalAlertsFullscreen
    val themeMode: StateFlow<WidgetThemeMode> = settingsStore.widgetThemeMode

    private val _language = MutableStateFlow(AppLocale.current(context))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()
    val diagnosticLogEnabled: StateFlow<Boolean> = settingsStore.diagnosticLogEnabled
    val debugToolsEnabled: StateFlow<Boolean> = settingsStore.debugToolsEnabled
    val preferredUsbVidPid: StateFlow<String?> = settingsStore.preferredUsbVidPid

    /** The service menu is unlocked (stored as [SettingsUiMode.ADVANCED]). */
    val serviceMenuUnlocked: StateFlow<Boolean> = settingsStore.settingsUiMode
        .map { it == SettingsUiMode.ADVANCED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settingsUiMode.value == SettingsUiMode.ADVANCED)

    /** One-shot text for the snackbar (calibration, import / export, service menu unlock). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** The service was killed during the head unit's sleep and no sleep has been survived since. */
    val killedInSleep: StateFlow<Boolean> = heartbeatStore.killedInSleepAtMs
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _usbDongleOptions = MutableStateFlow<List<String>>(emptyList())

    private val _setupStatus = MutableStateFlow(setupStatusProvider.current(context))
    val setupStatus: StateFlow<SetupStatus> = _setupStatus.asStateFlow()

    /**
     * Sensors offered by the wheel mapping: every seen sensor (on a wheel or not) with its last
     * reading, sorted by id, except placeholder readings of an empty AA55 slot, which are never
     * admitted to a wheel (wayfinder 11).
     */
    val mappableSensors: StateFlow<Map<String, TireSensor>> = repository.seenSensors
        .combine(repository.aa55EmptySlots) { seen, emptySlots ->
            seen.values
                .filter { it.label !in emptySlots }
                .sortedBy { it.id }
                .associateBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Slot label → sensor currently placed there (what "Auto" resolves to for a blank mapping): the
     * repository's published wheels, not a local re-derivation.
     */
    val wheelAssignment: StateFlow<Map<String, TireSensor>> = repository.wheels

    private var versionTaps = 0

    init {
        viewModelScope.launch {
            settingsStore.awaitLoaded()
            _targetPressureKpa.value = settingsStore.targetPressureKpa.value
            _highTemp.value = settingsStore.thresholds.value.highTempCelsius
        }
        viewModelScope.launch {
            settingsStore.targetPressureKpa.collect { _targetPressureKpa.value = it }
        }
        viewModelScope.launch {
            settingsStore.thresholds.collect { _highTemp.value = it.highTempCelsius }
        }
        refreshRuntimeSetupStatus()
    }

    fun refreshRuntimeSetupStatus() {
        _setupStatus.value = setupStatusProvider.current(context)
        refreshUsbDongleOptions()
    }

    private fun refreshUsbDongleOptions() {
        val auto = listOf("")
        val devices = usbConnection.listAllDevices()
            .map { UsbDeviceInfo.vidPid(it) }
            .distinct()
        _usbDongleOptions.value = auto + devices
    }

    fun clearMessage() {
        _message.value = null
    }

    // --- Tyres ---

    fun setPressureUnit(unit: PressureUnit) {
        viewModelScope.launch { settingsStore.setPressureUnit(unit) }
    }

    fun stepTargetPressure(up: Boolean) {
        val next = PressureTarget.stepped(_targetPressureKpa.value, pressureUnit.value, up)
        _targetPressureKpa.value = next
        viewModelScope.launch { settingsStore.setTargetPressureKpa(next) }
    }

    /** "Accept current pressures as the norm" (BMW / Mercedes reset): only from live data. */
    fun acceptCurrentPressures() {
        val live = repository.connectionSnapshot().hasLiveSession
        val result = if (live) PressureTarget.calibrate(repository.wheels.value) else PressureTarget.Calibration.MissingWheel
        _message.value = when (result) {
            is PressureTarget.Calibration.Accepted -> {
                _targetPressureKpa.value = result.targetKpa
                viewModelScope.launch { settingsStore.setTargetPressureKpa(result.targetKpa) }
                val unit = pressureUnit.value
                context.getString(
                    R.string.settings_calibrate_done,
                    unit.formatFromKpa(result.targetKpa),
                    context.getString(unitLabelRes(unit))
                )
            }
            PressureTarget.Calibration.MissingWheel -> context.getString(R.string.settings_calibrate_missing)
            PressureTarget.Calibration.TooLow -> context.getString(R.string.settings_calibrate_too_low)
            PressureTarget.Calibration.Uneven -> context.getString(R.string.settings_calibrate_uneven)
        }
    }

    private fun unitLabelRes(unit: PressureUnit): Int = when (unit) {
        PressureUnit.PSI -> R.string.unit_psi
        PressureUnit.KPA -> R.string.unit_kpa
        PressureUnit.BAR -> R.string.unit_bar
    }

    // --- Sensors ---

    fun wheelSlots(): List<String> = WheelLayout.allSlots(showSpareWheel.value)

    fun setWheelMapping(slot: String, sensorId: String) {
        viewModelScope.launch { settingsStore.setWheelMapping(slot, sensorId) }
    }

    /** Tyre rotation: the sensors of wheels [a] and [b] trade places; a second tap swaps them back. */
    fun swapWheels(a: String, b: String) {
        val entries = WheelAssignment.swap(mappableSensors.value, wheelMapping.value, wheelAssignment.value, a, b)
        viewModelScope.launch { settingsStore.setWheelMappings(entries) }
        _message.value = context.getString(
            R.string.settings_wheels_swapped,
            wheelPositionLabel(context, a),
            wheelPositionLabel(context, b)
        )
    }

    /** Cross rotation (FL ↔ RR, FR ↔ RL) in one write; a second tap swaps back. */
    fun swapWheelsCross() {
        val entries = WheelAssignment.swapCross(mappableSensors.value, wheelMapping.value, wheelAssignment.value)
        viewModelScope.launch { settingsStore.setWheelMappings(entries) }
        _message.value = context.getString(R.string.settings_wheels_swapped_cross)
    }

    fun setShowSpareWheel(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowSpareWheel(enabled) }
    }

    // --- Alerts and display ---

    fun openAlertChannelSettings() = TeyesPermissionHelper.openAlertChannelSettings(context)

    fun setCriticalAlertsFullscreen(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setCriticalAlertsFullscreen(enabled) }
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setFloatingOverlayEnabled(enabled)
            if (!enabled) {
                floatingOverlayController.hide(context)
            } else if (!floatingOverlayController.canDrawOverlays(context)) {
                TeyesPermissionHelper.openOverlaySettings(context)
            } else {
                TpmsMonitorService.wake(context)
            }
        }
    }

    /** App and widget appearance (day / night / auto). */
    fun setThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch {
            settingsStore.setWidgetThemeMode(mode)
            TpmsMonitorService.wake(context)
        }
    }

    /**
     * Android 13+ re-localizes the whole process and recreates the activity itself; below that
     * the channels, the activity and (via a service wake) the widgets are refreshed here.
     */
    fun setLanguage(language: AppLanguage, activityContext: Context) {
        if (language == _language.value) return
        _language.value = language
        AppLocale.set(context, language)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            (context as? TpmsApplication)?.refreshNotificationChannels()
            activityContext.findActivity()?.recreate()
        }
        TpmsMonitorService.wake(context)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun pinWidgetToHome(): WidgetPinResult {
        if (isTeyesDevice) {
            TeyesPermissionHelper.openFrontApp(context)
            return WidgetPinResult.NOT_SUPPORTED
        }
        val result = TpmsWidgetHelper.requestPinPanel(context)
        TpmsWidgetHelper.showPinResultToast(context, result, isTeyesDevice)
        refreshRuntimeSetupStatus()
        return result
    }

    // --- System check ---

    fun openAppDetails() = TeyesPermissionHelper.openAppDetails(context)
    fun openBatterySettings() = TeyesPermissionHelper.openBatteryOptimization(context)
    fun openNotificationSettings() = TeyesPermissionHelper.openNotificationSettings(context)
    fun openTeyesSettings() = TeyesPermissionHelper.openTeyesSettings(context)
    fun openFrontAppStore() = TeyesPermissionHelper.openFrontApp(context)

    fun setTeyesChecklistItem(key: String, checked: Boolean) {
        viewModelScope.launch {
            settingsStore.setTeyesChecklistItem(key, checked)
            refreshRuntimeSetupStatus()
        }
    }

    // --- About / service menu ---

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** Seven taps on the version unlock the service menu, as on Android's build number. */
    fun onVersionTapped() {
        if (serviceMenuUnlocked.value) return
        versionTaps++
        if (versionTaps >= SERVICE_UNLOCK_TAPS) {
            versionTaps = 0
            viewModelScope.launch { settingsStore.setSettingsUiMode(SettingsUiMode.ADVANCED) }
            _message.value = context.getString(R.string.settings_service_unlocked)
        }
    }

    fun lockServiceMenu() {
        viewModelScope.launch {
            settingsStore.setSettingsUiMode(SettingsUiMode.USER)
            if (settingsStore.debugToolsEnabled.value) settingsStore.setDebugToolsEnabled(false)
        }
    }

    fun stepHighTemp(up: Boolean) {
        val next = (_highTemp.value + if (up) HIGH_TEMP_STEP else -HIGH_TEMP_STEP).coerceIn(HIGH_TEMP_MIN, HIGH_TEMP_MAX)
        _highTemp.value = next
        viewModelScope.launch { settingsStore.setHighTempCelsius(next) }
    }

    fun setDongleProtocolMode(mode: DongleProtocolMode) {
        viewModelScope.launch { settingsStore.setDongleProtocolMode(mode) }
    }

    fun stepStaleFrameTimeout(up: Boolean) {
        val next = (staleFrameTimeoutSec.value + if (up) 10 else -10).coerceIn(30, 300)
        viewModelScope.launch { settingsStore.setStaleFrameTimeoutMs(next * 1000L) }
    }

    fun stepMinLiveWheelPressure(up: Boolean) {
        val next = (minLiveWheelPressure.value + if (up) 10f else -10f).coerceIn(0f, 500f)
        viewModelScope.launch { settingsStore.setMinLiveWheelPressureKpa(next) }
    }

    fun cyclePreferredUsbDevice() {
        refreshUsbDongleOptions()
        val options = _usbDongleOptions.value
        if (options.isEmpty()) return
        val current = preferredUsbVidPid.value.orEmpty()
        val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
        viewModelScope.launch {
            settingsStore.setPreferredUsbVidPid(next.takeIf { it.isNotBlank() })
            TpmsMonitorService.reconnectUsb(context)
        }
    }

    fun setSilentStartup(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSilentStartup(enabled) }
    }

    fun setDiagnosticLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setDiagnosticLogEnabled(enabled)
            if (!enabled) usbDebugLog.clear()
        }
    }

    fun setDebugToolsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDebugToolsEnabled(enabled) }
    }

    fun exportSettingsJson(): String = SettingsExporter.export(
        pressureUnit = pressureUnit.value,
        targetPressureKpa = _targetPressureKpa.value,
        highTempCelsius = _highTemp.value,
        dongleProtocolMode = dongleProtocolMode.value,
        staleFrameTimeoutMs = settingsStore.staleFrameTimeoutMs.value,
        wheelMapping = wheelMapping.value,
        showSpareWheel = showSpareWheel.value,
        minLiveWheelPressureKpa = minLiveWheelPressure.value,
        teyesChecklist = teyesChecklist.value,
        onboardingComplete = settingsStore.onboardingComplete.value,
        silentStartup = silentStartup.value,
        floatingOverlayEnabled = floatingOverlayEnabled.value,
        criticalAlertsFullscreen = criticalAlertsFullscreen.value,
        preferredUsbVidPid = preferredUsbVidPid.value,
        widgetThemeMode = themeMode.value,
        diagnosticLogEnabled = diagnosticLogEnabled.value,
        debugToolsEnabled = debugToolsEnabled.value
    )

    fun copySettingsToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tpms_settings", exportSettingsJson()))
        _message.value = context.getString(R.string.settings_export_copied)
    }

    fun importSettingsJson(json: String) {
        viewModelScope.launch {
            runCatching {
                settingsStore.applyImported(SettingsExporter.import(json.trim()))
                _message.value = context.getString(R.string.settings_import_ok)
            }.onFailure { error ->
                _message.value = context.getString(R.string.settings_import_failed, error.message ?: "error")
            }
        }
    }

    private companion object {
        const val SERVICE_UNLOCK_TAPS = 7
        const val HIGH_TEMP_STEP = 5f
        const val HIGH_TEMP_MIN = 60f
        const val HIGH_TEMP_MAX = 120f
    }
}
