package com.tpms.app.domain

import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.AlertNotificationPolicy.ShownAlert
import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotificationBookTest {

    private val now = 1_000_000L
    private val notMuted = 0L
    private val book = AlertNotificationBook(updateIntervalMs = 1_000L)

    private fun change(
        current: AlertType?,
        nowMs: Long = now,
        key: String = "A",
        mutedUntilMs: Long = notMuted
    ) = book.onChange(key, current, mutedUntilMs, nowMs)

    @Test
    fun `new alert posts with sound and is remembered`() {
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE))
        assertEquals(setOf("A"), book.keys())
    }

    // --- Two phases ---

    @Test
    fun `a plan without commit leaves the memory alone`() {
        val step = book.plan("A", AlertType.LOW_PRESSURE, notMuted, now)
        assertEquals(NotificationAction.POST_WITH_SOUND, step.action)
        assertTrue(book.keys().isEmpty())
        // The post failed and was not committed: the next reading rings again.
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, nowMs = now + 2_000))
    }

    @Test
    fun `a failed type change post rings again on the next reading`() {
        change(AlertType.LOW_PRESSURE)
        book.plan("A", AlertType.SENSOR_LOST, notMuted, now + 2_000) // not committed
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.SENSOR_LOST, nowMs = now + 3_000))
    }

    @Test
    fun `commit reports whether the persisted memory changed`() {
        assertTrue(book.commit(book.plan("A", AlertType.LOW_PRESSURE, notMuted, now)))
        // Same type, silent update: nothing to persist.
        assertFalse(book.commit(book.plan("A", AlertType.LOW_PRESSURE, notMuted, now + 5_000)))
        assertTrue(book.commit(book.plan("A", null, notMuted, now + 6_000)))
        assertFalse(book.commit(book.plan("A", null, notMuted, now + 7_000)))
    }

    // --- Throttle ---

    @Test
    fun `updates are throttled per wheel`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.NONE, change(AlertType.LOW_PRESSURE, nowMs = now + 500))
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now + 1_000))
        // Another wheel is not throttled by the first.
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, nowMs = now + 1_100, key = "B"))
    }

    @Test
    fun `a throttled update asks to retry when the window ends`() {
        change(AlertType.LOW_PRESSURE)
        val step = book.plan("A", AlertType.LOW_PRESSURE, notMuted, now + 300)
        assertEquals(NotificationAction.NONE, step.action)
        assertEquals(700L, step.retryInMs)
        book.commit(step)
        // The retry goes through.
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now + 1_000))
    }

    @Test
    fun `steps that are not throttled carry no retry`() {
        assertNull(book.plan("A", AlertType.LOW_PRESSURE, notMuted, now).retryInMs)
        change(AlertType.LOW_PRESSURE)
        assertNull(book.plan("A", AlertType.LOW_PRESSURE, notMuted, now + 2_000).retryInMs)
        assertNull(book.plan("A", null, notMuted, now + 100).retryInMs)
    }

    @Test
    fun `a type change is never throttled`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.SENSOR_LOST, nowMs = now + 100))
        // Back to a type seen in the episode: silent, but not throttled either (the title changes).
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now + 200))
    }

    @Test
    fun `a clock that went back does not block updates`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now - 60_000))
    }

    // --- Episode ---

    @Test
    fun `flapping between two types rings at most twice`() {
        var rings = 0
        var t = now
        repeat(5) {
            for (type in listOf(AlertType.SENSOR_LOST, AlertType.LOW_PRESSURE)) {
                if (change(type, nowMs = t) == NotificationAction.POST_WITH_SOUND) rings++
                t += 5_000
            }
        }
        assertEquals(2, rings)
    }

    @Test
    fun `clearing cancels and forgets the episode`() {
        change(AlertType.LOW_PRESSURE)
        change(AlertType.SENSOR_LOST, nowMs = now + 100)
        assertEquals(NotificationAction.CANCEL, change(null, nowMs = now + 200))
        assertTrue(book.keys().isEmpty())
        // The next onset rings again.
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, nowMs = now + 300))
    }

    // --- Swipe ---

    @Test
    fun `swiped stays hidden until a new type appears`() {
        change(AlertType.LOW_PRESSURE)
        change(AlertType.SENSOR_LOST, nowMs = now + 1_000)
        assertTrue(book.onSwiped("A"))
        assertEquals(NotificationAction.NONE, change(AlertType.SENSOR_LOST, nowMs = now + 5_000))
        assertEquals(NotificationAction.NONE, change(AlertType.LOW_PRESSURE, nowMs = now + 6_000))
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.HIGH_PRESSURE, nowMs = now + 7_000))
    }

    @Test
    fun `swipe of an unknown or already swiped wheel is ignored`() {
        assertFalse(book.onSwiped("A"))
        assertTrue(book.keys().isEmpty())
        change(AlertType.LOW_PRESSURE)
        assertTrue(book.onSwiped("A"))
        assertFalse(book.onSwiped("A"))
    }

    @Test
    fun `mute silences new types of the wheels shown, not a new wheel`() {
        change(AlertType.LOW_PRESSURE)
        assertTrue(book.onMuted())
        val until = now + 60_000
        assertEquals(NotificationAction.POST_SILENTLY, change(AlertType.SENSOR_LOST, nowMs = now + 1_000, mutedUntilMs = until))
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, key = "B", mutedUntilMs = until))
    }

    @Test
    fun `mute with nothing shown changes nothing`() {
        assertFalse(book.onMuted())
    }

    @Test
    fun `escalation passes the pressure through`() {
        assertEquals(NotificationAction.POST_WITH_SOUND, book.onChange("A", AlertType.LOW_PRESSURE, notMuted, now, 180f))
        assertEquals(NotificationAction.POST_WITH_SOUND, book.onChange("A", AlertType.LOW_PRESSURE, notMuted, now + 100, 150f))
    }

    // --- Persistence ---

    @Test
    fun `encode and load round-trip the memory`() {
        change(AlertType.LOW_PRESSURE)
        change(AlertType.SENSOR_LOST, nowMs = now + 100)
        change(AlertType.HIGH_PRESSURE, key = "B")
        book.onSwiped("B")
        val encoded = book.encode()

        val restored = AlertNotificationBook(updateIntervalMs = 1_000L)
        restored.load(encoded)
        assertEquals(
            ShownAlert(AlertType.SENSOR_LOST, setOf(AlertType.LOW_PRESSURE, AlertType.SENSOR_LOST)),
            restored.shown("A")
        )
        assertEquals(ShownAlert(AlertType.HIGH_PRESSURE, swiped = true), restored.shown("B"))
        // The same ongoing alert does not ring again after a restart; a swiped one stays hidden.
        assertEquals(NotificationAction.UPDATE_SILENTLY, restored.onChange("A", AlertType.LOW_PRESSURE, notMuted, now + 10_000))
        assertEquals(NotificationAction.NONE, restored.onChange("B", AlertType.HIGH_PRESSURE, notMuted, now + 10_000))
    }

    @Test
    fun `encode and load keep mute and escalation base`() {
        book.onChange("A", AlertType.LOW_PRESSURE, notMuted, now, 180f)
        book.onMuted()
        val restored = AlertNotificationBook(updateIntervalMs = 1_000L)
        restored.load(book.encode())
        assertEquals(ShownAlert(AlertType.LOW_PRESSURE, muted = true, rungPressureKpa = 180f), restored.shown("A"))
    }

    @Test
    fun `load replaces the memory and skips what it cannot read`() {
        change(AlertType.LOW_PRESSURE, key = "old")
        book.load(
            """{"A":{"type":"LOW_PRESSURE","episode":["LOW_PRESSURE","BOGUS","BATTERY_LOW"]},""" +
                """"B":{"type":"BATTERY_LOW"},"C":{"type":"NOPE"},"D":42}"""
        )
        assertEquals(setOf("A"), book.keys())
        assertEquals(ShownAlert(AlertType.LOW_PRESSURE), book.shown("A"))

        book.load("not json")
        assertTrue(book.keys().isEmpty())
        book.load(null)
        assertTrue(book.keys().isEmpty())
    }

    @Test
    fun `forget and clear drop memory`() {
        change(AlertType.LOW_PRESSURE)
        change(AlertType.SENSOR_LOST, key = "B")
        assertTrue(book.forget("A"))
        assertFalse(book.forget("A"))
        assertEquals(setOf("B"), book.keys())
        book.clear()
        assertTrue(book.keys().isEmpty())
        assertEquals("{}", book.encode())
    }
}
