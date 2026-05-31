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
            val views = RemoteViews(context.packageName, R.layout.widget_tpms)
            val intent = PendingIntent.getActivity(
                context, 0,
                MainActivity.newIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_container, intent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun pushUpdate(context: Context, sensors: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TpmsWidget::class.java)
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_tpms)
                views.setTextViewText(R.id.widget_text, sensors)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
