package com.paymv.posterminal.ui.screen

import android.net.Uri
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.ui.viewmodel.SettingsViewModel
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val editableSettings by viewModel.editableSettings.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    
    // Reactively determine if password dialog should show
    val needsPasswordAuth = !uiState.isAuthenticated && editableSettings.adminPassword.isNotEmpty()
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // Show dialog when screen opens and password is set
    LaunchedEffect(needsPasswordAuth) {
        if (needsPasswordAuth) {
            showPasswordDialog = true
        }
    }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Image picker - copies image to local storage to persist across restarts
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.copyAndSetStoreLogo(context, it)
        }
    }
    
    // Password Dialog
    if (showPasswordDialog && !uiState.isAuthenticated) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                onNavigateBack()
            },
            title = { Text("Enter Admin Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { 
                            passwordInput = it
                            viewModel.clearPasswordError()
                        },
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) 
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility 
                                    else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        isError = uiState.passwordError != null,
                        supportingText = {
                            if (uiState.passwordError != null) {
                                Text(uiState.passwordError ?: "")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.verifyPassword(passwordInput)) {
                        showPasswordDialog = false
                    }
                }) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPasswordDialog = false
                    onNavigateBack()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Show save success
    if (uiState.saveSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Store Name
            OutlinedTextField(
                value = editableSettings.storeName,
                onValueChange = { viewModel.updateStoreName(it) },
                label = { Text("Store Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hide Ads Toggle - Premium Feature (at top for visibility)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSubscribed) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSubscribed) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Hide Advertisements",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isSubscribed) "Premium Active" else "Premium Feature",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSubscribed)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        if (isSubscribed) {
                            Switch(
                                checked = editableSettings.hideAds,
                                onCheckedChange = { viewModel.updateHideAds(it) }
                            )
                        } else {
                            TextButton(
                                onClick = { viewModel.updateHideAds(true) }
                            ) {
                                Text(
                                    "Subscribe",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    if (!isSubscribed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Subscribe for ${viewModel.getSubscriptionPrice()} to remove all ads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Store Logo
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editableSettings.storeLogo.isNullOrEmpty()) 
                    "Upload Store Logo" else "Change Store Logo")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Account Name
            OutlinedTextField(
                value = editableSettings.accountName,
                onValueChange = { viewModel.updateAccountName(it) },
                label = { Text("Account Name") },
                placeholder = { Text("e.g., MOHD.NIHAD") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Account Number
            OutlinedTextField(
                value = editableSettings.accountNumber,
                onValueChange = { viewModel.updateAccountNumber(it) },
                label = { Text("Account Number (13 digits)") },
                placeholder = { Text("1234567890123") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("${editableSettings.accountNumber.length}/13")
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mobile Number
            OutlinedTextField(
                value = editableSettings.mobileNumber ?: "",
                onValueChange = { viewModel.updateMobileNumber(it) },
                label = { Text("Mobile Number (Optional)") },
                placeholder = { Text("+9607654321") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Viber Number (Optional)
            OutlinedTextField(
                value = editableSettings.viberNumber ?: "",
                onValueChange = { viewModel.updateViberNumber(it) },
                label = { Text("Viber Number (Optional)") },
                placeholder = { Text("+9607654321") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Customers will be asked to send slip to this number")
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Password Protection Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Password Protection", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (editableSettings.adminPassword.isEmpty()) "Disabled" else "Enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = editableSettings.adminPassword.isNotEmpty(),
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.updateAdminPassword("")
                        } else {
                            viewModel.updateAdminPassword("000000")
                        }
                    }
                )
            }
            
            // Password field (only shown when protection is enabled)
            if (editableSettings.adminPassword.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                var newPasswordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = editableSettings.adminPassword,
                    onValueChange = { viewModel.updateAdminPassword(it) },
                    label = { Text("Admin Password") },
                    placeholder = { Text("Enter password") },
                    visualTransformation = if (newPasswordVisible) 
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                if (newPasswordVisible) Icons.Default.Visibility 
                                else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Minimum 6 characters")
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Manual QR Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manual QR Input", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editableSettings.showManualQrInput,
                    onCheckedChange = { viewModel.updateShowManualQrInput(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Browser Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Browser Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Replace idle screen with web browser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = editableSettings.browserEnabled,
                            onCheckedChange = { viewModel.updateBrowserEnabled(it) }
                        )
                    }
                    
                    if (editableSettings.browserEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = editableSettings.browserUrl,
                            onValueChange = { viewModel.updateBrowserUrl(it) },
                            label = { Text("Browser URL") },
                            placeholder = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text("Enter the URL to display when app starts")
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Reload", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Periodically reload the page (every 5 min)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = editableSettings.browserAutoReload,
                                onCheckedChange = { viewModel.updateBrowserAutoReload(it) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Local Webhook Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Webhook Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Receives payment requests via HTTP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Server status indicator
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (uiState.webhookServerRunning) 
                                androidx.compose.ui.graphics.Color(0xFF4CAF50) 
                            else MaterialTheme.colorScheme.outline
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.webhookServerRunning) 
                                "Running on port ${editableSettings.webhookPort}" 
                            else "Starting...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val deviceIp = remember { getDeviceIpAddress() }
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Web UI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "http://$deviceIp:${editableSettings.webhookPort}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open this URL in a browser to send test payments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = editableSettings.webhookPort.toString(),
                        onValueChange = { viewModel.updateWebhookPort(it) },
                        label = { Text("Server Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("POST /payment on this port to trigger QR display")
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Restart server button (to apply port changes)
                    OutlinedButton(
                        onClick = { viewModel.restartWebhookServer() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart Server")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "API: POST http://$deviceIp:${editableSettings.webhookPort}/payment with JSON body {\"amount\": \"25.00\"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    
                    // Error display
                    if (uiState.webhookError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.webhookError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Validation Errors
            if (uiState.validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        uiState.validationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Save Button
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Save Settings")
            }
            
            // Success Message
            if (uiState.saveSuccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Settings saved successfully!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
    
    // Subscription Dialog
    if (uiState.showSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSubscriptionDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { 
                Text(
                    "Premium Subscription",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ) 
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Remove all advertisements from the app",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        viewModel.getSubscriptionPrice(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "per month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cancel anytime from Google Play",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activity?.let { viewModel.purchaseSubscription(it) }
                        viewModel.dismissSubscriptionDialog()
                    }
                ) {
                    Text("Subscribe Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissSubscriptionDialog() }
                ) {
                    Text("Maybe Later")
                }
            }
        )
    }
}

/**
 * Get the device's local IP address on the WiFi/LAN network.
 */
private fun getDeviceIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: continue
                }
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
    return "127.0.0.1"
}
