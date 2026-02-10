package com.paymv.posterminal.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
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
import com.paymv.posterminal.ui.screen.IdleScreen
import com.paymv.posterminal.ui.screen.QrDisplayScreen
import com.paymv.posterminal.ui.screen.SettingsScreen
import com.paymv.posterminal.ui.viewmodel.IdleViewModel
import com.paymv.posterminal.ui.viewmodel.QrDisplayViewModel
import com.paymv.posterminal.ui.viewmodel.SettingsViewModel
import com.paymv.posterminal.ui.viewmodel.ViewModelFactory
import com.paymv.posterminal.util.NetworkMonitor

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    
    // Initialize repositories
    val settingsRepository = SettingsRepository(context)
    val paymentRepository = PaymentRepository(RetrofitClient.paymentApi)
    val networkMonitor = NetworkMonitor(context)
    
    NavHost(navController = navController, startDestination = Screen.Idle.route) {
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
                    navController.popBackStack()
                }
            )
        }
    }
}
