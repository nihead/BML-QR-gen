package com.paymv.posterminal.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.data.model.PaymentRequest
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import com.paymv.posterminal.util.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class IdleViewModel(
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    companion object {
        private const val TAG = "IdleViewModel"
    }
    
    private val _uiState = MutableStateFlow(IdleUiState())
    val uiState: StateFlow<IdleUiState> = _uiState.asStateFlow()
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    private var paymentListenerJob: Job? = null
    private var lastMode: PaymentReceptionMode? = null
    private var qrScreenActive = false
    
    init {
        // Initialize device ID on first launch
        viewModelScope.launch {
            settingsRepository.initializeDeviceId()
            settingsRepository.refreshFcmToken()
        }
        
        // Observe settings changes to switch payment source
        viewModelScope.launch {
            settings.collect { currentSettings ->
                val mode = if (currentSettings.proMode) {
                    currentSettings.paymentReceptionMode
                } else {
                    PaymentReceptionMode.POLLING // Non-pro always uses polling
                }
                
                // Start/restart if mode changed or if no active listener
                if (mode != lastMode || paymentListenerJob?.isActive != true) {
                    startPaymentSource(mode, currentSettings.webhookPort)
                }
            }
        }
    }
    
    private fun startPaymentSource(mode: PaymentReceptionMode, webhookPort: Int) {
        // Cancel existing listener
        paymentListenerJob?.cancel()
        
        lastMode = mode
        Log.d(TAG, "Starting payment source: $mode")
        
        _uiState.update { it.copy(activeMode = mode, webhookError = null) }
        
        // If webhook, check if server is already running (started from Settings)
        if (mode == PaymentReceptionMode.WEBHOOK) {
            if (paymentRepository.activeSource?.isActive == true) {
                // Server already running, just collect from it
                collectPayments(mode, webhookPort)
            } else {
                viewModelScope.launch {
                    // Stop any previous non-webhook source first
                    paymentRepository.stopCurrentSource()
                    
                    val success = paymentRepository.startWebhookServer(webhookPort)
                    if (!success) {
                        _uiState.update { 
                            it.copy(webhookError = "Failed to start server on port $webhookPort") 
                        }
                        return@launch
                    }
                    collectPayments(mode, webhookPort)
                }
            }
        } else {
            // Stop previous source before starting new one
            viewModelScope.launch {
                paymentRepository.stopCurrentSource()
            }
            
            // Firebase subscription
            if (mode == PaymentReceptionMode.FIREBASE) {
                viewModelScope.launch {
                    settingsRepository.subscribeToDeviceTopic()
                }
            }
            collectPayments(mode, webhookPort)
        }
    }
    
    private fun collectPayments(mode: PaymentReceptionMode, webhookPort: Int) {
        paymentListenerJob = viewModelScope.launch {
            // Prefer manual webhook server if running, otherwise use configured mode
            val paymentFlow = paymentRepository.observeWebhookPayments()
                ?: paymentRepository.observePayments(mode, webhookPort)
            
            paymentFlow.collect { payment ->
                    if (payment != null && !qrScreenActive) {
                        _uiState.update { it.copy(pendingPayment = payment) }
                    }
                }
        }
    }
    
    fun onPaymentNavigated() {
        qrScreenActive = true
        _uiState.update { it.copy(pendingPayment = null) }
    }
    
    fun onReturnedFromQr() {
        qrScreenActive = false
    }
    
    fun updateManualAmount(amount: String) {
        // Clear error when user types
        _uiState.update { it.copy(manualAmount = amount, amountError = null) }
    }
    
    fun generateManualQR() {
        val amount = _uiState.value.manualAmount.trim()
        
        // Validate not empty
        if (amount.isEmpty()) {
            _uiState.update { it.copy(amountError = "Amount is required") }
            return
        }
        
        // Validate format (digits with optional decimal)
        val decimalRegex = Regex("^\\d+(\\.\\d{1,2})?$")
        if (!decimalRegex.matches(amount)) {
            _uiState.update { it.copy(amountError = "Invalid format (e.g., 25.00)") }
            return
        }
        
        // Validate range
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue < 0.01 || amountValue > 1000000.0) {
            _uiState.update { it.copy(amountError = "Amount must be between 0.01 and 1,000,000") }
            return
        }
        
        // Format to 2 decimal places
        val formattedAmount = String.format("%.2f", amountValue)
        
        // Clear input and generate QR
        _uiState.update { 
            it.copy(
                pendingPayment = PaymentRequest(amount = formattedAmount, timestamp = System.currentTimeMillis().toString()),
                manualAmount = "",
                amountError = null
            ) 
        }
    }
    
    fun generateTestQR() {
        val amount = String.format("%.2f", Random.nextDouble(10.0, 500.0))
        _uiState.update { it.copy(pendingPayment = PaymentRequest(amount = amount, timestamp = System.currentTimeMillis().toString())) }
    }
    
    fun clearTestMessage() {
        _uiState.update { it.copy(showTestMessage = false) }
    }
    
    override fun onCleared() {
        super.onCleared()
        paymentListenerJob?.cancel()
        viewModelScope.launch {
            paymentRepository.stopCurrentSource()
        }
    }
}

data class IdleUiState(
    val pendingPayment: PaymentRequest? = null,
    val showTestMessage: Boolean = false,
    val activeMode: PaymentReceptionMode = PaymentReceptionMode.POLLING,
    val webhookError: String? = null,
    val manualAmount: String = "",
    val amountError: String? = null
)
