package com.tpms.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRecoveryPolicyTest {

    private val now = 10_000_000L
    private val healthy = SessionOutcome(receivedBytes = true, readerStopped = true)
    private val silent = SessionOutcome(receivedBytes = false, readerStopped = true)
    private val stuck = SessionOutcome(receivedBytes = true, readerStopped = false)

    private fun afterSessions(vararg outcomes: SessionOutcome, lastRestartAtMs: Long? = null): RecoveryDecision {
        var state = RecoveryState()
        var decision: RecoveryDecision? = null
        for (outcome in outcomes) {
            decision = SessionRecoveryPolicy.afterSession(outcome, state, lastRestartAtMs, now)
            state = decision.state
        }
        return decision!!
    }

    // --- Healthy and silent sessions ---

    @Test
    fun healthySession_reopensAfterNormalDelay() {
        val decision = afterSessions(healthy)
        assertEquals(RecoveryAction.Reopen(SessionRecoveryPolicy.REOPEN_DELAY_MS), decision.action)
        assertEquals(RecoveryState(), decision.state)
    }

    @Test
    fun twoSilentSessions_stillReopenNormally() {
        val decision = afterSessions(silent, silent)
        assertEquals(RecoveryAction.Reopen(SessionRecoveryPolicy.REOPEN_DELAY_MS), decision.action)
        assertEquals(2, decision.state.silentSessions)
        assertNull(decision.state.fault)
    }

    @Test
    fun thirdSilentSession_reportsSilentDongleAndSlowsDown() {
        val decision = afterSessions(silent, silent, silent)
        assertEquals(RecoveryAction.Reopen(SessionRecoveryPolicy.SILENT_REOPEN_DELAY_MS), decision.action)
        assertEquals(UsbFault.DONGLE_SILENT, decision.state.fault)
    }

    @Test
    fun sessionWithBytes_resetsSilentStreak() {
        val decision = afterSessions(silent, silent, healthy, silent)
        assertEquals(1, decision.state.silentSessions)
        assertNull(decision.state.fault)
    }

    @Test
    fun bytes_clearSilentFaultMidSession() {
        val silentState = afterSessions(silent, silent, silent).state
        assertEquals(RecoveryState(), SessionRecoveryPolicy.onBytes(silentState))
    }

    @Test
    fun wake_clearsSilentStreakAndFault() {
        val silentState = afterSessions(silent, silent, silent).state
        assertEquals(RecoveryState(), SessionRecoveryPolicy.onWake(silentState))
    }

    @Test
    fun wake_keepsStuckReaderCount() {
        val state = RecoveryState(stuckReaders = 1, silentSessions = 3, fault = UsbFault.DONGLE_SILENT)
        assertEquals(RecoveryState(stuckReaders = 1), SessionRecoveryPolicy.onWake(state))
    }

    // --- Wake decision (screen on) ---

    private val streaming = WakeSession(streamsContinuously = true, silentForMs = 0L)
    private val hid = WakeSession(streamsContinuously = false, silentForMs = 60_000L)

    @Test
    fun wakeDecision_resetsStateLikeWake() {
        val state = RecoveryState(stuckReaders = 1, silentSessions = 3, fault = UsbFault.DONGLE_SILENT)
        val decision = SessionRecoveryPolicy.onWake(state, suspended = true, session = streaming)
        assertEquals(RecoveryState(stuckReaders = 1), decision.state)
    }

    @Test
    fun wakeDecision_keepsStuckFault() {
        val state = RecoveryState(stuckReaders = 2, silentSessions = 1, fault = UsbFault.USB_STUCK)
        val decision = SessionRecoveryPolicy.onWake(state, suspended = false, session = null)
        assertEquals(RecoveryState(stuckReaders = 2, fault = UsbFault.USB_STUCK), decision.state)
    }

    @Test
    fun wakeDecision_noSession_wakesSearch() {
        assertEquals(
            WakeAction.WakeSearch,
            SessionRecoveryPolicy.onWake(RecoveryState(), suspended = true, session = null).action
        )
        assertEquals(
            WakeAction.WakeSearch,
            SessionRecoveryPolicy.onWake(RecoveryState(), suspended = false, session = null).action
        )
    }

    @Test
    fun wakeDecision_afterSuspend_reopensStreamingSession() {
        val decision = SessionRecoveryPolicy.onWake(RecoveryState(), suspended = true, session = streaming)
        assertEquals(WakeAction.Reopen, decision.action)
    }

    @Test
    fun wakeDecision_afterSuspend_reopensHidSession() {
        val freshHid = hid.copy(silentForMs = 0L)
        val decision = SessionRecoveryPolicy.onWake(RecoveryState(), suspended = true, session = freshHid)
        assertEquals(WakeAction.Reopen, decision.action)
    }

    @Test
    fun wakeDecision_noSuspend_reopensQuietStreamingSession() {
        val quiet = streaming.copy(silentForMs = SessionRecoveryPolicy.PROBE_QUIET_MS + 1)
        val decision = SessionRecoveryPolicy.onWake(RecoveryState(), suspended = false, session = quiet)
        assertEquals(WakeAction.Reopen, decision.action)
    }

    @Test
    fun wakeDecision_noSuspend_keepsStreamingSessionWithRecentData() {
        val recent = streaming.copy(silentForMs = SessionRecoveryPolicy.PROBE_QUIET_MS)
        val decision = SessionRecoveryPolicy.onWake(RecoveryState(), suspended = false, session = recent)
        assertEquals(WakeAction.WakeSearch, decision.action)
    }

    @Test
    fun wakeDecision_noSuspend_keepsQuietHidSession() {
        // HID sends only on change, so silence alone says nothing about it.
        val decision = SessionRecoveryPolicy.onWake(RecoveryState(), suspended = false, session = hid)
        assertEquals(WakeAction.WakeSearch, decision.action)
    }

    // --- Stuck reader ---

    @Test
    fun firstStuckReader_reopensImmediately() {
        val decision = afterSessions(stuck)
        assertEquals(RecoveryAction.Reopen(SessionRecoveryPolicy.REOPEN_DELAY_MS), decision.action)
        assertEquals(1, decision.state.stuckReaders)
        assertNull(decision.state.fault)
    }

    @Test
    fun secondStuckReader_restartsProcess() {
        assertEquals(RecoveryAction.RestartProcess, afterSessions(stuck, healthy, stuck).action)
    }

    @Test
    fun secondStuckReader_withinRestartLimit_reportsStuckUsbInstead() {
        val decision = afterSessions(stuck, stuck, lastRestartAtMs = now - SessionRecoveryPolicy.RESTART_MIN_INTERVAL_MS + 1)
        assertEquals(RecoveryAction.Reopen(SessionRecoveryPolicy.REOPEN_DELAY_MS), decision.action)
        assertEquals(UsbFault.USB_STUCK, decision.state.fault)
    }

    @Test
    fun secondStuckReader_restartsOnceLimitHasPassed() {
        val decision = afterSessions(stuck, stuck, lastRestartAtMs = now - SessionRecoveryPolicy.RESTART_MIN_INTERVAL_MS)
        assertEquals(RecoveryAction.RestartProcess, decision.action)
    }

    @Test
    fun stuckFault_outranksSilentFault() {
        val decision = afterSessions(
            stuck, silent, silent, silent,
            lastRestartAtMs = now
        )
        assertEquals(UsbFault.DONGLE_SILENT, decision.state.fault)
        val stuckAgain = SessionRecoveryPolicy.afterSession(stuck, decision.state, now, now)
        assertEquals(UsbFault.USB_STUCK, stuckAgain.state.fault)
    }

    @Test
    fun bytes_keepStuckFault() {
        // The stuck reader is still leaked; only a process restart or a reboot clears it.
        val state = RecoveryState(stuckReaders = 2, silentSessions = 1, fault = UsbFault.USB_STUCK)
        assertEquals(RecoveryState(stuckReaders = 2, fault = UsbFault.USB_STUCK), SessionRecoveryPolicy.onBytes(state))
    }

    @Test
    fun healthySession_keepsStuckFault() {
        val stuckState = RecoveryState(stuckReaders = 2, fault = UsbFault.USB_STUCK)
        val decision = SessionRecoveryPolicy.afterSession(healthy, stuckState, lastRestartAtMs = now, now = now)
        assertEquals(UsbFault.USB_STUCK, decision.state.fault)
    }

    // --- Failed open ---

    @Test
    fun failedOpen_withoutStuckReader_keepsSearching() {
        val decision = SessionRecoveryPolicy.afterOpenFailed(RecoveryState(), lastRestartAtMs = null, now = now)
        assertNull(decision.action)
    }

    @Test
    fun failedOpen_whileReaderStuck_restartsProcess() {
        val state = afterSessions(stuck).state
        val decision = SessionRecoveryPolicy.afterOpenFailed(state, lastRestartAtMs = null, now = now)
        assertEquals(RecoveryAction.RestartProcess, decision.action)
    }

    @Test
    fun failedOpen_whileReaderStuck_withinLimit_reportsStuckUsb() {
        val state = afterSessions(stuck).state
        val decision = SessionRecoveryPolicy.afterOpenFailed(state, lastRestartAtMs = now - 1_000L, now = now)
        assertNull(decision.action)
        assertEquals(UsbFault.USB_STUCK, decision.state.fault)
    }
}
