package com.tpms.app.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    companion object {
        private val KEY_LAST_BEAT_MS = longPreferencesKey("last_beat_ms")
        private val KEY_LAST_PROCESS_RESTART_MS = longPreferencesKey("last_process_restart_ms")
        private val KEY_LAST_PROCESS_RESTART_REASON = stringPreferencesKey("last_process_restart_reason")
    }
}
