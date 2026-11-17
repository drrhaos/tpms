package com.tpms.app.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.tpms.app.R
import com.tpms.app.data.persistence.ServiceHeartbeatStore
import com.tpms.app.data.repository.TpmsRepository
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.domain.AlertPresentation
import com.tpms.app.domain.AlertPresentation.Entry
import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.ui.adviceRes
import com.tpms.app.ui.cardLabel
import com.tpms.app.ui.localizedLabel
import com.tpms.app.ui.main.MainActivity
import com.tpms.app.ui.wheelPositionLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Presents the alerts (wayfinder alerts 08): feeds the pure [AlertPresentation] with the wheels,
 * the live flag, "monitoring not working", the screen and the user's actions, and executes its plan
 * through [AlertNotifier] (notifications), [AlertOverlayController] (red window, yellow banner) and
 * [AlertSoundPlayer] (signals). The only owner of the alert timers: one pending [recompute] at the
 * plan's next check time. The state is persisted on every change.
 *
 * Without "display over other apps" (or with the alert window turned off) the red window falls
 * back to the notification's full-screen intent.
 *
 * Main thread only. Active between [onServiceCreated] and [onMonitoringStopped].
 */
@Singleton
class AlertCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TpmsRepository,
    private val settingsStore: SettingsStore,
    private val heartbeatStore: ServiceHeartbeatStore,
    private val notifier: AlertNotifier,
    private val overlay: AlertOverlayController,
    private val sound: AlertSoundPlayer
) {
    private val handler = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val tick = Runnable { recompute() }

    private var state = AlertPresentation.State()
    private var persisted: String? = null
    private var loaded = false
    private var active = false

    private var monitoringBlind = false
    private var monitoringReason: String? = null

    /** What the overlay shows now, so an unchanged plan does not rebuild it (and restart the pulse). */
    private var shownWindow: Pair<List<AlertOverlayController.Row>, Boolean>? = null
    private var shownBanner: List<AlertOverlayController.Row>? = null

    private val _faultUnacknowledged = MutableStateFlow(false)

    /** A fault waits for «Понятно» (main screen banner button). */
    val faultUnacknowledged: StateFlow<Boolean> = _faultUnacknowledged.asStateFlow()

    /** Service created: loads the state, clears notifications of an earlier process (re-posted below). */
    fun onServiceCreated() {
        active = true
        if (!loaded) {
            heartbeatStore.alertPresentationBlocking()?.let { stored ->
                state = AlertPresentation.decode(stored)
                persisted = stored
                loaded = true
            }
        }
        notifier.cancelAll()
        sound.prepare()
        recompute()
    }

    /**
     * Monitoring stopped: nothing may outlive it. [intentional] (ACTION_STOP) also forgets the state,
     * so the next monitoring session presents everything anew; a system stop keeps it.
     */
    fun onMonitoringStopped(intentional: Boolean) {
        active = false
        handler.removeCallbacks(tick)
        overlay.hideAll()
        shownWindow = null
        shownBanner = null
        notifier.cancelAll()
        sound.release()
        if (intentional) {
            state = AlertPresentation.State()
            persist(force = true)
        }
        _faultUnacknowledged.value = false
    }

    /**
     * A new trip (wayfinder: TripBoundary): every active alert is presented and rings again; windows
     * still open stay open (only the user closes them).
     */
    fun onNewTrip() {
        state = AlertPresentation.newTrip(state)
        loaded = true
        notifier.cancelAll()
        persist()
        recompute()
    }

    /** Wheels, the session or the screen changed. */
    fun update() = recompute()

    fun setMonitoringBlind(blind: Boolean, reason: String?) {
        if (blind == monitoringBlind && reason == monitoringReason) return
        monitoringBlind = blind
        monitoringReason = reason
        recompute()
    }

    /** «Понятно» (window, notification, main screen). */
    fun acknowledge() {
        state = AlertPresentation.acknowledge(state, System.currentTimeMillis(), settingsStore.alertReminderMs.value)
        recompute()
    }

    /** «Без звука 30 мин»: attention only. */
    fun muteAttention() {
        state = AlertPresentation.muteAttention(state, System.currentTimeMillis())
        recompute()
    }

    fun onNotificationSwiped(key: String) = notifier.repost(key)

    /**
     * "Test alert" from the settings: the red window (or the yellow banner) with its signal, closed
     * by its button like a real one; the real state is untouched. Returns whether the window could
     * be drawn (the signal plays either way).
     */
    fun postTest(level: AlertLevel): Boolean {
        val sample = AlertOverlayController.Row(
            key = "test",
            slot = "RL",
            title = wheelPositionLabel(context, "RL"),
            subtitle = (if (level == AlertLevel.FAULT) AlertType.PRESSURE_DROP else AlertType.LOW_PRESSURE).cardLabel(context),
            value = context.getString(R.string.alert_window_test_value),
            detail = null,
            advice = context.getString(R.string.alert_window_test_advice),
            fault = level == AlertLevel.FAULT
        )
        val shown = if (level == AlertLevel.FAULT) {
            overlay.showFault(listOf(sample), unacknowledged = true, faultActive = true, onAcknowledge = ::closeTest, onOpen = ::closeTest)
                .also { shownWindow = null }
        } else {
            overlay.showBanner(listOf(sample), ::closeTest).also { shownBanner = null }
        }
        sound.play(if (level == AlertLevel.FAULT) AlertPresentation.Sound.FAULT else AlertPresentation.Sound.ATTENTION)
        return shown
    }

    private fun recompute() {
        handler.removeCallbacks(tick)
        if (!active) return
        val now = System.currentTimeMillis()
        val wheels = repository.wheels.value
        val input = AlertPresentation.Input(
            wheels = wheels.values.mapNotNull { sensor -> sensor.toWheelAlert() },
            live = repository.connectionSnapshot().hasLiveSession,
            monitoringBlind = monitoringBlind,
            screenOn = powerManager?.isInteractive ?: true,
            nowMs = now,
            reminderIntervalMs = settingsStore.alertReminderMs.value
        )
        val (next, plan) = AlertPresentation.step(state, input)
        state = next
        persist()
        execute(plan)
        plan.nextCheckAtMs?.let { at -> handler.postDelayed(tick, (at - now).coerceAtLeast(MIN_TICK_MS)) }
    }

    private fun execute(plan: AlertPresentation.Plan) {
        _faultUnacknowledged.value = state.hasFault && !state.acknowledged
        val windowAllowed = settingsStore.criticalAlertsFullscreen.value
        var windowShown = false
        val window = plan.window
        if (window != null && windowAllowed) {
            val rows = window.map { rowOf(it) } + plan.windowResolved.map { resolvedRow(it) }
            val wanted = rows to plan.windowUnacknowledged
            val faultActive = window.any { it.level == AlertLevel.FAULT }
            windowShown = if (shownWindow == wanted) {
                true
            } else {
                overlay.showFault(rows, plan.windowUnacknowledged, faultActive, onAcknowledge = { acknowledge() }, onOpen = {
                    acknowledge()
                    runCatching { context.startActivity(MainActivity.newIntent(context)) }
                }).also { shownWindow = if (it) wanted else null }
            }
        } else if (shownWindow != null) {
            // Hidden, not closed: the screen is off or the window turned off; the state keeps it open.
            overlay.hideFault()
            shownWindow = null
        }

        val banner = plan.banner
        if (banner != null) {
            val rows = banner.map { rowOf(it) } + plan.bannerResolved.map { resolvedRow(it) }
            if (rows != shownBanner && overlay.showBanner(rows) { dismissBanner() }) shownBanner = rows
        } else if (shownBanner != null) {
            overlay.hideBanner()
            shownBanner = null
        }

        plan.sound?.let { sound.play(it) }
        // The full-screen intent stands in for a red window that could not be drawn.
        notifier.sync(plan.notifications, ::rowOf, faultUnacknowledged = plan.windowUnacknowledged && !windowShown)
    }

    /** A test window was closed by its button; the real alerts are presented again. */
    private fun closeTest() {
        overlay.hideAll()
        shownWindow = null
        shownBanner = null
        recompute()
    }

    private fun dismissBanner() {
        state = AlertPresentation.dismissBanner(state)
        recompute()
    }

    /**
     * Saves the state when it changed. Until the stored state was read ([loaded]) nothing is written
     * unless [force]d, so a slow disk at start never wipes it.
     */
    private fun persist(force: Boolean = false) {
        if (!loaded && !force) return
        val encoded = AlertPresentation.encode(state)
        if (encoded == persisted) return
        persisted = encoded
        heartbeatStore.recordAlertPresentationAsync(encoded)
    }

    private fun TireSensor.toWheelAlert(): AlertPresentation.WheelAlert? {
        val type = alertType ?: return null
        // BATTERY_LOW stays on screen only (wayfinder alerts 01: no window, no signal).
        if (type == AlertType.BATTERY_LOW) return null
        return AlertPresentation.WheelAlert(id, type, alertLevel ?: AlertLevel.ATTENTION, pressureKpa)
    }

    /** Row of an alert that cleared while its window was open: the wheel, "в норме". */
    private fun resolvedRow(key: String): AlertOverlayController.Row {
        val placed = repository.wheels.value.entries.firstOrNull { it.value.id == key }
        val unit = settingsStore.pressureUnit.value
        val title = when {
            key == AlertPresentation.MONITORING_KEY -> context.getString(R.string.alert_monitoring_title)
            placed != null -> wheelPositionLabel(context, placed.key)
            else -> key
        }
        return AlertOverlayController.Row(
            key = key,
            slot = placed?.key,
            title = title,
            subtitle = context.getString(R.string.alert_window_resolved),
            value = placed?.value?.takeIf { it.pressureKpa.isFinite() }?.let {
                "${unit.formatFromKpa(it.pressureKpa)} ${unit.localizedLabel(context)}"
            },
            detail = null,
            advice = null,
            fault = false,
            resolved = true
        )
    }

    /** Localized texts of [entry], from the wheel's current reading when it is still placed. */
    private fun rowOf(entry: Entry): AlertOverlayController.Row {
        if (entry.key == AlertPresentation.MONITORING_KEY) {
            return AlertOverlayController.Row(
                key = entry.key,
                slot = null,
                title = context.getString(R.string.alert_monitoring_title),
                subtitle = monitoringReason ?: context.getString(R.string.alert_monitoring_text),
                value = null,
                detail = null,
                advice = null,
                fault = true
            )
        }
        val placed = repository.wheels.value.entries.firstOrNull { it.value.id == entry.key }
        val sensor = placed?.value
        val type = entry.type ?: AlertType.SENSOR_LOST
        val unit = settingsStore.pressureUnit.value
        val unitLabel = unit.localizedLabel(context)
        val value = sensor?.takeIf { type != AlertType.SENSOR_LOST && it.pressureKpa.isFinite() }?.let {
            "${unit.formatFromKpa(it.pressureKpa)} $unitLabel · ${it.temperatureCelsius.roundToInt()} °C"
        }
        val detail = sensor?.recentDropKpa?.takeIf { type == AlertType.PRESSURE_DROP && it > 0f }?.let {
            context.getString(R.string.alert_window_drop_format, "${unit.formatFromKpa(it)} $unitLabel")
        }
        return AlertOverlayController.Row(
            key = entry.key,
            slot = placed?.key,
            title = placed?.key?.let { wheelPositionLabel(context, it) } ?: sensor?.label.orEmpty().ifBlank { entry.key },
            subtitle = type.cardLabel(context),
            value = value,
            detail = detail,
            advice = context.getString(type.adviceRes()),
            fault = entry.level == AlertLevel.FAULT
        )
    }

    companion object {
        private const val MIN_TICK_MS = 250L
    }
}
