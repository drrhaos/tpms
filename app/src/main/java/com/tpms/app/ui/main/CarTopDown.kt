package com.tpms.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.components.SensorBatteryIcon
import com.tpms.app.ui.components.wheelStatusColor
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.theme.TpmsColors

private const val SIDE_COLUMN_WEIGHT = 0.30f
private const val CAR_COLUMN_WEIGHT = 0.40f

@Composable
fun CarTopDown(
    sensors: List<TireSensor?>,
    wheelLabels: List<String> = listOf("FL", "FR", "RL", "RR"),
    pressureUnit: PressureUnit = PressureUnit.PSI,
    onWheelClick: (label: String, sensor: TireSensor?) -> Unit = { _, _ -> },
    dense: Boolean = false,
    modifier: Modifier = Modifier
) {
    val margin = if (dense) 2.dp else 6.dp
    val columnGap = if (dense) 2.dp else 4.dp

    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(TpmsColors.surfaceElevated)
            .border(1.dp, TpmsColors.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
    ) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints

        val panelHeight = maxHeight
        val spareBlockHeight = if (dense) {
            (panelHeight * 0.12f).coerceIn(28.dp, 56.dp)
        } else {
            (panelHeight * 0.14f).coerceIn(52.dp, 88.dp)
        }

        val indices = wheelLabels.indices.toList()
        val leftIndices = indices.filter { it % 2 == 0 && wheelLabels[it] != "SP" }
        val rightIndices = indices.filter { it % 2 == 1 }
        val spareIndex = indices.firstOrNull { wheelLabels[it] == "SP" }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(margin),
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            WheelSideColumn(
                indices = leftIndices,
                wheelLabels = wheelLabels,
                sensors = sensors,
                pressureUnit = pressureUnit,
                batteryOnStart = false,
                dense = dense,
                onWheelClick = onWheelClick,
                modifier = Modifier
                    .weight(SIDE_COLUMN_WEIGHT)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(CAR_COLUMN_WEIGHT)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.auto),
                        contentDescription = stringResource(R.string.widget_car_content_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                spareIndex?.let { index ->
                    val label = wheelLabels[index]
                    val sensor = sensors.getOrNull(index)
                    WheelInfoBlock(
                        sensor = sensor,
                        label = label,
                        pressureUnit = pressureUnit,
                        accentColor = sensor.wheelStatusColor(),
                        batteryOnStart = true,
                        dense = dense,
                        onClick = { onWheelClick(label, sensor) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = columnGap)
                            .height(spareBlockHeight)
                    )
                }
            }

            WheelSideColumn(
                indices = rightIndices,
                wheelLabels = wheelLabels,
                sensors = sensors,
                pressureUnit = pressureUnit,
                batteryOnStart = true,
                dense = dense,
                onWheelClick = onWheelClick,
                modifier = Modifier
                    .weight(SIDE_COLUMN_WEIGHT)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun WheelSideColumn(
    indices: List<Int>,
    wheelLabels: List<String>,
    sensors: List<TireSensor?>,
    pressureUnit: PressureUnit,
    batteryOnStart: Boolean,
    dense: Boolean,
    onWheelClick: (label: String, sensor: TireSensor?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (indices.isEmpty()) {
        Spacer(modifier = modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (dense) 3.dp else 6.dp)
    ) {
        indices.forEach { index ->
            val label = wheelLabels[index]
            val sensor = sensors.getOrNull(index)
            WheelInfoBlock(
                sensor = sensor,
                label = label,
                pressureUnit = pressureUnit,
                accentColor = sensor.wheelStatusColor(),
                batteryOnStart = batteryOnStart,
                dense = dense,
                onClick = { onWheelClick(label, sensor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun WheelInfoBlock(
    sensor: TireSensor?,
    label: String,
    pressureUnit: PressureUnit,
    accentColor: Color,
    batteryOnStart: Boolean,
    dense: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val batteryGap = if (dense) 4.dp else 10.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (batteryOnStart) {
            RotatedBatteryIcon(
                sensor = sensor,
                modifier = Modifier.fillMaxHeight()
            )
            Spacer(modifier = Modifier.width(batteryGap))
        }

        WheelParameterCard(
            sensor = sensor,
            label = label,
            pressureUnit = pressureUnit,
            accentColor = accentColor,
            dense = dense,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        if (!batteryOnStart) {
            Spacer(modifier = Modifier.width(batteryGap))
            RotatedBatteryIcon(
                sensor = sensor,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

@Composable
private fun RotatedBatteryIcon(
    sensor: TireSensor?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val shortSide = minOf(maxHeight * 0.15f, 16.dp).coerceIn(8.dp, 16.dp)
        val longSide = minOf(shortSide * 4f, maxHeight * 0.88f)
        val slotWidth = shortSide + 10.dp

        Box(
            modifier = Modifier
                .width(slotWidth)
                .height(longSide),
            contentAlignment = Alignment.Center
        ) {
            SensorBatteryIcon(
                batteryPercent = sensor?.batteryPercent,
                isLowBattery = sensor?.alertType == AlertType.BATTERY_LOW,
                width = longSide,
                height = shortSide,
                modifier = Modifier.graphicsLayer { rotationZ = 270f }
            )
        }
    }
}

@Composable
private fun WheelParameterCard(
    sensor: TireSensor?,
    label: String,
    pressureUnit: PressureUnit,
    accentColor: Color,
    dense: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (dense) 4.dp else 8.dp,
                vertical = if (dense) 2.dp else 4.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val labelCap = if (dense) 13f else 15f
        val pressureCap = if (dense) 28f else 34f
        val labelSize = (maxHeight.value * 0.14f).coerceIn(8f, labelCap).sp
        val pressureSize = (maxHeight.value * 0.32f).coerceIn(12f, pressureCap).sp
        val tempSize = (maxHeight.value * 0.14f).coerceIn(8f, labelCap).sp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                color = accentColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = formatPressure(sensor, pressureUnit),
                fontSize = pressureSize,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTemperature(sensor),
                fontSize = tempSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
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

internal fun clampOffset(value: Dp, min: Dp, max: Dp): Dp =
    clampOffset(value.value, min.value, max.value).dp

internal fun clampOffset(value: Float, min: Float, max: Float): Float =
    if (max < min) min else value.coerceIn(min, max)
