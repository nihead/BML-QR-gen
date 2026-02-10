package com.paymv.posterminal.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    
    private val _editableSettings = MutableStateFlow(settingsRepository.settings.value)
    val editableSettings: StateFlow<AppSettings> = _editableSettings.asStateFlow()
    
    init {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                _editableSettings.value = currentSettings
            }
        }
        // Check if webhook server is already running
        _uiState.update {
            it.copy(webhookServerRunning = paymentRepository.activeSource?.isActive == true 
                && settingsRepository.settings.value.paymentReceptionMode == PaymentReceptionMode.WEBHOOK)
        }
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
    
    fun updateAdminPassword(password: String) {
        _editableSettings.update { it.copy(adminPassword = password) }
    }
    
    fun updateProMode(enabled: Boolean) {
        _editableSettings.update { it.copy(proMode = enabled) }
    }
    
    fun updateStoreLogo(logoPath: String?) {
        _editableSettings.update { it.copy(storeLogo = logoPath) }
    }
    
    fun updatePaymentReceptionMode(mode: PaymentReceptionMode) {
        _editableSettings.update { it.copy(paymentReceptionMode = mode) }
    }
    
    fun updateWebhookPort(portStr: String) {
        val port = portStr.filter { it.isDigit() }.toIntOrNull() ?: return
        _editableSettings.update { it.copy(webhookPort = port) }
    }
    
    fun copyDeviceIdToClipboard(context: Context) {
        val deviceId = _editableSettings.value.deviceId
        if (deviceId.isEmpty()) return
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PayMV Device ID", deviceId)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(deviceIdCopied = true) }
    }
    
    fun clearDeviceIdCopied() {
        _uiState.update { it.copy(deviceIdCopied = false) }
    }
    
    fun toggleWebhookServer() {
        viewModelScope.launch {
            val currentPort = _editableSettings.value.webhookPort
            if (_uiState.value.webhookServerRunning) {
                // Stop the server
                paymentRepository.stopCurrentSource()
                _uiState.update { it.copy(webhookServerRunning = false, webhookError = null) }
            } else {
                // Start the server  
                try {
                    // First save the settings so port is persisted
                    val currentSettings = _editableSettings.value.copy(
                        paymentReceptionMode = PaymentReceptionMode.WEBHOOK
                    )
                    _editableSettings.update { currentSettings }
                    settingsRepository.saveSettings(currentSettings)
                    
                    val success = paymentRepository.startWebhookServer(currentPort)
                    if (success) {
                        _uiState.update { it.copy(webhookServerRunning = true, webhookError = null) }
                    } else {
                        _uiState.update { it.copy(webhookServerRunning = false, webhookError = "Failed to start server on port $currentPort") }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(webhookServerRunning = false, webhookError = e.message) }
                }
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
        
        // Validate webhook port if in webhook mode
        if (currentSettings.proMode && 
            currentSettings.paymentReceptionMode == PaymentReceptionMode.WEBHOOK &&
            !settingsRepository.validateWebhookPort(currentSettings.webhookPort)) {
            errors.add("Webhook port must be between 1024 and 65535")
        }
        
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }
        
        // Handle Firebase subscription changes
        if (currentSettings.proMode && 
            currentSettings.paymentReceptionMode == PaymentReceptionMode.FIREBASE) {
            viewModelScope.launch {
                settingsRepository.subscribeToDeviceTopic()
            }
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
    val deviceIdCopied: Boolean = false,
    val webhookServerRunning: Boolean = false,
    val webhookError: String? = null
)
