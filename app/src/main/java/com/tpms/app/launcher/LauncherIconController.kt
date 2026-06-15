package com.tpms.app.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tpms.app.data.repository.TpmsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherIconController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TpmsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastHealthy: Boolean? = null

    fun start() {
        scope.launch {
            combine(repository.state, repository.sensors) { state, sensors ->
                LauncherIconPolicy.isHealthy(state, sensors.values)
            }
                .distinctUntilChanged()
                .collect { healthy -> apply(healthy) }
        }
    }

    private fun apply(healthy: Boolean) {
        if (lastHealthy == healthy) return
        lastHealthy = healthy

        val pm = context.packageManager
        val ok = ComponentName(context, LAUNCHER_OK)
        val alert = ComponentName(context, LAUNCHER_ALERT)

        pm.setComponentEnabledSetting(
            ok,
            if (healthy) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            alert,
            if (healthy) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }

    companion object {
        private const val LAUNCHER_OK = "com.tpms.app.launcher.LauncherIconOk"
        private const val LAUNCHER_ALERT = "com.tpms.app.launcher.LauncherIconAlert"
    }
}
