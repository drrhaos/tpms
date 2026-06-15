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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Baseline landscape dashboard size (typical Teyes head-unit content area). */
private val DesignWidth = 960.dp
private val DesignHeight = 540.dp

@Composable
fun EmbeddedMainScreenFrame(content: @Composable () -> Unit) {
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
            val scale = minOf(
                maxWidth / DesignWidth,
                maxHeight / DesignHeight
            ).coerceIn(0.35f, 1f)

            Box(
                modifier = Modifier
                    .size(DesignWidth, DesignHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                content()
            }
        }
    }
}
