package com.tpms.app.domain

import com.tpms.app.domain.AlertPresentation.Input
import com.tpms.app.domain.AlertPresentation.Sound
import com.tpms.app.domain.AlertPresentation.State
import com.tpms.app.domain.AlertPresentation.WheelAlert
import com.tpms.app.domain.model.AlertLevel
import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPresentationTest {

    private val t0 = 1_000_000L
    private val reminder = 5 * 60_000L

    private fun fault(key: String, type: AlertType = AlertType.LOW_PRESSURE, kpa: Float? = 140f) =
        WheelAlert(key, type, AlertLevel.FAULT, kpa)

    private fun attention(key: String, type: AlertType = AlertType.LOW_PRESSURE, kpa: Float? = 175f) =
        WheelAlert(key, type, AlertLevel.ATTENTION, kpa)

    private fun input(
        vararg wheels: WheelAlert,
        now: Long,
        live: Boolean = true,
        blind: Boolean = false,
        screenOn: Boolean = true
    ) = Input(wheels.toList(), live, blind, screenOn, now, reminder)

    /** Runs steps in order, threading the state; returns the plans. */
    private class Run(var state: State = State()) {
        fun step(input: Input): AlertPresentation.Plan {
            val (next, plan) = AlertPresentation.step(state, input)
            state = next
            return plan
        }
    }

    // --- Fault ---

    @Test
    fun `new fault shows the red window and rings`() {
        val plan = Run().step(input(fault("FL"), now = t0))
        assertNotNull(plan.window)
        assertTrue(plan.windowUnacknowledged)
        assertEquals(Sound.FAULT, plan.sound)
        assertEquals(listOf("FL"), plan.notifications.map { it.key })
        assertEquals(t0 + AlertPresentation.FAULT_REPEAT_MS, plan.nextCheckAtMs)
    }

    @Test
    fun `unacknowledged fault repeats every 30 s`() {
        val run = Run()
        run.step(input(fault("FL"), now = t0))
        assertNull(run.step(input(fault("FL"), now = t0 + 10_000)).sound)
        assertEquals(Sound.FAULT, run.step(input(fault("FL"), now = t0 + 30_000)).sound)
    }

    @Test
    fun `acknowledged fault is silent until the reminder`() {
        val run = Run()
        run.step(input(fault("FL"), now = t0))
        run.state = AlertPresentation.acknowledge(run.state, t0 + 5_000, reminder)
        val quiet = run.step(input(fault("FL"), now = t0 + 60_000))
        assertNull(quiet.window)
        assertNull(quiet.sound)
        assertEquals(t0 + 5_000 + reminder, quiet.nextCheckAtMs)
        val back = run.step(input(fault("FL"), now = t0 + 5_000 + reminder))
        assertNotNull(back.window)
        assertEquals(Sound.FAULT, back.sound)
    }

    @Test
    fun `worsening rings at once and drops the acknowledgement`() {
        val cases = listOf(
            // another faulty wheel
            listOf(fault("FL"), fault("RR")),
            // a new fault type on the wheel
            listOf(fault("FL", AlertType.SENSOR_LOST, null)),
            // a further fall of the escalation step
            listOf(fault("FL", kpa = 140f - AlertPresentation.ESCALATION_STEP_KPA))
        )
        for (after in cases) {
            val run = Run()
            run.step(input(fault("FL"), now = t0))
            run.state = AlertPresentation.acknowledge(run.state, t0 + 1_000, reminder)
            val plan = run.step(input(*after.toTypedArray(), now = t0 + 2_000))
            assertEquals("case $after", Sound.FAULT, plan.sound)
            assertNotNull(plan.window)
            assertFalse(run.state.acknowledged)
        }
    }

    @Test
    fun `a small further fall is not a worsening`() {
        val run = Run()
        run.step(input(fault("FL"), now = t0))
        run.state = AlertPresentation.acknowledge(run.state, t0 + 1_000, reminder)
        assertNull(run.step(input(fault("FL", kpa = 130f), now = t0 + 2_000)).sound)
    }

    @Test
    fun `attention turning into fault is a worsening`() {
        val run = Run()
        run.step(input(attention("FL"), now = t0))
        val plan = run.step(input(fault("FL"), now = t0 + 60_000))
        assertEquals(Sound.FAULT, plan.sound)
        assertNotNull(plan.window)
    }

    @Test
    fun `easing to attention is silent`() {
        val run = Run()
        run.step(input(fault("FL"), now = t0))
        val plan = run.step(input(attention("FL"), now = t0 + 60_000))
        assertNull(plan.window)
        assertNull(plan.sound)
        assertNull(plan.banner)
        assertEquals(AlertLevel.ATTENTION, plan.notifications.single().level)
    }

    @Test
    fun `cleared fault removes window and notification`() {
        val run = Run()
        run.step(input(fault("FL"), now = t0))
        val plan = run.step(input(now = t0 + 1_000))
        assertNull(plan.window)
        assertTrue(plan.notifications.isEmpty())
        assertNull(plan.nextCheckAtMs)
    }

    @Test
    fun `window rows put faults first`() {
        val plan = Run().step(input(attention("FR"), fault("RL", AlertType.PRESSURE_DROP), now = t0))
        assertEquals(listOf("RL", "FR"), plan.window!!.map { it.key })
    }

    // --- Monitoring ---

    @Test
    fun `monitoring blind is a fault of its own`() {
        val run = Run()
        val plan = run.step(input(now = t0, blind = true))
        assertEquals(Sound.FAULT, plan.sound)
        assertEquals(AlertPresentation.MONITORING_KEY, plan.window!!.single().key)
        assertTrue(run.step(input(now = t0 + 1_000)).notifications.isEmpty())
    }

    @Test
    fun `without a live session wheel alerts are kept and nothing new rings`() {
        val run = Run()
        run.step(input(attention("FL"), now = t0))
        val plan = run.step(input(fault("RR"), now = t0 + 1_000, live = false))
        assertNull(plan.sound)
        assertEquals(listOf("FL"), plan.notifications.map { it.key })
    }

    // --- Attention ---

    @Test
    fun `attention shows a banner once per type per episode`() {
        val run = Run()
        val first = run.step(input(attention("FL"), now = t0))
        assertEquals("FL", first.banner)
        assertEquals(Sound.ATTENTION, first.sound)
        assertEquals(t0 + AlertPresentation.BANNER_MS, first.nextCheckAtMs)
        val later = run.step(input(attention("FL"), now = t0 + AlertPresentation.BANNER_MS))
        assertNull(later.banner)
        assertNull(later.sound)
        val newType = run.step(input(attention("FL", AlertType.HIGH_TEMP), now = t0 + 20_000))
        assertEquals("FL", newType.banner)
        // A new episode after the wheel was normal rings again.
        run.step(input(now = t0 + 30_000))
        assertEquals("FL", run.step(input(attention("FL"), now = t0 + 40_000)).banner)
    }

    @Test
    fun `attention during the red window joins it without a banner`() {
        val run = Run()
        run.step(input(fault("RL"), now = t0))
        val plan = run.step(input(fault("RL"), attention("FR"), now = t0 + 1_000))
        assertNull(plan.banner)
        assertNull(plan.sound)
        assertEquals(listOf("RL", "FR"), plan.window!!.map { it.key })
        // Shown as a row: no banner for it after "Понятно" either.
        run.state = AlertPresentation.acknowledge(run.state, t0 + 2_000, reminder)
        assertNull(run.step(input(fault("RL"), attention("FR"), now = t0 + 3_000)).banner)
    }

    @Test
    fun `mute silences attention but never a fault`() {
        val run = Run()
        run.state = AlertPresentation.muteAttention(run.state, t0)
        val attn = run.step(input(attention("FL"), now = t0 + 1_000))
        assertNull(attn.banner)
        assertNull(attn.sound)
        assertEquals(Sound.FAULT, run.step(input(attention("FL"), fault("RR"), now = t0 + 2_000)).sound)
    }

    @Test
    fun `dismissed banner goes away`() {
        val run = Run()
        run.step(input(attention("FL"), now = t0))
        run.state = AlertPresentation.dismissBanner(run.state)
        assertNull(run.step(input(attention("FL"), now = t0 + 1_000)).banner)
    }

    // --- Screen off ---

    @Test
    fun `screen off pauses and the due signal comes with the screen`() {
        val run = Run()
        val off = run.step(input(fault("FL"), now = t0, screenOn = false))
        assertNull(off.window)
        assertNull(off.sound)
        val on = run.step(input(fault("FL"), now = t0 + 60_000))
        assertNotNull(on.window)
        assertEquals(Sound.FAULT, on.sound)
    }

    // --- Persistence ---

    @Test
    fun `encode and decode keep entries acknowledgement and mute`() {
        val run = Run()
        run.step(input(fault("FL", AlertType.PRESSURE_DROP, 150f), attention("RR"), now = t0))
        run.state = AlertPresentation.acknowledge(run.state, t0 + 1_000, reminder)
        run.state = AlertPresentation.muteAttention(run.state, t0 + 2_000)
        val restored = AlertPresentation.decode(AlertPresentation.encode(run.state))
        assertEquals(run.state.entries, restored.entries)
        assertTrue(restored.acknowledged)
        assertEquals(run.state.nextReminderAtMs, restored.nextReminderAtMs)
        assertEquals(run.state.attentionMutedUntilMs, restored.attentionMutedUntilMs)
        // The same alerts after a restart: no new ring, no window (acknowledged).
        val plan = AlertPresentation.step(restored, input(fault("FL", AlertType.PRESSURE_DROP, 150f), attention("RR"), now = t0 + 3_000)).second
        assertNull(plan.sound)
        assertNull(plan.window)
    }

    @Test
    fun `decode tolerates junk`() {
        assertEquals(State(), AlertPresentation.decode(null))
        assertEquals(State(), AlertPresentation.decode("not json"))
        val state = AlertPresentation.decode("""{"entries":[{"key":"A","level":"NOPE"},{"key":"B","type":"BOGUS","level":"FAULT"},{"key":"monitoring","level":"FAULT"}]}""")
        assertEquals(setOf(AlertPresentation.MONITORING_KEY), state.entries.keys)
    }

    @Test
    fun `a new trip from an empty state rings everything again`() {
        val run = Run()
        run.step(input(fault("FL"), attention("RR"), now = t0))
        run.state = AlertPresentation.acknowledge(run.state, t0 + 1_000, reminder)
        val trip = Run(State())
        val plan = trip.step(input(fault("FL"), attention("RR"), now = t0 + 3_600_000))
        assertEquals(Sound.FAULT, plan.sound)
        assertNotNull(plan.window)
    }
}
