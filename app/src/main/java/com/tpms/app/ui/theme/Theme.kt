package com.tpms.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TpmsDarkScheme = darkColorScheme(
    primary = TpmsColors.primary,
    onPrimary = TpmsColors.onPrimary,
    primaryContainer = TpmsColors.primaryDim,
    onPrimaryContainer = TpmsColors.accent,
    secondary = TpmsColors.accent,
    onSecondary = TpmsColors.onPrimary,
    background = TpmsColors.background,
    onBackground = TpmsColors.onBackground,
    surface = TpmsColors.surface,
    onSurface = TpmsColors.onBackground,
    surfaceVariant = TpmsColors.surfaceVariant,
    onSurfaceVariant = TpmsColors.onSurfaceMuted,
    outline = TpmsColors.outline,
    error = StatusColors.alert,
    onError = TpmsColors.onBackground
)

@Composable
fun TpmsTheme(content: @Composable () -> Unit) {
    val colorScheme = TpmsDarkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TpmsColors.background.toArgb()
            window.navigationBarColor = TpmsColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = TpmsShapes,
        content = content
    )
}
