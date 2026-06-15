package com.tpms.app.startup

import com.tpms.app.data.settings.TeyesChecklist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeyesSetupStatusTest {

    @Test
    fun needsAttention_whenServiceStoppedOnTeyes() {
        val status = TeyesSetupStatus(
            isTeyesDevice = true,
            checklist = TeyesChecklist(autoStart = true, batteryUnrestricted = true, lockInRecents = true, bootCompleted = true, autoRunAwake = true),
            checklistComplete = true,
            batteryUnrestricted = true,
            notificationsEnabled = true,
            serviceRunning = false,
            widgetActive = true
        )
        assertTrue(status.needsAttention)
    }

    @Test
    fun needsAttention_falseOnNonTeyesEvenIfIncomplete() {
        val status = TeyesSetupStatus(
            isTeyesDevice = false,
            checklist = TeyesChecklist(),
            checklistComplete = false,
            batteryUnrestricted = false,
            notificationsEnabled = false,
            serviceRunning = false,
            widgetActive = false
        )
        assertFalse(status.needsAttention)
    }

    @Test
    fun showWidgetHint_whenNoWidgetOnTeyes() {
        val status = TeyesSetupStatus(
            isTeyesDevice = true,
            checklist = TeyesChecklist(autoStart = true, batteryUnrestricted = true, lockInRecents = true, bootCompleted = true, autoRunAwake = true),
            checklistComplete = true,
            batteryUnrestricted = true,
            notificationsEnabled = true,
            serviceRunning = true,
            widgetActive = false
        )
        assertTrue(status.showWidgetHint)
        assertFalse(status.needsAttention)
    }
}
