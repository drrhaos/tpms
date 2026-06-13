package com.tpms.app.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tpms.app.ui.debug.DebugScreen
import com.tpms.app.ui.settings.SettingsScreen

@Composable
fun TpmsNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDebug = { navController.navigate("debug") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDebug = { navController.navigate("debug") }
            )
        }
        composable("debug") {
            DebugScreen(onBack = { navController.popBackStack() })
        }
    }
}
