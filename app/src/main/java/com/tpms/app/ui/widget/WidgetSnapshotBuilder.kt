package com.tpms.app.ui.widget

import android.content.Context
import com.tpms.app.data.persistence.LastKnownSnapshotStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.WheelAssignment
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
        val liveWheels = repository.wheels.value
        // Before the repository restored the snapshot: place the persisted wheels by the same rule the
        // restore will apply (no AA55 empty slots are known yet).
        val wheels = liveWheels.ifEmpty {
            if (persisted.isEmpty()) {
                emptyMap()
            } else {
                WheelAssignment.assign(
                    seen = persisted,
                    wheelMapping = settingsStore.wheelMapping.value,
                    showSpareWheel = settingsStore.showSpareWheel.value
                )
            }
        }
        val state = when {
            wheels.isNotEmpty() && repository.state.value is TpmsState.Disconnected ->
                TpmsState.Connected(wheels.values.toList(), System.currentTimeMillis())
            else -> repository.state.value
        }
        // Connection-based (wayfinder 10): minutes since the last valid frame, not newest sensor age.
        val dataAgeSec = repository.dataAgeSec().takeIf { liveWheels.isNotEmpty() }
            ?: persistedAgeSec(persistedAtMs)
        // Persisted sensors not restored into the repository yet come from a snapshot: outdated.
        val dataStale = repository.isDataStale() || (liveWheels.isEmpty() && persisted.isNotEmpty())
        return WidgetSnapshot.from(
            context = context,
            state = state,
            wheels = wheels,
            unit = settingsStore.pressureUnit.value,
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
