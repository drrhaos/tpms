package com.tpms.app.service

import com.tpms.app.data.persistence.ServiceHeartbeatStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLivenessEvaluatorTest {

    @Test
    fun shouldRestart_whenHeartbeatStale() {
        val store = mockk<ServiceHeartbeatStore>()
        every { store.lastBeatMsBlocking() } returns 1_000L
        assertTrue(
            ServiceLivenessEvaluator.shouldRestart(store, now = 100_000L)
        )
    }

    @Test
    fun shouldRestart_falseWhenFresh() {
        val store = mockk<ServiceHeartbeatStore>()
        every { store.lastBeatMsBlocking() } returns 95_000L
        assertFalse(
            ServiceLivenessEvaluator.shouldRestart(store, now = 100_000L)
        )
    }

    @Test
    fun shouldRestart_falseWhenNeverRecorded() {
        val store = mockk<ServiceHeartbeatStore>()
        every { store.lastBeatMsBlocking() } returns 0L
        assertFalse(ServiceLivenessEvaluator.shouldRestart(store, now = 100_000L))
    }
}
