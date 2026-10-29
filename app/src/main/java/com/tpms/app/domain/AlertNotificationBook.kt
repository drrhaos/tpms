package com.tpms.app.domain

import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.AlertNotificationPolicy.ShownAlert
import com.tpms.app.domain.model.AlertType
import org.json.JSONArray
import org.json.JSONObject

/**
 * The alert notifier's per-wheel memory (keyed by sensor id) around [AlertNotificationPolicy].
 * Pure and not thread-safe: the notifier uses it from one thread.
 *
 * Adds to the policy:
 * - **Two phases**: [plan] decides without touching the memory, [commit] applies the decision once
 *   the notification was actually posted (or cancelled). A failed post leaves the memory as it
 *   was, so the next reading tries again, with sound if the decision had it.
 * - **Update throttle**: a silent update of the same type at most once per [updateIntervalMs] per
 *   wheel; a throttled step carries [Step.retryInMs], when the notifier must try again so the
 *   latest values are shown even if readings stop. Posts, type changes and cancels are never
 *   throttled.
 * - **Persistence**: [encode] / [load] (episode types, current type, swipe), so the memory survives
 *   a process kill. The throttle clock is not persisted.
 */
class AlertNotificationBook(private val updateIntervalMs: Long = UPDATE_INTERVAL_MS) {

    private val shown = HashMap<String, ShownAlert>()
    private val lastPostAtMs = HashMap<String, Long>()

    /** One wheel's decision; the memory changes only on [commit]. */
    class Step internal constructor(
        val key: String,
        val action: NotificationAction,
        /** Throttled update: try again after this many ms; null when nothing is deferred. */
        val retryInMs: Long?,
        internal val next: ShownAlert?,
        internal val nowMs: Long
    )

    /** What to do with [key]'s notification now that its alert is [current] (null = normal / left its slot). */
    fun plan(key: String, current: AlertType?, mutedUntilMs: Long, nowMs: Long): Step {
        val previous = shown[key]
        val decision = AlertNotificationPolicy.decide(previous, current, mutedUntilMs, nowMs)
        val throttled = decision.action == NotificationAction.UPDATE_SILENTLY &&
            decision.shown?.type == previous?.type &&
            updatedRecently(key, nowMs)
        return if (throttled) {
            Step(key, NotificationAction.NONE, retryIn(key, nowMs), decision.shown, nowMs)
        } else {
            Step(key, decision.action, null, decision.shown, nowMs)
        }
    }

    /**
     * Applies [step] to the memory: call it after the notification was posted or cancelled (always
     * for [NotificationAction.NONE] and [NotificationAction.CANCEL]). Returns whether the persisted
     * part of the memory changed (time to [encode] and save it).
     */
    fun commit(step: Step): Boolean {
        val before = shown[step.key]
        val next = step.next
        if (next == null) {
            shown.remove(step.key)
            lastPostAtMs.remove(step.key)
        } else {
            shown[step.key] = next
        }
        if (step.action == NotificationAction.POST_WITH_SOUND ||
            step.action == NotificationAction.POST_SILENTLY ||
            step.action == NotificationAction.UPDATE_SILENTLY
        ) {
            lastPostAtMs[step.key] = step.nowMs
        }
        return before != next
    }

    /** [plan] and [commit] in one go (the post cannot fail); returns the action. */
    fun onChange(key: String, current: AlertType?, mutedUntilMs: Long, nowMs: Long): NotificationAction =
        plan(key, current, mutedUntilMs, nowMs).also { commit(it) }.action

    /** The user swiped [key]'s notification away; false if nothing is known about it (or already swiped). */
    fun onSwiped(key: String): Boolean {
        val current = shown[key] ?: return false
        if (current.swiped) return false
        shown[key] = AlertNotificationPolicy.onSwiped(current)
        return true
    }

    /** [key]'s memory, null when nothing is posted or swiped for it. */
    fun shown(key: String): ShownAlert? = shown[key]

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

    /** The persisted part of the memory, for [load]. */
    fun encode(): String = JSONObject().apply {
        shown.forEach { (key, alert) ->
            put(
                key,
                JSONObject()
                    .put(FIELD_TYPE, alert.type.name)
                    .put(FIELD_EPISODE, JSONArray(alert.episode.map { it.name }))
                    .put(FIELD_SWIPED, alert.swiped)
            )
        }
    }.toString()

    /** Replaces the memory with what [encode] produced; unreadable input or entries are skipped. */
    fun load(encoded: String?) {
        clear()
        if (encoded.isNullOrBlank()) return
        val root = runCatching { JSONObject(encoded) }.getOrNull() ?: return
        root.keys().forEach { key ->
            runCatching { decodeEntry(root.getJSONObject(key)) }.getOrNull()?.let { shown[key] = it }
        }
    }

    /** A clock that went back counts as "long ago": the update goes through. */
    private fun updatedRecently(key: String, nowMs: Long): Boolean {
        val last = lastPostAtMs[key] ?: return false
        return nowMs >= last && nowMs - last < updateIntervalMs
    }

    private fun retryIn(key: String, nowMs: Long): Long {
        val last = lastPostAtMs[key] ?: return 0L
        return (last + updateIntervalMs - nowMs).coerceIn(0L, updateIntervalMs)
    }

    private fun decodeEntry(entry: JSONObject): ShownAlert? {
        val type = alertTypeOf(entry.getString(FIELD_TYPE)) ?: return null
        val names = entry.optJSONArray(FIELD_EPISODE)
        val episode = buildSet {
            add(type)
            if (names != null) for (i in 0 until names.length()) alertTypeOf(names.optString(i))?.let { add(it) }
        }
        return ShownAlert(type, episode, entry.optBoolean(FIELD_SWIPED, false))
    }

    /** Only ALERT types are kept; anything else (old names, warnings) is dropped. */
    private fun alertTypeOf(name: String?): AlertType? =
        AlertType.entries.firstOrNull { it.name == name }?.takeIf { it.toSeverity() == AlertSeverity.ALERT }

    companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        private const val FIELD_TYPE = "type"
        private const val FIELD_EPISODE = "episode"
        private const val FIELD_SWIPED = "swiped"
    }
}
