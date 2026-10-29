package com.tpms.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tpms.app.domain.model.WidgetThemeMode

private fun TpmsPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryDim,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = onPrimary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceElevated,
        surfaceContainerHigh = surfaceElevated,
        outline = outline,
        outlineVariant = outline,
        error = StatusColors.alert,
        onError = Color.White
    )
}

/** Day / night from the "Appearance" setting (shared with the widgets); AUTO follows the system. */
@Composable
fun WidgetThemeMode.isDarkTheme(): Boolean = when (this) {
    WidgetThemeMode.DARK -> true
    WidgetThemeMode.LIGHT -> false
    WidgetThemeMode.AUTO -> isSystemInDarkTheme()
}

@Composable
fun TpmsTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTpmsPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            shapes = TpmsShapes,
            typography = TpmsTypography,
            content = content
        )
    }
}
