package com.tpms.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.tpms.app.R

enum class WidgetPinResult {
    PINNED,
    NOT_SUPPORTED,
    DECLINED
}

object TpmsWidgetHelper {

    fun isPinSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPinPanel(context: Context): WidgetPinResult =
        requestPin(context, TpmsWidget::class.java)

    fun requestPinCompact(context: Context): WidgetPinResult =
        requestPin(context, TpmsWidgetCompact::class.java)

    private fun requestPin(context: Context, providerClass: Class<*>): WidgetPinResult {
        if (!isPinSupported(context)) {
            return WidgetPinResult.NOT_SUPPORTED
        }
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val accepted = manager.requestPinAppWidget(component, null, null)
        return if (accepted) WidgetPinResult.PINNED else WidgetPinResult.DECLINED
    }

    fun showPinResultToast(context: Context, result: WidgetPinResult, isTeyesDevice: Boolean) {
        val message = when (result) {
            WidgetPinResult.PINNED -> R.string.widget_pin_success
            WidgetPinResult.NOT_SUPPORTED -> {
                if (isTeyesDevice) {
                    R.string.settings_teyes_frontapp_hint
                } else {
                    R.string.widget_pin_manual_hint
                }
            }
            WidgetPinResult.DECLINED -> R.string.widget_pin_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun hasActiveWidgets(context: Context): Boolean =
        TpmsWidgetUpdater.hasAnyActiveWidget(context)
}
