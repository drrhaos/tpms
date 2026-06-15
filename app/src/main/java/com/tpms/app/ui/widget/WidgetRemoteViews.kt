package com.tpms.app.ui.widget

import android.content.Context
import android.widget.RemoteViews
import com.tpms.app.R

internal data class TireViewIds(
    val label: Int,
    val pressure: Int,
    val indicator: Int? = null
)

internal object WidgetRemoteViews {

    private val WIDGET_TIRE_SLOTS = listOf(
        TireViewIds(R.id.widget_fl_label, R.id.widget_fl_pressure, R.id.widget_fl_indicator),
        TireViewIds(R.id.widget_fr_label, R.id.widget_fr_pressure, R.id.widget_fr_indicator),
        TireViewIds(R.id.widget_rl_label, R.id.widget_rl_pressure, R.id.widget_rl_indicator),
        TireViewIds(R.id.widget_rr_label, R.id.widget_rr_pressure, R.id.widget_rr_indicator)
    )

    private val NOTIF_COLLAPSED_SLOTS = listOf(
        TireViewIds(R.id.notif_fl_label, R.id.notif_fl_pressure),
        TireViewIds(R.id.notif_fr_label, R.id.notif_fr_pressure),
        TireViewIds(R.id.notif_rl_label, R.id.notif_rl_pressure),
        TireViewIds(R.id.notif_rr_label, R.id.notif_rr_pressure)
    )

    private val NOTIF_EXPANDED_SLOTS = listOf(
        TireViewIds(R.id.notif_expanded_fl_label, R.id.notif_expanded_fl_pressure, R.id.notif_expanded_fl_indicator),
        TireViewIds(R.id.notif_expanded_fr_label, R.id.notif_expanded_fr_pressure, R.id.notif_expanded_fr_indicator),
        TireViewIds(R.id.notif_expanded_rl_label, R.id.notif_expanded_rl_pressure, R.id.notif_expanded_rl_indicator),
        TireViewIds(R.id.notif_expanded_rr_label, R.id.notif_expanded_rr_pressure, R.id.notif_expanded_rr_indicator)
    )

    fun forWidget(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tpms)
        bindSnapshot(
            views = views,
            snapshot = snapshot,
            tireSlots = WIDGET_TIRE_SLOTS,
            statusId = R.id.widget_status,
            unitId = R.id.widget_unit,
            showIndicators = true
        )
        return views
    }

    fun forNotificationCollapsed(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_tpms_collapsed)
        bindSnapshot(
            views = views,
            snapshot = snapshot,
            tireSlots = NOTIF_COLLAPSED_SLOTS,
            statusId = null,
            unitId = R.id.notif_unit,
            showIndicators = false
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
            showIndicators = true
        )
        return views
    }

    fun summaryLine(snapshot: WidgetSnapshot): String {
        val pressures = snapshot.tires.joinToString(" ") { "${it.label} ${it.pressureText}" }
        return "$pressures ${snapshot.unitLabel}"
    }

    private fun bindSnapshot(
        views: RemoteViews,
        snapshot: WidgetSnapshot,
        tireSlots: List<TireViewIds>,
        statusId: Int?,
        unitId: Int?,
        showIndicators: Boolean
    ) {
        statusId?.let { views.setTextViewText(it, snapshot.connectionStatus) }
        unitId?.let { views.setTextViewText(it, snapshot.unitLabel) }

        snapshot.tires.forEachIndexed { index, tire ->
            val ids = tireSlots.getOrNull(index) ?: return@forEachIndexed
            views.setTextViewText(ids.label, tire.label)
            views.setTextViewText(ids.pressure, tire.pressureText)
            if (showIndicators) {
                ids.indicator?.let {
                    views.setInt(it, "setBackgroundResource", indicatorDrawable(tire.status))
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
