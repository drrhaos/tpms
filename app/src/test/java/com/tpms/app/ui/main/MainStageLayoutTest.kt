package com.tpms.app.ui.main

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainStageLayoutTest {

    /** Main-screen stages (dp): head units 1024×600 (with / without alert banner), 683×400, 1280×720, phones. */
    private val stages = listOf(
        992f to 500f, 992f to 408f, 651f to 300f, 651f to 210f, 1248f to 620f, 379f to 750f, 379f to 480f
    )

    private fun inside(r: Rect, w: Float, h: Float) = r.left >= 0f && r.top >= 0f && r.right <= w + 0.01f && r.bottom <= h + 0.01f

    @Test
    fun readingsStayOnStageAndNeverOverlapEachOtherOrTheCar() {
        for ((w, h) in stages) for (spare in listOf(false, true)) {
            val layout = mainStageLayout(w, h, spare)
            val boxes = layout.readings.values + listOfNotNull(layout.spare)
            boxes.forEach { assertTrue("$w×$h $it", inside(it, w, h)) }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                assertFalse("$w×$h readings overlap", boxes[i].overlaps(boxes[j]))
            }
            boxes.forEach { assertFalse("$w×$h car covers $it", it.overlaps(layout.car)) }
            assertTrue("$w×$h car too small", layout.car.width > 40f)
            if (spare) assertNotNull(layout.spare)
        }
    }

    @Test
    fun readingsAreLargeOnAHeadUnit() {
        // 1024×600 head unit, main screen without an alert banner: far above the old 50 sp.
        assertTrue(mainStageLayout(992f, 500f, hasSpare = false).pressureSp >= 60f)
        // Smallest head unit stage with a banner: still above the old 30 sp floor.
        assertTrue(mainStageLayout(651f, 210f, hasSpare = false).pressureSp >= 30f)
    }

    @Test
    fun cornersFollowTheRender() {
        val layout = mainStageLayout(992f, 500f, hasSpare = false)
        val r = layout.readings
        assertTrue(r.getValue("RR").left < layout.car.left && r.getValue("RR").top < r.getValue("FR").top)
        assertTrue(r.getValue("RL").left > layout.car.right && r.getValue("RL").top < r.getValue("FL").top)
        assertEquals(r.getValue("RR").left, r.getValue("FR").left, 0f)
    }
}
