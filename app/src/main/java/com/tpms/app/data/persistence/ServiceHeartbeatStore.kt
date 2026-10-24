package com.tpms.app.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tpms.app.domain.PreviousRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.heartbeatDataStore: DataStore<Preferences> by preferencesDataStore(name = "tpms_heartbeat")

@Singleton
class ServiceHeartbeatStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun recordBeat(atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit { it[KEY_LAST_BEAT_MS] = atMs }
    }

    fun recordBeatBlocking(atMs: Long = System.currentTimeMillis()) {
        runBlocking { recordBeat(atMs) }
    }

    suspend fun lastBeatMs(): Long =
        context.heartbeatDataStore.data.first()[KEY_LAST_BEAT_MS] ?: 0L

    fun lastBeatMsBlocking(): Long = runBlocking { lastBeatMs() }

    /** Persisted so the restart limit, and why it happened, survive the restart they describe. */
    suspend fun recordProcessRestart(reason: String, atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit {
            it[KEY_LAST_PROCESS_RESTART_MS] = atMs
            it[KEY_LAST_PROCESS_RESTART_REASON] = reason
        }
    }

    suspend fun lastProcessRestartMs(): Long? =
        context.heartbeatDataStore.data.first()[KEY_LAST_PROCESS_RESTART_MS]

    suspend fun lastProcessRestartReason(): String? =
        context.heartbeatDataStore.data.first()[KEY_LAST_PROCESS_RESTART_REASON]

    /** Marks a run as started; [markRunEnded] removes it, so a marker left behind means the process was killed. */
    suspend fun markRunStarted(bootCount: Int?, atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit {
            it[KEY_RUN_STARTED_MS] = atMs
            if (bootCount != null) it[KEY_RUN_BOOT_COUNT] = bootCount else it.remove(KEY_RUN_BOOT_COUNT)
        }
    }

    /** Clean shutdown (`onDestroy`, including an intentional stop). */
    fun markRunEndedBlocking() {
        runBlocking {
            context.heartbeatDataStore.edit {
                it.remove(KEY_RUN_STARTED_MS)
                it.remove(KEY_RUN_BOOT_COUNT)
            }
        }
    }

    suspend fun recordScreenOff(atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit { it[KEY_LAST_SCREEN_OFF_MS] = atMs }
    }

    /** [survivedSleep]: this process also saw the SCREEN_OFF, so a previous sleep kill warning is stale. */
    suspend fun recordScreenOn(survivedSleep: Boolean, atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit {
            it[KEY_LAST_SCREEN_ON_MS] = atMs
            if (survivedSleep) it.remove(KEY_KILLED_IN_SLEEP_MS)
        }
    }

    /** What the previous process left behind; read before [markRunStarted] overwrites it. */
    suspend fun previousRun(): PreviousRun {
        val prefs = context.heartbeatDataStore.data.first()
        return PreviousRun(
            runStartedAtMs = prefs[KEY_RUN_STARTED_MS],
            runBootCount = prefs[KEY_RUN_BOOT_COUNT],
            lastScreenOffMs = prefs[KEY_LAST_SCREEN_OFF_MS],
            lastScreenOnMs = prefs[KEY_LAST_SCREEN_ON_MS],
            lastProcessRestartMs = prefs[KEY_LAST_PROCESS_RESTART_MS]
        )
    }

    suspend fun recordKilledInSleep(atMs: Long) {
        context.heartbeatDataStore.edit { it[KEY_KILLED_IN_SLEEP_MS] = atMs }
    }

    /**
     * When the service was last found killed during the head unit's sleep; null once a later
     * process survives a sleep (SCREEN_OFF and SCREEN_ON in the same process).
     */
    val killedInSleepAtMs: Flow<Long?> =
        context.heartbeatDataStore.data.map { it[KEY_KILLED_IN_SLEEP_MS] }.distinctUntilChanged()

    companion object {
        private val KEY_LAST_BEAT_MS = longPreferencesKey("last_beat_ms")
        private val KEY_LAST_PROCESS_RESTART_MS = longPreferencesKey("last_process_restart_ms")
        private val KEY_LAST_PROCESS_RESTART_REASON = stringPreferencesKey("last_process_restart_reason")
        private val KEY_RUN_STARTED_MS = longPreferencesKey("run_started_ms")
        private val KEY_RUN_BOOT_COUNT = intPreferencesKey("run_boot_count")
        private val KEY_LAST_SCREEN_OFF_MS = longPreferencesKey("last_screen_off_ms")
        private val KEY_LAST_SCREEN_ON_MS = longPreferencesKey("last_screen_on_ms")
        private val KEY_KILLED_IN_SLEEP_MS = longPreferencesKey("killed_in_sleep_ms")
    }
}
