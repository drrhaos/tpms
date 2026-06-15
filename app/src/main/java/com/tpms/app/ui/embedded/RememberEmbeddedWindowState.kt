package com.tpms.app.ui.embedded

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

@Composable
fun rememberEmbeddedWindowState(windowSize: IntSize): EmbeddedWindowState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val activity = context as? ComponentActivity

    return remember(activity, configuration, windowSize, density) {
        if (activity == null || windowSize == IntSize.Zero) {
            EmbeddedWindowState.fullScreen()
        } else {
            EmbeddedWindowDetector.detect(
                activity = activity,
                windowWidthPx = windowSize.width,
                windowHeightPx = windowSize.height,
                density = density
            )
        }
    }
}

@Composable
fun EmbeddedWindowProvider(content: @Composable () -> Unit) {
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val embedded = rememberEmbeddedWindowState(windowSize)
    CompositionLocalProvider(LocalEmbeddedWindow provides embedded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { windowSize = it }
        ) {
            content()
        }
    }
}
