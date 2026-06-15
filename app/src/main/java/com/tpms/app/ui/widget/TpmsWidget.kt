package com.tpms.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import com.tpms.app.di.WidgetEntryPoint
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TpmsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refreshWidgets(context, appWidgetManager, appWidgetIds, preferPersisted = true)
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
        refreshWidgets(context, appWidgetManager, intArrayOf(appWidgetId), preferPersisted = true)
    }

    private fun refreshWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        preferPersisted: Boolean
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val builder = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                ).widgetSnapshotBuilder()
                val snapshot = builder.build(context, preferPersisted = preferPersisted)
                for (appWidgetId in appWidgetIds) {
                    pushUpdate(context, appWidgetManager, appWidgetId, snapshot)
                }
                TpmsMonitorService.start(context)
            } catch (error: Exception) {
                Log.w(TAG, "Widget refresh failed, using empty snapshot", error)
                for (appWidgetId in appWidgetIds) {
                    pushUpdate(context, appWidgetManager, appWidgetId, WidgetSnapshot.empty(context))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TPMS_WIDGET"
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun pushUpdate(context: Context, snapshot: WidgetSnapshot) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, TpmsWidget::class.java)
                )
                for (id in ids) {
                    pushUpdate(context, manager, id, snapshot)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Widget pushUpdate failed", error)
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
            } catch (error: Exception) {
                Log.w(TAG, "Widget updateAppWidget failed for id=$appWidgetId", error)
            }
        }
    }
}
