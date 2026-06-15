package com.tpms.app.ui.embedded

import androidx.compose.runtime.compositionLocalOf

val LocalEmbeddedWindow = compositionLocalOf { EmbeddedWindowState.fullScreen() }
