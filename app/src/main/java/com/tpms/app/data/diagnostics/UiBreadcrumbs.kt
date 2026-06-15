package com.tpms.app.data.diagnostics

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiBreadcrumbs @Inject constructor() {

    @Volatile
    var lastScreen: String = "startup"
        private set

    @Volatile
    var lastScreenAtMs: Long = System.currentTimeMillis()
        private set

    fun setScreen(route: String) {
        lastScreen = route
        lastScreenAtMs = System.currentTimeMillis()
    }

    fun describe(): String {
        val ageSec = (System.currentTimeMillis() - lastScreenAtMs) / 1000
        return "Last screen: $lastScreen (${ageSec}s ago)"
    }
}
