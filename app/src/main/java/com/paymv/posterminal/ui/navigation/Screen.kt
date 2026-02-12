package com.paymv.posterminal.ui.navigation

sealed class Screen(val route: String) {
    object Idle : Screen("idle")
    object QRDisplay : Screen("qr_display/{amount}") {
        fun createRoute(amount: String) = "qr_display/$amount"
    }
    object Settings : Screen("settings")
    object Browser : Screen("browser")
}
