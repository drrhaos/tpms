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

    fun defaultSlot(sensor: TireSensor): String? =
        sensor.label.takeIf { it in WheelLayout.allSlots(showSpareWheel = true) }
            ?: HID_DEFAULT_SLOTS[sensor.id]

    /**
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
        val mappedIds = slots.mapNotNull { slot -> wheelMapping[slot]?.takeIf { it.isNotBlank() } }.toSet()
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

    /** Seen sensors that are on no wheel (for mapping UI and diagnostics), sorted by id. */
    fun unassigned(seen: Map<String, TireSensor>, assignment: Map<String, TireSensor>): List<TireSensor> {
        val placedIds = assignment.values.mapTo(HashSet()) { it.id }
        return seen.values.filter { it.id !in placedIds }.sortedBy { it.id }
    }
}
