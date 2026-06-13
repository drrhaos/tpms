package com.tpms.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.tpms.app.R
import com.tpms.app.ui.main.MainActivity

class TpmsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            pushUpdate(context, appWidgetManager, appWidgetId, WidgetSnapshot.empty())
        }
    }

    companion object {
        fun pushUpdate(context: Context, snapshot: WidgetSnapshot) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, TpmsWidget::class.java)
                )
                for (id in ids) {
                    pushUpdate(context, manager, id, snapshot)
                }
            } catch (_: Exception) {
                // Widget update is best-effort
            }
        }

        fun pushUpdate(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            snapshot: WidgetSnapshot
        ) {
            val views = buildRemoteViews(context, snapshot)
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun buildRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tpms)

            views.setTextViewText(R.id.widget_status, snapshot.connectionStatus)
            views.setTextViewText(R.id.widget_unit, snapshot.unitLabel)

            snapshot.tires.forEachIndexed { index, tire ->
                val (labelId, pressureId, indicatorId) = when (index) {
                    0 -> Triple(R.id.widget_fl_label, R.id.widget_fl_pressure, R.id.widget_fl_indicator)
                    1 -> Triple(R.id.widget_fr_label, R.id.widget_fr_pressure, R.id.widget_fr_indicator)
                    2 -> Triple(R.id.widget_rl_label, R.id.widget_rl_pressure, R.id.widget_rl_indicator)
                    3 -> Triple(R.id.widget_rr_label, R.id.widget_rr_pressure, R.id.widget_rr_indicator)
                    else -> return@forEachIndexed
                }
                views.setTextViewText(labelId, tire.label)
                views.setTextViewText(pressureId, tire.pressureText)
                views.setInt(indicatorId, "setBackgroundResource", indicatorDrawable(tire.status))
            }

            val intent = PendingIntent.getActivity(
                context, 0,
                MainActivity.newIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_container, intent)
            return views
        }

        private fun indicatorDrawable(status: WidgetTireStatus): Int = when (status) {
            WidgetTireStatus.OK -> R.drawable.widget_indicator_ok
            WidgetTireStatus.WARNING -> R.drawable.widget_indicator_warning
            WidgetTireStatus.ALERT -> R.drawable.widget_indicator_alert
            WidgetTireStatus.EMPTY -> R.drawable.widget_indicator_empty
        }
    }
}
