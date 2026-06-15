package com.tpms.app.ui.embedded

import android.app.Activity
import android.graphics.Rect
import android.util.DisplayMetrics
import kotlin.math.roundToInt

object EmbeddedWindowDetector {

    const val FRONTAPP_PACKAGE = "ru.fytmods.frontapp"
    const val EXTRA_FORCE_FULLSCREEN = "com.tpms.app.extra.FORCE_FULLSCREEN"
    const val EXTRA_EMBEDDED_WINDOW = "com.tpms.app.extra.EMBEDDED_WINDOW"

    private const val CONSTRAINED_RATIO = 0.92f

    fun detect(
        activity: Activity,
        windowWidthPx: Int = activity.window.decorView.width,
        windowHeightPx: Int = activity.window.decorView.height,
        density: Float = activity.resources.displayMetrics.density
    ): EmbeddedWindowState {
        if (activity.intent.getBooleanExtra(EXTRA_FORCE_FULLSCREEN, false)) {
            return EmbeddedWindowState.fullScreen(
                widthDp = pxToDp(windowWidthPx, density),
                heightDp = pxToDp(windowHeightPx, density)
            )
        }

        val widthDp = pxToDp(windowWidthPx, density)
        val heightDp = pxToDp(windowHeightPx, density)
        val display = displaySizeDp(activity)
        val fromFrontApp = isLaunchedFromFrontApp(activity)
        val intentEmbedded = activity.intent.getBooleanExtra(EXTRA_EMBEDDED_WINDOW, false)
        val multiWindow = activity.isInMultiWindowMode
        val constrained = isConstrainedWindow(widthDp, heightDp, display.first, display.second)
        val embedded = fromFrontApp || intentEmbedded || (multiWindow && constrained) ||
            (fromFrontAppHost(activity) && constrained)

        return EmbeddedWindowState(
            isEmbedded = embedded,
            launchedFromFrontApp = fromFrontApp,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }

    fun isLaunchedFromFrontApp(activity: Activity): Boolean {
        val referrer = activity.referrer ?: return false
        return referrer.scheme == "android-app" && referrer.host == FRONTAPP_PACKAGE
    }

    internal fun isConstrainedWindow(
        windowWidthDp: Int,
        windowHeightDp: Int,
        displayWidthDp: Int,
        displayHeightDp: Int
    ): Boolean {
        if (windowWidthDp <= 0 || windowHeightDp <= 0) return false
        if (displayWidthDp <= 0 || displayHeightDp <= 0) return false
        val widthRatio = windowWidthDp.toFloat() / displayWidthDp
        val heightRatio = windowHeightDp.toFloat() / displayHeightDp
        return widthRatio < CONSTRAINED_RATIO || heightRatio < CONSTRAINED_RATIO
    }

    private fun fromFrontAppHost(activity: Activity): Boolean {
        return activity.callingPackage == FRONTAPP_PACKAGE
    }

    private fun displaySizeDp(activity: Activity): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        val density = metrics.density
        return pxToDp(metrics.widthPixels, density) to pxToDp(metrics.heightPixels, density)
    }

    private fun pxToDp(px: Int, density: Float): Int =
        if (density <= 0f) 0 else (px / density).roundToInt()

    fun windowBoundsPx(activity: Activity): Rect {
        val rect = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(rect)
        return rect
    }
}
