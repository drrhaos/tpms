package com.tpms.app.domain.model

/**
 * Alert limits. The "Внимание" limits ([lowPressureKpa], [highPressureKpa], [highTempCelsius]) and
 * the "Неисправность" ones ([criticalLowPressureKpa], [criticalTempCelsius]); see
 * [com.tpms.app.domain.PressureTarget.thresholds].
 */
data class AlertThresholds(
    val lowPressureKpa: Float = 150f,
    val highPressureKpa: Float = 350f,
    val highTempCelsius: Float = 85f,
    val criticalLowPressureKpa: Float = lowPressureKpa * CRITICAL_LOW_OF_LOW,
    val criticalTempCelsius: Float = highTempCelsius + CRITICAL_TEMP_MARGIN_C
) {
    companion object {
        /** 65 % of the target against the 80 % low limit. */
        const val CRITICAL_LOW_OF_LOW = 0.65f / 0.80f
        const val CRITICAL_TEMP_MARGIN_C = 15f
    }
}
