package com.tpms.app.domain

import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.AlertNotificationPolicy.ShownAlert
import com.tpms.app.domain.model.AlertType

/**
 * The alert notifier's per-wheel memory (keyed by sensor id) around [AlertNotificationPolicy].
 * Pure and not thread-safe: the notifier uses it from one thread.
 *
 * Adds to the policy:
 * - **Resync** (service start, app resume): a post the policy wants with sound is made silently,
 *   so alerts already active before a process restart do not ring again.
 * - **Update throttle**: [NotificationAction.UPDATE_SILENTLY] at most once per [updateIntervalMs]
 *   per wheel (the next reading after the interval carries the latest values).
 * - **Restore** from notifications still in the shade (they survive a process kill), so the same
 *   alert arriving again updates silently instead of ringing.
 */
class AlertNotificationBook(private val updateIntervalMs: Long = UPDATE_INTERVAL_MS) {

    private val shown = HashMap<String, ShownAlert>()
    private val lastPostAtMs = HashMap<String, Long>()

    /** What to do with [key]'s notification now that its alert is [current] (null = normal / left its slot). */
    fun onChange(
        key: String,
        current: AlertType?,
        mutedUntilMs: Long,
        nowMs: Long,
        resync: Boolean = false
    ): NotificationAction {
        val decision = AlertNotificationPolicy.decide(shown[key], current, mutedUntilMs, nowMs)
        val next = decision.shown
        if (next == null) {
            shown.remove(key)
            lastPostAtMs.remove(key)
        } else {
            shown[key] = next
        }
        val action = when {
            resync && decision.action == NotificationAction.POST_WITH_SOUND -> NotificationAction.POST_SILENTLY
            decision.action == NotificationAction.UPDATE_SILENTLY && updatedRecently(key, nowMs) -> NotificationAction.NONE
            else -> decision.action
        }
        if (action == NotificationAction.POST_WITH_SOUND ||
            action == NotificationAction.POST_SILENTLY ||
            action == NotificationAction.UPDATE_SILENTLY
        ) {
            lastPostAtMs[key] = nowMs
        }
        return action
    }

    /** The user swiped [key]'s notification away; false if nothing is known about it. */
    fun onSwiped(key: String): Boolean {
        val current = shown[key] ?: return false
        shown[key] = AlertNotificationPolicy.onSwiped(current)
        return true
    }

    /**
     * Remembers a notification found in the shade for [key] (posted for [type] by an earlier
     * process). Known keys and non-ALERT types are ignored; returns whether it was taken.
     */
    fun restore(key: String, type: AlertType): Boolean {
        if (key in shown || type.toSeverity() != AlertSeverity.ALERT) return false
        shown[key] = ShownAlert(type)
        return true
    }

    /** Forgets [key] (its notification is gone or cannot be shown); returns whether it was known. */
    fun forget(key: String): Boolean {
        lastPostAtMs.remove(key)
        return shown.remove(key) != null
    }

    /** Keys with a notification posted or swiped. */
    fun keys(): Set<String> = shown.keys.toSet()

    fun clear() {
        shown.clear()
        lastPostAtMs.clear()
    }

    /** A clock that went back counts as "long ago": the update goes through. */
    private fun updatedRecently(key: String, nowMs: Long): Boolean {
        val last = lastPostAtMs[key] ?: return false
        return nowMs >= last && nowMs - last < updateIntervalMs
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
