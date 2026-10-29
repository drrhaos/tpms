package com.tpms.app.data.persistence

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tpms.app.domain.PreviousRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** USB permission requests since install (diagnostics: firmwares that forget "use by default"). */
data class UsbPermissionStats(
    val requests: Int,
    /** Of [requests], asked by the user (header tap, notification action); the rest were automatic. */
    val explicitRequests: Int,
    val denials: Int,
    val lastRequestMs: Long?
)

private val Context.heartbeatDataStore: DataStore<Preferences> by preferencesDataStore(name = "tpms_heartbeat")

@Singleton
class ServiceHeartbeatStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Owns the bounded main-thread writes, so a timed-out wait does not cancel the write. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun recordBeat(atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit { it[KEY_LAST_BEAT_MS] = atMs }
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

    /**
     * Returns what the previous process left behind and, in the same write, marks this run as
     * started; [markRunEndedBlocking] removes the marker, so a marker left behind means the process
     * was killed. Must run before this process records any screen event.
     */
    suspend fun beginRun(bootCount: Int?, atMs: Long = System.currentTimeMillis()): PreviousRun {
        var previous: PreviousRun? = null
        context.heartbeatDataStore.edit {
            previous = PreviousRun(
                runStartedAtMs = it[KEY_RUN_STARTED_MS],
                runBootCount = it[KEY_RUN_BOOT_COUNT],
                lastScreenOffMs = it[KEY_LAST_SCREEN_OFF_MS],
                lastScreenOnMs = it[KEY_LAST_SCREEN_ON_MS],
                lastProcessRestartMs = it[KEY_LAST_PROCESS_RESTART_MS]
            )
            it[KEY_RUN_STARTED_MS] = atMs
            if (bootCount != null) it[KEY_RUN_BOOT_COUNT] = bootCount else it.remove(KEY_RUN_BOOT_COUNT)
        }
        return checkNotNull(previous)
    }

    /**
     * Main-thread [beginRun], bounded by [MAIN_THREAD_TIMEOUT_MS]. Null on timeout or failure: the
     * previous run is unknown, so no "killed in sleep" verdict. A timed-out edit still completes in
     * the background, queued ahead of this process's screen events (see [boundedBlocking]).
     */
    fun beginRunBlocking(bootCount: Int?): PreviousRun? =
        boundedBlocking("beginRun") { beginRun(bootCount) }

    /**
     * Clean shutdown (`onDestroy`, including an intentional stop). Bounded; on timeout the removal
     * finishes in the background unless the process dies first, in which case the marker stays.
     */
    fun markRunEndedBlocking() {
        boundedBlocking("markRunEnded") {
            context.heartbeatDataStore.edit {
                it.remove(KEY_RUN_STARTED_MS)
                it.remove(KEY_RUN_BOOT_COUNT)
            }
        }
    }

    /**
     * Blocking: the process may be killed right after the screen goes off, so it should hit disk
     * first. Bounded; on timeout the write finishes in the background (best effort).
     */
    fun recordScreenOffBlocking(atMs: Long = System.currentTimeMillis()) {
        boundedBlocking("recordScreenOff") {
            context.heartbeatDataStore.edit { it[KEY_LAST_SCREEN_OFF_MS] = atMs }
        }
    }

    /**
     * Main-thread DataStore access must never hang the service (ANR): waits at most
     * [MAIN_THREAD_TIMEOUT_MS], null on timeout or failure. The work runs in [writeScope], so a
     * timeout only stops the wait, not the write. Started undispatched: the edit is queued in
     * DataStore before this returns, and DataStore applies edits in queue order.
     */
    private fun <T : Any> boundedBlocking(what: String, block: suspend () -> T): T? = try {
        val work = writeScope.async(start = CoroutineStart.UNDISPATCHED) { block() }
        runBlocking { withTimeoutOrNull(MAIN_THREAD_TIMEOUT_MS) { work.await() } }
            .also { if (it == null) Log.w(TAG, "$what timed out after $MAIN_THREAD_TIMEOUT_MS ms") }
    } catch (e: Exception) {
        Log.w(TAG, "$what failed", e)
        null
    }

    /** [survivedSleep]: this process also saw the SCREEN_OFF, so a previous sleep kill warning is stale. */
    suspend fun recordScreenOn(survivedSleep: Boolean, atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit {
            it[KEY_LAST_SCREEN_ON_MS] = atMs
            if (survivedSleep) it.remove(KEY_KILLED_IN_SLEEP_MS)
        }
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

    /** Counts one `requestPermission` call ([explicit]: asked by the user). */
    suspend fun recordUsbPermissionRequest(explicit: Boolean, atMs: Long = System.currentTimeMillis()) {
        context.heartbeatDataStore.edit {
            it[KEY_USB_PERMISSION_REQUESTS] = (it[KEY_USB_PERMISSION_REQUESTS] ?: 0) + 1
            if (explicit) it[KEY_USB_PERMISSION_EXPLICIT] = (it[KEY_USB_PERMISSION_EXPLICIT] ?: 0) + 1
            it[KEY_USB_PERMISSION_LAST_MS] = atMs
        }
    }

    suspend fun recordUsbPermissionDenied() {
        context.heartbeatDataStore.edit {
            it[KEY_USB_PERMISSION_DENIALS] = (it[KEY_USB_PERMISSION_DENIALS] ?: 0) + 1
        }
    }

    suspend fun usbPermissionStats(): UsbPermissionStats {
        val prefs = context.heartbeatDataStore.data.first()
        return UsbPermissionStats(
            requests = prefs[KEY_USB_PERMISSION_REQUESTS] ?: 0,
            explicitRequests = prefs[KEY_USB_PERMISSION_EXPLICIT] ?: 0,
            denials = prefs[KEY_USB_PERMISSION_DENIALS] ?: 0,
            lastRequestMs = prefs[KEY_USB_PERMISSION_LAST_MS]
        )
    }

    /** Main-thread [usbPermissionStats] (diagnostics export), bounded; null on timeout or failure. */
    fun usbPermissionStatsBlocking(): UsbPermissionStats? =
        boundedBlocking("usbPermissionStats") { usbPermissionStats() }

    /**
     * End of the alert mute window ("Mute 30 min", wayfinder 15), wall clock; persisted so the mute
     * survives a service restart. Main-thread read, bounded: 0 (not muted) on timeout or failure.
     */
    fun alertMutedUntilMsBlocking(): Long =
        boundedBlocking("alertMutedUntil") { context.heartbeatDataStore.data.first()[KEY_ALERT_MUTED_UNTIL_MS] ?: 0L }
            ?: 0L

    /** Main-thread write of the mute window end; bounded, on timeout it finishes in the background. */
    fun recordAlertMutedUntilBlocking(untilMs: Long) {
        boundedBlocking("recordAlertMutedUntil") {
            context.heartbeatDataStore.edit { it[KEY_ALERT_MUTED_UNTIL_MS] = untilMs }
        }
    }

    /**
     * The alert notifier's per-wheel memory (wayfinder 15: episode types, current type, swipe), as
     * encoded by `AlertNotificationBook.encode`; persisted so a process kill / START_STICKY restart
     * neither re-rings an ongoing alert nor brings back a swiped one. Read once per process on the
     * main thread, bounded: null on timeout or failure, "" when nothing is stored.
     */
    fun alertNotificationsBlocking(): String? =
        boundedBlocking("alertNotifications") { context.heartbeatDataStore.data.first()[KEY_ALERT_NOTIFICATIONS] ?: "" }

    /**
     * Non-blocking write of the notifier's memory: only the newest value is written, in order, by
     * one writer. Use it for the frequent changes; the main thread never waits on disk.
     */
    fun recordAlertNotificationsAsync(encoded: String) {
        pendingAlertNotifications.value = encoded
    }

    private val pendingAlertNotifications = MutableStateFlow<String?>(null)

    init {
        writeScope.launch {
            pendingAlertNotifications.filterNotNull().collect { encoded ->
                runCatching {
                    context.heartbeatDataStore.edit { it[KEY_ALERT_NOTIFICATIONS] = encoded }
                }.onFailure { Log.w(TAG, "recordAlertNotifications failed", it) }
            }
        }
    }

    companion object {
        private const val TAG = "ServiceHeartbeat"
        private const val MAIN_THREAD_TIMEOUT_MS = 500L
        private val KEY_LAST_BEAT_MS = longPreferencesKey("last_beat_ms")
        private val KEY_LAST_PROCESS_RESTART_MS = longPreferencesKey("last_process_restart_ms")
        private val KEY_LAST_PROCESS_RESTART_REASON = stringPreferencesKey("last_process_restart_reason")
        private val KEY_RUN_STARTED_MS = longPreferencesKey("run_started_ms")
        private val KEY_RUN_BOOT_COUNT = intPreferencesKey("run_boot_count")
        private val KEY_LAST_SCREEN_OFF_MS = longPreferencesKey("last_screen_off_ms")
        private val KEY_LAST_SCREEN_ON_MS = longPreferencesKey("last_screen_on_ms")
        private val KEY_KILLED_IN_SLEEP_MS = longPreferencesKey("killed_in_sleep_ms")
        private val KEY_USB_PERMISSION_REQUESTS = intPreferencesKey("usb_permission_requests")
        private val KEY_USB_PERMISSION_EXPLICIT = intPreferencesKey("usb_permission_explicit_requests")
        private val KEY_USB_PERMISSION_DENIALS = intPreferencesKey("usb_permission_denials")
        private val KEY_USB_PERMISSION_LAST_MS = longPreferencesKey("usb_permission_last_request_ms")
        private val KEY_ALERT_MUTED_UNTIL_MS = longPreferencesKey("alert_muted_until_ms")
        private val KEY_ALERT_NOTIFICATIONS = stringPreferencesKey("alert_notifications")
    }
}
