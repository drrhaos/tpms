package com.tpms.app.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

    companion object {
        private val KEY_LAST_BEAT_MS = longPreferencesKey("last_beat_ms")
    }
}
