package com.tpms.app.ui.widget

import com.tpms.app.domain.model.PressureUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSnapshotTest {

    @Test
    fun formatTireSlot_showsLostWhenSensorMissing() {
        val snapshot = WidgetSnapshot.empty(PressureUnit.BAR)
        val fl = snapshot.tires.first()

        assertEquals("FL", fl.label)
        assertEquals("-- Bar", fl.pressureText)
        assertEquals(WidgetTireStatus.EMPTY, fl.status)
    }
}
