package com.tpms.app.ui.embedded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWindowDetectorTest {

    @Test
    fun isConstrainedWindow_whenSmallerThanDisplay() {
        assertTrue(EmbeddedWindowDetector.isConstrainedWindow(400, 200, 800, 480))
    }

    @Test
    fun isConstrainedWindow_falseWhenFullDisplay() {
        assertFalse(EmbeddedWindowDetector.isConstrainedWindow(800, 480, 800, 480))
    }
}
