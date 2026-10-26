package com.tpms.app.domain

import com.tpms.app.domain.AlertNotificationPolicy.NotificationAction
import com.tpms.app.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mutedUntilMs: Long = notMuted,
        resync: Boolean = false
    ) = book.onChange(key, current, mutedUntilMs, nowMs, resync)

    @Test
    fun `new alert posts with sound and is remembered`() {
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE))
        assertEquals(setOf("A"), book.keys())
    }

    @Test
    fun `resync posts a new alert silently`() {
        assertEquals(NotificationAction.POST_SILENTLY, change(AlertType.LOW_PRESSURE, resync = true))
    }

    @Test
    fun `resync of a shown alert only updates`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(
            NotificationAction.UPDATE_SILENTLY,
            change(AlertType.LOW_PRESSURE, nowMs = now + 5_000, resync = true)
        )
    }

    @Test
    fun `updates are throttled per wheel`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.NONE, change(AlertType.LOW_PRESSURE, nowMs = now + 500))
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now + 1_000))
        // Another wheel is not throttled by the first.
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, nowMs = now + 1_100, key = "B"))
    }

    @Test
    fun `a type change is never throttled`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.SENSOR_LOST, nowMs = now + 100))
    }

    @Test
    fun `a clock that went back does not block updates`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE, nowMs = now - 60_000))
    }

    @Test
    fun `clearing cancels and forgets`() {
        change(AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.CANCEL, change(null, nowMs = now + 100))
        assertTrue(book.keys().isEmpty())
        // The next onset rings again.
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.LOW_PRESSURE, nowMs = now + 200))
    }

    @Test
    fun `swiped stays hidden until the type changes`() {
        change(AlertType.LOW_PRESSURE)
        assertTrue(book.onSwiped("A"))
        assertEquals(NotificationAction.NONE, change(AlertType.LOW_PRESSURE, nowMs = now + 5_000))
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.SENSOR_LOST, nowMs = now + 6_000))
    }

    @Test
    fun `swipe of an unknown wheel is ignored`() {
        assertFalse(book.onSwiped("A"))
        assertTrue(book.keys().isEmpty())
    }

    @Test
    fun `restored notification makes the same alert update silently`() {
        assertTrue(book.restore("A", AlertType.LOW_PRESSURE))
        assertEquals(NotificationAction.UPDATE_SILENTLY, change(AlertType.LOW_PRESSURE))
    }

    @Test
    fun `restored notification still rings on a type change`() {
        book.restore("A", AlertType.LOW_PRESSURE)
        assertEquals(NotificationAction.POST_WITH_SOUND, change(AlertType.HIGH_PRESSURE))
    }

    @Test
    fun `restore ignores warnings and known wheels`() {
        assertFalse(book.restore("A", AlertType.HIGH_TEMP))
        change(AlertType.LOW_PRESSURE)
        book.onSwiped("A")
        assertFalse(book.restore("A", AlertType.SENSOR_LOST))
        // The swipe is kept.
        assertEquals(NotificationAction.NONE, change(AlertType.LOW_PRESSURE, nowMs = now + 5_000))
    }

    @Test
    fun `muted onset posts silently`() {
        assertEquals(
            NotificationAction.POST_SILENTLY,
            change(AlertType.LOW_PRESSURE, mutedUntilMs = now + 60_000)
        )
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
    }
}
