package com.tpms.app.domain

/** A USB problem the user has to see, because reopening alone is not fixing it. */
enum class UsbFault {
    /** Opens fine but sends nothing, session after session: replug the dongle. */
    DONGLE_SILENT,

    /** Reader threads stuck in the kernel and no process restart left: reboot the head unit. */
    USB_STUCK
}

/** How a dongle session ended. */
data class SessionOutcome(
    /** At least one byte arrived while the session was open. */
    val receivedBytes: Boolean,
    /** The reader thread exited on close; false means it is stuck in the kernel. */
    val readerStopped: Boolean
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

/**
 * Decides what the service does after a session dies or a dongle fails to open.
 * - A stuck reader is left behind and the dongle reopened; a second one in the same process, or a
 *   failed open while one is stuck, restarts the process (at most once per 30 min, else [UsbFault.USB_STUCK]).
 * - Three sessions in a row without a single byte report [UsbFault.DONGLE_SILENT] and slow retries
 *   to 30 s, until a byte arrives or the head unit wakes.
 */
object SessionRecoveryPolicy {
    const val REOPEN_DELAY_MS = 1_000L
    const val SILENT_REOPEN_DELAY_MS = 30_000L
    const val SILENT_SESSIONS_FOR_FAULT = 3
    const val STUCK_READERS_FOR_RESTART = 2
    const val RESTART_MIN_INTERVAL_MS = 30 * 60_000L

    fun afterSession(
        outcome: SessionOutcome,
        state: RecoveryState,
        lastRestartAtMs: Long?,
        now: Long
    ): RecoveryDecision {
        if (!outcome.readerStopped) {
            val stuck = state.copy(stuckReaders = state.stuckReaders + 1)
            if (stuck.stuckReaders < STUCK_READERS_FOR_RESTART) {
                return RecoveryDecision(RecoveryAction.Reopen(REOPEN_DELAY_MS), stuck)
            }
            return restartOrReportStuck(stuck, lastRestartAtMs, now, RecoveryAction.Reopen(REOPEN_DELAY_MS))
        }
        if (outcome.receivedBytes) {
            return RecoveryDecision(RecoveryAction.Reopen(REOPEN_DELAY_MS), onBytes(state))
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

    /** The head unit woke (screen on) or the dongle was attached: retry at full speed. */
    fun onWake(state: RecoveryState): RecoveryState = clearSilence(state)

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
