package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    const val SPARE_SLOT = "SP"
    val FALLBACK_IDS = listOf("SENSOR_01", "SENSOR_02", "SENSOR_03", "SENSOR_04")

    fun isSpare(sensor: TireSensor): Boolean =
        sensor.id == SPARE_SLOT || sensor.label == SPARE_SLOT

    fun allSlots(showSpareWheel: Boolean = false): List<String> =
        if (showSpareWheel) ORDER + SPARE_SLOT else ORDER

    fun orderedSlots(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap(),
        showSpareWheel: Boolean = false
    ): List<TireSensor?> {
        val slots = allSlots(showSpareWheel)
        val hasCustomMapping = wheelMapping.values.any { it.isNotBlank() }
        val usableSensors = if (showSpareWheel) {
            sensors
        } else {
            sensors.filterKeys { it != SPARE_SLOT }
                .filterValues { it.label != SPARE_SLOT }
        }
        if (hasCustomMapping) {
            return slots.map { label ->
                wheelMapping[label]?.takeIf { it.isNotBlank() }?.let { usableSensors[it] }
            }
        }
        val placed = slots.mapIndexed { index, label ->
            usableSensors[label] ?: usableSensors[FALLBACK_IDS.getOrNull(index)]
        }.toMutableList()
        // Only sensors without a wheel label (HID) may fill a free slot by order. A sensor labelled
        // for another position must not be shown on a wheel it does not belong to.
        val usedIds = placed.mapNotNull { it?.id }.toSet()
        val unlabelled = usableSensors.values
            .filter { it.label.isBlank() && it.id !in usedIds }
            .iterator()
        for (index in placed.indices) {
            if (placed[index] == null && unlabelled.hasNext()) {
                placed[index] = unlabelled.next()
            }
        }
        return placed
    }

    fun orderedValues(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap(),
        showSpareWheel: Boolean = false
    ): List<TireSensor> {
        val usable = if (showSpareWheel) {
            sensors
        } else {
            sensors.filterKeys { it != SPARE_SLOT }
                .filterValues { it.label != SPARE_SLOT }
        }
        return orderedSlots(usable, wheelMapping, showSpareWheel).filterNotNull().ifEmpty {
            allSlots(showSpareWheel).mapNotNull { label ->
                usable[label] ?: usable.values.firstOrNull { it.label == label }
            }.ifEmpty { usable.values.sortedBy { it.id }.take(allSlots(showSpareWheel).size) }
        }
    }

    fun resolveWheelLabel(sensor: TireSensor, wheelMapping: Map<String, String>): String {
        wheelMapping.entries
            .firstOrNull { it.value == sensor.id }
            ?.key
            ?.let { return it }
        val slotByLabel = allSlots(showSpareWheel = true)
            .firstOrNull { it == sensor.label || it == sensor.id }
        if (slotByLabel != null) return slotByLabel
        return sensor.label.ifEmpty { sensor.id }
    }
}
