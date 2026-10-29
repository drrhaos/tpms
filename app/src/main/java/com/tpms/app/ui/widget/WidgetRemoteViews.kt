package com.tpms.app.ui.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.tpms.app.R
import com.tpms.app.ui.theme.DarkPalette
import com.tpms.app.ui.theme.LightPalette

internal data class TireViewIds(
    val label: Int,
    val pressure: Int,
    val temperature: Int? = null,
    val battery: Int? = null,
    val indicator: Int? = null
)

internal object WidgetRemoteViews {

    /** widget_tpms_car.xml: 10 dp padding each side; 8 + 4 dp padding and a ~18 dp status line. */
    private const val CAR_PADDING_H_DP = 20f
    private const val CAR_PADDING_V_DP = 30f

    private val WIDGET_COMPACT_SLOTS = listOf(
        TireViewIds(
            R.id.widget_compact_fl_label, R.id.widget_compact_fl_pressure,
            indicator = R.id.widget_compact_fl_indicator
        ),
        TireViewIds(
            R.id.widget_compact_fr_label, R.id.widget_compact_fr_pressure,
            indicator = R.id.widget_compact_fr_indicator
        ),
        TireViewIds(
            R.id.widget_compact_rl_label, R.id.widget_compact_rl_pressure,
            indicator = R.id.widget_compact_rl_indicator
        ),
        TireViewIds(
            R.id.widget_compact_rr_label, R.id.widget_compact_rr_pressure,
            indicator = R.id.widget_compact_rr_indicator
        )
    )

    private val NOTIF_COLLAPSED_SLOTS = listOf(
        TireViewIds(R.id.notif_fl_label, R.id.notif_fl_pressure),
        TireViewIds(R.id.notif_fr_label, R.id.notif_fr_pressure),
        TireViewIds(R.id.notif_rl_label, R.id.notif_rl_pressure),
        TireViewIds(R.id.notif_rr_label, R.id.notif_rr_pressure)
    )

    private val NOTIF_EXPANDED_SLOTS = listOf(
        TireViewIds(R.id.notif_expanded_fl_label, R.id.notif_expanded_fl_pressure, indicator = R.id.notif_expanded_fl_indicator),
        TireViewIds(R.id.notif_expanded_fr_label, R.id.notif_expanded_fr_pressure, indicator = R.id.notif_expanded_fr_indicator),
        TireViewIds(R.id.notif_expanded_rl_label, R.id.notif_expanded_rl_pressure, indicator = R.id.notif_expanded_rl_indicator),
        TireViewIds(R.id.notif_expanded_rr_label, R.id.notif_expanded_rr_pressure, indicator = R.id.notif_expanded_rr_indicator)
    )

    /** [widthDp] × [heightDp]: the widget's size on the home screen (the scene is drawn to fit). */
    fun forWidgetCar(context: Context, snapshot: WidgetSnapshot, widthDp: Float, heightDp: Float): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tpms_car)
        applyTheme(views, snapshot.useLightTheme)
        val muted = (if (snapshot.useLightTheme) LightPalette else DarkPalette).onSurfaceMuted.toArgb()
        views.setTextViewText(R.id.widget_status, snapshot.connectionStatus)
        views.setTextColor(R.id.widget_status, muted)
        views.setTextViewText(R.id.widget_unit, snapshot.unitLabel)
        views.setTextColor(R.id.widget_unit, muted)
        // The scene sits under the status line, inside the container padding.
        val sceneW = (widthDp - CAR_PADDING_H_DP).coerceAtLeast(1f)
        val sceneH = (heightDp - CAR_PADDING_V_DP).coerceAtLeast(1f)
        views.setImageViewBitmap(R.id.widget_car, WidgetCarRenderer.render(context, snapshot, sceneW, sceneH))
        views.setContentDescription(R.id.widget_car, summaryLine(snapshot))
        return views
    }

    fun forWidgetCompact(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tpms_compact)
        applyTheme(views, snapshot.useLightTheme)
        bindSnapshot(
            context = context,
            views = views,
            snapshot = snapshot,
            tireSlots = WIDGET_COMPACT_SLOTS,
            statusId = R.id.widget_compact_status,
            unitId = R.id.widget_compact_unit,
            showIndicators = true,
            showDetails = false,
            useLightTheme = snapshot.useLightTheme
        )
        return views
    }

    fun forNotificationCollapsed(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_tpms_collapsed)
        bindSnapshot(
            context = context,
            views = views,
            snapshot = snapshot,
            tireSlots = NOTIF_COLLAPSED_SLOTS,
            statusId = null,
            unitId = R.id.notif_unit,
            showIndicators = false,
            showDetails = false,
            useLightTheme = snapshot.useLightTheme
        )
        return views
    }

    fun forNotificationExpanded(
        context: Context,
        snapshot: WidgetSnapshot,
        statusLine: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_tpms_expanded)
        views.setTextViewText(R.id.notif_expanded_status, statusLine)
        bindSnapshot(
            context = context,
            views = views,
            snapshot = snapshot,
            tireSlots = NOTIF_EXPANDED_SLOTS,
            statusId = null,
            unitId = R.id.notif_expanded_unit,
            showIndicators = true,
            showDetails = false,
            useLightTheme = snapshot.useLightTheme
        )
        return views
    }

    fun summaryLine(snapshot: WidgetSnapshot): String {
        val pressures = snapshot.tires.joinToString(" ") { tire ->
            val value = tire.pressureText.substringBefore(' ')
            "${tire.label} $value"
        }
        return "$pressures ${snapshot.unitLabel}"
    }

    private fun applyTheme(views: RemoteViews, useLightTheme: Boolean) {
        views.setInt(
            R.id.widget_container,
            "setBackgroundResource",
            if (useLightTheme) R.drawable.widget_bg_light else R.drawable.widget_bg
        )
    }

    private fun bindSnapshot(
        context: Context,
        views: RemoteViews,
        snapshot: WidgetSnapshot,
        tireSlots: List<TireViewIds>,
        statusId: Int?,
        unitId: Int?,
        showIndicators: Boolean,
        showDetails: Boolean,
        useLightTheme: Boolean
    ) {
        val palette = if (useLightTheme) LightPalette else DarkPalette
        val muted = palette.onSurfaceMuted.toArgb()
        val primary = palette.onBackground.toArgb()

        statusId?.let {
            views.setTextViewText(it, snapshot.connectionStatus)
            views.setTextColor(it, muted)
        }
        unitId?.let {
            views.setTextViewText(it, snapshot.unitLabel)
            views.setTextColor(it, muted)
        }

        snapshot.tires.forEachIndexed { index, tire ->
            val ids = tireSlots.getOrNull(index) ?: return@forEachIndexed
            views.setTextViewText(ids.label, withStaleCaption(context, tire.label, tire))
            views.setTextColor(ids.label, muted)
            views.setTextViewText(ids.pressure, tire.pressureText)
            views.setTextColor(ids.pressure, if (tire.muted) muted else primary)
            if (showDetails) {
                ids.temperature?.let {
                    views.setTextViewText(it, tire.temperatureText)
                    views.setTextColor(it, muted)
                }
                ids.battery?.let {
                    views.setTextViewText(it, tire.batteryText)
                    views.setTextColor(it, muted)
                }
            }
            if (showIndicators) {
                ids.indicator?.let {
                    views.setImageViewResource(it, indicatorDrawable(tire.displayStatus))
                }
            }
        }
    }

    /** Appends the stale wheel's "N min" caption to [text]. */
    private fun withStaleCaption(context: Context, text: String, tire: WidgetTireSlot): String =
        tire.staleMinutes?.let { context.getString(R.string.wheel_stale_caption_format, text, it) } ?: text

    private fun indicatorDrawable(status: WidgetTireStatus): Int = when (status) {
        WidgetTireStatus.OK -> R.drawable.widget_indicator_ok
        WidgetTireStatus.WARNING -> R.drawable.widget_indicator_warning
        WidgetTireStatus.ALERT -> R.drawable.widget_indicator_alert
        WidgetTireStatus.EMPTY -> R.drawable.widget_indicator_empty
    }
}
