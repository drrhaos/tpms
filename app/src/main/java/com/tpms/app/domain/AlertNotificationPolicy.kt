package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType

/**
 * Decides, per wheel, what the alert notifier does with that wheel's notification. Pure: the
 * notifier keeps one [ShownAlert] per wheel (keyed by wheel/sensor id), feeds it back on every
 * change and stores the returned one.
 *
 * - Only ALERT severity (LOW_PRESSURE, HIGH_PRESSURE, SENSOR_LOST) gets a notification; a warning
 *   or no alert cancels what is shown.
 * - A new alert, or a change to another alert type, posts with sound, or silently while muted.
 * - The same alert type updates silently (content such as pressure may change).
 * - A swiped notification stays hidden until the type changes or the alert clears.
 * - A cleared alert, or a wheel that left its slot, cancels and forgets, swipe included.
 */
object AlertNotificationPolicy {

    /** What the notifier must do with the wheel's notification. */
    enum class NotificationAction {
        /** Post (or replace) with sound and heads-up. */
        POST_WITH_SOUND,

        /** Post (or replace) without sound or heads-up: the mute window is on. */
        POST_SILENTLY,

        /** Refresh the shown notification without alerting again (`onlyAlertOnce`). */
        UPDATE_SILENTLY,

        /** Remove the notification. */
        CANCEL,

        /** Leave things as they are. */
        NONE
    }

    /**
     * Per-wheel memory: the alert [type] the wheel's notification was posted for, and whether
     * the user [swiped] it away.
     */
    data class ShownAlert(val type: AlertType, val swiped: Boolean = false)

    /** The [action] to take and the wheel's new memory; [shown] is null when nothing is kept. */
    data class NotificationDecision(val action: NotificationAction, val shown: ShownAlert?)

    /**
     * @param shown the wheel's memory from the last decision, or null if nothing is shown.
     * @param current the wheel's current alert type; null when there is no alert or the wheel
     *   left its slot.
     * @param mutedUntilMs end of the mute window (exclusive); 0 or any past time means not muted.
     * @param nowMs current wall-clock time, same clock as [mutedUntilMs].
     */
    fun decide(shown: ShownAlert?, current: AlertType?, mutedUntilMs: Long, nowMs: Long): NotificationDecision {
        val alert = current?.takeIf { it.toSeverity() == AlertSeverity.ALERT }
            ?: return NotificationDecision(
                if (shown != null) NotificationAction.CANCEL else NotificationAction.NONE,
                null
            )
        return when {
            shown == null || shown.type != alert -> NotificationDecision(
                if (isMuted(mutedUntilMs, nowMs)) NotificationAction.POST_SILENTLY else NotificationAction.POST_WITH_SOUND,
                ShownAlert(alert)
            )
            shown.swiped -> NotificationDecision(NotificationAction.NONE, shown)
            else -> NotificationDecision(NotificationAction.UPDATE_SILENTLY, shown)
        }
    }

    /** The user swiped the wheel's notification away: keep it hidden until the type changes. */
    fun onSwiped(shown: ShownAlert): ShownAlert = shown.copy(swiped = true)

    /** True while [nowMs] is before [mutedUntilMs]. */
    fun isMuted(mutedUntilMs: Long, nowMs: Long): Boolean = nowMs < mutedUntilMs
}
