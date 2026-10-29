package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor

object WheelLayout {
    val ORDER = listOf("FL", "FR", "RL", "RR")
    const val SPARE_SLOT = "SP"

    fun isSpare(sensor: TireSensor): Boolean =
        sensor.id == SPARE_SLOT || sensor.label == SPARE_SLOT

    fun allSlots(showSpareWheel: Boolean = false): List<String> =
        if (showSpareWheel) ORDER + SPARE_SLOT else ORDER
}
