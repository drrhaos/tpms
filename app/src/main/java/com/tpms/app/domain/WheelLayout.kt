package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    const val SPARE_SLOT = "SP"

    fun isSpare(sensor: TireSensor): Boolean =
        sensor.id == SPARE_SLOT || sensor.label == SPARE_SLOT

    fun allSlots(showSpareWheel: Boolean = false): List<String> =
        if (showSpareWheel) ORDER + SPARE_SLOT else ORDER

    /** One entry per [allSlots] slot; placement follows [WheelAssignment.assign] (no fallback). */
    fun orderedSlots(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap(),
        showSpareWheel: Boolean = false
    ): List<TireSensor?> {
        val assignment = WheelAssignment.assign(sensors, wheelMapping, showSpareWheel)
        return allSlots(showSpareWheel).map { assignment[it] }
    }

    /** Sensors on a wheel, in slot order. */
    fun orderedValues(
        sensors: Map<String, TireSensor>,
        wheelMapping: Map<String, String> = emptyMap(),
        showSpareWheel: Boolean = false
    ): List<TireSensor> = orderedSlots(sensors, wheelMapping, showSpareWheel).filterNotNull()

    /** Mapped slot first, then the sensor's default slot, else its label or id. */
    fun resolveWheelLabel(sensor: TireSensor, wheelMapping: Map<String, String>): String {
        allSlots(showSpareWheel = true)
            .firstOrNull { wheelMapping[it] == sensor.id }
            ?.let { return it }
        WheelAssignment.defaultSlot(sensor)?.let { return it }
        return sensor.label.ifEmpty { sensor.id }
    }
}
