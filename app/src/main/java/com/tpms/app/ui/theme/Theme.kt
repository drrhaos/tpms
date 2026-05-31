package com.tpms.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color.Black,
    secondary = Color(0xFF81C784),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFF44336)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color.White,
    secondary = Color(0xFF388E3C),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = Color(0xFFD32F2F)
)

object StatusColors {
    val ok = Color(0xFF4CAF50)
    val warning = Color(0xFFFFC107)
    val alert = Color(0xFFF44336)
    val disconnected = Color(0xFF9E9E9E)
}

@Composable
fun TpmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
