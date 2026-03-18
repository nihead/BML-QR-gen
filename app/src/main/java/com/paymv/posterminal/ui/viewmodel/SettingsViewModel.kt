package com.paymv.posterminal.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paymv.posterminal.data.billing.SubscriptionRepository
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    
    private val _editableSettings = MutableStateFlow(settingsRepository.settings.value)
    val editableSettings: StateFlow<AppSettings> = _editableSettings.asStateFlow()
    
    // Subscription state
    val isSubscribed: StateFlow<Boolean> = subscriptionRepository.isSubscribed
    
    init {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                _editableSettings.value = currentSettings
            }
        }
        // Check if webhook server is already running
        val isWebhookActive = paymentRepository.isWebhookServerRunning
        _uiState.update {
            it.copy(webhookServerRunning = isWebhookActive)
        }
        android.util.Log.d("SettingsViewModel", "Init: Webhook server running = $isWebhookActive")
        
        // Auto-authenticate if no password is set
        if (settingsRepository.settings.value.adminPassword.isEmpty()) {
            _uiState.update { it.copy(isAuthenticated = true) }
        }
    }
    
    fun verifyPassword(password: String): Boolean {
        val isValid = settingsRepository.validatePassword(password)
        if (isValid) {
            _uiState.update { it.copy(isAuthenticated = true, passwordError = null) }
        } else {
            _uiState.update { it.copy(passwordError = "Incorrect password") }
        }
        return isValid
    }
    
    fun updateStoreName(name: String) {
        _editableSettings.update { it.copy(storeName = name) }
    }
    
    fun updateAccountName(name: String) {
        _editableSettings.update { it.copy(accountName = name) }
    }
    
    fun updateAccountNumber(number: String) {
        // Only allow digits
        val filtered = number.filter { it.isDigit() }
        if (filtered.length <= 13) {
            _editableSettings.update { it.copy(accountNumber = filtered) }
        }
    }
    
    fun updateMobileNumber(number: String) {
        _editableSettings.update { it.copy(mobileNumber = number) }
    }
    
    fun updateViberNumber(number: String) {
        _editableSettings.update { it.copy(viberNumber = number.ifBlank { null }) }
    }
    
    fun updateAdminPassword(password: String) {
        _editableSettings.update { it.copy(adminPassword = password) }
    }
    
    fun updateProMode(enabled: Boolean) {
        _editableSettings.update { it.copy(proMode = enabled) }
    }
    
    fun updateShowManualQrInput(enabled: Boolean) {
        _editableSettings.update { it.copy(showManualQrInput = enabled) }
    }
    
    fun updateHideAds(enabled: Boolean) {
        // Only allow enabling hideAds if subscribed
        if (enabled && !subscriptionRepository.isSubscribed.value) {
            // Show subscription required dialog
            _uiState.update { it.copy(showSubscriptionDialog = true) }
            return
        }
        _editableSettings.update { it.copy(hideAds = enabled) }
    }
    
    /**
     * Get formatted subscription price
     */
    fun getSubscriptionPrice(): String {
        return subscriptionRepository.getFormattedPrice()
    }
    
    /**
     * Launch subscription purchase flow
     */
    fun purchaseSubscription(activity: Activity): Boolean {
        return subscriptionRepository.purchaseSubscription(activity)
    }
    
    /**
     * Dismiss subscription dialog
     */
    fun dismissSubscriptionDialog() {
        _uiState.update { it.copy(showSubscriptionDialog = false) }
    }
    
    /**
     * Refresh subscription status
     */
    fun refreshSubscription() {
        viewModelScope.launch {
            subscriptionRepository.refreshSubscriptionStatus()
        }
    }
    
    fun updateBrowserEnabled(enabled: Boolean) {
        _editableSettings.update { it.copy(browserEnabled = enabled) }
    }
    
    fun updateBrowserUrl(url: String) {
        _editableSettings.update { it.copy(browserUrl = url) }
    }
    
    fun updateBrowserAutoReload(enabled: Boolean) {
        _editableSettings.update { it.copy(browserAutoReload = enabled) }
    }
    
    fun updateStoreLogo(logoPath: String?) {
        _editableSettings.update { it.copy(storeLogo = logoPath) }
    }
    
    /**
     * Copy image from content:// URI to app-local storage so it persists across restarts.
     */
    fun copyAndSetStoreLogo(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val logoFile = java.io.File(context.filesDir, "store_logo.png")
                logoFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                val localPath = logoFile.absolutePath
                _editableSettings.update { it.copy(storeLogo = localPath) }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to copy store logo: ${e.message}")
            }
        }
    }
    
    fun updateWebhookPort(portStr: String) {
        val port = portStr.filter { it.isDigit() }.toIntOrNull() ?: return
        _editableSettings.update { it.copy(webhookPort = port) }
    }
    
    fun restartWebhookServer() {
        viewModelScope.launch {
            val currentPort = _editableSettings.value.webhookPort
            android.util.Log.d("SettingsViewModel", "Restarting webhook server on port $currentPort")
            
            // Stop existing server
            paymentRepository.stopWebhookServer()
            _uiState.update { it.copy(webhookServerRunning = false, webhookError = null) }
            
            // Start with new port
            try {
                val success = paymentRepository.startWebhookServer(currentPort)
                if (success) {
                    _uiState.update { it.copy(webhookServerRunning = true, webhookError = null) }
                    android.util.Log.d("SettingsViewModel", "Webhook server restarted successfully")
                } else {
                    _uiState.update { it.copy(webhookServerRunning = false, webhookError = "Failed to start server on port $currentPort") }
                    android.util.Log.e("SettingsViewModel", "Failed to restart webhook server")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(webhookServerRunning = false, webhookError = e.message) }
                android.util.Log.e("SettingsViewModel", "Exception restarting webhook server: ${e.message}", e)
            }
        }
    }
    
    fun saveSettings() {
        val currentSettings = _editableSettings.value
        
        // Validate
        val errors = mutableListOf<String>()
        
        if (currentSettings.storeName.isEmpty()) {
            errors.add("Store name is required")
        }
        
        if (currentSettings.accountNumber.isEmpty()) {
            errors.add("Account number is required")
        } else if (!settingsRepository.validateAccountNumber(currentSettings.accountNumber)) {
            errors.add("Account number must be exactly 13 digits")
        }
        
        if (!currentSettings.mobileNumber.isNullOrEmpty() && 
            !settingsRepository.validateMobileNumber(currentSettings.mobileNumber)) {
            errors.add("Invalid mobile number format (use +960XXXXXXX)")
        }
        
        if (currentSettings.adminPassword.isNotEmpty() && currentSettings.adminPassword.length < 6) {
            errors.add("Password must be at least 6 characters (or leave empty for no protection)")
        }
        
        // Validate browser settings
        if (currentSettings.browserEnabled) {
            if (currentSettings.browserUrl.isEmpty()) {
                errors.add("Browser URL is required when browser mode is enabled")
            } else if (!currentSettings.browserUrl.startsWith("http://") && 
                       !currentSettings.browserUrl.startsWith("https://")) {
                errors.add("Browser URL must start with http:// or https://")
            }
        }
        
        // Validate webhook port
        if (!settingsRepository.validateWebhookPort(currentSettings.webhookPort)) {
            errors.add("Webhook port must be between 1024 and 65535")
        }
        
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }
        
        // Save
        settingsRepository.saveSettings(currentSettings)
        _uiState.update { it.copy(saveSuccess = true, validationErrors = emptyList()) }
    }
    
    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
    
    fun clearPasswordError() {
        _uiState.update { it.copy(passwordError = null) }
    }
}

data class SettingsUiState(
    val isAuthenticated: Boolean = false,
    val passwordError: String? = null,
    val validationErrors: List<String> = emptyList(),
    val saveSuccess: Boolean = false,
    val webhookServerRunning: Boolean = false,
    val webhookError: String? = null,
    val showSubscriptionDialog: Boolean = false
)
