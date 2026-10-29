package com.tpms.app.data.usb

import com.tpms.app.domain.model.SensorFlags
import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ReadingConfirmationFilterTest {

    private lateinit var filter: ReadingConfirmationFilter

    @Before
    fun setUp() {
        filter = ReadingConfirmationFilter(windowMs = 5_000L)
    }

    @Test
    fun firstReading_isHeldUntilRepeated() {
        assertNull(filter.offer(reading(240f, at = 0L)))

        val confirmed = filter.offer(reading(240f, at = 500L))

        assertEquals(240f, confirmed!!.pressureKpa, 0.01f)
        assertEquals(500L, confirmed.timestamp)
    }

    @Test
    fun confirmedValue_passesThroughOnEveryRepeat() {
        filter.offer(reading(240f, at = 0L))
        filter.offer(reading(240f, at = 500L))

        assertEquals(60_000L, filter.offer(reading(240f, at = 60_000L))!!.timestamp)
    }

    @Test
    fun singleGarbageFrame_neverReachesCaller() {
        filter.offer(reading(240f, at = 0L))
        filter.offer(reading(240f, at = 500L))

        assertNull(filter.offer(reading(17f, at = 1_000L)))
        assertEquals(240f, filter.offer(reading(240f, at = 1_500L))!!.pressureKpa, 0.01f)
        assertNull(filter.offer(reading(240f, flags = SensorFlags(noSignal = true), at = 2_000L)))
    }

    @Test
    fun newValue_isAcceptedOnceRepeatedWithinWindow() {
        filter.offer(reading(240f, at = 0L))
        filter.offer(reading(240f, at = 500L))

        assertNull(filter.offer(reading(200f, at = 1_000L)))
        assertEquals(200f, filter.offer(reading(200f, at = 1_500L))!!.pressureKpa, 0.01f)
    }

    @Test
    fun repeatOutsideWindow_startsConfirmationAgain() {
        assertNull(filter.offer(reading(200f, at = 0L)))
        assertNull(filter.offer(reading(200f, at = 5_001L)))

        assertEquals(200f, filter.offer(reading(200f, at = 5_500L))!!.pressureKpa, 0.01f)
    }

    @Test
    fun sensorsAreConfirmedIndependently() {
        filter.offer(reading(240f, id = "FL", at = 0L))
        assertNull(filter.offer(reading(240f, id = "FR", at = 100L)))

        assertEquals("FL", filter.offer(reading(240f, id = "FL", at = 200L))!!.id)
    }

    @Test
    fun hid_singleTransmissionsOfAFallingPressureStillConfirm() {
        val hid = ReadingConfirmationFilter.forHid()
        assertNull(hid.offer(reading(230f, at = 0L)))
        // One transmission a minute, each a few kPa lower: never the same value twice.
        assertEquals(226f, hid.offer(reading(226f, at = 60_000L))!!.pressureKpa, 0.01f)
        assertEquals(221f, hid.offer(reading(221f, at = 120_000L))!!.pressureKpa, 0.01f)
    }

    @Test
    fun hid_bigJumpStillNeedsASecondCloseReading() {
        val hid = ReadingConfirmationFilter.forHid()
        hid.offer(reading(230f, at = 0L))
        hid.offer(reading(230f, at = 60_000L))
        assertNull(hid.offer(reading(30f, at = 120_000L)))
        assertEquals(31f, hid.offer(reading(31f, at = 180_000L))!!.pressureKpa, 0.01f)
    }

    @Test
    fun hid_secondReadingOutsideTheWindowIsNotConfirmed() {
        val hid = ReadingConfirmationFilter.forHid()
        assertNull(hid.offer(reading(230f, at = 0L)))
        assertNull(hid.offer(reading(230f, at = ReadingConfirmationFilter.HID_WINDOW_MS + 1)))
    }

    private fun reading(
        pressureKpa: Float,
        id: String = "FL",
        flags: SensorFlags = SensorFlags.NONE,
        at: Long
    ) = TireSensor(
        id = id,
        label = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = 20f,
        batteryPercent = null,
        alertType = null,
        timestamp = at,
        flags = flags
    )
}
