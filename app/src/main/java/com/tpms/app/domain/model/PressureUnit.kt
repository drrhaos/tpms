package com.tpms.app.domain.model

enum class PressureUnit(val label: String, val toKpa: Float) {
    PSI("PSI", 6.89476f),
    KPA("kPa", 1f),
    BAR("Bar", 100f);

    fun fromKpa(kpa: Float): Float = kpa / toKpa

    fun formatFromKpa(kpa: Float): String = when (this) {
        BAR -> "%.1f".format(fromKpa(kpa))
        else -> "%.0f".format(fromKpa(kpa))
    }
}
