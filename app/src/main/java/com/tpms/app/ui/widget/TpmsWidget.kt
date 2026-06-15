package com.tpms.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.tpms.app.ui.main.MainActivity

class TpmsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            pushUpdate(context, appWidgetManager, appWidgetId, WidgetSnapshot.empty(context))
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
            try {
                val views = WidgetRemoteViews.forWidget(context, snapshot)
                val intent = PendingIntent.getActivity(
                    context, 0,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(com.tpms.app.R.id.widget_container, intent)
                manager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {
                // Widget update is best-effort
            }
        }
    }
}
