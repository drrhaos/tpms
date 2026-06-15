package com.tpms.app.launcher

import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState

object LauncherIconPolicy {
    fun isHealthy(state: TpmsState, sensors: Collection<TireSensor>): Boolean {
        if (state is TpmsState.Alert) return false
        if (state !is TpmsState.Connected) return false
        if (sensors.isEmpty()) return false
        return sensors.all { it.alertType == null }
    }
}
