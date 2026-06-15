package com.tpms.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.tpms.app.R
import com.tpms.app.di.WidgetEntryPoint
import com.tpms.app.service.TpmsMonitorService
import com.tpms.app.ui.main.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class WidgetLayoutKind {
    CAR,
    COMPACT
}

object TpmsWidgetUpdater {

    private const val TAG = "TPMS_WIDGET"
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val ALL_PROVIDERS: List<Pair<Class<*>, WidgetLayoutKind>> = listOf(
        TpmsWidget::class.java to WidgetLayoutKind.CAR,
        TpmsWidgetCompact::class.java to WidgetLayoutKind.COMPACT
    )

    fun refreshWidgets(
        context: Context,
        providerClass: Class<*>,
        layoutKind: WidgetLayoutKind,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        preferPersisted: Boolean,
        onComplete: (() -> Unit)? = null
    ) {
        widgetScope.launch {
            try {
                val builder = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                ).widgetSnapshotBuilder()
                val snapshot = builder.build(context, preferPersisted = preferPersisted)
                for (appWidgetId in appWidgetIds) {
                    pushUpdate(context, appWidgetManager, layoutKind, appWidgetId, snapshot)
                }
                TpmsMonitorService.start(context)
            } catch (error: Exception) {
                Log.w(TAG, "Widget refresh failed", error)
                val empty = WidgetSnapshot.empty(context)
                for (appWidgetId in appWidgetIds) {
                    pushUpdate(context, appWidgetManager, layoutKind, appWidgetId, empty)
                }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun pushUpdateAll(context: Context, snapshot: WidgetSnapshot) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            ALL_PROVIDERS.forEach { (_, layoutKind) ->
                val providerClass = providerClassFor(layoutKind)
                val ids = manager.getAppWidgetIds(ComponentName(context, providerClass))
                ids.forEach { id ->
                    pushUpdate(context, manager, layoutKind, id, snapshot)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Widget pushUpdateAll failed", error)
        }
    }

    fun pushUpdate(
        context: Context,
        manager: AppWidgetManager,
        layoutKind: WidgetLayoutKind,
        appWidgetId: Int,
        snapshot: WidgetSnapshot
    ) {
        try {
            val views = when (layoutKind) {
                WidgetLayoutKind.CAR -> WidgetRemoteViews.forWidgetCar(context, snapshot)
                WidgetLayoutKind.COMPACT -> WidgetRemoteViews.forWidgetCompact(context, snapshot)
            }
            val intent = PendingIntent.getActivity(
                context, 0,
                MainActivity.newIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_container, intent)
            manager.updateAppWidget(appWidgetId, views)
        } catch (error: Exception) {
            Log.w(TAG, "Widget update failed for id=$appWidgetId", error)
        }
    }

    fun hasAnyActiveWidget(context: Context): Boolean =
        ALL_PROVIDERS.any { (providerClass, _) ->
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, providerClass))
                .isNotEmpty()
        }

    private fun providerClassFor(layoutKind: WidgetLayoutKind): Class<*> = when (layoutKind) {
        WidgetLayoutKind.CAR -> TpmsWidget::class.java
        WidgetLayoutKind.COMPACT -> TpmsWidgetCompact::class.java
    }
}
