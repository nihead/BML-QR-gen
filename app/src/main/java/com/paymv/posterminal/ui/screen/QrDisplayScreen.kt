package com.paymv.posterminal.ui.screen

import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.ui.component.QRCodeView
import com.paymv.posterminal.ui.theme.DarkPrimary
import com.paymv.posterminal.ui.theme.Gray
import com.paymv.posterminal.ui.viewmodel.QrDisplayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrDisplayScreen(
    viewModel: QrDisplayViewModel,
    amount: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentAmount by viewModel.currentAmount.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val shouldNavigateBack by viewModel.shouldNavigateBack.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    
    // Keep screen awake
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Auto navigate back when timeout
    LaunchedEffect(shouldNavigateBack) {
        if (shouldNavigateBack) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SCAN TO PAY") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackPressed() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))
            
            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating QR Code...")
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                // Amount with animation
                Text(
                    text = "MVR $currentAmount",
                    style = MaterialTheme.typography.displayLarge,
                    color = DarkPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer(alpha = if (isUpdating) 0.3f else 1f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // QR Code with fade animation
                Box(
                    modifier = Modifier.graphicsLayer(alpha = if (isUpdating) 0.3f else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    QRCodeView(bitmap = uiState.qrBitmap)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Account Info
                Text(
                    text = "${settings.accountName} - ${settings.accountNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Timer
                Text(
                    text = "Auto-close in ${timeRemaining}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Back Button
            OutlinedButton(
                onClick = { viewModel.onBackPressed() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Back to Home")
            }
        }
    }
}
