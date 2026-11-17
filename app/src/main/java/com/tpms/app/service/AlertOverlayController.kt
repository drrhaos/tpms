package com.tpms.app.service

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tpms.app.R
import com.tpms.app.domain.WheelLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * The alert windows over every app (wayfinder alerts 03 / 06), `TYPE_APPLICATION_OVERLAY`, needs
 * "display over other apps":
 * - **Red "Неисправность" window** — card in the middle over a dimmed screen: the first (worst) row
 *   in full (car with the wheel lit, value, detail, advice), the others compact; big «Понятно» and
 *   «Открыть»; the frame pulses while unacknowledged; once no fault is left the subtitle says so.
 * - **Yellow "Внимание" banner** — top centre, one line per alert, «×».
 *
 * Neither closes by itself (drhaos, 2026-11-17): only the user's buttons do; alerts that cleared
 * stay as [Row.resolved] rows.
 *
 * Always dark, whatever the app theme. Content is rebuilt on every show. Main thread only; every
 * WindowManager call is guarded (the permission may be revoked at any time).
 */
@Singleton
class AlertOverlayController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** One row of a window, already localized by the coordinator. [slot] lights a wheel; null = no car. */
    data class Row(
        val key: String,
        val slot: String?,
        val title: String,
        val subtitle: String,
        val value: String?,
        val detail: String?,
        val advice: String?,
        val fault: Boolean,
        /** The alert cleared after the window opened: drawn green, "в норме". */
        val resolved: Boolean = false
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var faultView: View? = null
    private var bannerView: View? = null
    private var pulse: ValueAnimator? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Shows or refreshes the red window; false if it cannot be shown (no permission, refused).
     * [faultActive]: some row is still a fault (else the subtitle says the fault is gone).
     */
    fun showFault(
        rows: List<Row>,
        unacknowledged: Boolean,
        faultActive: Boolean,
        onAcknowledge: () -> Unit,
        onOpen: () -> Unit
    ): Boolean {
        if (rows.isEmpty() || !canShow()) {
            hideFault()
            return false
        }
        val view = buildFault(rows, unacknowledged, faultActive, onAcknowledge, onOpen)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        return replace(faultView, view, params).also { shown -> faultView = if (shown) view else null }
    }

    fun hideFault() {
        pulse?.cancel()
        pulse = null
        faultView?.let { remove(it) }
        faultView = null
    }

    /** Shows or refreshes the yellow banner (one line per alert); false if it cannot be shown. */
    fun showBanner(rows: List<Row>, onDismiss: () -> Unit): Boolean {
        if (rows.isEmpty() || !canShow()) return false
        val view = buildBanner(rows, onDismiss)
        val params = WindowManager.LayoutParams(
            min(dp(BANNER_WIDTH_DP), screenWidth() - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(16)
        }
        return replace(bannerView, view, params).also { shown -> bannerView = if (shown) view else null }
    }

    fun hideBanner() {
        bannerView?.let { remove(it) }
        bannerView = null
    }

    fun hideAll() {
        hideFault()
        hideBanner()
    }

    // --- Red window ---

    private fun buildFault(
        rows: List<Row>,
        unacknowledged: Boolean,
        faultActive: Boolean,
        onAcknowledge: () -> Unit,
        onOpen: () -> Unit
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(FAULT_BG)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(3), ALERT)
            }
            clipToOutline = true
        }
        card.addView(faultHeader(faultActive))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), 0)
        }
        rows.forEachIndexed { index, row -> body.addView(if (index == 0) fullRow(row) else compactRow(row)) }
        card.addView(body)
        card.addView(faultButtons(onAcknowledge, onOpen))

        if (unacknowledged) startPulse(card.background as GradientDrawable) else pulse?.cancel()

        val width = min(dp(FAULT_MAX_WIDTH_DP), (screenWidth() * 0.82f).toInt())
        return FrameLayout(context).apply {
            setBackgroundColor(SCRIM)
            addView(card, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            // The dimmed area swallows touches: the red window is answered with its buttons.
            isClickable = true
        }
    }

    private fun faultHeader(faultActive: Boolean): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ALERT)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        addView(badge("!", Color.WHITE, ALERT, 38))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(context.getString(R.string.alert_window_fault_title), 24f, Color.WHITE, bold = true))
            val subtitle = if (faultActive) R.string.alert_window_fault_subtitle else R.string.alert_window_fault_cleared
            addView(text(context.getString(subtitle), 15f, 0xE6FFFFFF.toInt()))
        })
    }

    private fun fullRow(row: Row): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(rowBackground(row), 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = rowParams()
        if (row.slot != null) addView(CarIcon(context, row.slot, row.fault && !row.resolved), LinearLayout.LayoutParams(dp(54), dp(84)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(text("${row.title} · ${row.subtitle}", 22f, TEXT, bold = true))
            row.value?.let { addView(text(it, 22f, accent(row), bold = true)) }
            row.detail?.let { addView(text(it, 17f, TEXT)) }
            row.advice?.let { addView(text(it, 15f, if (row.fault) ADVICE_FAULT else ADVICE_WARN)) }
        })
    }

    private fun compactRow(row: Row): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(rowBackground(row), 10)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = rowParams()
        if (row.slot != null) addView(CarIcon(context, row.slot, row.fault), LinearLayout.LayoutParams(dp(24), dp(36)))
        val line = listOfNotNull(row.title, row.subtitle, row.value).joinToString(" · ")
        val color = when {
            row.resolved -> OK
            row.fault -> TEXT
            else -> ADVICE_WARN
        }
        addView(text(line, 18f, color, bold = row.fault && !row.resolved).apply {
            setPadding(dp(12), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    private fun faultButtons(onAcknowledge: () -> Unit, onOpen: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(18), dp(12), dp(18), dp(16))
        addView(button(context.getString(R.string.alert_window_acknowledge), Color.WHITE, ALERT_DEEP, 22f, onAcknowledge),
            LinearLayout.LayoutParams(0, dp(68), 1f))
        addView(button(context.getString(R.string.alert_window_open), Color.TRANSPARENT, Color.WHITE, 18f, onOpen).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), 0x80FFFFFF.toInt())
            }
        }, LinearLayout.LayoutParams(0, dp(68), 0.55f).apply { marginStart = dp(12) })
    }

    private fun startPulse(frame: GradientDrawable) {
        pulse?.cancel()
        pulse = ValueAnimator.ofObject(ArgbEvaluator(), ALERT, ALERT_LIGHT).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { frame.setStroke(dp(3), it.animatedValue as Int) }
            start()
        }
    }

    // --- Yellow banner ---

    private fun buildBanner(rows: List<Row>, onDismiss: () -> Unit): View {
        val first = rows.first()
        val lines = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            val title = if (rows.size == 1) first.title else context.getString(R.string.alert_window_attention_many, rows.size)
            addView(text(context.getString(R.string.alert_window_attention_title, title), 19f, TEXT, bold = true))
            rows.forEach { row ->
                val line = if (rows.size == 1) listOfNotNull(row.value, row.subtitle) else listOfNotNull(row.title, row.subtitle, row.value)
                addView(text(line.joinToString(" — "), 16f, if (row.resolved) OK else WARNING))
            }
            val advice = if (rows.size == 1 && !first.resolved) first.advice else null
            addView(text(listOfNotNull(context.getString(R.string.alert_window_attention_drive_on), advice).joinToString(". "), 15f, ADVICE_WARN))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(WARN_BG)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), WARNING)
            }
            addView(badge("!", WARNING, WARN_BG, 34))
            addView(lines, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button("×", Color.TRANSPARENT, ADVICE_WARN, 22f, onDismiss).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), 0x99F0A020.toInt())
                }
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
    }

    // --- Helpers ---

    private fun replace(old: View?, view: View, params: WindowManager.LayoutParams): Boolean {
        old?.let { remove(it) }
        return runCatching { windowManager.addView(view, params) }.isSuccess
    }

    private fun remove(view: View) {
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun badge(symbol: String, background: Int, foreground: Int, sizeDp: Int) = TextView(context).apply {
        text = symbol
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.6f)
        setTextColor(foreground)
        typeface = Typeface.DEFAULT_BOLD
        this.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(background)
        }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun button(label: String, background: Int, foreground: Int, sp: Float, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(foreground)
        typeface = Typeface.DEFAULT_BOLD
        this.background = rounded(background, 14)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun rowBackground(row: Row): Int = when {
        row.resolved -> ROW_OK_BG
        row.fault -> ROW_BG
        else -> ROW_WARN_BG
    }

    private fun accent(row: Row): Int = when {
        row.resolved -> OK
        row.fault -> ALERT
        else -> WARNING
    }

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(10) }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** Top view of a car with the wheel of [slot] lit (red for a fault, amber otherwise). */
    private class CarIcon(context: Context, private val slot: String, private val fault: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val wheelW = w * 0.22f
            val wheelH = h * 0.26f
            val stroke = w * 0.05f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = OUTLINE
            rect.set(wheelW * 0.6f, stroke, w - wheelW * 0.6f, h - stroke)
            canvas.drawRoundRect(rect, w * 0.3f, w * 0.3f, paint)
            paint.style = Paint.Style.FILL
            for (wheel in WheelLayout.ORDER) {
                paint.color = when {
                    wheel != slot -> OUTLINE
                    fault -> ALERT
                    else -> WARNING
                }
                val left = if (wheel.endsWith("L")) 0f else w - wheelW
                val top = if (wheel.startsWith("F")) h * 0.12f else h - h * 0.12f - wheelH
                rect.set(left, top, left + wheelW, top + wheelH)
                canvas.drawRoundRect(rect, wheelW * 0.25f, wheelW * 0.25f, paint)
            }
        }
    }

    companion object {
        private const val FAULT_MAX_WIDTH_DP = 820
        private const val BANNER_WIDTH_DP = 620
        // Alert windows are always dark (wayfinder alerts 06); colours from the app's status palette.
        private const val ALERT = 0xFFE5484D.toInt()
        private const val ALERT_LIGHT = 0xFFFF9A9D.toInt()
        private const val ALERT_DEEP = 0xFF8E1C21.toInt()
        private const val WARNING = 0xFFF0A020.toInt()
        private const val FAULT_BG = 0xFF2A0F11.toInt()
        private const val WARN_BG = 0xFF2B2008.toInt()
        private const val OK = 0xFF3DB46D.toInt()
        private const val ROW_OK_BG = 0xFF0F2A18.toInt()
        private const val ROW_BG = 0x0AFFFFFF
        private const val ROW_WARN_BG = 0xFF2B2008.toInt()
        private const val TEXT = 0xFFECEFF3.toInt()
        private const val ADVICE_FAULT = 0xFFF3C2C4.toInt()
        private const val ADVICE_WARN = 0xFFF2D49A.toInt()
        private const val OUTLINE = 0xFF5A6470.toInt()
        private const val SCRIM = 0x59000000
    }
}
