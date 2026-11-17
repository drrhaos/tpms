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
 * - **Windows close only by the user** (drhaos, 2026-11-17): the red window by «Понятно»
 *   ([acknowledge]), the yellow banner by «×» ([dismissBanner]). An alert that cleared meanwhile stays
 *   in its window as a resolved row ([Plan.windowResolved] / [Plan.bannerResolved]); with the screen
 *   off nothing is drawn, the windows come back with the screen.
 * - **Fault**: one red window lists every alert. Until acknowledged the fault signal repeats every
 *   [FAULT_REPEAT_MS]; afterwards the window and the signal come back after the reminder interval.
 *   A worsening — a new faulty wheel, a new fault type on a wheel, a further [ESCALATION_STEP_KPA]
 *   fall, attention turning into fault, monitoring going blind — opens the window and rings at once
 *   and resets the acknowledgement. Easing to attention is silent (an open window stays).
 * - **Attention**: a yellow banner with a short signal, once per type per wheel episode (from the
 *   wheel's first alert until it is normal again or left its slot); newer alerts join an open banner.
 *   While the red window is up, an attention alert only joins it as a row. [muteAttention] silences
 *   banners and signals of attention for [MUTE_MS]; faults are never muted.
 * - Only live data starts or changes anything: without a live session the wheel alerts are kept as
 *   they were and nothing rings for them (monitoring blind is the fault for that).
 * - Screen off: neither window nor signal; what fell due rings when the screen is on again.
 * - A new trip ([newTrip]) forgets episodes, «Понятно» and mute — everything active rings again —
 *   but keeps the windows still open.
 */
object AlertPresentation {

    const val FAULT_REPEAT_MS = 30_000L
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
        /** The red window is open (until «Понятно»); [windowKeys] — every alert it has shown. */
        val windowOpen: Boolean = false,
        val windowKeys: Set<String> = emptySet(),
        /** Alerts of the open yellow banner (until «×»), oldest first. */
        val bannerKeys: List<String> = emptyList(),
        val attentionMutedUntilMs: Long = 0L
    ) {
        val hasFault: Boolean get() = entries.values.any { it.level == AlertLevel.FAULT }
    }

    enum class Sound { FAULT, ATTENTION }

    data class Plan(
        /** Active rows of the red window (fault rows first, worst first), or null when it is hidden. */
        val window: List<Entry>?,
        /** Keys of the red window whose alert cleared since it opened (shown as resolved). */
        val windowResolved: List<String>,
        /** A fault waits for "Понятно" (pulsing frame, repeating signal). */
        val windowUnacknowledged: Boolean,
        /** Active alerts of the yellow banner, or null when it is hidden. */
        val banner: List<Entry>?,
        /** Keys of the yellow banner whose alert cleared since it opened. */
        val bannerResolved: List<String>,
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

        // An unacknowledged fault opens the red window; only «Понятно» closes it.
        val ringing = next.hasFault && !next.acknowledged
        if (ringing && !next.windowOpen) next = next.copy(windowOpen = true)
        if (next.windowOpen) next = next.copy(windowKeys = next.windowKeys + entries.keys)
        val windowUp = next.windowOpen && input.screenOn
        var sound: Sound? = null
        if (ringing && input.screenOn) {
            val last = next.lastFaultSoundAtMs
            if (last == null || now - last >= FAULT_REPEAT_MS || now < last) {
                sound = Sound.FAULT
                next = next.copy(lastFaultSoundAtMs = now)
            }
        }

        // Yellow banner: new attention alerts join it (not over the red window, not while muted,
        // not with the screen off — then they only count as announced); only «×» closes it.
        val muted = now < next.attentionMutedUntilMs
        if (announceBanner.isNotEmpty() && !windowUp && !muted && input.screenOn) {
            next = next.copy(bannerKeys = next.bannerKeys + announceBanner.filter { it !in next.bannerKeys })
            if (sound == null) sound = Sound.ATTENTION
        }
        val bannerUp = next.bannerKeys.isNotEmpty() && input.screenOn && !windowUp

        val ordered = ordered(next.entries.values)
        val plan = Plan(
            window = if (windowUp) ordered else null,
            windowResolved = if (windowUp) next.windowKeys.filter { it !in entries }.sorted() else emptyList(),
            windowUnacknowledged = windowUp && ringing,
            banner = if (bannerUp) ordered(next.bannerKeys.mapNotNull { entries[it] }) else null,
            bannerResolved = if (bannerUp) next.bannerKeys.filter { it !in entries } else emptyList(),
            sound = sound,
            notifications = ordered,
            nextCheckAtMs = nextCheck(next, ringing && input.screenOn, now)
        )
        return next to plan
    }

    /**
     * "Понятно": closes the red window; an active fault comes back with it after the reminder
     * interval.
     */
    fun acknowledge(state: State, nowMs: Long, reminderIntervalMs: Long): State {
        val closed = state.copy(windowOpen = false, windowKeys = emptySet())
        return if (!state.hasFault) closed else closed.copy(acknowledged = true, nextReminderAtMs = nowMs + reminderIntervalMs)
    }

    /** "Без звука 30 мин": attention banners and signals stop for [MUTE_MS]; faults still ring. */
    fun muteAttention(state: State, nowMs: Long): State = state.copy(attentionMutedUntilMs = nowMs + MUTE_MS)

    /** «×»: closes the yellow banner. */
    fun dismissBanner(state: State): State = state.copy(bannerKeys = emptyList())

    /**
     * A new trip: episodes, «Понятно» and mute are forgotten, so everything active is presented and
     * rings again; windows still open stay open — only the user closes them.
     */
    fun newTrip(state: State): State =
        State(windowOpen = state.windowOpen, windowKeys = state.windowKeys, bannerKeys = state.bannerKeys)

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
        }
        return times.minOrNull()
    }

    // --- Persistence ---

    fun encode(state: State): String = JSONObject().apply {
        put("acknowledged", state.acknowledged)
        state.nextReminderAtMs?.let { put("next_reminder_ms", it) }
        put("muted_until_ms", state.attentionMutedUntilMs)
        put("window_open", state.windowOpen)
        put("window_keys", JSONArray(state.windowKeys.toList()))
        put("banner_keys", JSONArray(state.bannerKeys))
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
     * again (its timer is not worth persisting).
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
            attentionMutedUntilMs = root.optLong("muted_until_ms", 0L),
            windowOpen = root.optBoolean("window_open", false),
            windowKeys = strings(root.optJSONArray("window_keys")).toSet(),
            bannerKeys = strings(root.optJSONArray("banner_keys"))
        )
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun types(names: JSONArray?): Set<AlertType> {
        if (names == null) return emptySet()
        return (0 until names.length()).mapNotNull { j -> AlertType.entries.firstOrNull { it.name == names.optString(j) } }.toSet()
    }
}
