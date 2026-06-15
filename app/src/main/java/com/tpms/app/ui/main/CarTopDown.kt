package com.tpms.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.toSeverity
import com.tpms.app.ui.localizedLabel
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
    wheelLabels: List<String> = WHEELS.map { it.label },
    pressureUnit: PressureUnit = PressureUnit.PSI,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onWheelClick: (label: String, sensor: TireSensor?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val margin = 8.dp
    val toolbarHeight = 40.dp

    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(TpmsColors.surfaceElevated)
            .border(1.dp, TpmsColors.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        if (boxW <= 0.dp || boxH <= 0.dp) return@BoxWithConstraints

        val cardW = minOf(142.dp, boxW * 0.32f)
        val cardH = minOf(118.dp, boxH * 0.32f)
        val imgSide = minOf(boxW, boxH - toolbarHeight) * 0.76f
        val offsetX = (boxW - imgSide) / 2f
        val offsetY = toolbarHeight + (boxH - toolbarHeight - imgSide) / 2f

        Image(
            painter = painterResource(R.drawable.auto),
            contentDescription = stringResource(R.string.widget_car_content_description),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = toolbarHeight + 8.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TpmsColors.carCanvas),
            contentScale = ContentScale.Fit
        )

        Row(
            modifier = Modifier
                .width(boxW)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToDebug, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = stringResource(R.string.cd_debug_log),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val positions = wheelPositions(wheelLabels)
        positions.forEachIndexed { index, pos ->
            val sensor = sensors.getOrNull(index)
            val dotColor = sensor.toSeverity().statusColor()

            val isLeft = index % 2 == 0
            val wheelCenterX = offsetX + imgSide * pos.xFrac
            val wheelCenterY = offsetY + imgSide * pos.yFrac

            val cardX: Dp = if (isLeft) margin else maxOf(margin, boxW - cardW - margin)
            val cardY = clampOffset(wheelCenterY - cardH / 2f, toolbarHeight + margin, maxOf(toolbarHeight + margin, boxH - cardH - margin))

            Box(
                modifier = Modifier
                    .offset(x = cardX, y = cardY)
                    .width(cardW)
                    .height(cardH)
                    .clip(MaterialTheme.shapes.small)
                    .background(dotColor.copy(alpha = 0.12f))
                    .border(1.dp, dotColor.copy(alpha = 0.35f), MaterialTheme.shapes.small)
                    .clickable { onWheelClick(pos.label, sensor) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                WheelCardContent(sensor = sensor, label = pos.label, pressureUnit = pressureUnit, accentColor = dotColor)
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

@Composable
private fun WheelCardContent(
    sensor: TireSensor?,
    label: String,
    pressureUnit: PressureUnit,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accentColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = formatPressure(sensor, pressureUnit),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = formatTemperature(sensor),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = formatBattery(sensor),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = batteryColor(sensor),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun formatPressure(sensor: TireSensor?, unit: PressureUnit): String {
    val lost = stringResource(R.string.label_lost)
    val noData = stringResource(R.string.value_no_data_pressure, unit.localizedLabel())
    return when {
        sensor?.alertType == AlertType.SENSOR_LOST -> lost
        sensor != null && sensor.pressureKpa.isFinite() -> unit.formatPressure(sensor.pressureKpa)
        else -> noData
    }
}

private fun formatTemperature(sensor: TireSensor?): String =
    if (sensor != null && sensor.temperatureCelsius.isFinite()) {
        "%.0f°C".format(sensor.temperatureCelsius)
    } else {
        "--°C"
    }

private fun formatBattery(sensor: TireSensor?): String =
    if (sensor != null) "${sensor.batteryPercent}%" else "--%"

@Composable
private fun batteryColor(sensor: TireSensor?) = when (sensor?.alertType) {
    AlertType.BATTERY_LOW -> MaterialTheme.colorScheme.error
    null -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun clampOffset(value: Dp, min: Dp, max: Dp): Dp =
    clampOffset(value.value, min.value, max.value).dp

internal fun clampOffset(value: Float, min: Float, max: Float): Float =
    if (max < min) min else value.coerceIn(min, max)

private fun wheelPositions(labels: List<String>): List<WheelPos> {
    val coords = listOf(
        0.25f to 0.25f,
        0.75f to 0.25f,
        0.25f to 0.75f,
        0.75f to 0.75f,
        0.5f to 0.55f
    )
    return labels.mapIndexed { index, label ->
        val (x, y) = coords.getOrElse(index) { 0.5f to 0.5f }
        WheelPos(x, y, label)
    }
}
