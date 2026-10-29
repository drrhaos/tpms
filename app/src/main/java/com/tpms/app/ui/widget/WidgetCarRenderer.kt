package com.tpms.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import com.tpms.app.ui.main.ENV_RECT
import com.tpms.app.ui.main.WHEEL_SPOTS
import com.tpms.app.ui.main.stageGeometry
import com.tpms.app.ui.theme.DarkPalette
import com.tpms.app.ui.theme.LightPalette
import com.tpms.app.ui.theme.StatusColors
import com.tpms.app.ui.wheelPositionLabel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The car widget's scene, drawn like the main screen ([com.tpms.app.ui.main.CarTopDown]): the road,
 * the x-ray car with its running gear, each tyre tinted by its state and each reading — pressure
 * over temperature or the alert — on its axle's line. RemoteViews cannot lay images and text out
 * like that, so the whole stage is one bitmap; the layout comes from the same [stageGeometry].
 */
internal object WidgetCarRenderer {

    private const val WIDGET_CAR_SCALE = 0.95f

    /** Longest bitmap side: the bitmap crosses binder with every update, a widget needs no more. */
    private const val MAX_SIDE_PX = 1100f

    /** Decoded renders by (resource, sample size): updates come up to once a second. */
    private val images = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun render(context: Context, snapshot: WidgetSnapshot, widthDp: Float, heightDp: Float): Bitmap {
        val scale = min(context.resources.displayMetrics.density, MAX_SIDE_PX / max(widthDp, heightDp))
        val bitmap = Bitmap.createBitmap(
            (widthDp * scale).roundToInt().coerceAtLeast(1),
            (heightDp * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale) // everything below is in dp, as in CarTopDown
        // Home-screen widgets are small: compact type sizes and a larger share for the car.
        val geo = stageGeometry(widthDp, heightDp, compact = true, carScale = WIDGET_CAR_SCALE)
        val palette = if (snapshot.useLightTheme) LightPalette else DarkPalette
        val car = geo.car.toRectF()

        drawRoad(context, canvas, snapshot.useLightTheme, car, widthDp, heightDp, scale)
        drawImage(
            context, canvas,
            if (snapshot.useLightTheme) R.drawable.car_3d_light else R.drawable.car_3d_dark,
            car, scale
        )
        // Running gear under the tyres, in the theme's text colour, as on the main screen.
        drawImage(context, canvas, R.drawable.car_3d_axles, car, scale, tint = palette.onBackground.toArgb(), alpha = 0.8f)

        val bySlot = snapshot.tires.associateBy { it.label }
        WHEEL_SPOTS.forEach { spot ->
            val tire = bySlot[spot.slot] ?: return@forEach
            drawImage(context, canvas, spot.mask, car, scale, tint = tyreColor(tire.displayStatus, palette.onSurfaceMuted.toArgb()), alpha = 0.92f)
        }

        val text = ReadingPaints(palette.background.toArgb())
        WHEEL_SPOTS.forEach { spot ->
            val tire = bySlot[spot.slot] ?: return@forEach
            val cx = car.left + spot.reading.x * car.width()
            val cy = car.top + spot.reading.y * car.height()
            val left = clamp(cx - geo.readingW / 2f, 0f, widthDp - geo.readingW)
            val top = clamp(cy - geo.readingH / 2f, 0f, heightDp - geo.readingH)
            drawReading(context, canvas, text, tire, snapshot.unitLabel, left + geo.readingW / 2f, top, geo.readingW, geo.pressureSp, palette.onBackground.toArgb(), palette.onSurfaceMuted.toArgb(), position = null)
        }
        bySlot[WheelLayout.SPARE_SLOT]?.let { spare ->
            drawReading(
                context, canvas, text, spare, snapshot.unitLabel, geo.readingW / 2f, 0f, geo.readingW,
                geo.pressureSp * 0.7f, palette.onBackground.toArgb(), palette.onSurfaceMuted.toArgb(),
                position = wheelPositionLabel(context, WheelLayout.SPARE_SLOT)
            )
        }
        return bitmap
    }

    /** The road, faded out at the stage edges so it melts into the widget background. */
    private fun drawRoad(context: Context, canvas: Canvas, light: Boolean, car: RectF, w: Float, h: Float, scale: Float) {
        val env = RectF(
            car.left + ENV_RECT.left * car.width(),
            car.top + ENV_RECT.top * car.height(),
            car.left + ENV_RECT.right * car.width(),
            car.top + ENV_RECT.bottom * car.height()
        )
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        drawImage(context, canvas, if (light) R.drawable.car_3d_env_light else R.drawable.car_3d_env_dark, env, scale)
        val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        val stops = floatArrayOf(0f, 0.12f, 0.88f, 1f)
        val colors = intArrayOf(0, BLACK, BLACK, 0)
        fade.shader = LinearGradient(0f, 0f, 0f, h, colors, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fade)
        fade.shader = LinearGradient(0f, 0f, w, 0f, colors, floatArrayOf(0f, 0.08f, 0.92f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fade)
        canvas.restoreToCount(layer)
    }

    /** [tint] multiplies the render (white masks take the state colour, shading kept). */
    private fun drawImage(
        context: Context,
        canvas: Canvas,
        @DrawableRes res: Int,
        dst: RectF,
        scale: Float,
        tint: Int? = null,
        alpha: Float = 1f
    ) {
        val image = decode(context, res, (dst.width() * scale).roundToInt()) ?: return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = (alpha * 255).roundToInt()
            tint?.let { colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.MULTIPLY) }
        }
        canvas.drawBitmap(image, null, dst, paint)
    }

    /** Decoded at the largest power-of-two reduction that still covers [targetWidthPx]. */
    private fun decode(context: Context, @DrawableRes res: Int, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, res, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx.coerceAtLeast(1)) sample *= 2
        val key = "$res:$sample"
        images.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
        return BitmapFactory.decodeResource(context.resources, res, options)?.also { images.put(key, it) }
    }

    private class ReadingPaints(background: Int) {
        private val shadow = (background and 0x00FFFFFF) or (0xE6 shl 24)
        val value = paint(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        val small = paint(Typeface.DEFAULT)
        val second = paint(Typeface.create("sans-serif-medium", Typeface.NORMAL))

        private fun paint(typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
            setShadowLayer(4f, 0f, 0f, shadow)
        }
    }

    /** Pressure (with its unit) over the alert, the stale age or the temperature, centred on [cx]. */
    private fun drawReading(
        context: Context,
        canvas: Canvas,
        paints: ReadingPaints,
        tire: WidgetTireSlot,
        unit: String,
        cx: Float,
        top: Float,
        maxWidth: Float,
        pressureSize: Float,
        normal: Int,
        muted: Int,
        position: String?
    ) {
        val deviation = when (tire.displayStatus) {
            WidgetTireStatus.ALERT -> StatusColors.alert.toArgb()
            WidgetTireStatus.WARNING -> StatusColors.warning.toArgb()
            else -> null
        }
        val valueColor = deviation ?: if (tire.displayStatus == WidgetTireStatus.EMPTY) muted else normal
        val smallSize = max(pressureSize * 0.42f, 10f)
        var y = top

        position?.let {
            paints.small.textSize = smallSize
            paints.small.color = muted
            y += smallSize
            drawCentred(canvas, fit(it, paints.small, maxWidth), paints.small, cx, y)
            y += smallSize * 0.3f
        }

        // A number gets the unit in small type; "--" / "LOST" texts already say everything.
        val number = tire.pressureText
        val withUnit = number.firstOrNull()?.isDigit() == true
        paints.value.textSize = pressureSize
        paints.value.color = valueColor
        paints.small.textSize = smallSize
        paints.small.color = valueColor
        val unitText = if (withUnit) " $unit" else ""
        val numberWidth = paints.value.measureText(number)
        val total = numberWidth + paints.small.measureText(unitText)
        y += pressureSize
        canvas.drawText(number, cx - total / 2f, y, paints.value)
        if (withUnit) canvas.drawText(unitText, cx - total / 2f + numberWidth, y, paints.small)

        val second = tire.alertText
            ?: tire.staleMinutes?.let { context.getString(R.string.wheel_stale_caption_format, tire.temperatureText, it) }
            ?: tire.temperatureText
        paints.second.textSize = pressureSize * 0.6f
        paints.second.color = deviation ?: valueColor
        y += pressureSize * 0.75f
        drawCentred(canvas, fit(second, paints.second, maxWidth), paints.second, cx, y)
    }

    private fun drawCentred(canvas: Canvas, text: String, paint: Paint, cx: Float, baseline: Float) {
        canvas.drawText(text, cx - paint.measureText(text) / 2f, baseline, paint)
    }

    /** Shrinks the text size until [text] fits [maxWidth] (at most to 70 %), then ellipsizes. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        val floor = paint.textSize * 0.7f
        while (paint.measureText(text) > maxWidth && paint.textSize > floor) paint.textSize *= 0.95f
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun tyreColor(status: WidgetTireStatus, muted: Int): Int = when (status) {
        WidgetTireStatus.OK -> StatusColors.ok.toArgb()
        WidgetTireStatus.WARNING -> StatusColors.warning.toArgb()
        WidgetTireStatus.ALERT -> StatusColors.alert.toArgb()
        WidgetTireStatus.EMPTY -> muted
    }

    private fun Rect.toRectF() = RectF(left, top, right, bottom)

    private fun clamp(value: Float, min: Float, max: Float) = if (max < min) min else value.coerceIn(min, max)

    private const val BLACK = 0xFF000000.toInt()
}
