package com.tpms.app.domain

import com.tpms.app.domain.model.TireSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WheelAssignmentTest {

    private fun sensor(id: String, label: String = "", at: Long = 1L) = TireSensor(
        id = id,
        label = label,
        pressureKpa = 240f,
        temperatureCelsius = 20f,
        batteryPercent = 80,
        alertType = null,
        timestamp = at
    )

    private fun aa55(label: String) = sensor(label, label)

    private fun seen(vararg sensors: TireSensor) = linkedMapOf(*sensors.map { it.id to it }.toTypedArray())

    private fun Map<String, TireSensor>.ids() = mapValues { it.value.id }

    @Test
    fun defaultSlot_usesAa55Label() {
        assertEquals("FL", WheelAssignment.defaultSlot(aa55("FL")))
        assertEquals("RR", WheelAssignment.defaultSlot(aa55("RR")))
        assertEquals("SP", WheelAssignment.defaultSlot(aa55("SP")))
    }

    @Test
    fun defaultSlot_mapsHidIdsToWheels() {
        assertEquals("FL", WheelAssignment.defaultSlot(sensor("SENSOR_01")))
        assertEquals("FR", WheelAssignment.defaultSlot(sensor("SENSOR_02")))
        assertEquals("RL", WheelAssignment.defaultSlot(sensor("SENSOR_03")))
        assertEquals("RR", WheelAssignment.defaultSlot(sensor("SENSOR_04")))
        assertEquals("SP", WheelAssignment.defaultSlot(sensor("SENSOR_05")))
    }

    @Test
    fun defaultSlot_isNullForOtherIds() {
        assertNull(WheelAssignment.defaultSlot(sensor("SENSOR_06")))
        assertNull(WheelAssignment.defaultSlot(sensor("SENSOR_0A")))
        assertNull(WheelAssignment.defaultSlot(sensor("SENSOR_0102")))
        assertNull(WheelAssignment.defaultSlot(sensor("ABC123", "XX")))
    }

    @Test
    fun assign_placesAa55SensorsByLabel() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), aa55("FR"), aa55("RL"), aa55("RR")),
            wheelMapping = emptyMap(),
            showSpareWheel = false
        )

        assertEquals(mapOf("FL" to "FL", "FR" to "FR", "RL" to "RL", "RR" to "RR"), result.ids())
    }

    @Test
    fun assign_placesHidSensorsById() {
        val result = WheelAssignment.assign(
            seen(sensor("SENSOR_03"), sensor("SENSOR_01")),
            wheelMapping = emptyMap(),
            showSpareWheel = false
        )

        assertEquals(mapOf("FL" to "SENSOR_01", "RL" to "SENSOR_03"), result.ids())
    }

    @Test
    fun assign_doesNotFillFreeSlotsByOrder() {
        val result = WheelAssignment.assign(
            seen(sensor("SENSOR_0A"), sensor("SENSOR_0B"), aa55("FL")),
            wheelMapping = emptyMap(),
            showSpareWheel = true
        )

        assertEquals(mapOf("FL" to "FL"), result.ids())
    }

    @Test
    fun assign_spareOnlyWhenEnabled() {
        val sensors = seen(aa55("FL"), aa55("SP"), sensor("SENSOR_05"))

        assertEquals(
            mapOf("FL" to "FL"),
            WheelAssignment.assign(sensors, emptyMap(), showSpareWheel = false).ids()
        )
        assertEquals(
            mapOf("FL" to "FL", "SP" to "SP"),
            WheelAssignment.assign(seen(aa55("FL"), aa55("SP")), emptyMap(), showSpareWheel = true).ids()
        )
    }

    @Test
    fun assign_mappingReplacesOnlyItsSlot() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), aa55("FR"), sensor("ABC123")),
            wheelMapping = mapOf("FR" to "ABC123", "FL" to ""),
            showSpareWheel = false
        )

        assertEquals(mapOf("FL" to "FL", "FR" to "ABC123"), result.ids())
    }

    @Test
    fun assign_mappedButUnseenSensorLeavesSlotEmpty() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), aa55("FR")),
            wheelMapping = mapOf("FR" to "ABC123"),
            showSpareWheel = false
        )

        assertEquals(mapOf("FL" to "FL"), result.ids())
    }

    @Test
    fun assign_explicitlyMappedSensorLeavesItsDefaultSlot() {
        val result = WheelAssignment.assign(
            seen(sensor("SENSOR_01"), sensor("SENSOR_02")),
            wheelMapping = mapOf("RR" to "SENSOR_01"),
            showSpareWheel = false
        )

        assertEquals(mapOf("FR" to "SENSOR_02", "RR" to "SENSOR_01"), result.ids())
    }

    @Test
    fun assign_sameSensorMappedTwiceKeepsFirstSlot() {
        val result = WheelAssignment.assign(
            seen(sensor("ABC123")),
            wheelMapping = mapOf("RR" to "ABC123", "FR" to "ABC123"),
            showSpareWheel = false
        )

        assertEquals(mapOf("FR" to "ABC123"), result.ids())
    }

    @Test
    fun assign_skipsSensorsOfEmptyAa55Slots() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), aa55("SP")),
            wheelMapping = emptyMap(),
            showSpareWheel = true,
            emptySlots = setOf("SP")
        )

        assertEquals(mapOf("FL" to "FL"), result.ids())
    }

    @Test
    fun assign_emptyAa55SlotIsNeverPlacedEvenWhenMapped() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), aa55("SP")),
            wheelMapping = mapOf("RR" to "SP"),
            showSpareWheel = false,
            emptySlots = setOf("SP")
        )

        assertEquals(mapOf("FL" to "FL"), result.ids())
    }

    @Test
    fun assign_sensorMappedToSpareWhileSpareIsOffIsOnNoWheel() {
        val result = WheelAssignment.assign(
            seen(sensor("SENSOR_01"), sensor("SENSOR_02")),
            wheelMapping = mapOf("SP" to "SENSOR_01"),
            showSpareWheel = false
        )

        // The explicit mapping still takes SENSOR_01 off its default slot FL; with the spare hidden it is unassigned.
        assertEquals(mapOf("FR" to "SENSOR_02"), result.ids())
    }

    @Test
    fun assign_doesNotPlaceForeignLabelledSensor() {
        val result = WheelAssignment.assign(
            seen(aa55("FL"), sensor("SENSOR_07", "SENSOR_07")),
            wheelMapping = emptyMap(),
            showSpareWheel = false
        )

        assertEquals(mapOf("FL" to "FL"), result.ids())
    }

    @Test
    fun assign_resultIsInSlotOrder() {
        val result = WheelAssignment.assign(
            seen(aa55("RR"), aa55("SP"), aa55("FL")),
            wheelMapping = emptyMap(),
            showSpareWheel = true
        )

        assertEquals(listOf("FL", "RR", "SP"), result.keys.toList())
    }

    @Test
    fun retainSeen_dropsOnlyOldSensorsOffTheWheelsAndUnmapped() {
        val now = 10 * WheelAssignment.SEEN_SENSOR_MAX_AGE_MS
        val old = now - WheelAssignment.SEEN_SENSOR_MAX_AGE_MS - 1
        val sensors = seen(
            sensor("SENSOR_01", at = old),          // on a wheel: kept however old
            sensor("ABC123", at = old),             // mapped to the hidden spare: kept
            sensor("SENSOR_0A", at = now - 1_000L), // recent, off the wheels: kept for mapping
            sensor("SENSOR_0B", at = old)           // old, off the wheels, unmapped: dropped
        )
        val mapping = mapOf("SP" to "ABC123", "FR" to "")
        val wheels = WheelAssignment.assign(sensors, mapping, showSpareWheel = false)

        val kept = WheelAssignment.retainSeen(sensors, wheels, mapping, now)

        assertEquals(setOf("SENSOR_01", "ABC123", "SENSOR_0A"), kept.keys)
    }

    @Test
    fun retainSeen_keepsSensorExactlyAtMaxAge() {
        val now = 10 * WheelAssignment.SEEN_SENSOR_MAX_AGE_MS
        val sensors = seen(sensor("SENSOR_0A", at = now - WheelAssignment.SEEN_SENSOR_MAX_AGE_MS))

        val kept = WheelAssignment.retainSeen(sensors, emptyMap(), emptyMap(), now)

        assertEquals(setOf("SENSOR_0A"), kept.keys)
    }

    @Test
    fun unassigned_listsSeenSensorsOffTheWheelsSortedById() {
        val sensors = seen(sensor("SENSOR_0B"), aa55("FL"), sensor("SENSOR_0A"))
        val assignment = WheelAssignment.assign(sensors, emptyMap(), showSpareWheel = false)

        assertEquals(
            listOf("SENSOR_0A", "SENSOR_0B"),
            WheelAssignment.unassigned(sensors, assignment).map { it.id }
        )
    }

    private fun swapAndAssign(
        sensors: Map<String, TireSensor>,
        mapping: Map<String, String>,
        a: String,
        b: String
    ): Pair<Map<String, String>, Map<String, String>> {
        val wheels = WheelAssignment.assign(sensors, mapping, showSpareWheel = false)
        val entries = WheelAssignment.swap(sensors, mapping, wheels, a, b)
        return entries to WheelAssignment.assign(sensors, mapping + entries, showSpareWheel = false).ids()
    }

    @Test
    fun swap_exchangesTwoDefaultWheels() {
        val sensors = seen(aa55("FL"), aa55("FR"), aa55("RL"), aa55("RR"))

        val (entries, wheels) = swapAndAssign(sensors, emptyMap(), "FL", "RL")

        assertEquals(mapOf("FL" to "RL", "RL" to "FL"), entries)
        assertEquals(mapOf("FL" to "RL", "FR" to "FR", "RL" to "FL", "RR" to "RR"), wheels)
    }

    @Test
    fun swap_backToDefaultSlotsClearsMapping() {
        val sensors = seen(aa55("FL"), aa55("FR"), aa55("RL"), aa55("RR"))
        val swapped = mapOf("FL" to "RL", "RL" to "FL")

        val (entries, wheels) = swapAndAssign(sensors, swapped, "FL", "RL")

        assertEquals(mapOf("FL" to "", "RL" to ""), entries)
        assertEquals(mapOf("FL" to "FL", "FR" to "FR", "RL" to "RL", "RR" to "RR"), wheels)
    }

    @Test
    fun swap_withEmptyWheelMovesTheSensorOver() {
        val sensors = seen(aa55("FL"), aa55("FR"), aa55("RR"))

        val (entries, wheels) = swapAndAssign(sensors, emptyMap(), "FL", "RL")

        assertEquals(mapOf("FL" to "", "RL" to "FL"), entries)
        assertEquals(mapOf("FR" to "FR", "RL" to "FL", "RR" to "RR"), wheels)
    }

    @Test
    fun swap_movesMappedSensorsWithoutDefaultSlot() {
        val sensors = seen(sensor("A1B2C3"), sensor("D4E5F6"))
        val mapping = mapOf("FR" to "A1B2C3", "RR" to "D4E5F6")

        val (entries, wheels) = swapAndAssign(sensors, mapping, "FR", "RR")

        assertEquals(mapOf("FR" to "D4E5F6", "RR" to "A1B2C3"), entries)
        assertEquals(mapOf("FR" to "D4E5F6", "RR" to "A1B2C3"), wheels)
    }

    @Test
    fun swapCross_exchangesBothDiagonals() {
        val sensors = seen(aa55("FL"), aa55("FR"), aa55("RL"), aa55("RR"))
        val wheels = WheelAssignment.assign(sensors, emptyMap(), showSpareWheel = false)

        val entries = WheelAssignment.swapCross(sensors, emptyMap(), wheels)
        val after = WheelAssignment.assign(sensors, entries, showSpareWheel = false).ids()

        assertEquals(mapOf("FL" to "RR", "FR" to "RL", "RL" to "FR", "RR" to "FL"), after)
        val back = WheelAssignment.swapCross(sensors, entries, WheelAssignment.assign(sensors, entries, false))
        assertEquals(mapOf("FL" to "", "RR" to "", "FR" to "", "RL" to ""), back)
    }
}
