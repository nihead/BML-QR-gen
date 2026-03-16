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

class BrowserViewModel(
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    companion object {
        private const val TAG = "BrowserViewModel"
    }
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
    
    // Expose webhook server status for consistency
    val webhookServerStatus: StateFlow<Boolean> = paymentRepository.webhookServerStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    private var paymentListenerJob: Job? = null
    private var qrScreenActive = false
    
    init {
        // Auto-start webhook server on init (if not already running)
        viewModelScope.launch {
            if (!paymentRepository.isWebhookServerRunning) {
                val port = settingsRepository.settings.value.webhookPort
                Log.d(TAG, "Auto-starting webhook server on port $port")
                val success = paymentRepository.startWebhookServer(port)
                if (success) {
                    collectPayments()
                } else {
                    Log.e(TAG, "Failed to start webhook server on port $port")
                }
            } else {
                // Server already running, just start collecting
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
    
    fun onPageStartLoading() {
        _uiState.update { it.copy(isLoading = true, hasError = false) }
    }
    
    fun onPageFinishLoading() {
        _uiState.update { it.copy(isLoading = false, hasError = false) }
    }
    
    fun onPageError(error: String) {
        _uiState.update { it.copy(isLoading = false, hasError = true, errorMessage = error) }
    }
    
    override fun onCleared() {
        super.onCleared()
        paymentListenerJob?.cancel()
        // Don't stop webhook server here - let IdleViewModel manage it
        Log.d(TAG, "BrowserViewModel cleared")
    }
}

data class BrowserUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val pendingPayment: PaymentRequest? = null
)
