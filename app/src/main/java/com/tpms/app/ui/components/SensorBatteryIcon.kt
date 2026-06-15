package com.tpms.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tpms.app.R
import com.tpms.app.ui.theme.StatusColors

@Composable
fun SensorBatteryIcon(
    batteryPercent: Int?,
    isLowBattery: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 28.dp,
    height: Dp = 28.dp
) {
    val unknown = stringResource(R.string.value_no_data_battery)
    val description = if (batteryPercent != null) {
        stringResource(R.string.detail_battery) + ": $batteryPercent%"
    } else {
        unknown
    }

    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val fillColor = when {
        batteryPercent == null -> outlineColor.copy(alpha = 0.25f)
        isLowBattery -> MaterialTheme.colorScheme.error
        batteryPercent <= 20 -> StatusColors.warning
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    }
    val level = batteryPercent?.coerceIn(0, 100)?.div(100f) ?: 0f

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .semantics { contentDescription = description }
    ) {
        val stroke = (minOf(size.width, size.height) * 0.07f).coerceAtLeast(1.5f)
        val tipW = size.width * 0.12f
        val tipH = size.height * 0.34f
        val bodyW = size.width - tipW - stroke
        val bodyH = size.height
        val corner = CornerRadius(stroke * 1.4f, stroke * 1.4f)

        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(0f, 0f),
            size = Size(bodyW, bodyH),
            cornerRadius = corner,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyW, (bodyH - tipH) / 2f),
            size = Size(tipW, tipH),
            cornerRadius = CornerRadius(stroke, stroke)
        )

        if (level > 0f) {
            val inset = stroke * 1.6f
            val innerW = (bodyW - inset * 2f) * level
            val innerH = bodyH - inset * 2f
            if (innerW > 0f && innerH > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(inset, inset),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(stroke, stroke)
                )
            }
        }
    }
}
