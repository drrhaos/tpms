package com.tpms.app.domain

import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType
import org.json.JSONArray
import org.json.JSONObject

/**
 * How alerts are presented over time (wayfinder alerts 05): the red "Неисправность" window, the
 * yellow "Внимание" banner, the signals and the per-alert notifications. Pure: the coordinator
 * feeds [step] with the current alerts and executes the returned [Plan]; [State] is persisted
 * ([encode] / [decode]) so a process restart in the middle of a trip remembers everything.
 *
 * - **Fault**: one red window lists every alert. Until acknowledged ([acknowledge]) the fault
 *   signal repeats every [FAULT_REPEAT_MS]; afterwards the window and the signal come back after the
 *   reminder interval. A worsening — a new faulty wheel, a new fault type on a wheel, a further
 *   [ESCALATION_STEP_KPA] fall, attention turning into fault, monitoring going blind — shows the
 *   window and rings at once and resets the acknowledgement. Easing to attention is silent.
 * - **Attention**: a yellow banner for [BANNER_MS] with a short signal, once per type per wheel
 *   episode (from the wheel's first alert until it is normal again or left its slot). While the red
 *   window is up, an attention alert only joins it as a row. [muteAttention] silences banners and
 *   signals of attention for [MUTE_MS]; faults are never muted.
 * - Only live data starts or changes anything: without a live session the wheel alerts are kept as
 *   they were and nothing rings for them (monitoring blind is the fault for that).
 * - Screen off: neither window nor signal; what fell due rings when the screen is on again.
 * - A new trip starts from [State] () — everything active rings again.
 */
object AlertPresentation {

    const val FAULT_REPEAT_MS = 30_000L
    const val BANNER_MS = 10_000L
    const val MUTE_MS = 30 * 60_000L
    const val ESCALATION_STEP_KPA = 25f
    const val DEFAULT_REMINDER_MS = 5 * 60_000L
    val REMINDER_CHOICES_MS = listOf(1, 2, 5, 10, 15, 30).map { it * 60_000L }

    /** Key of the "monitoring not working" alert, next to the sensor ids. */
    const val MONITORING_KEY = "monitoring"

    /** A wheel's current alert, as the repository publishes it. [key] = sensor id. */
    data class WheelAlert(
        val key: String,
        val type: AlertType,
        val level: AlertLevel,
        val pressureKpa: Float? = null
    )

    data class Input(
        val wheels: List<WheelAlert>,
        /** The wheel alerts come from a live session. */
        val live: Boolean,
        val monitoringBlind: Boolean,
        val screenOn: Boolean,
        val nowMs: Long,
        val reminderIntervalMs: Long = DEFAULT_REMINDER_MS
    )

    /**
     * One alert being presented. [type] is null for [MONITORING_KEY]. In this episode:
     * [announced] — attention types that had their banner; [faultTypes] — fault types that rang;
     * [rungPressureKpa] — the escalation base.
     */
    data class Entry(
        val key: String,
        val type: AlertType?,
        val level: AlertLevel,
        val announced: Set<AlertType> = emptySet(),
        val faultTypes: Set<AlertType> = emptySet(),
        val rungPressureKpa: Float? = null
    )

    data class State(
        val entries: Map<String, Entry> = emptyMap(),
        /** The red window was acknowledged; it comes back at [nextReminderAtMs]. */
        val acknowledged: Boolean = false,
        val nextReminderAtMs: Long? = null,
        /** Last fault signal; null = one is due now. */
        val lastFaultSoundAtMs: Long? = null,
        /** Yellow banner on screen: whose, and until when. */
        val bannerKey: String? = null,
        val bannerUntilMs: Long = 0L,
        val attentionMutedUntilMs: Long = 0L
    ) {
        val hasFault: Boolean get() = entries.values.any { it.level == AlertLevel.FAULT }
    }

    enum class Sound { FAULT, ATTENTION }

    data class Plan(
        /** Rows of the red window (fault rows first, worst first), or null when it is hidden. */
        val window: List<Entry>?,
        /** The red window is waiting for "Понятно" (pulsing frame). */
        val windowUnacknowledged: Boolean,
        /** Key of the yellow banner on screen, or null. */
        val banner: String?,
        val sound: Sound?,
        /** Every alert that must have a notification now (the notifier cancels the rest). */
        val notifications: List<Entry>,
        /** When [step] must run again even without new input; null = only on input. */
        val nextCheckAtMs: Long?
    )

    fun step(state: State, input: Input): Pair<State, Plan> {
        val now = input.nowMs
        var worsened = false
        val announceBanner = mutableListOf<String>()
        val entries = LinkedHashMap<String, Entry>()

        // Wheels: only live data moves them; otherwise keep what is there.
        if (input.live) {
            for (wheel in input.wheels) {
                val prev = state.entries[wheel.key]
                val (entry, worse, banner) = updateWheel(prev, wheel)
                entries[wheel.key] = entry
                if (worse) worsened = true
                if (banner) announceBanner += wheel.key
            }
        } else {
            state.entries.values.filter { it.key != MONITORING_KEY }.forEach { entries[it.key] = it }
        }
        // Monitoring: a fault of its own.
        if (input.monitoringBlind) {
            val prev = state.entries[MONITORING_KEY]
            if (prev == null) worsened = true
            entries[MONITORING_KEY] = prev ?: Entry(MONITORING_KEY, null, AlertLevel.FAULT)
        }

        var next = state.copy(entries = entries)
        if (!next.hasFault) {
            next = next.copy(acknowledged = false, nextReminderAtMs = null, lastFaultSoundAtMs = null)
        } else if (worsened) {
            next = next.copy(acknowledged = false, nextReminderAtMs = null, lastFaultSoundAtMs = null)
        }
        // The reminder fell due: the window comes back unacknowledged.
        if (next.acknowledged && next.nextReminderAtMs != null && now >= next.nextReminderAtMs!!) {
            next = next.copy(acknowledged = false, nextReminderAtMs = null, lastFaultSoundAtMs = null)
        }

        val windowUp = next.hasFault && !next.acknowledged && input.screenOn
        var sound: Sound? = null
        if (windowUp) {
            val last = next.lastFaultSoundAtMs
            if (last == null || now - last >= FAULT_REPEAT_MS || now < last) {
                sound = Sound.FAULT
                next = next.copy(lastFaultSoundAtMs = now)
            }
        }

        // Yellow banner: not over the red window, not while muted, not with the screen off.
        val muted = now < next.attentionMutedUntilMs
        if (next.bannerKey != null && (now >= next.bannerUntilMs || windowUp || next.bannerKey !in entries)) {
            next = next.copy(bannerKey = null, bannerUntilMs = 0L)
        }
        val bannerKey = announceBanner.lastOrNull()
        if (bannerKey != null && !windowUp && !muted && input.screenOn) {
            next = next.copy(bannerKey = bannerKey, bannerUntilMs = now + BANNER_MS)
            if (sound == null) sound = Sound.ATTENTION
        }

        val ordered = ordered(next.entries.values)
        val plan = Plan(
            window = if (windowUp) ordered else null,
            windowUnacknowledged = windowUp,
            banner = next.bannerKey,
            sound = sound,
            notifications = ordered,
            nextCheckAtMs = nextCheck(next, windowUp, now)
        )
        return next to plan
    }

    /** "Понятно": hides the red window until the reminder. No fault: nothing to acknowledge. */
    fun acknowledge(state: State, nowMs: Long, reminderIntervalMs: Long): State =
        if (!state.hasFault) state else state.copy(acknowledged = true, nextReminderAtMs = nowMs + reminderIntervalMs)

    /** "Без звука 30 мин": attention banners and signals stop for [MUTE_MS]; faults still ring. */
    fun muteAttention(state: State, nowMs: Long): State =
        state.copy(attentionMutedUntilMs = nowMs + MUTE_MS, bannerKey = null, bannerUntilMs = 0L)

    /** The user closed the yellow banner early. */
    fun dismissBanner(state: State): State = state.copy(bannerKey = null, bannerUntilMs = 0L)

    /** Returns (entry, worsened, ring an attention banner). */
    private fun updateWheel(prev: Entry?, wheel: WheelAlert): Triple<Entry, Boolean, Boolean> {
        // Pressure means something only for the pressure-loss types (a lost sensor reports junk).
        val pressure = wheel.pressureKpa?.takeIf {
            it.isFinite() && (wheel.type == AlertType.LOW_PRESSURE || wheel.type == AlertType.PRESSURE_DROP)
        }
        val announced = prev?.announced.orEmpty()
        val faultTypes = prev?.faultTypes.orEmpty()
        if (wheel.level == AlertLevel.FAULT) {
            val base = Entry(wheel.key, wheel.type, AlertLevel.FAULT, announced, faultTypes + wheel.type)
            // New fault on this wheel, attention turned into fault, or a fault type not rung yet.
            if (prev == null || prev.level != AlertLevel.FAULT || wheel.type !in faultTypes) {
                return Triple(base.copy(rungPressureKpa = pressure ?: prev?.rungPressureKpa), true, false)
            }
            if (escalated(prev.rungPressureKpa, pressure)) return Triple(base.copy(rungPressureKpa = pressure), true, false)
            return Triple(base.copy(rungPressureKpa = prev.rungPressureKpa ?: pressure), false, false)
        }
        // Attention: a type new to the episode gets a banner; easing from a fault is silent.
        val fromFault = prev?.level == AlertLevel.FAULT
        val isNew = wheel.type !in announced
        val entry = Entry(wheel.key, wheel.type, AlertLevel.ATTENTION, announced + wheel.type, faultTypes, prev?.rungPressureKpa)
        return Triple(entry, false, isNew && !fromFault)
    }

    private fun escalated(rung: Float?, pressure: Float?): Boolean =
        rung != null && pressure != null && pressure <= rung - ESCALATION_STEP_KPA

    private val LEVEL_THEN_TYPE = Comparator<Entry> { a, b ->
        val byLevel = b.level.compareTo(a.level)
        if (byLevel != 0) return@Comparator byLevel
        // Monitoring first among faults: without it no other row is trustworthy.
        val ta = a.type ?: return@Comparator -1
        val tb = b.type ?: return@Comparator 1
        AlertRank.compare(tb to b.level, ta to a.level)
    }

    private fun ordered(entries: Collection<Entry>): List<Entry> = entries.sortedWith(LEVEL_THEN_TYPE)

    private fun nextCheck(state: State, windowUp: Boolean, now: Long): Long? {
        val times = buildList {
            if (windowUp) add((state.lastFaultSoundAtMs ?: now) + FAULT_REPEAT_MS)
            if (state.acknowledged) state.nextReminderAtMs?.let { add(it) }
            if (state.bannerKey != null) add(state.bannerUntilMs)
        }
        return times.minOrNull()
    }

    // --- Persistence ---

    fun encode(state: State): String = JSONObject().apply {
        put("acknowledged", state.acknowledged)
        state.nextReminderAtMs?.let { put("next_reminder_ms", it) }
        put("muted_until_ms", state.attentionMutedUntilMs)
        put("entries", JSONArray().apply {
            state.entries.values.forEach { e ->
                put(JSONObject().apply {
                    put("key", e.key)
                    e.type?.let { put("type", it.name) }
                    put("level", e.level.name)
                    put("announced", JSONArray(e.announced.map { it.name }))
                    put("fault_types", JSONArray(e.faultTypes.map { it.name }))
                    e.rungPressureKpa?.let { put("rung_kpa", it.toDouble()) }
                })
            }
        })
    }.toString()

    /**
     * The state [encode] produced; unreadable input gives an empty state. The fault signal is due
     * again and no banner is up (timers that are not worth persisting).
     */
    fun decode(encoded: String?): State {
        if (encoded.isNullOrBlank()) return State()
        val root = runCatching { JSONObject(encoded) }.getOrNull() ?: return State()
        val entries = LinkedHashMap<String, Entry>()
        val array = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val key = obj.optString("key").takeIf { it.isNotBlank() } ?: continue
            val level = runCatching { AlertLevel.valueOf(obj.getString("level")) }.getOrNull() ?: continue
            val type = obj.optString("type").takeIf { it.isNotBlank() }?.let { name -> AlertType.entries.firstOrNull { it.name == name } }
            if (type == null && key != MONITORING_KEY) continue
            val rung = obj.optDouble("rung_kpa", Double.NaN).toFloat().takeIf { it.isFinite() }
            entries[key] = Entry(key, type, level, types(obj.optJSONArray("announced")), types(obj.optJSONArray("fault_types")), rung)
        }
        return State(
            entries = entries,
            acknowledged = root.optBoolean("acknowledged", false),
            nextReminderAtMs = if (root.has("next_reminder_ms")) root.optLong("next_reminder_ms") else null,
            attentionMutedUntilMs = root.optLong("muted_until_ms", 0L)
        )
    }

    private fun types(names: JSONArray?): Set<AlertType> {
        if (names == null) return emptySet()
        return (0 until names.length()).mapNotNull { j -> AlertType.entries.firstOrNull { it.name == names.optString(j) } }.toSet()
    }
}
