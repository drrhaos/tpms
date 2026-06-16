package com.tpms.app.data.usb

import com.tpms.app.data.diagnostics.SystemDiagnostics
import com.tpms.app.data.settings.SettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsbDebugLogTest {

    private lateinit var settingsStore: SettingsStore
    private lateinit var log: UsbDebugLog
    private val logEnabled = MutableStateFlow(false)
    private val debugEnabled = MutableStateFlow(false)

    @Before
    fun setUp() {
        settingsStore = mockk(relaxed = true)
        every { settingsStore.diagnosticLogEnabled } returns logEnabled
        every { settingsStore.debugToolsEnabled } returns debugEnabled
        log = UsbDebugLog(mockk<SystemDiagnostics>(relaxed = true), settingsStore)
    }

    @Test
    fun recordsNothing_whenBothDisabled() {
        log.info("T", "hello")
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun recordsInfo_whenLogEnabled() {
        logEnabled.value = true
        log.info("T", "hello")
        assertEquals(1, log.entries.value.size)
    }

    @Test
    fun recordsRaw_onlyWhenLogAndDebugEnabled() {
        debugEnabled.value = true
        log.raw("T", "frame")
        assertTrue(log.entries.value.isEmpty())

        logEnabled.value = true
        log.raw("T", "frame")
        assertEquals(1, log.entries.value.size)
    }

    @Test
    fun recordsUsb_whenEitherLogOrDebugEnabled() {
        debugEnabled.value = true
        log.usb("T", "attached")
        assertEquals(1, log.entries.value.size)
    }
}
