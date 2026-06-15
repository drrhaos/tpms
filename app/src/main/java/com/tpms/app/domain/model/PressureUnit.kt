package com.tpms.app.domain.model

import java.util.Locale

enum class PressureUnit(val label: String, val toKpa: Float) {
    PSI("PSI", 6.89476f),
    KPA("kPa", 1f),
    BAR("Bar", 100f);

    fun fromKpa(kpa: Float): Float = kpa / toKpa

    fun toKpa(value: Float): Float = value * toKpa

    fun formatFromKpa(kpa: Float): String = when (this) {
        BAR -> String.format(Locale.US, "%.1f", fromKpa(kpa))
        else -> String.format(Locale.US, "%.0f", fromKpa(kpa))
    }

    fun formatPressure(kpa: Float): String = if (kpa.isFinite()) {
        "${formatFromKpa(kpa)} $label"
    } else {
        "—"
    }

    fun formatThresholdValue(value: Float): String = when (this) {
        BAR -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.0f", value)
    }
}
