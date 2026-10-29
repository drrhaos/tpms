package com.tpms.app.domain

import com.tpms.app.domain.model.AlertType

/**
 * Decides, per wheel, what the alert notifier does with that wheel's notification. Pure: the
 * notifier keeps one [ShownAlert] per wheel (keyed by wheel/sensor id), feeds it back on every
 * change and stores the returned one.
 *
 * - Notified types: ALERT severity (LOW_PRESSURE, HIGH_PRESSURE, SENSOR_LOST) and HIGH_TEMP (an
 *   overheating tyre precedes a failure); see [notifies]. BATTERY_LOW or no alert cancels what is shown.
 * - **Episode**: from the wheel's first notified alert until it clears (or the wheel leaves its
 *   slot). Each alert type rings once per episode: a type not seen yet in the episode posts with
 *   sound; the same type, or a return to a type already seen, updates silently (content such as
 *   pressure may change). So SENSOR_LOST <-> LOW_PRESSURE flapping rings at most twice.
 * - **Escalation**: LOW_PRESSURE rings again whenever the pressure fell another
 *   [ESCALATION_STEP_KPA] below the value it last rang with, even if swiped or muted.
 * - A swiped notification stays hidden until a type new to the episode appears or it escalates.
 * - **Mute** covers only the wheels that were alerting when the user muted ([ShownAlert.muted]):
 *   their new types post silently. A wheel with no notification yet always rings.
 * - A cleared alert, or a wheel that left its slot, cancels and forgets, episode and swipe included.
 */
object AlertNotificationPolicy {

    /** A further pressure drop that rings an ongoing LOW_PRESSURE again (0.25 bar). */
    const val ESCALATION_STEP_KPA = 25f

    /** What the notifier must do with the wheel's notification. */
    enum class NotificationAction {
        /** Post (or replace) with sound and heads-up. */
        POST_WITH_SOUND,

        /** Post (or replace) without sound or heads-up: the wheel is muted. */
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
     * this [episode] (already rung, or posted while muted; includes [type]), whether the user
     * [swiped] it away, whether it was alerting when the user muted ([muted]), and the pressure the
     * last LOW_PRESSURE ring was made with ([rungPressureKpa], base of the escalation).
     */
    data class ShownAlert(
        val type: AlertType,
        val episode: Set<AlertType> = setOf(type),
        val swiped: Boolean = false,
        val muted: Boolean = false,
        val rungPressureKpa: Float? = null
    )

    /** The [action] to take and the wheel's new memory; [shown] is null when nothing is kept. */
    data class NotificationDecision(val action: NotificationAction, val shown: ShownAlert?)

    /**
     * @param shown the wheel's memory from the last decision, or null if nothing is shown.
     * @param current the wheel's current alert type; null when there is no alert or the wheel
     *   left its slot.
     * @param mutedUntilMs end of the mute window (exclusive); 0 or any past time means not muted.
     *   It applies only to a wheel whose memory is [ShownAlert.muted].
     * @param nowMs current wall-clock time, same clock as [mutedUntilMs].
     * @param pressureKpa the wheel's current pressure, for the LOW_PRESSURE escalation; null if unknown.
     */
    fun decide(
        shown: ShownAlert?,
        current: AlertType?,
        mutedUntilMs: Long,
        nowMs: Long,
        pressureKpa: Float? = null
    ): NotificationDecision {
        val alert = current?.takeIf { it.notifies() }
            ?: return NotificationDecision(
                if (shown != null) NotificationAction.CANCEL else NotificationAction.NONE,
                null
            )
        val pressure = pressureKpa?.takeIf { alert == AlertType.LOW_PRESSURE && it.isFinite() }
        return when {
            shown == null -> NotificationDecision(
                NotificationAction.POST_WITH_SOUND,
                ShownAlert(alert, rungPressureKpa = pressure)
            )
            alert !in shown.episode -> NotificationDecision(
                if (shown.muted && isMuted(mutedUntilMs, nowMs)) NotificationAction.POST_SILENTLY else NotificationAction.POST_WITH_SOUND,
                shown.copy(
                    type = alert,
                    episode = shown.episode + alert,
                    swiped = false,
                    rungPressureKpa = pressure ?: shown.rungPressureKpa
                )
            )
            escalated(shown, pressure) -> NotificationDecision(
                NotificationAction.POST_WITH_SOUND,
                shown.copy(type = alert, swiped = false, rungPressureKpa = pressure)
            )
            shown.swiped -> NotificationDecision(NotificationAction.NONE, shown.copy(type = alert, rungPressureKpa = shown.rungPressureKpa ?: pressure))
            else -> NotificationDecision(
                NotificationAction.UPDATE_SILENTLY,
                shown.copy(type = alert, rungPressureKpa = shown.rungPressureKpa ?: pressure)
            )
        }
    }

    /** The user swiped the wheel's notification away: keep it hidden until a new type appears in the episode. */
    fun onSwiped(shown: ShownAlert): ShownAlert = shown.copy(swiped = true)

    /** The user muted: the wheel's alerts of this episode post silently while the mute window lasts. */
    fun onMuted(shown: ShownAlert): ShownAlert = shown.copy(muted = true)

    /** True while [nowMs] is before [mutedUntilMs]. */
    fun isMuted(mutedUntilMs: Long, nowMs: Long): Boolean = nowMs < mutedUntilMs

    /** LOW_PRESSURE fell at least [ESCALATION_STEP_KPA] below the pressure it last rang with. */
    private fun escalated(shown: ShownAlert, pressure: Float?): Boolean {
        val rung = shown.rungPressureKpa ?: return false
        return pressure != null && pressure <= rung - ESCALATION_STEP_KPA
    }
}
