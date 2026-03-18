package com.paymv.posterminal.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.paymv.posterminal.ui.component.AdBanner
import com.paymv.posterminal.ui.component.ConnectionStatus
import com.paymv.posterminal.ui.component.ViberSlipMessage
import com.paymv.posterminal.ui.theme.Gray
import com.paymv.posterminal.ui.theme.DarkPrimary
import com.paymv.posterminal.ui.viewmodel.IdleViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdleScreen(
    viewModel: IdleViewModel,
    onNavigateToQR: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val webhookServerStatus by viewModel.webhookServerStatus.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Clear QR-active flag when IdleScreen enters composition (returning from QR)
    LaunchedEffect(Unit) {
        viewModel.onReturnedFromQr()
    }
    
    // Navigate to QR screen when payment is detected
    LaunchedEffect(uiState.pendingPayment) {
        uiState.pendingPayment?.let { payment ->
            onNavigateToQR(payment.amount)
            viewModel.onPaymentNavigated()
        }
    }
    
    // Show test message
    if (uiState.showTestMessage) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearTestMessage()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Test payment sent successfully")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PayMV POS Terminal") },
                actions = {
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Ad Banner (if ads not hidden)
            if (!settings.hideAds) {
                AdBanner(text = "Advertisement Space - Top")
            }
            
            Spacer(modifier = Modifier.weight(0.5f))
            
            // Store Logo
            if (!settings.storeLogo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(settings.storeLogo!!))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Store Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder logo
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = settings.storeName.take(2).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Store Name
            Text(
                text = settings.storeName,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Connection Status
            ConnectionStatus(isConnected = isConnected)
            
            // Webhook Server Status
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (webhookServerStatus) 
                    MaterialTheme.colorScheme.surfaceVariant 
                else 
                    MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Status indicator dot
                    Surface(
                        shape = CircleShape,
                        color = if (webhookServerStatus) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Local Server: ${if (webhookServerStatus) "ACTIVE" else "INACTIVE"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Active webhook indicator
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = DarkPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Webhook :${settings.webhookPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkPrimary
                    )
                }
            }
            
            // Webhook error display
            if (uiState.webhookError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.webhookError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info Text
            Text(
                text = "Waiting for payment request...",
                style = MaterialTheme.typography.bodyLarge,
                color = Gray,
                textAlign = TextAlign.Center
            )
            
            // Viber slip message (if Viber number is set)
            if (!settings.viberNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                ViberSlipMessage(viberNumber = settings.viberNumber!!)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Manual QR Input (if enabled in settings)
            if (settings.showManualQrInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.manualAmount,
                        onValueChange = { viewModel.updateManualAmount(it) },
                        label = { Text("Amount (MVR)") },
                        placeholder = { Text("25.00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.amountError != null,
                        supportingText = {
                            if (uiState.amountError != null) {
                                Text(
                                    text = uiState.amountError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )
                    Button(
                        onClick = { viewModel.generateManualQR() },
                        modifier = Modifier
                            .width(80.dp)
                            .height(56.dp),
                        enabled = uiState.manualAmount.isNotBlank()
                    ) {
                        Text("GO")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bottom Ad Banner (if ads not hidden)
            if (!settings.hideAds) {
                AdBanner(text = "Advertisement Space - Bottom")
            }
        }
    }
}
