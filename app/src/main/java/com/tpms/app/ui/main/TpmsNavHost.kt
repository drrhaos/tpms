package com.tpms.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tpms.app.di.UiBreadcrumbEntryPoint
import com.tpms.app.ui.embedded.LocalEmbeddedWindow
import com.tpms.app.ui.debug.DebugScreen
import com.tpms.app.ui.onboarding.OnboardingScreen
import com.tpms.app.ui.settings.SensorsScreen
import com.tpms.app.ui.settings.ServiceMenuScreen
import com.tpms.app.ui.settings.SettingsScreen
import com.tpms.app.ui.settings.SystemCheckScreen
import dagger.hilt.android.EntryPointAccessors

@Composable
fun TpmsNavHost(navHostViewModel: NavHostViewModel = hiltViewModel()) {
    val embedded = LocalEmbeddedWindow.current
    val startDestination by navHostViewModel.startDestination.collectAsState()
    if (startDestination == null) return

    val context = LocalContext.current

    if (embedded.isEmbedded) {
        val openFullScreen: () -> Unit = {
            context.startActivity(MainActivity.fullScreenIntent(context))
        }
        MainScreen(
            onNavigateToSettings = openFullScreen,
            onNavigateToDebug = openFullScreen
        )
        return
    }

    val appContext = context.applicationContext
    val navController = rememberNavController()
    val breadcrumbs = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            UiBreadcrumbEntryPoint::class.java
        ).uiBreadcrumbs()
    }
    val backStackEntry = navController.currentBackStackEntryAsState()

    LaunchedEffect(backStackEntry.value) {
        backStackEntry.value?.destination?.route?.let { route ->
            breadcrumbs.setScreen(route)
        }
    }

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDebug = { navController.navigate("debug") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSensors = { navController.navigate("settings/sensors") },
                onOpenSystemCheck = { navController.navigate("settings/system") },
                onOpenServiceMenu = { navController.navigate("settings/service") }
            )
        }
        composable("settings/sensors") {
            SensorsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/system") {
            SystemCheckScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/service") {
            ServiceMenuScreen(
                onBack = { navController.popBackStack() },
                onOpenDebug = { navController.navigate("debug") }
            )
        }
        composable("debug") {
            DebugScreen(onBack = { navController.popBackStack() })
        }
    }
}
