package com.tpms.app.ui.embedded

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedMainScreenFrameTest {

    @Test
    fun fitWithinAspectRatio_fillsWidthWhenSlotIsTaller() {
        val (width, height) = fitWithinAspectRatio(500.dp, 400.dp, 16f / 9f)
        assertEquals(500.dp, width)
        assertEquals((500f / (16f / 9f)).dp, height)
    }

    @Test
    fun fitWithinAspectRatio_fillsHeightWhenSlotIsWider() {
        val (width, height) = fitWithinAspectRatio(600.dp, 300.dp, 16f / 9f)
        assertEquals((300f * (16f / 9f)).dp, width)
        assertEquals(300.dp, height)
    }

    @Test
    fun fitWithinAspectRatio_exactMatchUsesFullArea() {
        val (width, height) = fitWithinAspectRatio(960.dp, 540.dp, 16f / 9f)
        assertEquals(960.dp, width)
        assertEquals(540.dp, height)
    }
}
