package com.tpms.app.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.tpms.app.service.TpmsMonitorService

class TpmsWidgetCompact : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        TpmsWidgetUpdater.refreshWidgets(
            context = context,
            providerClass = TpmsWidgetCompact::class.java,
            layoutKind = WidgetLayoutKind.COMPACT,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            preferPersisted = true,
            onComplete = pending?.let { { it.finish() } }
        )
    }

    override fun onEnabled(context: Context) {
        TpmsMonitorService.start(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        TpmsWidgetUpdater.refreshWidgets(
            context = context,
            providerClass = TpmsWidgetCompact::class.java,
            layoutKind = WidgetLayoutKind.COMPACT,
            appWidgetManager = appWidgetManager,
            appWidgetIds = intArrayOf(appWidgetId),
            preferPersisted = true,
            onComplete = pending?.let { { it.finish() } }
        )
    }
}
