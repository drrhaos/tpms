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
        return slots.mapIndexed { index, label ->
            if (hasCustomMapping) {
                wheelMapping[label]?.takeIf { it.isNotBlank() }?.let { sensors[it] }
            } else {
                sensors[label]
                    ?: sensors[FALLBACK_IDS.getOrNull(index)]
                    ?: sensors.values.elementAtOrNull(index)
            }
        }
    }

    fun orderedValues(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap(),
        showSpareWheel: Boolean = false
    ): List<TireSensor> =
        orderedSlots(sensors, wheelMapping, showSpareWheel).filterNotNull().ifEmpty {
            allSlots(showSpareWheel).mapNotNull { label ->
                sensors[label] ?: sensors.values.firstOrNull { it.label == label }
            }.ifEmpty { sensors.values.sortedBy { it.id }.take(allSlots(showSpareWheel).size) }
        }

    fun resolveWheelLabel(
        sensor: TireSensor,
        wheelMapping: Map<String, String>,
        wheelNames: Map<String, String> = emptyMap()
    ): String {
        wheelMapping.entries
            .firstOrNull { it.value == sensor.id }
            ?.key
            ?.let { slot -> return wheelNames[slot]?.takeIf { it.isNotBlank() } ?: slot }
        val slotByLabel = allSlots(showSpareWheel = true)
            .firstOrNull { it == sensor.label || it == sensor.id }
        if (slotByLabel != null) {
            wheelNames[slotByLabel]?.takeIf { it.isNotBlank() }?.let { return it }
            return slotByLabel
        }
        return sensor.label.ifEmpty { sensor.id }
    }
}
