package com.tpms.app.domain

import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.AlertNotificationPolicy.ShownAlert
import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotificationPolicyTest {

    private val now = 1_000_000L
    private val notMuted = 0L

    private fun decide(
        shown: ShownAlert?,
        current: AlertType?,
        mutedUntilMs: Long = notMuted,
        nowMs: Long = now
    ) = AlertNotificationPolicy.decide(shown, current, mutedUntilMs, nowMs)

    // --- Nothing shown ---

    @Test
    fun `no alert and nothing shown does nothing`() {
        val decision = decide(shown = null, current = null)
        assertEquals(NotificationAction.NONE, decision.action)
        assertNull(decision.shown)
    }

    @Test
    fun `warnings never get a notification`() {
        for (warning in listOf(AlertType.HIGH_TEMP, AlertType.BATTERY_LOW)) {
            val decision = decide(shown = null, current = warning)
            assertEquals(NotificationAction.NONE, decision.action)
            assertNull(decision.shown)
        }
    }

    @Test
    fun `new alert posts with sound`() {
        for (alert in listOf(AlertType.LOW_PRESSURE, AlertType.HIGH_PRESSURE, AlertType.SENSOR_LOST)) {
            val decision = decide(shown = null, current = alert)
            assertEquals(NotificationAction.POST_WITH_SOUND, decision.action)
            assertEquals(ShownAlert(alert), decision.shown)
        }
    }

    // --- Shown alert ---

    @Test
    fun `same alert updates silently`() {
        val shown = ShownAlert(AlertType.LOW_PRESSURE)
        val decision = decide(shown = shown, current = AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.UPDATE_SILENTLY, decision.action)
        assertEquals(shown, decision.shown)
    }

    @Test
    fun `alert type change posts again with sound`() {
        val decision = decide(shown = ShownAlert(AlertType.LOW_PRESSURE), current = AlertType.SENSOR_LOST)
        assertEquals(NotificationAction.POST_WITH_SOUND, decision.action)
        assertEquals(
            ShownAlert(AlertType.SENSOR_LOST, episode = setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST)),
            decision.shown
        )
    }

    @Test
    fun `a new alert starts its episode with its own type`() {
        assertEquals(setOf(AlertType.LOW_PRESSURE), decide(shown = null, current = AlertType.LOW_PRESSURE).shown!!.episode)
    }

    @Test
    fun `returning to a type already seen in the episode updates silently`() {
        val shown = ShownAlert(AlertType.SENSOR_LOST, episode = setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST))
        val decision = decide(shown = shown, current = AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.UPDATE_SILENTLY, decision.action)
        assertEquals(shown.copy(type = AlertType.LOW_PRESSURE), decision.shown)
    }

    @Test
    fun `flapping between two types rings at most twice`() {
        var shown: ShownAlert? = null
        var rings = 0
        repeat(5) {
            for (type in listOf(AlertType.SENSOR_LOST, AlertType.LOW_PRESSURE)) {
                val decision = decide(shown = shown, current = type)
                if (decision.action == NotificationAction.POST_WITH_SOUND) rings++
                shown = decision.shown
            }
        }
        assertEquals(2, rings)
    }

    @Test
    fun `a third type in the episode rings again`() {
        val shown = ShownAlert(AlertType.SENSOR_LOST, episode = setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST))
        val decision = decide(shown = shown, current = AlertType.HIGH_PRESSURE)
        assertEquals(NotificationAction.POST_WITH_SOUND, decision.action)
        assertEquals(AlertType.values().filter { it.toSeverity() == AlertSeverity.ALERT }.toSet(), decision.shown!!.episode)
    }

    @Test
    fun `a cleared episode rings again for a type it had seen`() {
        val shown = ShownAlert(AlertType.SENSOR_LOST, episode = setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST))
        val cleared = decide(shown = shown, current = null)
        assertNull(cleared.shown)
        assertEquals(NotificationAction.POST_WITH_SOUND, decide(shown = cleared.shown, current = AlertType.LOW_PRESSURE).action)
    }

    @Test
    fun `cleared alert cancels and forgets`() {
        val decision = decide(shown = ShownAlert(AlertType.HIGH_PRESSURE), current = null)
        assertEquals(NotificationAction.CANCEL, decision.action)
        assertNull(decision.shown)
    }

    @Test
    fun `alert dropping to a warning cancels and forgets`() {
        val decision = decide(shown = ShownAlert(AlertType.LOW_PRESSURE), current = AlertType.HIGH_TEMP)
        assertEquals(NotificationAction.CANCEL, decision.action)
        assertNull(decision.shown)
    }

    // --- Swiping ---

    @Test
    fun `onSwiped marks the shown alert`() {
        val swiped = AlertNotificationPolicy.onSwiped(ShownAlert(AlertType.LOW_PRESSURE))
        assertTrue(swiped.swiped)
        assertEquals(AlertType.LOW_PRESSURE, swiped.type)
        assertFalse(ShownAlert(AlertType.LOW_PRESSURE).swiped)
    }

    @Test
    fun `swiped alert stays hidden while the type holds`() {
        val swiped = AlertNotificationPolicy.onSwiped(ShownAlert(AlertType.LOW_PRESSURE))
        val decision = decide(shown = swiped, current = AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.NONE, decision.action)
        assertEquals(swiped, decision.shown)
    }

    @Test
    fun `swiped alert posts again when a new type appears`() {
        val swiped = AlertNotificationPolicy.onSwiped(ShownAlert(AlertType.LOW_PRESSURE))
        val decision = decide(shown = swiped, current = AlertType.SENSOR_LOST)
        assertEquals(NotificationAction.POST_WITH_SOUND, decision.action)
        assertEquals(AlertType.SENSOR_LOST, decision.shown!!.type)
        assertFalse(decision.shown!!.swiped)
    }

    @Test
    fun `swiped alert stays hidden when it returns to a type seen in the episode`() {
        val swiped = AlertNotificationPolicy.onSwiped(
            ShownAlert(AlertType.SENSOR_LOST, episode = setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST))
        )
        val decision = decide(shown = swiped, current = AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.NONE, decision.action)
        assertEquals(swiped.copy(type = AlertType.LOW_PRESSURE), decision.shown)
        assertTrue(decision.shown!!.swiped)
    }

    @Test
    fun `clearing a swiped alert forgets the swipe`() {
        val swiped = AlertNotificationPolicy.onSwiped(ShownAlert(AlertType.LOW_PRESSURE))
        val cleared = decide(shown = swiped, current = null)
        assertEquals(NotificationAction.CANCEL, cleared.action)
        assertNull(cleared.shown)

        val again = decide(shown = cleared.shown, current = AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.POST_WITH_SOUND, again.action)
        assertEquals(ShownAlert(AlertType.LOW_PRESSURE), again.shown)
    }

    // --- Mute window ---

    @Test
    fun `new alert while muted posts silently`() {
        val decision = decide(shown = null, current = AlertType.LOW_PRESSURE, mutedUntilMs = now + 1)
        assertEquals(NotificationAction.POST_SILENTLY, decision.action)
        assertEquals(ShownAlert(AlertType.LOW_PRESSURE), decision.shown)
    }

    @Test
    fun `type change while muted posts silently`() {
        val decision = decide(
            shown = ShownAlert(AlertType.LOW_PRESSURE),
            current = AlertType.SENSOR_LOST,
            mutedUntilMs = now + 30 * 60_000L
        )
        assertEquals(NotificationAction.POST_SILENTLY, decision.action)
        assertEquals(AlertType.SENSOR_LOST, decision.shown!!.type)
        // Seen while muted: it does not ring once the mute ends.
        assertEquals(NotificationAction.UPDATE_SILENTLY, decide(decision.shown, AlertType.SENSOR_LOST).action)
    }

    @Test
    fun `mute ends exactly at mutedUntil`() {
        val atEnd = decide(shown = null, current = AlertType.LOW_PRESSURE, mutedUntilMs = now, nowMs = now)
        assertEquals(NotificationAction.POST_WITH_SOUND, atEnd.action)

        val justBefore = decide(shown = null, current = AlertType.LOW_PRESSURE, mutedUntilMs = now, nowMs = now - 1)
        assertEquals(NotificationAction.POST_SILENTLY, justBefore.action)
    }

    @Test
    fun `expired mute posts with sound`() {
        val decision = decide(shown = null, current = AlertType.LOW_PRESSURE, mutedUntilMs = now - 60_000L)
        assertEquals(NotificationAction.POST_WITH_SOUND, decision.action)
    }

    @Test
    fun `mute does not change update cancel or swiped outcomes`() {
        val muted = now + 60_000L
        assertEquals(
            NotificationAction.UPDATE_SILENTLY,
            decide(ShownAlert(AlertType.LOW_PRESSURE), AlertType.LOW_PRESSURE, muted).action
        )
        assertEquals(NotificationAction.CANCEL, decide(ShownAlert(AlertType.LOW_PRESSURE), null, muted).action)
        assertEquals(
            NotificationAction.NONE,
            decide(AlertNotificationPolicy.onSwiped(ShownAlert(AlertType.LOW_PRESSURE)), AlertType.LOW_PRESSURE, muted).action
        )
        assertEquals(NotificationAction.NONE, decide(null, AlertType.BATTERY_LOW, muted).action)
    }

    @Test
    fun `isMuted is true strictly before mutedUntil`() {
        assertTrue(AlertNotificationPolicy.isMuted(mutedUntilMs = now + 1, nowMs = now))
        assertFalse(AlertNotificationPolicy.isMuted(mutedUntilMs = now, nowMs = now))
        assertFalse(AlertNotificationPolicy.isMuted(mutedUntilMs = 0L, nowMs = now))
    }
}
