package com.tpms.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class CarTopDownLayoutTest {

    @Test
    fun clampOffset_returnsValueInsideRange() {
        assertEquals(12f, clampOffset(12f, 8f, 20f), 0f)
    }

    @Test
    fun clampOffset_clampsToMin() {
        assertEquals(8f, clampOffset(4f, 8f, 20f), 0f)
    }

    @Test
    fun clampOffset_clampsToMax() {
        assertEquals(20f, clampOffset(30f, 8f, 20f), 0f)
    }

    @Test
    fun clampOffset_doesNotCrashWhenMaxLessThanMin() {
        assertEquals(8f, clampOffset(12f, 8f, 4f), 0f)
    }
}
