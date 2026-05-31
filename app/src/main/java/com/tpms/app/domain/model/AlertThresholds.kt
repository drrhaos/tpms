package com.tpms.app.domain.model

data class AlertThresholds(
    val lowPressureKpa: Float = 150f,
    val highPressureKpa: Float = 350f,
    val highTempCelsius: Float = 85f
)
