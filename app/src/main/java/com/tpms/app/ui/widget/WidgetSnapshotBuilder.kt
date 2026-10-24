package com.tpms.app.ui.widget

import android.content.Context
import com.tpms.app.data.persistence.LastKnownSnapshotStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSnapshotBuilder @Inject constructor(
    private val repository: TpmsRepository,
    private val settingsStore: SettingsStore,
    private val lastKnownSnapshotStore: LastKnownSnapshotStore
) {
    suspend fun build(context: Context, preferPersisted: Boolean = false): WidgetSnapshot {
        settingsStore.awaitLoaded()
        val persisted = if (preferPersisted) lastKnownSnapshotStore.load().orEmpty() else emptyMap()
        val persistedAtMs = if (preferPersisted) lastKnownSnapshotStore.savedAtMs() else 0L
        val persistedProtocol = if (preferPersisted) lastKnownSnapshotStore.protocol() else null
        return buildSnapshot(context, persisted, persistedAtMs, persistedProtocol)
    }

    fun buildNow(context: Context): WidgetSnapshot =
        buildSnapshot(context, persisted = emptyMap(), persistedAtMs = 0L, persistedProtocol = null)

    private fun buildSnapshot(
        context: Context,
        persisted: Map<String, TireSensor>,
        persistedAtMs: Long,
        persistedProtocol: DongleProtocol?
    ): WidgetSnapshot {
        val liveSensors = repository.sensors.value
        val sensors = liveSensors.ifEmpty { persisted }
        val state = when {
            sensors.isNotEmpty() && repository.state.value is TpmsState.Disconnected ->
                TpmsState.Connected(sensors.values.toList(), System.currentTimeMillis())
            else -> repository.state.value
        }
        // Connection-based (wayfinder 10): minutes since the last valid frame, not newest sensor age.
        val dataAgeSec = repository.dataAgeSec().takeIf { liveSensors.isNotEmpty() }
            ?: persistedAgeSec(persistedAtMs)
        val dataStale = when {
            liveSensors.isNotEmpty() -> repository.isDataStale()
            sensors.isNotEmpty() -> true
            else -> false
        }
        return WidgetSnapshot.from(
            context = context,
            state = state,
            sensors = sensors,
            unit = settingsStore.pressureUnit.value,
            wheelMapping = settingsStore.wheelMapping.value,
            showSpareWheel = settingsStore.showSpareWheel.value,
            dataAgeSec = dataAgeSec,
            dataStale = dataStale,
            useLightTheme = WidgetSnapshot.resolveLightTheme(
                context,
                settingsStore.widgetThemeMode.value
            ),
            // Before the repository restored the snapshot, its protocol comes from the store.
            wheelProtocol = repository.wheelFreshnessProtocol() ?: persistedProtocol
        )
    }

    private fun persistedAgeSec(savedAtMs: Long): Long? {
        if (savedAtMs <= 0L) return null
        return ((System.currentTimeMillis() - savedAtMs) / 1000).coerceAtLeast(1)
    }
}
