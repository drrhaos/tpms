package com.tpms.app.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lastKnownDataStore: DataStore<Preferences> by preferencesDataStore(name = "tpms_last_known")

@Singleton
class LastKnownSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun save(sensors: Map<String, TireSensor>, savedAtMs: Long = System.currentTimeMillis()) {
        if (sensors.isEmpty()) return
        context.lastKnownDataStore.edit { prefs ->
            prefs[KEY_SAVED_AT_MS] = savedAtMs
            prefs[KEY_SENSORS_JSON] = encode(sensors)
        }
    }

    suspend fun load(): Map<String, TireSensor>? {
        val prefs = context.lastKnownDataStore.data.first()
        val json = prefs[KEY_SENSORS_JSON] ?: return null
        return decode(json).takeIf { it.isNotEmpty() }
    }

    suspend fun savedAtMs(): Long =
        context.lastKnownDataStore.data.first()[KEY_SAVED_AT_MS] ?: 0L

    private fun encode(sensors: Map<String, TireSensor>): String {
        val array = JSONArray()
        sensors.values.forEach { sensor ->
            array.put(
                JSONObject().apply {
                    put("id", sensor.id)
                    put("label", sensor.label)
                    put("pressureKpa", sensor.pressureKpa.toDouble())
                    put("temperatureCelsius", sensor.temperatureCelsius.toDouble())
                    put("batteryPercent", sensor.batteryPercent)
                    put("alertType", sensor.alertType?.name)
                    put("timestamp", sensor.timestamp)
                }
            )
        }
        return array.toString()
    }

    private fun decode(json: String): Map<String, TireSensor> = runCatching {
        val array = JSONArray(json)
        buildMap {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val alertName = obj.optString("alertType", "").takeIf { it.isNotBlank() }
                val sensor = TireSensor(
                    id = obj.getString("id"),
                    label = obj.optString("label", ""),
                    pressureKpa = obj.getDouble("pressureKpa").toFloat(),
                    temperatureCelsius = obj.getDouble("temperatureCelsius").toFloat(),
                    batteryPercent = obj.getInt("batteryPercent"),
                    alertType = alertName?.let { runCatching { AlertType.valueOf(it) }.getOrNull() },
                    timestamp = obj.getLong("timestamp")
                )
                put(sensor.id, sensor)
            }
        }
    }.getOrDefault(emptyMap())

    companion object {
        private val KEY_SAVED_AT_MS = longPreferencesKey("saved_at_ms")
        private val KEY_SENSORS_JSON = stringPreferencesKey("sensors_json")
    }
}
