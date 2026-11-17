package com.tpms.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tpms.app.R
import com.tpms.app.domain.AlertSeverity
import com.tpms.app.domain.WheelLayout
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.toSeverity
import com.tpms.app.ui.cardLabel
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.theme.PressureValueStyle
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.theme.TpmsColors
import com.tpms.app.ui.wheelPositionLabel

/**
 * The four readings — wheel name, pressure, temperature or alert — in the stage corners, as large as
 * the stage allows ([mainStageLayout]); between them the x-ray car with its running gear, each tyre
 * tinted by its state (green normal, amber warning, red alert, grey without data). The car is only
 * decoration: it never covers a reading.
 */
@Composable
fun CarTopDown(
    sensors: List<TireSensor?>,
    wheelLabels: List<String> = WheelLayout.ORDER,
    pressureUnit: PressureUnit = PressureUnit.PSI,
    /** Per slot (parallel to [sensors]): "N min" caption of a stale wheel, null when fresh. */
    staleMinutes: List<Long?> = emptyList(),
    /** Per slot: stale wheel without an active alert, drawn muted. */
    muted: List<Boolean> = emptyList(),
    onWheelClick: (label: String, sensor: TireSensor?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints
        fun indexOf(slot: String) = wheelLabels.indexOf(slot).takeIf { it >= 0 }
        // Readings first: they take the corners, the car fills what is left (MainStageLayout).
        val layout = mainStageLayout(maxWidth.value, maxHeight.value, hasSpare = indexOf(WheelLayout.SPARE_SLOT) != null)
        val car = layout.car
        // The layout is in dp and already as large as fits: the system font scale must not push the
        // text out of its box (it would only be cut off).
        val fontScale = LocalDensity.current.fontScale
        val carModifier = Modifier
            .offset(car.left.dp, car.top.dp)
            .size(car.width.dp, car.height.dp)

        // The road the car stands on, as on OEM vehicle-status pages; faded out at the stage edges
        // so it melts into the page instead of ending on a hard line.
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(STAGE_FADE_VERTICAL, blendMode = BlendMode.DstIn)
                    drawRect(STAGE_FADE_HORIZONTAL, blendMode = BlendMode.DstIn)
                }
        ) {
            Image(
                painter = painterResource(if (TpmsColors.isDark) R.drawable.car_3d_env_dark else R.drawable.car_3d_env_light),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset((car.left + ENV_RECT.left * car.width).dp, (car.top + ENV_RECT.top * car.height).dp)
                    .size((ENV_RECT.width * car.width).dp, (ENV_RECT.height * car.height).dp)
            )
        }

        Image(
            painter = painterResource(if (TpmsColors.isDark) R.drawable.car_3d_dark else R.drawable.car_3d_light),
            contentDescription = stringResource(R.string.widget_car_content_description),
            contentScale = ContentScale.Fit,
            modifier = carModifier
        )

        // Running gear (axles, prop shaft, suspension, brakes) under the tyres, so the wheels read as
        // one chassis rather than four loose discs; neutral, in the theme's text colour.
        Image(
            painter = painterResource(R.drawable.car_3d_axles),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(TpmsColors.onBackground, BlendMode.Modulate),
            alpha = 0.8f,
            modifier = carModifier
        )

        WHEEL_SPOTS.forEach { spot ->
            val index = indexOf(spot.slot) ?: return@forEach
            val sensor = sensors.getOrNull(index)
            val tint by animateColorAsState(tyreColor(sensor, muted.getOrNull(index) == true), label = "tyre")
            Image(
                painter = painterResource(spot.mask),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
                alpha = 0.92f,
                modifier = carModifier
            )
        }

        WHEEL_SPOTS.forEach { spot ->
            val index = indexOf(spot.slot) ?: return@forEach
            val rect = layout.readings[spot.slot] ?: return@forEach
            WheelReading(
                sensor = sensors.getOrNull(index),
                slot = spot.slot,
                pressureUnit = pressureUnit,
                muted = muted.getOrNull(index) == true,
                staleMinutes = staleMinutes.getOrNull(index),
                pressureSp = layout.pressureSp / fontScale,
                onClick = { onWheelClick(spot.slot, sensors.getOrNull(index)) },
                modifier = Modifier
                    .offset(rect.left.dp, rect.top.dp)
                    .size(rect.width.dp, rect.height.dp)
            )
        }

        indexOf(WheelLayout.SPARE_SLOT)?.let { index ->
            val rect = layout.spare ?: return@let
            WheelReading(
                sensor = sensors.getOrNull(index),
                slot = WheelLayout.SPARE_SLOT,
                pressureUnit = pressureUnit,
                muted = muted.getOrNull(index) == true,
                staleMinutes = staleMinutes.getOrNull(index),
                pressureSp = layout.pressureSp * SPARE_SCALE / fontScale,
                onClick = { onWheelClick(WheelLayout.SPARE_SLOT, sensors.getOrNull(index)) },
                modifier = Modifier
                    .offset(rect.left.dp, rect.top.dp)
                    .size(rect.width.dp, rect.height.dp)
            )
        }
    }
}

private val STAGE_FADE_VERTICAL = Brush.verticalGradient(
    0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent
)
private val STAGE_FADE_HORIZONTAL = Brush.horizontalGradient(
    0f to Color.Transparent, 0.08f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent
)

/** Tyre tint: the state colour; grey when there is no data or the wheel is stale without an alert. */
@Composable
private fun tyreColor(sensor: TireSensor?, muted: Boolean): Color = when {
    sensor == null -> TpmsColors.onSurfaceMuted
    else -> when (sensor.toSeverity()) {
        AlertSeverity.ALERT -> StatusColors.alert
        AlertSeverity.WARNING -> if (muted) TpmsColors.onSurfaceMuted else StatusColors.warning
        AlertSeverity.OK -> if (muted) TpmsColors.onSurfaceMuted else StatusColors.ok
        AlertSeverity.DISCONNECTED -> TpmsColors.onSurfaceMuted
    }
}

/** Reading colour: only a deviation is coloured, a normal value stays neutral. */
private fun deviationColor(sensor: TireSensor?, muted: Boolean): Color? = when (sensor?.toSeverity()) {
    AlertSeverity.ALERT -> StatusColors.alert
    AlertSeverity.WARNING -> if (muted) null else StatusColors.warning
    else -> null
}

/**
 * One wheel's corner: its name, the pressure (large) and the temperature — or the alert, up to two
 * lines — centred in its box ([MainStageLayout]), with a soft shadow for legibility over the road.
 */
@Composable
private fun WheelReading(
    sensor: TireSensor?,
    slot: String,
    pressureUnit: PressureUnit,
    muted: Boolean,
    staleMinutes: Long?,
    pressureSp: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviation = deviationColor(sensor, muted)
    val dimmed = sensor == null || (muted && deviation == null)
    val valueColor by animateColorAsState(
        targetValue = deviation ?: if (dimmed) TpmsColors.onSurfaceMuted else TpmsColors.onBackground,
        label = "wheelValue"
    )
    val position = wheelPositionLabel(slot)
    val small = (pressureSp * 0.42f).coerceAtLeast(10f)
    val shadow = Shadow(color = TpmsColors.background.copy(alpha = 0.9f), blurRadius = 8f)
    val second = sensor?.alertType?.cardLabel()
        ?: when {
            sensor == null -> stringResource(R.string.wheel_no_data)
            staleMinutes != null -> stringResource(R.string.wheel_stale_caption_format, temperatureText(sensor), staleMinutes)
            else -> temperatureText(sensor)
        }

    val alert = sensor?.alertType != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = position }
    ) {
        // The wheel's name: the reading sits in a corner, not on the wheel itself.
        Text(
            text = position,
            fontSize = small.sp,
            color = TpmsColors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildAnnotatedString {
                append(pressureNumber(sensor, pressureUnit))
                withStyle(SpanStyle(fontSize = small.sp, fontWeight = FontWeight.Normal)) {
                    append(" ")
                    append(pressureUnit.localizedLabel())
                }
            },
            style = PressureValueStyle.copy(shadow = shadow),
            fontSize = pressureSp.sp,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        // An alert name may need two lines ("Быстрая потеря давления"); it gets them, a little smaller.
        Text(
            text = second,
            style = PressureValueStyle.copy(shadow = shadow, fontWeight = FontWeight.Medium),
            fontSize = (pressureSp * if (alert) 0.48f else 0.6f).sp,
            lineHeight = (pressureSp * if (alert) 0.56f else 0.7f).sp,
            color = deviation ?: valueColor,
            textAlign = TextAlign.Center,
            maxLines = if (alert) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun pressureNumber(sensor: TireSensor?, unit: PressureUnit): String = when {
    sensor == null || sensor.alertType == AlertType.SENSOR_LOST || !sensor.pressureKpa.isFinite() ->
        stringResource(R.string.value_em_dash)
    else -> unit.formatFromKpa(sensor.pressureKpa)
}

private fun temperatureText(sensor: TireSensor): String =
    if (sensor.temperatureCelsius.isFinite()) "%.0f °C".format(sensor.temperatureCelsius) else "-- °C"

internal fun clampOffset(value: Dp, min: Dp, max: Dp): Dp =
    clampOffset(value.value, min.value, max.value).dp

internal fun clampOffset(value: Float, min: Float, max: Float): Float =
    if (max < min) min else value.coerceIn(min, max)
