package com.tpms.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.PressureUnit
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

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pressureUnit = MutableStateFlow(PressureUnit.KPA)
    val pressureUnit = _pressureUnit.asStateFlow()

    private val _thresholds = MutableStateFlow(AlertThresholds())
    val thresholds = _thresholds.asStateFlow()

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

    companion object {
        private val KEY_PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        private val KEY_LOW_PRESSURE = floatPreferencesKey("low_pressure_kpa")
        private val KEY_HIGH_PRESSURE = floatPreferencesKey("high_pressure_kpa")
        private val KEY_HIGH_TEMP = floatPreferencesKey("high_temp_celsius")
    }
}
