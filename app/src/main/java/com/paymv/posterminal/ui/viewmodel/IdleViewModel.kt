package com.paymv.posterminal.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paymv.posterminal.data.model.AppSettings
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
    
    // Expose webhook server status for UI display
    val webhookServerStatus: StateFlow<Boolean> = paymentRepository.webhookServerStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    private var paymentListenerJob: Job? = null
    private var qrScreenActive = false
    
    init {
        // Auto-start webhook server on app launch
        viewModelScope.launch {
            val port = settingsRepository.settings.value.webhookPort
            Log.d(TAG, "Auto-starting webhook server on port $port")
            val success = paymentRepository.startWebhookServer(port)
            if (!success) {
                _uiState.update { 
                    it.copy(webhookError = "Failed to start server on port $port") 
                }
            } else {
                // Start collecting payments once server is up
                collectPayments()
            }
        }
        
        // Re-collect payments when webhook server status changes
        viewModelScope.launch {
            paymentRepository.webhookServerStatus.collect { isActive ->
                Log.d(TAG, "Webhook server status changed: $isActive")
                if (isActive && paymentListenerJob?.isActive != true) {
                    collectPayments()
                }
            }
        }
    }
    
    private fun collectPayments() {
        paymentListenerJob?.cancel()
        paymentListenerJob = viewModelScope.launch {
            val paymentFlow = paymentRepository.observePayments()
            if (paymentFlow == null) {
                Log.w(TAG, "No payment flow available - webhook server not running")
                return@launch
            }
            
            paymentFlow.collect { payment ->
                if (payment != null) {
                    // Skip completion-only signals (no amount, just completed flag)
                    if (payment.completed == true && payment.amount.isEmpty()) {
                        Log.d(TAG, "Skipping completion-only signal (no amount to display)")
                        return@collect
                    }
                    // Process any payment with an amount
                    if (payment.amount.isNotEmpty() && !qrScreenActive) {
                        Log.d(TAG, "Payment received: ${payment.amount} - triggering QR display")
                        _uiState.update { it.copy(pendingPayment = payment) }
                    }
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
            paymentRepository.stopWebhookServer()
            Log.d(TAG, "IdleViewModel cleared - webhook server stopped")
        }
    }
}

data class IdleUiState(
    val pendingPayment: PaymentRequest? = null,
    val showTestMessage: Boolean = false,
    val webhookError: String? = null,
    val manualAmount: String = "",
    val amountError: String? = null
)
