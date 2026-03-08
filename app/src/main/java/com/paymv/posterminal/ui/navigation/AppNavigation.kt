package com.paymv.posterminal.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paymv.posterminal.data.api.RetrofitClient
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import com.paymv.posterminal.ui.screen.BrowserScreen
import com.paymv.posterminal.ui.screen.IdleScreen
import com.paymv.posterminal.ui.screen.QrDisplayScreen
import com.paymv.posterminal.ui.screen.SettingsScreen
import com.paymv.posterminal.ui.viewmodel.BrowserViewModel
import com.paymv.posterminal.ui.viewmodel.IdleViewModel
import com.paymv.posterminal.ui.viewmodel.QrDisplayViewModel
import com.paymv.posterminal.ui.viewmodel.SettingsViewModel
import com.paymv.posterminal.ui.viewmodel.ViewModelFactory
import com.paymv.posterminal.util.NetworkMonitor

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    
    // Initialize repositories (remember to prevent recreation on recomposition)
    val settingsRepository = remember { SettingsRepository(context) }
    val paymentRepository = remember { PaymentRepository(RetrofitClient.paymentApi) { settingsRepository.settings.value } }
    val networkMonitor = remember { NetworkMonitor(context) }
    
    // Determine start destination based on browser settings
    // Use remember to capture the initial value and prevent NavHost reconfiguration
    val startDestination = remember {
        val settings = settingsRepository.settings.value
        if (settings.browserEnabled && settings.browserUrl.isNotEmpty()) {
            Screen.Browser.route
        } else {
            Screen.Idle.route
        }
    }
    
    NavHost(navController = navController, startDestination = startDestination) {
        // Idle Screen
        composable(Screen.Idle.route) {
            val viewModel: IdleViewModel = viewModel(
                factory = ViewModelFactory.createIdleViewModelFactory(
                    paymentRepository,
                    settingsRepository,
                    networkMonitor
                )
            )
            
            IdleScreen(
                viewModel = viewModel,
                onNavigateToQR = { amount ->
                    navController.navigate(Screen.QRDisplay.createRoute(amount))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // QR Display Screen
        composable(
            route = Screen.QRDisplay.route,
            arguments = listOf(navArgument("amount") { type = NavType.StringType })
        ) { backStackEntry ->
            val amount = backStackEntry.arguments?.getString("amount") ?: "0.00"
            
            val viewModel: QrDisplayViewModel = viewModel(
                factory = ViewModelFactory.createQrDisplayViewModelFactory(
                    application,
                    paymentRepository,
                    settingsRepository,
                    amount
                )
            )
            
            QrDisplayScreen(
                viewModel = viewModel,
                amount = amount,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Settings Screen
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = ViewModelFactory.createSettingsViewModelFactory(settingsRepository, paymentRepository)
            )
            
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    // Navigate to the correct home screen based on current settings
                    val currentSettings = settingsRepository.settings.value
                    val homeRoute = if (currentSettings.browserEnabled && currentSettings.browserUrl.isNotEmpty()) {
                        Screen.Browser.route
                    } else {
                        Screen.Idle.route
                    }
                    navController.navigate(homeRoute) {
                        // Clear entire back stack so home screen is the root
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Browser Screen
        composable(Screen.Browser.route) {
            val viewModel: BrowserViewModel = viewModel(
                factory = ViewModelFactory.createBrowserViewModelFactory(
                    paymentRepository,
                    settingsRepository,
                    networkMonitor
                )
            )
            
            BrowserScreen(
                viewModel = viewModel,
                onNavigateToQR = { amount ->
                    navController.navigate(Screen.QRDisplay.createRoute(amount))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
    }
}
