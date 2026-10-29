package com.tpms.app.data.usb.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Aa55EmptySlotsTest {

    @Test
    fun emptyAnswerMarksSlotEmpty() {
        val result = Aa55EmptySlots.apply(emptySet(), listOf(Aa55AuxFrame.SensorId("SP", null)))

        assertEquals(setOf("SP"), result)
    }

    @Test
    fun sensorIdClearsSlot() {
        val result = Aa55EmptySlots.apply(
            setOf("SP", "RR"),
            listOf(Aa55AuxFrame.SensorId("SP", "A1B2C3D4"))
        )

        assertEquals(setOf("RR"), result)
    }

    @Test
    fun laterAnswerForSameSlotWins() {
        val result = Aa55EmptySlots.apply(
            emptySet(),
            listOf(
                Aa55AuxFrame.SensorId("FL", null),
                Aa55AuxFrame.SensorId("FL", "A1B2C3D4"),
                Aa55AuxFrame.SensorId("SP", "A1B2C3D5"),
                Aa55AuxFrame.SensorId("SP", null)
            )
        )

        assertEquals(setOf("SP"), result)
    }
}
