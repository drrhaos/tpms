package com.tpms.app.ui.embedded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EmbeddedMainScreenFrame(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val screenAspectRatio = configuration.screenWidthDp.toFloat() /
        configuration.screenHeightDp.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val (fitWidth, fitHeight) = fitWithinAspectRatio(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                aspectRatio = screenAspectRatio
            )
            Box(modifier = Modifier.size(fitWidth, fitHeight)) {
                content()
            }
        }
    }
}

/** Largest size that fits in [maxWidth] x [maxHeight] while keeping [aspectRatio] (width / height). */
internal fun fitWithinAspectRatio(maxWidth: Dp, maxHeight: Dp, aspectRatio: Float): Pair<Dp, Dp> {
    if (maxWidth <= 0.dp || maxHeight <= 0.dp || aspectRatio <= 0f) {
        return maxWidth to maxHeight
    }
    val containerRatio = maxWidth.value / maxHeight.value
    return if (containerRatio > aspectRatio) {
        val height = maxHeight
        val width = (height.value * aspectRatio).dp
        width to height
    } else {
        val width = maxWidth
        val height = (width.value / aspectRatio).dp
        width to height
    }
}
