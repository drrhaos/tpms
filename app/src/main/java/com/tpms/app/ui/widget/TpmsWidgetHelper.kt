package com.tpms.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.tpms.app.R
import com.tpms.app.startup.TeyesDeviceDetector

enum class WidgetPinResult {
    PINNED,
    NOT_SUPPORTED,
    DECLINED
}

object TpmsWidgetHelper {

    fun isPinSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPinPanel(context: Context): WidgetPinResult {
        return requestPin(context, TpmsWidget::class.java, R.string.widget_pin_failed)
    }

    fun requestPinCompact(context: Context): WidgetPinResult {
        return requestPin(context, TpmsWidgetCompact::class.java, R.string.widget_pin_compact_failed)
    }

    private fun requestPin(context: Context, providerClass: Class<*>, failMessageId: Int): WidgetPinResult {
        if (!isPinSupported(context)) {
            return WidgetPinResult.NOT_SUPPORTED
        }
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, providerClass)
        val accepted = manager.requestPinAppWidget(component, null, null)
        return if (accepted) WidgetPinResult.PINNED else WidgetPinResult.DECLINED
    }

    fun showPinResultToast(context: Context, result: WidgetPinResult) {
        val message = when (result) {
            WidgetPinResult.PINNED -> R.string.widget_pin_success
            WidgetPinResult.NOT_SUPPORTED -> {
                if (TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)) {
                    R.string.settings_teyes_frontapp_hint
                } else {
                    R.string.widget_pin_manual_hint
                }
            }
            WidgetPinResult.DECLINED -> R.string.widget_pin_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showTeyesFrontAppHint(context: Context) {
        Toast.makeText(context, R.string.settings_teyes_frontapp_hint, Toast.LENGTH_LONG).show()
    }

    fun hasActiveWidgets(context: Context): Boolean =
        TpmsWidgetUpdater.hasAnyActiveWidget(context)

    /** @deprecated use [hasActiveWidgets] */
    fun hasActiveWidgetsLegacy(context: Context): Boolean = hasActiveWidgets(context)
}
