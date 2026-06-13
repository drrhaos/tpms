package com.tpms.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.tpms.app.R

object TpmsWidgetHelper {

    fun isPinSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPinToTeyesPanel(context: Context): Boolean {
        if (!isPinSupported(context)) {
            Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show()
            return false
        }
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TpmsWidget::class.java)
        val accepted = manager.requestPinAppWidget(component, null, null)
        if (!accepted) {
            Toast.makeText(context, R.string.widget_pin_failed, Toast.LENGTH_LONG).show()
        }
        return accepted
    }

    fun hasActiveWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TpmsWidget::class.java))
        return ids.isNotEmpty()
    }
}
