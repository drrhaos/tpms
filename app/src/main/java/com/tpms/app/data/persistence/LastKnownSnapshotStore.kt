package com.tpms.app.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.SensorFlags
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
    /**
     * Saves [sensors] with the [protocol] that produced them (drives per-wheel staleness after a
     * restart). A null [protocol] keeps the previously stored one.
     */
    suspend fun save(
        sensors: Map<String, TireSensor>,
        protocol: DongleProtocol?,
        savedAtMs: Long = System.currentTimeMillis()
    ) {
        if (sensors.isEmpty()) return
        context.lastKnownDataStore.edit { prefs ->
            prefs[KEY_SAVED_AT_MS] = savedAtMs
            prefs[KEY_SENSORS_JSON] = encode(sensors)
            if (protocol != null) prefs[KEY_PROTOCOL] = protocol.name
        }
    }

    /** Forgets the snapshot (no wheel left after a settings change); the protocol is kept. */
    suspend fun clear() {
        context.lastKnownDataStore.edit { prefs ->
            prefs.remove(KEY_SENSORS_JSON)
            prefs.remove(KEY_SAVED_AT_MS)
        }
    }

    suspend fun load(): Map<String, TireSensor>? {
        val prefs = context.lastKnownDataStore.data.first()
        val json = prefs[KEY_SENSORS_JSON] ?: return null
        return decode(json).takeIf { it.isNotEmpty() }
    }

    suspend fun savedAtMs(): Long =
        context.lastKnownDataStore.data.first()[KEY_SAVED_AT_MS] ?: 0L

    /** Protocol of the session that produced the snapshot; null for snapshots saved before it was stored. */
    suspend fun protocol(): DongleProtocol? =
        DongleProtocol.fromNameOrNull(context.lastKnownDataStore.data.first()[KEY_PROTOCOL])

    private fun encode(sensors: Map<String, TireSensor>): String {
        val array = JSONArray()
        sensors.values.forEach { sensor ->
            array.put(
                JSONObject().apply {
                    put("id", sensor.id)
                    put("label", sensor.label)
                    put("pressureKpa", sensor.pressureKpa.toDouble())
                    put("temperatureCelsius", sensor.temperatureCelsius.toDouble())
                    sensor.batteryPercent?.let { put("batteryPercent", it) }
                    put("noSignal", sensor.flags.noSignal)
                    put("leak", sensor.flags.leak)
                    put("lowBattery", sensor.flags.lowBattery)
                    put("alertType", sensor.alertType?.name)
                    put("timestamp", sensor.timestamp)
                    put("pressureOffsetKpa", sensor.pressureOffsetKpa.toDouble())
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
                    batteryPercent = if (obj.isNull("batteryPercent")) null else obj.getInt("batteryPercent"),
                    alertType = alertName?.let { runCatching { AlertType.valueOf(it) }.getOrNull() },
                    timestamp = obj.getLong("timestamp"),
                    flags = SensorFlags(
                        noSignal = obj.optBoolean("noSignal"),
                        leak = obj.optBoolean("leak"),
                        lowBattery = obj.optBoolean("lowBattery")
                    ),
                    pressureOffsetKpa = obj.optDouble("pressureOffsetKpa", 0.0).toFloat()
                )
                put(sensor.id, sensor)
            }
        }
    }.getOrDefault(emptyMap())

    companion object {
        private val KEY_SAVED_AT_MS = longPreferencesKey("saved_at_ms")
        private val KEY_SENSORS_JSON = stringPreferencesKey("sensors_json")
        private val KEY_PROTOCOL = stringPreferencesKey("protocol")
    }
}
