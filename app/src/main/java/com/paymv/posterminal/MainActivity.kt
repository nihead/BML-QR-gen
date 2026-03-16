package com.paymv.posterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.paymv.posterminal.ui.navigation.AppNavigation
import com.paymv.posterminal.ui.theme.PayMVPOSTerminalTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PayMVPOSTerminalTheme {
                // System UI Controller for status bar
                val systemUiController = rememberSystemUiController()
                val backgroundColor = MaterialTheme.colorScheme.background
                
                systemUiController.setSystemBarsColor(
                    color = backgroundColor,
                    darkIcons = false
                )
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}
