package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    val FALLBACK_IDS = listOf("SENSOR_01", "SENSOR_02", "SENSOR_03", "SENSOR_04")

    fun orderedSlots(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap()
    ): List<TireSensor?> {
        val hasCustomMapping = wheelMapping.values.any { it.isNotBlank() }
        return ORDER.mapIndexed { index, label ->
            if (hasCustomMapping) {
                wheelMapping[label]?.takeIf { it.isNotBlank() }?.let { sensors[it] }
            } else {
                sensors[label]
                    ?: sensors[FALLBACK_IDS[index]]
                    ?: sensors.values.elementAtOrNull(index)
            }
        }
    }

    fun orderedValues(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap()
    ): List<TireSensor> =
        orderedSlots(sensors, wheelMapping).filterNotNull().ifEmpty {
            ORDER.mapNotNull { label ->
                sensors[label] ?: sensors.values.firstOrNull { it.label == label }
            }.ifEmpty { sensors.values.sortedBy { it.id }.take(ORDER.size) }
        }

    fun resolveWheelLabel(sensor: TireSensor, wheelMapping: Map<String, String>): String {
        wheelMapping.entries
            .firstOrNull { it.value == sensor.id }
            ?.key
            ?.let { return it }
        return sensor.label.ifEmpty { sensor.id }
    }
}
