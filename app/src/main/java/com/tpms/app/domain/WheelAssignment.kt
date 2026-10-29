package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

/**
 * Which seen sensor sits on which wheel (wayfinder 11). Pure: seen readings + per-slot mapping +
 * spare toggle + AA55 empty slots in, "slot label → sensor" out.
 *
 * - Default ("Авто") slot: AA55/Deelife receiver label `FL/FR/RL/RR/SP`, or HID id
 *   `SENSOR_01..04` → `FL..RR`, `SENSOR_05` → `SP`. Any other sensor reaches a wheel only via mapping.
 * - A non-blank mapping changes only its own slot; a mapped but unseen sensor leaves the slot empty.
 * - One sensor occupies at most one slot: an explicit mapping wins over the sensor's default slot;
 *   if one id is mapped to several slots, the first slot in [WheelLayout.allSlots] order keeps it.
 * - A mapping to `SP` counts even while the spare is hidden: the sensor leaves its default slot and,
 *   with the spare off, is on no wheel at all (unassigned) until the spare is shown again.
 * - No order-based fallback.
 */
object WheelAssignment {
    private val HID_DEFAULT_SLOTS = mapOf(
        "SENSOR_01" to "FL",
        "SENSOR_02" to "FR",
        "SENSOR_03" to "RL",
        "SENSOR_04" to "RR",
        "SENSOR_05" to WheelLayout.SPARE_SLOT
    )

    /** How long a seen sensor that is on no wheel and not mapped is kept for the mapping UI. */
    const val SEEN_SENSOR_MAX_AGE_MS = 30 * 60_000L

    fun defaultSlot(sensor: TireSensor): String? =
        sensor.label.takeIf { it in WheelLayout.allSlots(showSpareWheel = true) }
            ?: HID_DEFAULT_SLOTS[sensor.id]

    /**
     * @param wheelMapping slot → sensor id, blank for "Авто"; the `SP` entry is honoured as "taken off
     *   its default slot" even when [showSpareWheel] is false.
     * @param emptySlots receiver slots the AA55 dongle reported as empty; sensors carrying such a
     *   label are never placed, not even through a mapping.
     * @return slot label → sensor, only for slots of [WheelLayout.allSlots] that got a sensor,
     *   in slot order.
     */
    fun assign(
        seen: Map<String, TireSensor>,
        wheelMapping: Map<String, String>,
        showSpareWheel: Boolean,
        emptySlots: Set<String> = emptySet()
    ): Map<String, TireSensor> {
        val slots = WheelLayout.allSlots(showSpareWheel)
        val placeable = seen.filterValues { it.label !in emptySlots }
        val mappedIds = mappedIds(wheelMapping)
        val used = mutableSetOf<String>()
        val result = LinkedHashMap<String, TireSensor>()
        for (slot in slots) {
            val mappedId = wheelMapping[slot]?.takeIf { it.isNotBlank() }
            val sensor = if (mappedId != null) {
                placeable[mappedId]
            } else {
                placeable.values.firstOrNull { defaultSlot(it) == slot && it.id !in mappedIds }
            }
            if (sensor != null && used.add(sensor.id)) result[slot] = sensor
        }
        return result
    }

    /**
     * Bounds the seen-sensor list: keeps sensors on a wheel ([wheels], slot → sensor), sensors mapped to
     * any slot (the hidden spare included) and sensors read within [maxAgeMs] of [now]; drops the rest
     * (foreign sensors passing by, placeholders of an AA55 slot that later answered empty).
     */
    fun retainSeen(
        seen: Map<String, TireSensor>,
        wheels: Map<String, TireSensor>,
        wheelMapping: Map<String, String>,
        now: Long,
        maxAgeMs: Long = SEEN_SENSOR_MAX_AGE_MS
    ): Map<String, TireSensor> {
        val keep = wheels.values.mapTo(HashSet()) { it.id } + mappedIds(wheelMapping)
        return seen.filterValues { it.id in keep || now - it.timestamp <= maxAgeMs }
    }

    /**
     * Mapping entries that swap the sensors of wheels [a] and [b] (tyre rotation): each slot gets
     * the other's sensor — the one on it now ([wheels]), else its mapped id. A sensor landing on its
     * default slot is stored as "Авто" (blank); a wheel without a sensor hands over a blank.
     * Fed back through [assign], the two wheels come out swapped.
     */
    fun swap(
        seen: Map<String, TireSensor>,
        wheelMapping: Map<String, String>,
        wheels: Map<String, TireSensor>,
        a: String,
        b: String
    ): Map<String, String> {
        fun onWheel(slot: String) = wheels[slot]?.id ?: wheelMapping[slot]?.takeIf { it.isNotBlank() }
        fun entry(slot: String, id: String?) = when {
            id == null -> ""
            seen[id]?.let(::defaultSlot) == slot -> ""
            else -> id
        }
        val onA = onWheel(a)
        val onB = onWheel(b)
        return mapOf(a to entry(a, onB), b to entry(b, onA))
    }

    /** Ids explicitly mapped to any slot, whether or not the slot is shown. */
    private fun mappedIds(wheelMapping: Map<String, String>): Set<String> =
        WheelLayout.allSlots(showSpareWheel = true)
            .mapNotNull { slot -> wheelMapping[slot]?.takeIf { it.isNotBlank() } }
            .toSet()

    /** Seen sensors that are on no wheel (for mapping UI and diagnostics), sorted by id. */
    fun unassigned(seen: Map<String, TireSensor>, assignment: Map<String, TireSensor>): List<TireSensor> {
        val placedIds = assignment.values.mapTo(HashSet()) { it.id }
        return seen.values.filter { it.id !in placedIds }.sortedBy { it.id }
    }
}
