package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertNotificationBook
import com.tpms.app.domain.AlertNotificationPolicy
import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.toSeverity
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.notificationText
import com.tpms.app.ui.wheelPositionLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-wheel ALERT notifications (wayfinder 15): executes [AlertNotificationPolicy] through
 * [AlertNotificationBook]. One notification per wheel on [TpmsApplication.CHANNEL_ALERT], posted
 * under tag = sensor id and the shared id [NOTIF_ID] (so ids never collide with other notifications).
 *
 * - Each alert type rings once per episode, a further LOW_PRESSURE drop rings again (see
 *   [AlertNotificationPolicy]); a new trip ([onNewTrip]) starts new episodes. The per-wheel memory is
 *   persisted in [ServiceHeartbeatStore] whenever it changes, so a process kill / START_STICKY
 *   restart neither re-rings an ongoing alert nor brings back a swiped one. An intentional stop
 *   forgets it ([onMonitoringStopped]): the next monitoring session rings once again.
 * - Only live data may post: alerts from restored / reconnecting data are skipped (the next live
 *   reading posts them), like [resync] without a live session.
 * - The memory is committed only after a successful post, so a failed post rings on the next reading.
 * - A ringing ALERT with "Full-screen warning" on also takes over the screen: full-screen intent,
 *   and MainActivity started directly when the overlay permission allows a background start (a head
 *   unit's screen is always on, so the full-screen intent alone would only be a heads-up).
 * - A throttled update is retried when the throttle window ends, so the last values are shown even
 *   if readings stop.
 *
 * Main thread only: the service posts the repository's alert callbacks and wheel changes to the
 * main looper, the swipe arrives in [AlertNotificationReceiver.onReceive].
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TpmsRepository,
    private val settingsStore: SettingsStore,
    private val heartbeatStore: ServiceHeartbeatStore
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val book = AlertNotificationBook()
    private val handler = Handler(Looper.getMainLooper())

    /** The book was loaded from the store (once per process; a failed load counts, memory starts empty). */
    private var loaded = false

    /** Last load attempt (elapsedRealtime): a failed read is retried at most every [LOAD_RETRY_MS]. */
    private var lastLoadAttemptMs = Long.MIN_VALUE / 2

    /** End of the mute window, wall clock; loaded from the store on first use. */
    private var mutedUntilMs: Long? = null

    /** Slot each shown notification's title names; a change re-posts it silently ([onWheelsChanged]). */
    private val postedSlot = HashMap<String, String?>()

    /** Wheels with a trailing (throttled) update scheduled on [handler]. */
    private val pendingRetry = HashMap<String, Runnable>()

    /**
     * The alert of [sensorId] changed (or a confirmed reading of the same alert arrived): [sensor]
     * is its current reading, null when it is normal again or left its slot. [live]: from a live
     * session; an alert from restored / reconnecting data is not posted (a cancel always applies).
     */
    fun onAlertChanged(sensorId: String, sensor: TireSensor?, live: Boolean = true) {
        if (sensor != null && !live) return
        apply(sensorId, sensor)
    }

    /**
     * Service create: loads the persisted memory and removes alert notifications in the shade it
     * knows nothing about (left by a process whose memory was lost).
     */
    fun onServiceCreated() {
        if (!ensureLoaded()) return
        runCatching {
            val known = book.keys()
            notificationManager.activeNotifications
                .filter { it.id == NOTIF_ID && it.tag != null && it.tag !in known }
                .forEach { notificationManager.cancel(it.tag, NOTIF_ID) }
        }
    }

    /**
     * Service start / app resume: shows the alerts of the current wheels by the normal rules (the
     * persisted memory keeps a restart from ringing again). Without a live session (restored or
     * reconnecting data) nothing is posted: the readings to come do it.
     */
    fun resync() {
        if (!NotificationHelper.canPostAlerts(context)) {
            cancelAll()
            forgetAll()
            return
        }
        ensureLoaded()
        if (!repository.connectionSnapshot().hasLiveSession) return
        repository.wheels.value.values.forEach { apply(it.id, it) }
    }

    /** The user swiped [sensorId]'s notification away. */
    fun onSwiped(sensorId: String) {
        ensureLoaded()
        if (book.onSwiped(sensorId)) persist()
        postedSlot.remove(sensorId)
        cancelRetry(sensorId)
    }

    /**
     * "Mute 30 min": the wheels alerting now post their new types without sound or heads-up until
     * [untilMs]. Another wheel, or a further pressure drop, still rings.
     */
    fun mute(untilMs: Long) {
        ensureLoaded()
        mutedUntilMs = untilMs
        heartbeatStore.recordAlertMutedUntilBlocking(untilMs)
        if (book.onMuted()) persist()
    }

    /**
     * A new trip began (the head unit woke from a sleep, or this process starts after one): ongoing
     * alerts must ring again, so every episode, swipe and mute is forgotten and the shown
     * notifications are removed; the next live readings post them anew.
     */
    fun onNewTrip() {
        cancelAll()
        // A deliberate reset: the stored memory is replaced even if it could not be read yet.
        book.clear()
        loaded = true
        persist()
        mutedUntilMs = 0L
        heartbeatStore.recordAlertMutedUntilBlocking(0L)
        // A session that stayed live through the screen-off rings at once, not at its next reading.
        resync()
    }

    /**
     * "Test alert" from the settings: one notification on the alert channel with sound, removed after
     * [TEST_VISIBLE_MS], so the user can check volume and heads-up on this head unit. False if refused.
     */
    fun postTest(): Boolean {
        if (!NotificationHelper.canPostAlerts(context)) return false
        val notification = NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tpms)
            .setContentTitle(context.getString(R.string.notification_test_alert_title))
            .setContentText(context.getString(R.string.notification_test_alert_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        val posted = runCatching { notificationManager.notify(TEST_TAG, NOTIF_ID, notification) }.isSuccess
        if (posted) handler.postDelayed({ notificationManager.cancel(TEST_TAG, NOTIF_ID) }, TEST_VISIBLE_MS)
        return posted
    }

    /**
     * Wheels re-placed (main thread): a shown notification whose sensor moved to another slot is
     * re-posted silently, so its title names the new slot without waiting for the next reading.
     */
    fun onWheelsChanged(wheels: Map<String, TireSensor>) {
        if (postedSlot.isEmpty() || !NotificationHelper.canPostAlerts(context)) return
        wheels.forEach { (slot, sensor) ->
            if (sensor.id !in postedSlot || postedSlot[sensor.id] == slot) return@forEach
            val shown = book.shown(sensor.id) ?: return@forEach
            if (shown.swiped || sensor.alertType != shown.type) return@forEach
            post(sensor.id, sensor, shown.type, NotificationAction.UPDATE_SILENTLY, slot)
        }
    }

    /**
     * Monitoring stopped (service destroyed): every alert notification goes, they must not outlive
     * monitoring. [intentional] (ACTION_STOP) also forgets the memory, so the next monitoring
     * session rings once again; otherwise (system stop) it is kept for the next start.
     */
    fun onMonitoringStopped(intentional: Boolean) {
        cancelAll()
        if (intentional) forgetAll()
    }

    private fun apply(sensorId: String, sensor: TireSensor?) {
        cancelRetry(sensorId)
        ensureLoaded()
        if (!NotificationHelper.canPostAlerts(context)) {
            // Nothing can be shown: forget, so the resync after notifications are allowed posts it.
            if (book.forget(sensorId)) {
                notificationManager.cancel(sensorId, NOTIF_ID)
                persist()
            }
            postedSlot.remove(sensorId)
            return
        }
        val step = book.plan(sensorId, sensor?.alertType, mutedUntil(), System.currentTimeMillis(), sensor?.pressureKpa)
        val done = when (step.action) {
            NotificationAction.POST_WITH_SOUND,
            NotificationAction.POST_SILENTLY,
            NotificationAction.UPDATE_SILENTLY -> {
                val reading = sensor ?: return
                val type = reading.alertType ?: return
                post(sensorId, reading, type, step.action, slotOf(sensorId))
            }
            NotificationAction.CANCEL -> {
                notificationManager.cancel(sensorId, NOTIF_ID)
                postedSlot.remove(sensorId)
                true
            }
            NotificationAction.NONE -> true
        }
        // A failed post leaves the memory as it was: the next reading tries again (with sound if due).
        if (done && book.commit(step)) persist()
        step.retryInMs?.let { scheduleRetry(sensorId, it) }
    }

    /** Posts [sensorId]'s notification; false if the system refused it. */
    private fun post(sensorId: String, sensor: TireSensor, type: AlertType, action: NotificationAction, slot: String?): Boolean {
        val posted = runCatching {
            notificationManager.notify(sensorId, NOTIF_ID, build(sensorId, sensor, type, action, slot))
        }.isSuccess
        if (posted) postedSlot[sensorId] = slot
        if (posted && takesOverScreen(type, action)) takeOverScreen()
        return posted
    }

    private fun takesOverScreen(type: AlertType, action: NotificationAction): Boolean =
        action == NotificationAction.POST_WITH_SOUND &&
            type.toSeverity() == AlertSeverity.ALERT &&
            settingsStore.criticalAlertsFullscreen.value

    /** Brings MainActivity forward; a background start is allowed only with the overlay permission. */
    private fun takeOverScreen() {
        if (!Settings.canDrawOverlays(context)) return
        runCatching {
            context.startActivity(MainActivity.fullScreenIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Full-screen intent of a ringing ALERT; null when off or not allowed (Android 14+). */
    private fun fullScreenIntent(type: AlertType, action: NotificationAction): PendingIntent? {
        if (!takesOverScreen(type, action)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !notificationManager.canUseFullScreenIntent()
        ) {
            return null
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_FULL_SCREEN,
            MainActivity.fullScreenIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Trailing update of a throttled wheel, from its reading at that time (live data only). */
    private fun scheduleRetry(sensorId: String, delayMs: Long) {
        if (sensorId in pendingRetry) return
        val retry = Runnable {
            pendingRetry.remove(sensorId)
            val sensor = repository.wheels.value.values.firstOrNull { it.id == sensorId } ?: return@Runnable
            onAlertChanged(sensorId, sensor, live = repository.connectionSnapshot().hasLiveSession)
        }
        pendingRetry[sensorId] = retry
        handler.postDelayed(retry, delayMs)
    }

    private fun cancelRetry(sensorId: String) {
        pendingRetry.remove(sensorId)?.let { handler.removeCallbacks(it) }
    }

    /** Cancels every alert notification, also those of an earlier process; the memory is kept. */
    private fun cancelAll() {
        pendingRetry.values.forEach { handler.removeCallbacks(it) }
        pendingRetry.clear()
        book.keys().forEach { notificationManager.cancel(it, NOTIF_ID) }
        postedSlot.clear()
        runCatching {
            notificationManager.activeNotifications
                .filter { it.id == NOTIF_ID && it.tag != null }
                .forEach { notificationManager.cancel(it.tag, NOTIF_ID) }
        }
    }

    private fun forgetAll() {
        ensureLoaded()
        if (book.keys().isEmpty()) return
        book.clear()
        persist()
    }

    /**
     * Loads the persisted memory once. A timed-out or failed read leaves [loaded] false: the book
     * works from empty for now, but nothing is swept or persisted until a later load succeeds, so
     * a slow disk never wipes the stored memory.
     */
    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastLoadAttemptMs < LOAD_RETRY_MS) return false
        lastLoadAttemptMs = now
        val stored = heartbeatStore.alertNotificationsBlocking() ?: return false
        book.load(stored)
        loaded = true
        return true
    }

    private fun persist() {
        if (!loaded) return
        heartbeatStore.recordAlertNotificationsAsync(book.encode())
    }

    private fun mutedUntil(): Long =
        mutedUntilMs ?: heartbeatStore.alertMutedUntilMsBlocking().also { mutedUntilMs = it }

    private fun slotOf(sensorId: String): String? =
        repository.wheels.value.entries.firstOrNull { it.value.id == sensorId }?.key

    private fun build(sensorId: String, sensor: TireSensor, type: AlertType, action: NotificationAction, slot: String?) =
        NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tpms)
            .setContentTitle(
                context.getString(
                    R.string.notification_alert_title_format,
                    slot?.let { wheelPositionLabel(context, it) } ?: sensor.label.ifBlank { sensor.id },
                    type.notificationText(context)
                )
            )
            .setContentText(pressureText(sensor))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(sensor.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(false)
            .setOngoing(false)
            // An update must stay silent also when its notification is no longer in the shade
            // (cancelled on a stop, cleared by a reboot): onlyAlertOnce alone would ring then.
            .setSilent(action != NotificationAction.POST_WITH_SOUND)
            .setOnlyAlertOnce(action == NotificationAction.UPDATE_SILENTLY)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setDeleteIntent(swipeIntent(sensorId))
            .apply { fullScreenIntent(type, action)?.let { setFullScreenIntent(it, true) } }
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                context.getString(R.string.notification_action_mute),
                PendingIntent.getService(
                    context,
                    REQUEST_MUTE,
                    Intent(context, TpmsMonitorService::class.java).setAction(TpmsMonitorService.ACTION_MUTE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    /** Per wheel: the data URI keeps the PendingIntents of different wheels apart. */
    private fun swipeIntent(sensorId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SWIPED,
        Intent(context, AlertNotificationReceiver::class.java).apply {
            action = AlertNotificationReceiver.ACTION_ALERT_SWIPED
            data = Uri.fromParts(URI_SCHEME, sensorId, null)
            putExtra(AlertNotificationReceiver.EXTRA_SENSOR_ID, sensorId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun pressureText(sensor: TireSensor): String? {
        if (!sensor.pressureKpa.isFinite()) return null
        val unit = settingsStore.pressureUnit.value
        val value = "${unit.formatFromKpa(sensor.pressureKpa)} ${unit.localizedLabel(context)}"
        return context.getString(R.string.notification_alert_pressure_format, value)
    }

    companion object {
        /** Shared by every wheel's alert notification; the tag (sensor id) tells them apart. */
        const val NOTIF_ID = 1200
        private const val LOAD_RETRY_MS = 30_000L
        private const val TEST_TAG = "test"
        private const val TEST_VISIBLE_MS = 10_000L
        private const val URI_SCHEME = "tpms-alert"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_MUTE = 2
        private const val REQUEST_FULL_SCREEN = 3
        private const val REQUEST_SWIPED = 1200
    }
}
