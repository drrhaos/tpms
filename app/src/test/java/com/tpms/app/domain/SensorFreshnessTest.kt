package com.tpms.app.domain

import com.tpms.app.domain.SensorFreshness.ConnectionSnapshot
import com.tpms.app.domain.SensorFreshness.FRAMES_STALE_AFTER_MS
import com.tpms.app.domain.SensorFreshness.HID_STALE_AFTER_MS
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.DongleProtocol
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFreshnessTest {

    private val now = 100_000_000L

    private fun sensor(timestamp: Long) = TireSensor(
        id = "FL",
        pressureKpa = 230f,
        temperatureCelsius = 20f,
        batteryPercent = null,
        alertType = null,
        timestamp = timestamp
    )

    // --- per-wheel staleness ---

    @Test
    fun hidWheel_staleAfterFifteenMinutes() {
        assertTrue(SensorFreshness.isWheelStale(sensor(now - HID_STALE_AFTER_MS - 1), DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun hidWheel_notStaleAtExactlyFifteenMinutes() {
        assertFalse(SensorFreshness.isWheelStale(sensor(now - HID_STALE_AFTER_MS), DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun hidWheel_notStaleWhenRecent() {
        assertFalse(SensorFreshness.isWheelStale(sensor(now - 60_000L), DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun streamingWheels_neverStale() {
        val ancient = sensor(now - 10 * HID_STALE_AFTER_MS)
        assertFalse(SensorFreshness.isWheelStale(ancient, DongleProtocol.SERIAL_AA55, now))
        assertFalse(SensorFreshness.isWheelStale(ancient, DongleProtocol.DEELIFE, now))
    }

    @Test
    fun unknownProtocolWheel_neverStale() {
        assertFalse(SensorFreshness.isWheelStale(sensor(now - 10 * HID_STALE_AFTER_MS), null, now))
    }

    @Test
    fun ageMinutes_floorsToWholeMinutes() {
        assertEquals(0L, SensorFreshness.ageMinutes(sensor(now - 59_999L), now))
        assertEquals(1L, SensorFreshness.ageMinutes(sensor(now - 60_000L), now))
        assertEquals(15L, SensorFreshness.ageMinutes(sensor(now - HID_STALE_AFTER_MS - 1), now))
    }

    @Test
    fun ageMinutes_futureTimestampIsZero() {
        assertEquals(0L, SensorFreshness.ageMinutes(sensor(now + 120_000L), now))
    }

    // --- connection-based staleness ---

    private fun live(
        protocol: DongleProtocol?,
        openedAt: Long = now - 10 * FRAMES_STALE_AFTER_MS,
        lastFrame: Long = 0L
    ) = ConnectionSnapshot(
        hasLiveSession = true,
        protocol = protocol,
        sessionOpenedAtMs = openedAt,
        lastValidFrameAtMs = lastFrame
    )

    @Test
    fun noLiveSession_isStale() {
        val noSession = ConnectionSnapshot(
            hasLiveSession = false,
            protocol = null,
            sessionOpenedAtMs = 0L,
            lastValidFrameAtMs = 0L
        )
        assertTrue(SensorFreshness.isDataStale(noSession, now))
    }

    @Test
    fun noLiveSession_isStaleEvenWithRecentFrame() {
        val reconnecting = live(DongleProtocol.SERIAL_AA55, lastFrame = now - 1_000L).copy(hasLiveSession = false)
        assertTrue(SensorFreshness.isDataStale(reconnecting, now))
    }

    @Test
    fun hidLiveSession_neverStaleByFrames() {
        assertFalse(SensorFreshness.isDataStale(live(DongleProtocol.HID_GENERIC, lastFrame = 0L), now))
        assertFalse(SensorFreshness.isDataStale(live(DongleProtocol.HID_GENERIC, lastFrame = now - 3_600_000L), now))
    }

    @Test
    fun unknownProtocolLiveSession_notStale() {
        assertFalse(SensorFreshness.isDataStale(live(null), now))
    }

    @Test
    fun streamingSession_recentFrame_notStale() {
        assertFalse(SensorFreshness.isDataStale(live(DongleProtocol.SERIAL_AA55, lastFrame = now - 5_000L), now))
    }

    @Test
    fun streamingSession_frameExactlySixtySecondsAgo_notStale() {
        assertFalse(SensorFreshness.isDataStale(live(DongleProtocol.DEELIFE, lastFrame = now - FRAMES_STALE_AFTER_MS), now))
    }

    @Test
    fun streamingSession_frameOlderThanSixtySeconds_isStale() {
        assertTrue(SensorFreshness.isDataStale(live(DongleProtocol.DEELIFE, lastFrame = now - FRAMES_STALE_AFTER_MS - 1), now))
        assertTrue(SensorFreshness.isDataStale(live(DongleProtocol.SERIAL_AA55, lastFrame = now - FRAMES_STALE_AFTER_MS - 1), now))
    }

    @Test
    fun streamingSession_noFrameYet_graceFromOpen() {
        val openedRecently = live(DongleProtocol.SERIAL_AA55, openedAt = now - FRAMES_STALE_AFTER_MS, lastFrame = 0L)
        assertFalse(SensorFreshness.isDataStale(openedRecently, now))
    }

    @Test
    fun streamingSession_noFrameSinceOpen_isStale() {
        val openedLongAgo = live(DongleProtocol.SERIAL_AA55, openedAt = now - FRAMES_STALE_AFTER_MS - 1, lastFrame = 0L)
        assertTrue(SensorFreshness.isDataStale(openedLongAgo, now))
    }

    @Test
    fun streamingSession_frameFromPreviousSession_countsFromOpen() {
        val reopened = live(DongleProtocol.SERIAL_AA55, openedAt = now - 10_000L, lastFrame = now - 10 * FRAMES_STALE_AFTER_MS)
        assertFalse(SensorFreshness.isDataStale(reopened, now))
    }

    // --- display helpers ---

    @Test
    fun staleWheelMinutes_givenForStaleHidWheel() {
        val stale = sensor(now - 20 * 60_000L)
        assertEquals(20L, SensorFreshness.staleWheelMinutes(stale, DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun staleWheelMinutes_nullWhenFreshMissingOrStreaming() {
        assertNull(SensorFreshness.staleWheelMinutes(sensor(now - 60_000L), DongleProtocol.HID_GENERIC, now))
        assertNull(SensorFreshness.staleWheelMinutes(null, DongleProtocol.HID_GENERIC, now))
        assertNull(SensorFreshness.staleWheelMinutes(sensor(now - 20 * 60_000L), DongleProtocol.SERIAL_AA55, now))
    }

    @Test
    fun isWheelMuted_staleWithoutAlert() {
        assertTrue(SensorFreshness.isWheelMuted(sensor(now - 20 * 60_000L), DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun isWheelMuted_activeAlertWinsOverMuting() {
        val alerting = sensor(now - 20 * 60_000L).copy(alertType = AlertType.LOW_PRESSURE)
        assertFalse(SensorFreshness.isWheelMuted(alerting, DongleProtocol.HID_GENERIC, now))
        assertEquals(20L, SensorFreshness.staleWheelMinutes(alerting, DongleProtocol.HID_GENERIC, now))
    }

    @Test
    fun isWheelMuted_falseWhenFreshOrMissing() {
        assertFalse(SensorFreshness.isWheelMuted(sensor(now - 60_000L), DongleProtocol.HID_GENERIC, now))
        assertFalse(SensorFreshness.isWheelMuted(null, DongleProtocol.HID_GENERIC, now))
    }
}
