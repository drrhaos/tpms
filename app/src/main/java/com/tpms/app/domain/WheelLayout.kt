package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    val FALLBACK_IDS = listOf("SENSOR_01", "SENSOR_02", "SENSOR_03", "SENSOR_04")

    fun orderedSlots(sensors: Map<String, TireSensor>): List<TireSensor?> =
        ORDER.mapIndexed { index, label ->
            sensors[label]
                ?: sensors[FALLBACK_IDS[index]]
                ?: sensors.values.elementAtOrNull(index)
        }

    fun orderedValues(sensors: Map<String, TireSensor>): List<TireSensor> =
        ORDER.mapNotNull { label ->
            sensors[label] ?: sensors.values.firstOrNull { it.label == label }
        }.ifEmpty { sensors.values.sortedBy { it.id }.take(ORDER.size) }
}
