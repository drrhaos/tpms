package com.tpms.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Neutral surfaces and one restrained accent; colour is reserved for tyre state ([StatusColors]). */
@Immutable
data class TpmsPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryDim: Color,
    val onPrimary: Color,
    val accent: Color,
    val outline: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color
)

val DarkPalette = TpmsPalette(
    isDark = true,
    background = Color(0xFF0B0D10),
    surface = Color(0xFF14171C),
    surfaceElevated = Color(0xFF1A1E24),
    surfaceVariant = Color(0xFF242930),
    primary = Color(0xFF5EB6E0),
    primaryDim = Color(0xFF1E3E4E),
    onPrimary = Color(0xFF04202D),
    accent = Color(0xFF9AD3EE),
    outline = Color(0xFF2B3139),
    onBackground = Color(0xFFECEFF3),
    onSurfaceMuted = Color(0xFF8A939F)
)

val LightPalette = TpmsPalette(
    isDark = false,
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8EC),
    primary = Color(0xFF0B6E99),
    primaryDim = Color(0xFFD3E8F2),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF0B6E99),
    outline = Color(0xFFD5D9DF),
    onBackground = Color(0xFF14171C),
    onSurfaceMuted = Color(0xFF5D6570)
)

val LocalTpmsPalette = staticCompositionLocalOf { DarkPalette }

/** The current theme's palette (day or night). */
object TpmsColors {
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.isDark
    val background: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.background
    val surface: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.surface
    val surfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.surfaceElevated
    val surfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.surfaceVariant
    val primary: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.primary
    val primaryDim: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.primaryDim
    val onPrimary: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.onPrimary
    val accent: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.accent
    val outline: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.outline
    val onBackground: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.onBackground
    val onSurfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalTpmsPalette.current.onSurfaceMuted
}

/** Tyre / connection state; the same in both themes (picked to read on light and dark). */
object StatusColors {
    val ok = Color(0xFF3DB46D)
    val warning = Color(0xFFF0A020)
    val alert = Color(0xFFE5484D)
    val disconnected = Color(0xFF7A838F)
}
