package com.tpms.app.ui.widget

import android.content.Context
import android.widget.RemoteViews
import com.tpms.app.R

internal data class TireViewIds(
    val label: Int,
    val pressure: Int,
    val temperature: Int? = null,
    val battery: Int? = null,
    val indicator: Int? = null
)

internal object WidgetRemoteViews {

    private val WIDGET_CAR_SLOTS = listOf(
        TireViewIds(
            R.id.widget_fl_label, R.id.widget_fl_pressure,
            R.id.widget_fl_temp, R.id.widget_fl_battery, R.id.widget_fl_indicator
        ),
        TireViewIds(
            R.id.widget_fr_label, R.id.widget_fr_pressure,
            R.id.widget_fr_temp, R.id.widget_fr_battery, R.id.widget_fr_indicator
        ),
        TireViewIds(
            R.id.widget_rl_label, R.id.widget_rl_pressure,
            R.id.widget_rl_temp, R.id.widget_rl_battery, R.id.widget_rl_indicator
        ),
        TireViewIds(
            R.id.widget_rr_label, R.id.widget_rr_pressure,
            R.id.widget_rr_temp, R.id.widget_rr_battery, R.id.widget_rr_indicator
        )
    )

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

    fun forWidgetCar(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tpms_car)
        applyTheme(views, snapshot.useLightTheme)
        bindSnapshot(
            views = views,
            snapshot = snapshot,
            tireSlots = WIDGET_CAR_SLOTS,
            statusId = R.id.widget_status,
            unitId = R.id.widget_unit,
            showIndicators = true,
            showDetails = true,
            useLightTheme = snapshot.useLightTheme
        )
        return views
    }

    fun forWidgetCompact(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tpms_compact)
        applyTheme(views, snapshot.useLightTheme)
        bindSnapshot(
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

    /** @deprecated use [forWidgetCar] */
    fun forWidget(context: Context, snapshot: WidgetSnapshot): RemoteViews =
        forWidgetCar(context, snapshot)

    fun forNotificationCollapsed(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_tpms_collapsed)
        bindSnapshot(
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
        views: RemoteViews,
        snapshot: WidgetSnapshot,
        tireSlots: List<TireViewIds>,
        statusId: Int?,
        unitId: Int?,
        showIndicators: Boolean,
        showDetails: Boolean,
        useLightTheme: Boolean
    ) {
        val muted = if (useLightTheme) 0xFF5A6570.toInt() else 0xFF8FA3BC.toInt()
        val primary = if (useLightTheme) 0xFF1A2332.toInt() else 0xFFE8EDF4.toInt()

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
            views.setTextViewText(ids.label, tire.label)
            views.setTextColor(ids.label, muted)
            views.setTextViewText(ids.pressure, tire.pressureText)
            views.setTextColor(ids.pressure, primary)
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
                    views.setImageViewResource(it, indicatorDrawable(tire.status))
                }
            }
        }
    }

    private fun indicatorDrawable(status: WidgetTireStatus): Int = when (status) {
        WidgetTireStatus.OK -> R.drawable.widget_indicator_ok
        WidgetTireStatus.WARNING -> R.drawable.widget_indicator_warning
        WidgetTireStatus.ALERT -> R.drawable.widget_indicator_alert
        WidgetTireStatus.EMPTY -> R.drawable.widget_indicator_empty
    }
}
