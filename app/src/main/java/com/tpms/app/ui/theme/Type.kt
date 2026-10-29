package com.tpms.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tabular figures so changing pressures do not jitter sideways. */
const val TABULAR_FIGURES = "tnum"

/** Big tyre value on the main screen; the size is scaled to the card by the caller. */
val PressureValueStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = TABULAR_FIGURES,
    letterSpacing = (-0.5).sp
)

private val base = Typography()

val TpmsTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.4.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp)
)
