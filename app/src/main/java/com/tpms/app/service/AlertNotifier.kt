package com.tpms.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.tpms.app.R
import com.tpms.app.TpmsApplication
import com.tpms.app.domain.AlertPresentation
import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-alert notifications (wayfinder alerts 02 / 05), executed for [AlertCoordinator]: one per
 * wheel alert plus one for "monitoring not working", tag = alert key, shared id [NOTIF_ID], on the
 * silent [TpmsApplication.CHANNEL_ALERT] (the signal is [AlertSoundPlayer]'s).
 *
 * - Ongoing for as long as the alert lasts: not swipeable up to Android 13; on 14+ a swipe hits the
 *   delete intent and the coordinator re-posts it at once ([repost]), silently.
 * - A fault carries «Понятно» and, while unacknowledged, a full-screen intent (a sticky heads-up
 *   when the alert window cannot be drawn); attention carries «Без звука 30 мин».
 * - Same content is not re-posted; a changed one at most once per [UPDATE_INTERVAL_MS] per alert.
 *
 * Main thread only.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** What each posted notification shows, to skip identical re-posts. */
    private val posted = HashMap<String, Content>()
    private val postedAtMs = HashMap<String, Long>()

    private data class Content(val level: AlertLevel, val title: String, val text: String?, val fullScreen: Boolean)

    /**
     * Brings the shade in line with [entries]: posts or updates each, cancels the rest. [rowOf]
     * gives the localized texts; [faultUnacknowledged] adds the full-screen intent to faults.
     */
    fun sync(
        entries: List<AlertPresentation.Entry>,
        rowOf: (AlertPresentation.Entry) -> AlertOverlayController.Row,
        faultUnacknowledged: Boolean
    ) {
        if (!NotificationHelper.canPostAlerts(context)) return
        val keys = entries.mapTo(HashSet()) { it.key }
        (posted.keys - keys).forEach { cancel(it) }
        val now = SystemClock.elapsedRealtime()
        for (entry in entries) {
            val row = rowOf(entry)
            val content = Content(
                level = entry.level,
                title = listOf(row.title, row.subtitle).joinToString(" · "),
                text = listOfNotNull(row.value, row.detail).joinToString(" · ").ifBlank { null },
                fullScreen = entry.level == AlertLevel.FAULT && faultUnacknowledged
            )
            val previous = posted[entry.key]
            if (previous == content) continue
            val sameKind = previous?.level == content.level && previous.fullScreen == content.fullScreen
            if (sameKind && now - (postedAtMs[entry.key] ?: 0L) < UPDATE_INTERVAL_MS) continue
            post(entry.key, content)
        }
    }

    /** The user swiped [key]'s notification (Android 14+): put it back, silently. */
    fun repost(key: String) {
        val content = posted[key] ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - (postedAtMs[key] ?: 0L) < REPOST_MIN_INTERVAL_MS) return
        post(key, content.copy(fullScreen = false))
    }

    /** Removes every alert notification, also those of an earlier process. */
    fun cancelAll() {
        posted.keys.toList().forEach { cancel(it) }
        runCatching {
            notificationManager.activeNotifications
                .filter { it.id == NOTIF_ID && it.tag != null }
                .forEach { notificationManager.cancel(it.tag, NOTIF_ID) }
        }
    }

    private fun cancel(key: String) {
        notificationManager.cancel(key, NOTIF_ID)
        posted.remove(key)
        postedAtMs.remove(key)
    }

    private fun post(key: String, content: Content) {
        val ok = runCatching { notificationManager.notify(key, NOTIF_ID, build(key, content)) }.isSuccess
        if (ok) {
            posted[key] = content
            postedAtMs[key] = SystemClock.elapsedRealtime()
        }
    }

    private fun build(key: String, content: Content) =
        NotificationCompat.Builder(context, TpmsApplication.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tpms)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setColor(if (content.level == AlertLevel.FAULT) FAULT_COLOR else ATTENTION_COLOR)
            .setColorized(content.level == AlertLevel.FAULT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityIntent())
            .setDeleteIntent(swipeIntent(key))
            .apply {
                if (content.level == AlertLevel.FAULT) {
                    addAction(0, context.getString(R.string.alert_window_acknowledge), serviceIntent(TpmsMonitorService.ACTION_ACKNOWLEDGE, REQUEST_ACK))
                } else {
                    addAction(0, context.getString(R.string.notification_action_mute), serviceIntent(TpmsMonitorService.ACTION_MUTE, REQUEST_MUTE))
                }
                if (content.fullScreen && canUseFullScreenIntent()) setFullScreenIntent(activityIntent(), true)
            }
            .build()

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notificationManager.canUseFullScreenIntent()

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        MainActivity.newIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun serviceIntent(action: String, request: Int): PendingIntent = PendingIntent.getService(
        context,
        request,
        Intent(context, TpmsMonitorService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Per alert: the data URI keeps the PendingIntents of different alerts apart. */
    private fun swipeIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SWIPED,
        Intent(context, AlertNotificationReceiver::class.java).apply {
            action = AlertNotificationReceiver.ACTION_ALERT_SWIPED
            data = Uri.fromParts(URI_SCHEME, key, null)
            putExtra(AlertNotificationReceiver.EXTRA_ALERT_KEY, key)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        /** Shared by every alert notification; the tag (alert key) tells them apart. */
        const val NOTIF_ID = 1200
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val REPOST_MIN_INTERVAL_MS = 2_000L
        private const val URI_SCHEME = "tpms-alert"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_MUTE = 2
        private const val REQUEST_ACK = 4
        private const val REQUEST_SWIPED = 1200
        private const val FAULT_COLOR = 0xFFE5484D.toInt()
        private const val ATTENTION_COLOR = 0xFFF0A020.toInt()
    }
}
