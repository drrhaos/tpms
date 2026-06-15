package com.tpms.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tpms.app.domain.ConnectionHealthPolicy
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.DongleProtocolMode
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.SettingsUiMode
import com.tpms.app.domain.model.WidgetThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tpms_settings")

data class TeyesChecklist(
    val autoStart: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val lockInRecents: Boolean = false,
    val bootCompleted: Boolean = false,
    val autoRunAwake: Boolean = false
)

data class AlertNotificationPrefs(
    val soundEnabled: Boolean = true
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pressureUnit = MutableStateFlow(PressureUnit.KPA)
    val pressureUnit = _pressureUnit.asStateFlow()

    private val _thresholds = MutableStateFlow(AlertThresholds())
    val thresholds = _thresholds.asStateFlow()

    private val _dongleProtocolMode = MutableStateFlow(DongleProtocolMode.AUTO)
    val dongleProtocolMode = _dongleProtocolMode.asStateFlow()

    private val _sensorTimeoutMs = MutableStateFlow(DEFAULT_SENSOR_TIMEOUT_MS)
    val sensorTimeoutMs = _sensorTimeoutMs.asStateFlow()

    private val _staleFrameTimeoutMs = MutableStateFlow(ConnectionHealthPolicy.DEFAULT_STALE_FRAME_MS)
    val staleFrameTimeoutMs = _staleFrameTimeoutMs.asStateFlow()

    private val _wheelMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val wheelMapping = _wheelMapping.asStateFlow()

    private val _showSpareWheel = MutableStateFlow(false)
    val showSpareWheel = _showSpareWheel.asStateFlow()

    private val _minLiveWheelPressureKpa = MutableStateFlow(SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA)
    val minLiveWheelPressureKpa = _minLiveWheelPressureKpa.asStateFlow()

    private val _teyesChecklist = MutableStateFlow(TeyesChecklist())
    val teyesChecklist = _teyesChecklist.asStateFlow()

    private val _alertNotificationPrefs = MutableStateFlow(AlertNotificationPrefs())
    val alertNotificationPrefs = _alertNotificationPrefs.asStateFlow()

    private val _settingsUiMode = MutableStateFlow(SettingsUiMode.USER)
    val settingsUiMode = _settingsUiMode.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete = _onboardingComplete.asStateFlow()

    private val _silentStartup = MutableStateFlow(false)
    val silentStartup = _silentStartup.asStateFlow()

    private val _floatingOverlayEnabled = MutableStateFlow(false)
    val floatingOverlayEnabled = _floatingOverlayEnabled.asStateFlow()

    private val _criticalAlertsFullscreen = MutableStateFlow(true)
    val criticalAlertsFullscreen = _criticalAlertsFullscreen.asStateFlow()

    private val _preferredUsbVidPid = MutableStateFlow<String?>(null)
    val preferredUsbVidPid = _preferredUsbVidPid.asStateFlow()

    private val _widgetThemeMode = MutableStateFlow(WidgetThemeMode.AUTO)
    val widgetThemeMode = _widgetThemeMode.asStateFlow()

    init {
        scope.launch {
            context.settingsDataStore.data.collect { prefs ->
                _pressureUnit.value = prefs[KEY_PRESSURE_UNIT]?.let { name ->
                    PressureUnit.entries.find { it.name == name }
                } ?: PressureUnit.KPA

                _thresholds.value = AlertThresholds(
                    lowPressureKpa = prefs[KEY_LOW_PRESSURE] ?: AlertThresholds().lowPressureKpa,
                    highPressureKpa = prefs[KEY_HIGH_PRESSURE] ?: AlertThresholds().highPressureKpa,
                    highTempCelsius = prefs[KEY_HIGH_TEMP] ?: AlertThresholds().highTempCelsius
                )

                _dongleProtocolMode.value = prefs[KEY_DONGLE_PROTOCOL]?.let { name ->
                    DongleProtocolMode.entries.find { it.name == name }
                } ?: DongleProtocolMode.AUTO

                _sensorTimeoutMs.value = prefs[KEY_SENSOR_TIMEOUT_MS] ?: DEFAULT_SENSOR_TIMEOUT_MS
                _staleFrameTimeoutMs.value =
                    prefs[KEY_STALE_FRAME_TIMEOUT_MS] ?: ConnectionHealthPolicy.DEFAULT_STALE_FRAME_MS

                val showSpare = prefs[KEY_SHOW_SPARE_WHEEL] ?: false
                _showSpareWheel.value = showSpare

                _wheelMapping.value = WheelLayout.allSlots(showSpare).associateWith { slot ->
                    prefs[wheelMappingKey(slot)] ?: ""
                }

                _minLiveWheelPressureKpa.value =
                    prefs[KEY_MIN_LIVE_WHEEL_PRESSURE] ?: SensorValidatorDefaults.MIN_LIVE_WHEEL_PRESSURE_KPA

                _teyesChecklist.value = TeyesChecklist(
                    autoStart = prefs[KEY_TEYES_AUTO_START] ?: false,
                    batteryUnrestricted = prefs[KEY_TEYES_BATTERY] ?: false,
                    lockInRecents = prefs[KEY_TEYES_LOCK] ?: false,
                    bootCompleted = prefs[KEY_TEYES_BOOT] ?: false,
                    autoRunAwake = prefs[KEY_TEYES_AUTO_RUN_AWAKE] ?: false
                )

                _alertNotificationPrefs.value = AlertNotificationPrefs(
                    soundEnabled = prefs[KEY_ALERT_SOUND] ?: true
                )

                _settingsUiMode.value = SettingsUiMode.fromName(prefs[KEY_SETTINGS_UI_MODE])
                _onboardingComplete.value = prefs[KEY_ONBOARDING_COMPLETE] ?: false
                _silentStartup.value = prefs[KEY_SILENT_STARTUP] ?: false
                _floatingOverlayEnabled.value = prefs[KEY_FLOATING_OVERLAY] ?: false
                _criticalAlertsFullscreen.value = prefs[KEY_CRITICAL_ALERTS_FULLSCREEN] ?: true
                _preferredUsbVidPid.value = prefs[KEY_PREFERRED_USB_VID_PID]?.takeIf { it.isNotBlank() }
                _widgetThemeMode.value = WidgetThemeMode.fromName(prefs[KEY_WIDGET_THEME_MODE])
            }
        }
    }

    suspend fun awaitLoaded() {
        context.settingsDataStore.data.first()
    }

    suspend fun setPressureUnit(unit: PressureUnit) {
        context.settingsDataStore.edit { it[KEY_PRESSURE_UNIT] = unit.name }
    }

    suspend fun setThresholds(thresholds: AlertThresholds) {
        context.settingsDataStore.edit {
            it[KEY_LOW_PRESSURE] = thresholds.lowPressureKpa
            it[KEY_HIGH_PRESSURE] = thresholds.highPressureKpa
            it[KEY_HIGH_TEMP] = thresholds.highTempCelsius
        }
    }

    suspend fun setDongleProtocolMode(mode: DongleProtocolMode) {
        context.settingsDataStore.edit { it[KEY_DONGLE_PROTOCOL] = mode.name }
    }

    suspend fun setSensorTimeoutMs(timeoutMs: Long) {
        context.settingsDataStore.edit { it[KEY_SENSOR_TIMEOUT_MS] = timeoutMs }
    }

    suspend fun setStaleFrameTimeoutMs(timeoutMs: Long) {
        context.settingsDataStore.edit { it[KEY_STALE_FRAME_TIMEOUT_MS] = timeoutMs }
    }

    suspend fun setShowSpareWheel(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_SPARE_WHEEL] = enabled }
    }

    suspend fun setMinLiveWheelPressureKpa(value: Float) {
        context.settingsDataStore.edit { it[KEY_MIN_LIVE_WHEEL_PRESSURE] = value }
    }

    suspend fun setWheelMapping(slot: String, sensorId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[wheelMappingKey(slot)] = sensorId
        }
    }

    suspend fun setTeyesChecklistItem(key: String, checked: Boolean) {
        val prefKey = when (key) {
            "auto_start" -> KEY_TEYES_AUTO_START
            "battery" -> KEY_TEYES_BATTERY
            "lock" -> KEY_TEYES_LOCK
            "boot" -> KEY_TEYES_BOOT
            "auto_run_awake" -> KEY_TEYES_AUTO_RUN_AWAKE
            else -> return
        }
        context.settingsDataStore.edit { it[prefKey] = checked }
    }

    suspend fun setSettingsUiMode(mode: SettingsUiMode) {
        context.settingsDataStore.edit { it[KEY_SETTINGS_UI_MODE] = mode.name }
    }

    suspend fun setAlertNotificationPrefs(prefs: AlertNotificationPrefs) {
        context.settingsDataStore.edit {
            it[KEY_ALERT_SOUND] = prefs.soundEnabled
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setSilentStartup(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SILENT_STARTUP] = enabled }
    }

    suspend fun setFloatingOverlayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_FLOATING_OVERLAY] = enabled }
    }

    suspend fun setCriticalAlertsFullscreen(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_CRITICAL_ALERTS_FULLSCREEN] = enabled }
    }

    suspend fun setPreferredUsbVidPid(vidPid: String?) {
        context.settingsDataStore.edit {
            if (vidPid.isNullOrBlank()) {
                it.remove(KEY_PREFERRED_USB_VID_PID)
            } else {
                it[KEY_PREFERRED_USB_VID_PID] = vidPid
            }
        }
    }

    suspend fun setWidgetThemeMode(mode: WidgetThemeMode) {
        context.settingsDataStore.edit { it[KEY_WIDGET_THEME_MODE] = mode.name }
    }

    suspend fun applyImported(imported: ImportedSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PRESSURE_UNIT] = imported.pressureUnit.name
            prefs[KEY_LOW_PRESSURE] = imported.thresholds.lowPressureKpa
            prefs[KEY_HIGH_PRESSURE] = imported.thresholds.highPressureKpa
            prefs[KEY_HIGH_TEMP] = imported.thresholds.highTempCelsius
            prefs[KEY_DONGLE_PROTOCOL] = imported.dongleProtocolMode.name
            prefs[KEY_SENSOR_TIMEOUT_MS] = imported.sensorTimeoutMs
            prefs[KEY_STALE_FRAME_TIMEOUT_MS] = imported.staleFrameTimeoutMs
            prefs[KEY_SHOW_SPARE_WHEEL] = imported.showSpareWheel
            prefs[KEY_MIN_LIVE_WHEEL_PRESSURE] = imported.minLiveWheelPressureKpa
            prefs[KEY_ALERT_SOUND] = imported.alertNotificationPrefs.soundEnabled
            prefs[KEY_TEYES_AUTO_START] = imported.teyesChecklist.autoStart
            prefs[KEY_TEYES_BATTERY] = imported.teyesChecklist.batteryUnrestricted
            prefs[KEY_TEYES_LOCK] = imported.teyesChecklist.lockInRecents
            prefs[KEY_TEYES_BOOT] = imported.teyesChecklist.bootCompleted
            prefs[KEY_TEYES_AUTO_RUN_AWAKE] = imported.teyesChecklist.autoRunAwake
            prefs[KEY_ONBOARDING_COMPLETE] = imported.onboardingComplete
            prefs[KEY_SILENT_STARTUP] = imported.silentStartup
            prefs[KEY_FLOATING_OVERLAY] = imported.floatingOverlayEnabled
            prefs[KEY_CRITICAL_ALERTS_FULLSCREEN] = imported.criticalAlertsFullscreen
            imported.preferredUsbVidPid?.let { prefs[KEY_PREFERRED_USB_VID_PID] = it }
                ?: prefs.remove(KEY_PREFERRED_USB_VID_PID)
            prefs[KEY_WIDGET_THEME_MODE] = imported.widgetThemeMode.name
            WheelLayout.allSlots(imported.showSpareWheel).forEach { slot ->
                prefs[wheelMappingKey(slot)] = imported.wheelMapping[slot].orEmpty()
            }
        }
    }

    fun isTeyesChecklistComplete(): Boolean {
        val checklist = _teyesChecklist.value
        return checklist.autoStart &&
            checklist.batteryUnrestricted &&
            checklist.lockInRecents &&
            checklist.bootCompleted &&
            checklist.autoRunAwake
    }

    companion object {
        const val DEFAULT_SENSOR_TIMEOUT_MS = 60_000L

        private val KEY_SETTINGS_UI_MODE = stringPreferencesKey("settings_ui_mode")

        private val KEY_PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        private val KEY_LOW_PRESSURE = floatPreferencesKey("low_pressure_kpa")
        private val KEY_HIGH_PRESSURE = floatPreferencesKey("high_pressure_kpa")
        private val KEY_HIGH_TEMP = floatPreferencesKey("high_temp_celsius")
        private val KEY_DONGLE_PROTOCOL = stringPreferencesKey("dongle_protocol")
        private val KEY_SENSOR_TIMEOUT_MS = longPreferencesKey("sensor_timeout_ms")
        private val KEY_STALE_FRAME_TIMEOUT_MS = longPreferencesKey("stale_frame_timeout_ms")
        private val KEY_SHOW_SPARE_WHEEL = booleanPreferencesKey("show_spare_wheel")
        private val KEY_MIN_LIVE_WHEEL_PRESSURE = floatPreferencesKey("min_live_wheel_pressure_kpa")
        private val KEY_TEYES_AUTO_START = booleanPreferencesKey("teyes_auto_start")
        private val KEY_TEYES_BATTERY = booleanPreferencesKey("teyes_battery")
        private val KEY_TEYES_LOCK = booleanPreferencesKey("teyes_lock")
        private val KEY_TEYES_BOOT = booleanPreferencesKey("teyes_boot")
        private val KEY_TEYES_AUTO_RUN_AWAKE = booleanPreferencesKey("teyes_auto_run_awake")
        private val KEY_ALERT_SOUND = booleanPreferencesKey("alert_sound")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_SILENT_STARTUP = booleanPreferencesKey("silent_startup")
        private val KEY_FLOATING_OVERLAY = booleanPreferencesKey("floating_overlay")
        private val KEY_CRITICAL_ALERTS_FULLSCREEN = booleanPreferencesKey("critical_alerts_fullscreen")
        private val KEY_PREFERRED_USB_VID_PID = stringPreferencesKey("preferred_usb_vid_pid")
        private val KEY_WIDGET_THEME_MODE = stringPreferencesKey("widget_theme_mode")

        private fun wheelMappingKey(slot: String) = stringPreferencesKey("wheel_map_$slot")
    }
}
