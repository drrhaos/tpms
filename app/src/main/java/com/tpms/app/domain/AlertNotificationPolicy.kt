package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType

/**
 * Decides, per wheel, what the alert notifier does with that wheel's notification. Pure: the
 * notifier keeps one [ShownAlert] per wheel (keyed by wheel/sensor id), feeds it back on every
 * change and stores the returned one.
 *
 * - Only ALERT severity (LOW_PRESSURE, HIGH_PRESSURE, SENSOR_LOST) gets a notification; a warning
 *   or no alert cancels what is shown.
 * - **Episode**: from the wheel's first ALERT until the alert clears (or the wheel leaves its
 *   slot). Each alert type rings once per episode: a type not seen yet in the episode posts with
 *   sound (or silently while muted); the same type, or a return to a type already seen, updates
 *   silently (content such as pressure may change). So SENSOR_LOST <-> LOW_PRESSURE flapping
 *   rings at most twice.
 * - A swiped notification stays hidden until a type new to the episode appears.
 * - A cleared alert, or a wheel that left its slot, cancels and forgets, episode and swipe included.
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
     * Per-wheel memory: the alert [type] the wheel's notification shows now, every type seen in
     * this [episode] (already rung, or posted while muted; includes [type]), and whether the user
     * [swiped] it away.
     */
    data class ShownAlert(
        val type: AlertType,
        val episode: Set<AlertType> = setOf(type),
        val swiped: Boolean = false
    )

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
            shown == null || alert !in shown.episode -> NotificationDecision(
                if (isMuted(mutedUntilMs, nowMs)) NotificationAction.POST_SILENTLY else NotificationAction.POST_WITH_SOUND,
                ShownAlert(alert, episode = shown?.episode.orEmpty() + alert)
            )
            shown.swiped -> NotificationDecision(NotificationAction.NONE, shown.copy(type = alert))
            else -> NotificationDecision(NotificationAction.UPDATE_SILENTLY, shown.copy(type = alert))
        }
    }

    /** The user swiped the wheel's notification away: keep it hidden until a new type appears in the episode. */
    fun onSwiped(shown: ShownAlert): ShownAlert = shown.copy(swiped = true)

    /** True while [nowMs] is before [mutedUntilMs]. */
    fun isMuted(mutedUntilMs: Long, nowMs: Long): Boolean = nowMs < mutedUntilMs
}
