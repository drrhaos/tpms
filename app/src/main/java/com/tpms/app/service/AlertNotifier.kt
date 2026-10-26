package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertNotificationBook
import com.tpms.app.domain.AlertNotificationPolicy
import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.notificationText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-wheel ALERT notifications (wayfinder 15): executes [AlertNotificationPolicy] through
 * [AlertNotificationBook]. One notification per wheel on [TpmsApplication.CHANNEL_ALERT], posted
 * under tag = sensor id and the shared id [NOTIF_ID] (so ids never collide with other notifications).
 *
 * Main thread only: the service posts the repository's alert callbacks to the main looper, the
 * swipe arrives in [AlertNotificationReceiver.onReceive].
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

    /** End of the mute window, wall clock; loaded from the store on first use. */
    private var mutedUntilMs: Long? = null

    /**
     * The alert of [sensorId] changed (or a confirmed reading of the same alert arrived):
     * [sensor] is its current reading, null when it is normal again or left its slot.
     */
    fun onAlertChanged(sensorId: String, sensor: TireSensor?) {
        apply(sensorId, sensor, resync = false)
    }

    /**
     * Service start / app resume: adopts the alert notifications an earlier process left in the
     * shade, then shows the alerts of the current wheels. New ones are posted silently, so a
     * process restart does not ring again. Without a live session (only restored, possibly old
     * data) nothing is posted: the readings to come do it.
     */
    fun resync() {
        if (!NotificationHelper.canPostAlerts(context)) {
            clear()
            return
        }
        adoptShown()
        if (repository.state.value is TpmsState.Disconnected) return
        repository.wheels.value.values.forEach { apply(it.id, it, resync = true) }
    }

    /** The user swiped [sensorId]'s notification away. */
    fun onSwiped(sensorId: String) {
        book.onSwiped(sensorId)
    }

    /** "Mute 30 min": new or changed alerts post without sound or heads-up until [untilMs]. */
    fun mute(untilMs: Long) {
        mutedUntilMs = untilMs
        heartbeatStore.recordAlertMutedUntilBlocking(untilMs)
    }

    /** Cancels every alert notification and forgets them (service stopped). */
    fun clear() {
        book.keys().forEach { notificationManager.cancel(it, NOTIF_ID) }
        book.clear()
        // Also those of an earlier process this one never adopted.
        runCatching {
            notificationManager.activeNotifications
                .filter { it.id == NOTIF_ID && it.tag != null }
                .forEach { notificationManager.cancel(it.tag, NOTIF_ID) }
        }
    }

    private fun apply(sensorId: String, sensor: TireSensor?, resync: Boolean) {
        if (!NotificationHelper.canPostAlerts(context)) {
            // Nothing can be shown: forget, so the resync after notifications are allowed posts it.
            if (book.forget(sensorId)) notificationManager.cancel(sensorId, NOTIF_ID)
            return
        }
        val now = System.currentTimeMillis()
        val action = book.onChange(sensorId, sensor?.alertType, mutedUntil(), now, resync)
        when (action) {
            NotificationAction.POST_WITH_SOUND,
            NotificationAction.POST_SILENTLY,
            NotificationAction.UPDATE_SILENTLY -> {
                val reading = sensor ?: return
                val type = reading.alertType ?: return
                runCatching { notificationManager.notify(sensorId, NOTIF_ID, build(sensorId, reading, type, action)) }
            }
            NotificationAction.CANCEL -> notificationManager.cancel(sensorId, NOTIF_ID)
            NotificationAction.NONE -> Unit
        }
    }

    private fun mutedUntil(): Long =
        mutedUntilMs ?: heartbeatStore.alertMutedUntilMsBlocking().also { mutedUntilMs = it }

    /**
     * Alert notifications an earlier process posted (they survive a process kill) become known
     * again, so the same alert arriving now updates them silently instead of ringing. Those that
     * cannot be adopted are removed. Called on service create and by [resync].
     */
    fun adoptShown() {
        runCatching {
            notificationManager.activeNotifications
                .filter { it.id == NOTIF_ID && it.tag != null }
                .forEach { sbn ->
                    val type = sbn.notification.extras.getString(EXTRA_ALERT_TYPE)
                        ?.let { name -> AlertType.entries.firstOrNull { it.name == name } }
                    if (type == null || !book.restore(sbn.tag, type)) {
                        if (sbn.tag !in book.keys()) notificationManager.cancel(sbn.tag, NOTIF_ID)
                    }
                }
        }
    }

    private fun build(sensorId: String, sensor: TireSensor, type: AlertType, action: NotificationAction) =
        NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(
                context.getString(R.string.notification_alert_title_format, wheelLabel(sensor), type.notificationText(context))
            )
            .setContentText(pressureText(sensor))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(sensor.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(false)
            .setOngoing(false)
            .setSilent(action == NotificationAction.POST_SILENTLY)
            .setOnlyAlertOnce(action == NotificationAction.UPDATE_SILENTLY)
            .addExtras(Bundle().apply { putString(EXTRA_ALERT_TYPE, type.name) })
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setDeleteIntent(swipeIntent(sensorId))
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

    /** The wheel slot (e.g. "FL") the sensor sits on now; its own label or id otherwise. */
    private fun wheelLabel(sensor: TireSensor): String =
        repository.wheels.value.entries.firstOrNull { it.value.id == sensor.id }?.key
            ?: sensor.label.ifBlank { sensor.id }

    private fun pressureText(sensor: TireSensor): String? {
        if (!sensor.pressureKpa.isFinite()) return null
        val unit = settingsStore.pressureUnit.value
        val value = "${unit.formatFromKpa(sensor.pressureKpa)} ${unit.localizedLabel(context)}"
        return context.getString(R.string.notification_alert_pressure_format, value)
    }

    companion object {
        /** Shared by every wheel's alert notification; the tag (sensor id) tells them apart. */
        const val NOTIF_ID = 1200
        private const val EXTRA_ALERT_TYPE = "com.tpms.app.extra.ALERT_TYPE"
        private const val URI_SCHEME = "tpms-alert"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_MUTE = 2
        private const val REQUEST_SWIPED = 1200
    }
}
