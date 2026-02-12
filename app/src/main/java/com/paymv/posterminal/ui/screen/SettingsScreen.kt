package com.paymv.posterminal.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val editableSettings by viewModel.editableSettings.collectAsState()
    val context = LocalContext.current
    
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
            
            // Local Webhook Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Webhook Server",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Run a local HTTP server to receive payment requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = editableSettings.webhookPort.toString(),
                        onValueChange = { viewModel.updateWebhookPort(it) },
                        label = { Text("Webhook Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.webhookServerRunning,
                        supportingText = {
                            Text("POST /payment on this port to trigger QR display")
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Server toggle button
                    Button(
                        onClick = { viewModel.toggleWebhookServer() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (uiState.webhookServerRunning) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.webhookServerRunning) 
                                Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.webhookServerRunning) 
                                "Stop Webhook Server" else "Start Webhook Server"
                        )
                    }
                    
                    // Server status
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
                                "Server running on port ${editableSettings.webhookPort}" 
                            else "Server stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Error display
                    if (uiState.webhookError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.webhookError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When server is running, send POST requests to http://<device-ip>:${editableSettings.webhookPort}/payment with JSON body {\"amount\": \"25.00\"} to display QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pro Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pro Mode (Hide Ads)", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editableSettings.proMode,
                    onCheckedChange = { viewModel.updateProMode(it) }
                )
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
}
