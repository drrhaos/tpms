package com.tpms.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.toSeverity
import com.tpms.app.ui.theme.statusColor
import com.tpms.app.ui.theme.TpmsColors

private data class WheelPos(val xFrac: Float, val yFrac: Float, val label: String)

private val WHEELS = listOf(
    WheelPos(0.25f, 0.25f, "FL"),
    WheelPos(0.75f, 0.25f, "FR"),
    WheelPos(0.25f, 0.75f, "RL"),
    WheelPos(0.75f, 0.75f, "RR"),
)

@Composable
fun CarTopDown(
    sensors: List<TireSensor?>,
    pressureUnit: PressureUnit = PressureUnit.PSI,
    modifier: Modifier = Modifier
) {
    val margin = 8.dp

    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(TpmsColors.surfaceElevated)
            .border(1.dp, TpmsColors.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        if (boxW <= 0.dp || boxH <= 0.dp) return@BoxWithConstraints

        val cardW = minOf(110.dp, boxW * 0.26f)
        val cardH = minOf(76.dp, boxH * 0.22f)
        val imgSide = minOf(boxW, boxH) * 0.85f
        val offsetX = (boxW - imgSide) / 2f
        val offsetY = (boxH - imgSide) / 2f

        Image(
            painter = painterResource(R.drawable.auto),
            contentDescription = "Car top-down view",
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TpmsColors.carCanvas),
            contentScale = ContentScale.Fit
        )

        WHEELS.forEachIndexed { index, pos ->
            val sensor = sensors.getOrNull(index)
            val dotColor = sensor.toSeverity().statusColor()

            val isLeft = index % 2 == 0
            val wheelCenterX = offsetX + imgSide * pos.xFrac
            val wheelCenterY = offsetY + imgSide * pos.yFrac

            val cardX: Dp = if (isLeft) margin else maxOf(margin, boxW - cardW - margin)
            val cardY = clampOffset(wheelCenterY - cardH / 2f, margin, maxOf(margin, boxH - cardH - margin))

            Box(
                modifier = Modifier
                    .offset(x = cardX, y = cardY)
                    .width(cardW)
                    .height(cardH)
                    .clip(MaterialTheme.shapes.small)
                    .background(dotColor.copy(alpha = 0.12f))
                    .border(1.dp, dotColor.copy(alpha = 0.35f), MaterialTheme.shapes.small)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = pos.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = dotColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (sensor != null && sensor.pressureKpa.isFinite())
                            "%.0f".format(pressureUnit.fromKpa(sensor.pressureKpa))
                        else "--",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (sensor != null && sensor.temperatureCelsius.isFinite())
                            "%.0f°C".format(sensor.temperatureCelsius)
                        else "--°C",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = wheelCenterX - 5.dp, y = wheelCenterY - 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

internal fun clampOffset(value: Dp, min: Dp, max: Dp): Dp =
    clampOffset(value.value, min.value, max.value).dp

internal fun clampOffset(value: Float, min: Float, max: Float): Float =
    if (max < min) min else value.coerceIn(min, max)
