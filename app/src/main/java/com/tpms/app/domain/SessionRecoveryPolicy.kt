package com.tpms.app.domain

/** A USB problem the user has to see, because reopening alone is not fixing it. */
enum class UsbFault {
    /** Opens fine but sends nothing, session after session: replug the dongle. */
    DONGLE_SILENT,

    /** Reader threads stuck in the kernel and no process restart left: reboot the head unit. */
    USB_STUCK,

    /** A dongle is present but the app has no USB permission for it (see [UsbPermissionPolicy]). */
    NO_PERMISSION
}

/** How a dongle session ended. */
data class SessionOutcome(
    /** At least one byte arrived while the session was open. */
    val receivedBytes: Boolean,
    /** The reader thread exited on close; false means it is stuck in the kernel. */
    val readerStopped: Boolean,
    /**
     * The service closed it on purpose at screen-on ([WakeAction.Reopen]); a session with no bytes
     * then says nothing about the dongle (a quiet HID dongle) and does not count as silent.
     */
    val closedByWake: Boolean = false
)

/** Recovery history for the lifetime of the service process. */
data class RecoveryState(
    val stuckReaders: Int = 0,
    val silentSessions: Int = 0,
    val fault: UsbFault? = null
)

sealed interface RecoveryAction {
    data class Reopen(val delayMs: Long) : RecoveryAction
    data object RestartProcess : RecoveryAction
}

/** [action] null means "keep searching as usual". */
data class RecoveryDecision(val action: RecoveryAction?, val state: RecoveryState)

/** What the service knows about the open session at screen-on; null in [SessionRecoveryPolicy.onScreenOn] means no session. */
data class WakeSession(
    /** The protocol streams frames continuously (AA55, Deelife); HID sends only on change. */
    val streamsContinuously: Boolean,
    /** Time since the session last received a byte. */
    val silentForMs: Long
)

sealed interface WakeAction {
    /** Keep the session (if any); wake the search loop at minimum backoff. */
    data object WakeSearch : WakeAction

    /** Close the open session and reopen the dongle now. */
    data object Reopen : WakeAction
}

data class WakeDecision(val action: WakeAction, val state: RecoveryState)

/**
 * Decides what the service does after a session dies or a dongle fails to open.
 * - A stuck reader is left behind and the dongle reopened; a second one in the same process, or a
 *   failed open while one is stuck, restarts the process (at most once per 30 min, else [UsbFault.USB_STUCK]).
 * - Three sessions in a row without a single byte report [UsbFault.DONGLE_SILENT] and slow retries
 *   to 30 s, until a byte arrives or the head unit wakes. A session closed by a screen-on reopen
 *   does not count and is reopened at once ([WAKE_REOPEN_DELAY_MS]).
 * - On screen-on the silent streak is cleared; a session that went through a real suspend is
 *   reopened whatever its protocol, otherwise only a streaming session quiet for > [PROBE_QUIET_MS].
 */
object SessionRecoveryPolicy {
    const val REOPEN_DELAY_MS = 1_000L
    /** A session closed by a screen-on reopen: the wake is the reason, reopen at once. */
    const val WAKE_REOPEN_DELAY_MS = 0L
    const val SILENT_REOPEN_DELAY_MS = 30_000L
    const val SILENT_SESSIONS_FOR_FAULT = 3
    const val STUCK_READERS_FOR_RESTART = 2
    const val RESTART_MIN_INTERVAL_MS = 30 * 60_000L
    /** A streaming session silent longer than this at screen-on is reopened even without suspend. */
    const val PROBE_QUIET_MS = 5_000L

    fun afterSession(
        outcome: SessionOutcome,
        state: RecoveryState,
        lastRestartAtMs: Long?,
        now: Long
    ): RecoveryDecision {
        val reopen = RecoveryAction.Reopen(if (outcome.closedByWake) WAKE_REOPEN_DELAY_MS else REOPEN_DELAY_MS)
        if (!outcome.readerStopped) {
            val stuck = state.copy(stuckReaders = state.stuckReaders + 1)
            if (stuck.stuckReaders < STUCK_READERS_FOR_RESTART) {
                return RecoveryDecision(reopen, stuck)
            }
            return restartOrReportStuck(stuck, lastRestartAtMs, now, reopen)
        }
        if (outcome.receivedBytes) {
            return RecoveryDecision(reopen, onBytes(state))
        }
        if (outcome.closedByWake) {
            return RecoveryDecision(reopen, state)
        }
        val silentSessions = state.silentSessions + 1
        val silent = silentSessions >= SILENT_SESSIONS_FOR_FAULT
        val next = state.copy(
            silentSessions = silentSessions,
            fault = if (silent && state.fault == null) UsbFault.DONGLE_SILENT else state.fault
        )
        val delayMs = if (silent) SILENT_REOPEN_DELAY_MS else REOPEN_DELAY_MS
        return RecoveryDecision(RecoveryAction.Reopen(delayMs), next)
    }

    fun afterOpenFailed(state: RecoveryState, lastRestartAtMs: Long?, now: Long): RecoveryDecision {
        if (state.stuckReaders == 0) return RecoveryDecision(null, state)
        return restartOrReportStuck(state, lastRestartAtMs, now, fallback = null)
    }

    /**
     * Data is flowing again: the silent streak is over. [UsbFault.USB_STUCK] stays — the stuck
     * reader is still leaked until the process restarts.
     */
    fun onBytes(state: RecoveryState): RecoveryState = clearSilence(state)

    /** The dongle was attached or USB permission granted: retry at full speed. */
    fun onWake(state: RecoveryState): RecoveryState = clearSilence(state)

    /**
     * The screen turned on (head unit woke). Recovery is reset as in [onWake]; the open [session]
     * (null = none) is reopened if the device really [suspended], or, without suspend, if it
     * streams continuously and has been quiet for more than [PROBE_QUIET_MS]. Otherwise only the
     * search is woken. A session closed here is reported with [SessionOutcome.closedByWake].
     */
    fun onScreenOn(state: RecoveryState, suspended: Boolean, session: WakeSession?): WakeDecision {
        val reopen = session != null &&
            (suspended || (session.streamsContinuously && session.silentForMs > PROBE_QUIET_MS))
        return WakeDecision(if (reopen) WakeAction.Reopen else WakeAction.WakeSearch, onWake(state))
    }

    private fun clearSilence(state: RecoveryState): RecoveryState = state.copy(
        silentSessions = 0,
        fault = state.fault.takeIf { it == UsbFault.USB_STUCK }
    )

    private fun restartOrReportStuck(
        state: RecoveryState,
        lastRestartAtMs: Long?,
        now: Long,
        fallback: RecoveryAction?
    ): RecoveryDecision {
        val restartAllowed = lastRestartAtMs == null || now - lastRestartAtMs >= RESTART_MIN_INTERVAL_MS
        return if (restartAllowed) {
            RecoveryDecision(RecoveryAction.RestartProcess, state)
        } else {
            RecoveryDecision(fallback, state.copy(fault = UsbFault.USB_STUCK))
        }
    }
}
