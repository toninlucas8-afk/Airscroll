package dev.airscroll.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.airscroll.app.ui.calibration.CalibrationScreen
import dev.airscroll.app.ui.home.HomeScreen
import dev.airscroll.app.ui.onboarding.GuidedSetupScreen
import dev.airscroll.app.ui.practice.PracticeScreen
import dev.airscroll.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALIBRATION = "calibration"
    const val SETTINGS = "settings"
    const val PRACTICE = "practice"
}

@Composable
fun AirScrollApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // I permessi si concedono fuori dall'app: vanno riletti a ogni ritorno.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startDestination = remember(settings.onboardingCompleted) {
        if (settings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.ONBOARDING) {
                GuidedSetupScreen(
                    viewModel = viewModel,
                    onOpenCalibration = { navController.navigate(Routes.CALIBRATION) },
                    onOpenPractice = { navController.navigate(Routes.PRACTICE) },
                    onFinished = {
                        viewModel.completeOnboarding()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenCalibration = { navController.navigate(Routes.CALIBRATION) },
                    onOpenPractice = { navController.navigate(Routes.PRACTICE) },
                    onOpenSetup = { navController.navigate(Routes.ONBOARDING) },
                )
            }
            composable(Routes.CALIBRATION) {
                CalibrationScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.CALIBRATION) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPractice = { navController.navigate(Routes.PRACTICE) },
                    onOpenSetup = { navController.navigate(Routes.ONBOARDING) },
                )
            }
            composable(Routes.PRACTICE) {
                PracticeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
