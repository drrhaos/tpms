package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    const val SPARE_SLOT = "SP"
    val FALLBACK_IDS = listOf("SENSOR_01", "SENSOR_02", "SENSOR_03", "SENSOR_04")

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
        return slots.mapIndexed { index, label ->
            if (hasCustomMapping) {
                wheelMapping[label]?.takeIf { it.isNotBlank() }?.let { usableSensors[it] }
            } else {
                usableSensors[label]
                    ?: usableSensors[FALLBACK_IDS.getOrNull(index)]
                    ?: usableSensors.values.elementAtOrNull(index)
            }
        }
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
