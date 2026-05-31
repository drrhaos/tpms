package com.tpms.app.domain.model

enum class PressureUnit(val label: String, val toKpa: Float) {
    PSI("PSI", 6.89476f),
    KPA("kPa", 1f),
    BAR("Bar", 100f);

    fun fromKpa(kpa: Float): Float = kpa / toKpa
}
