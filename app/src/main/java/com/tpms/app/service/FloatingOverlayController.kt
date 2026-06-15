package com.tpms.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.tpms.app.R
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.widget.WidgetSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingOverlayController @Inject constructor() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun showOrUpdate(context: Context, snapshot: WidgetSnapshot) {
        if (!canDrawOverlays(context)) return
        val appContext = context.applicationContext
        val wm = windowManager ?: appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = overlayView ?: createOverlayView(appContext, wm).also { overlayView = it }
        bindSnapshot(view, snapshot)
        view.setOnClickListener {
            appContext.startActivity(MainActivity.newIntent(appContext))
        }
    }

    fun hide(context: Context) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        runCatching { wm.removeView(view) }
        overlayView = null
    }

    private fun createOverlayView(context: Context, wm: WindowManager): View {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_tpms, null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24
        }
        wm.addView(view, params)
        return view
    }

    private fun bindSnapshot(view: View, snapshot: WidgetSnapshot) {
        view.findViewById<android.widget.TextView>(R.id.overlay_status)?.text = snapshot.connectionStatus
        val labels = listOf(R.id.overlay_fl, R.id.overlay_fr, R.id.overlay_rl, R.id.overlay_rr)
        snapshot.tires.forEachIndexed { index, tire ->
            val id = labels.getOrNull(index) ?: return@forEachIndexed
            view.findViewById<android.widget.TextView>(id)?.text =
                "${tire.label} ${tire.pressureText.substringBefore(' ')}"
        }
    }
}
