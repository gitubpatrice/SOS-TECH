package com.filestech.sos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filestech.sos.security.AppLockManager
import com.filestech.sos.ui.navigation.AppDestination
import com.filestech.sos.ui.screens.cascade.CascadeScreen
import com.filestech.sos.ui.screens.contacts.ContactsScreen
import com.filestech.sos.ui.screens.emergency.EmergencyScreen
import com.filestech.sos.ui.screens.home.HomeScreen
import com.filestech.sos.ui.screens.livegps.LiveGpsScreen
import com.filestech.sos.ui.screens.lock.LockScreen
import com.filestech.sos.ui.screens.lock.LockViewModel
import com.filestech.sos.ui.screens.recording.RecordingScreen
import com.filestech.sos.ui.screens.settings.SettingsScreen
import com.filestech.sos.ui.screens.siren.SirenScreen
import com.filestech.sos.ui.screens.splash.SplashScreen
import com.filestech.sos.ui.screens.voice.VoiceScreen
import com.filestech.sos.ui.screens.webhook.WebhookScreen

@Composable
fun AppRoot() {
    val lockViewModel: LockViewModel = hiltViewModel()
    val lockState by lockViewModel.state.collectAsStateWithLifecycle()

    // Gate: Locked or LockedOut → show LockScreen full-screen, no NavHost visible.
    // Disabled / Unlocked / PanicDecoy → proceed to NavHost.
    val showLock = when (lockState) {
        AppLockManager.LockState.Locked,
        is AppLockManager.LockState.LockedOut -> true
        else -> false
    }

    if (showLock) {
        LockScreen(viewModel = lockViewModel)
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Splash.route,
    ) {
        composable(AppDestination.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo(AppDestination.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.Home.route) {
            HomeScreen(
                onNavigateToEmergency = { navController.navigate(AppDestination.Emergency.route) },
                onNavigateToVoice = { navController.navigate(AppDestination.Voice.route) },
                onNavigateToCascade = { navController.navigate(AppDestination.Cascade.route) },
                onNavigateToSiren = { navController.navigate(AppDestination.Siren.route) },
                onNavigateToLiveGps = { navController.navigate(AppDestination.LiveGps.route) },
                onNavigateToRecording = { navController.navigate(AppDestination.Recording.route) },
                onNavigateToWebhook = { navController.navigate(AppDestination.Webhook.route) },
                onNavigateToContacts = { navController.navigate(AppDestination.Contacts.route) },
                onNavigateToSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }
        composable(AppDestination.Emergency.route) {
            EmergencyScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Voice.route) {
            VoiceScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Cascade.route) {
            CascadeScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Siren.route) {
            SirenScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.LiveGps.route) {
            LiveGpsScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Recording.route) {
            RecordingScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Webhook.route) {
            WebhookScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Contacts.route) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
