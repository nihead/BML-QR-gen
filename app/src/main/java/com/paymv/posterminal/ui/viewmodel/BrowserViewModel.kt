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
    
    private var paymentListenerJob: Job? = null
    private var lastMode: PaymentReceptionMode? = null
    private var qrScreenActive = false
    
    init {
        // Initialize device ID / FCM token
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
                    PaymentReceptionMode.POLLING
                }
                
                if (mode != lastMode || paymentListenerJob?.isActive != true) {
                    startPaymentSource(mode, currentSettings.webhookPort)
                }
            }
        }
    }
    
    private fun startPaymentSource(mode: PaymentReceptionMode, webhookPort: Int) {
        paymentListenerJob?.cancel()
        lastMode = mode
        Log.d(TAG, "Starting payment source: $mode")
        
        if (mode == PaymentReceptionMode.WEBHOOK) {
            if (paymentRepository.activeSource?.isActive == true) {
                collectPayments(mode, webhookPort)
            } else {
                viewModelScope.launch {
                    paymentRepository.stopCurrentSource()
                    val success = paymentRepository.startWebhookServer(webhookPort)
                    if (!success) {
                        Log.e(TAG, "Failed to start webhook server on port $webhookPort")
                        return@launch
                    }
                    collectPayments(mode, webhookPort)
                }
            }
        } else {
            viewModelScope.launch { paymentRepository.stopCurrentSource() }
            if (mode == PaymentReceptionMode.FIREBASE) {
                viewModelScope.launch { settingsRepository.subscribeToDeviceTopic() }
            }
            collectPayments(mode, webhookPort)
        }
    }
    
    private fun collectPayments(mode: PaymentReceptionMode, webhookPort: Int) {
        paymentListenerJob = viewModelScope.launch {
            paymentRepository.observePayments(mode, webhookPort)
                .collect { payment ->
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
        viewModelScope.launch {
            paymentRepository.stopCurrentSource()
        }
    }
}

data class BrowserUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val pendingPayment: PaymentRequest? = null
)
