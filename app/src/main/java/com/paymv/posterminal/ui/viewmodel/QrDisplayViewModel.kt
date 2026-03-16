package com.paymv.posterminal.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.repository.PaymentRepository
import com.paymv.posterminal.data.repository.SettingsRepository
import com.paymv.posterminal.util.PayMVQrGenerator
import com.paymv.posterminal.util.QRCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class QrDisplayViewModel(
    application: Application,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val amount: String
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(QrDisplayUiState())
    val uiState: StateFlow<QrDisplayUiState> = _uiState.asStateFlow()
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    
    private val _currentAmount = MutableStateFlow(amount)
    val currentAmount: StateFlow<String> = _currentAmount.asStateFlow()
    
    private val _timeRemaining = MutableStateFlow(60)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()
    
    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private var countdownJob: kotlinx.coroutines.Job? = null
    private var paymentListenerJob: kotlinx.coroutines.Job? = null
    private var updateJob: kotlinx.coroutines.Job? = null
    private var lastProcessedAmount: String? = null
    private var isNavigatingAway = false
    
    init {
        lastProcessedAmount = amount
        // Track initial display under updateJob so it can be cancelled by incoming payment
        updateJob = viewModelScope.launch {
            generateQRCode()
            startCountdown()
        }
        listenForNewPayments()
    }
    
    private fun listenForNewPayments() {
        paymentListenerJob?.cancel()
        paymentListenerJob = viewModelScope.launch {
            // Observe payments from webhook server
            val paymentFlow = paymentRepository.observePayments()
            if (paymentFlow == null) {
                Log.d("QrDisplayViewModel", "No payment flow available")
                return@launch
            }
            
            paymentFlow.collect { payment ->
                // If countdown already expired and navigating away, ignore new payments
                if (isNavigatingAway) {
                    Log.d("QrDisplayViewModel", "Ignoring payment - already navigating away")
                    return@collect
                }
                // Ignore null payments
                if (payment == null) {
                    Log.d("QrDisplayViewModel", "Ignoring null payment")
                    return@collect
                }
                Log.d("QrDisplayViewModel", "Payment received: amount=${payment.amount}, completed=${payment.completed}")
                // Check if this is a completion notification FIRST (before amount check)
                if (payment.completed == true) {
                    // Payment is completed, navigate back
                    Log.d("QrDisplayViewModel", "Payment completed! Setting shouldNavigateBack=true")
                    isNavigatingAway = true
                    viewModelScope.launch {
                        paymentRepository.consumePayment()
                    }
                    paymentListenerJob?.cancel()
                    countdownJob?.cancel()
                    updateJob?.cancel()
                    _shouldNavigateBack.value = true
                } else if (!payment.amount.isNullOrBlank() && payment.amount != lastProcessedAmount) {
                    // Only process if it has an amount and is a genuinely new payment
                    Log.d("QrDisplayViewModel", "New payment amount detected: ${payment.amount}")
                    lastProcessedAmount = payment.amount
                    viewModelScope.launch {
                        // Consume the payment to prevent replay when screen is recreated
                        paymentRepository.consumePayment()
                    }
                    updatePaymentDisplay(payment.amount)
                }
            }
        }
    }
    
    private fun updatePaymentDisplay(newAmount: String) {
        // If countdown already expired, don't restart it
        if (isNavigatingAway) {
            Log.d("QrDisplayViewModel", "Not updating payment display - already navigating away")
            return
        }
        // Cancel any in-flight update to prevent double display
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _isUpdating.value = true
            _currentAmount.value = newAmount
            
            // Cancel existing countdown
            countdownJob?.cancel()
            _timeRemaining.value = 60
            
            // Fade out - wait a bit
            delay(300)
            
            // Regenerate QR with new amount
            generateQRCode()
            
            // Fade in
            delay(300)
            _isUpdating.value = false
            
            // Start new countdown
            startCountdown()
        }
    }
    
    private suspend fun generateQRCode() {
        val currentSettings = settings.value
        val amt = _currentAmount.value
        
        if (!PayMVQrGenerator.canGenerateQR(currentSettings)) {
            _uiState.update { 
                it.copy(
                    error = "Please configure account settings first",
                    isLoading = false
                ) 
            }
            return
        }
        
        try {
            val qrString = PayMVQrGenerator.generate(
                amount = amt,
                settings = currentSettings,
                transactionRef = "POS${System.currentTimeMillis().toString().takeLast(6)}"
            )
            
            val bitmap = QRCodeGenerator.generateQRCodeBitmap(qrString, 512)
            
            _uiState.update { 
                it.copy(
                    qrBitmap = bitmap,
                    isLoading = false,
                    error = if (bitmap == null) "Failed to generate QR code" else null
                ) 
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    error = "Error: ${e.message}",
                    isLoading = false
                ) 
            }
        }
    }
    
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000)
                _timeRemaining.update { it - 1 }
            }
            // Timer expired - set flag first to prevent new payments from restarting it
            isNavigatingAway = true
            // Clean up before navigating back (same as onBackPressed)
            paymentListenerJob?.cancel()
            updateJob?.cancel()
            _shouldNavigateBack.value = true
        }
    }
    
    fun onBackPressed() {
        paymentListenerJob?.cancel()
        countdownJob?.cancel()
        updateJob?.cancel()
        _shouldNavigateBack.value = true
    }
    
    fun resetNavigationFlag() {
        _shouldNavigateBack.value = false
        isNavigatingAway = false
    }
}

data class QrDisplayUiState(
    val qrBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
