package com.tpms.app.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRemoteViewsTest {

    @Test
    fun summaryLine_joinsWheelPressuresWithUnit() {
        val snapshot = WidgetSnapshot(
            connectionStatus = "Live",
            unitLabel = "Bar",
            tires = listOf(
                WidgetTireSlot("FL", "2.2", WidgetTireStatus.OK),
                WidgetTireSlot("FR", "2.2", WidgetTireStatus.OK),
                WidgetTireSlot("RL", "2.1", WidgetTireStatus.WARNING),
                WidgetTireSlot("RR", "--", WidgetTireStatus.EMPTY)
            )
        )

        assertEquals(
            "FL 2.2 FR 2.2 RL 2.1 RR -- Bar",
            WidgetRemoteViews.summaryLine(snapshot)
        )
    }
}
