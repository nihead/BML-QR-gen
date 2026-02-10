package com.paymv.posterminal.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
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
    
    private val _timeRemaining = MutableStateFlow(60)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()
    
    private val _shouldNavigateBack = MutableStateFlow(false)
    val shouldNavigateBack: StateFlow<Boolean> = _shouldNavigateBack.asStateFlow()
    
    init {
        generateQRCode()
        startCountdown()
        clearPaymentFromBackend()
    }
    
    private fun generateQRCode() {
        viewModelScope.launch {
            val currentSettings = settings.value
            
            if (!PayMVQrGenerator.canGenerateQR(currentSettings)) {
                _uiState.update { 
                    it.copy(
                        error = "Please configure account settings first",
                        isLoading = false
                    ) 
                }
                return@launch
            }
            
            try {
                val qrString = PayMVQrGenerator.generate(
                    amount = amount,
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
    }
    
    private fun startCountdown() {
        viewModelScope.launch {
            while (_timeRemaining.value > 0) {
                delay(1000)
                _timeRemaining.update { it - 1 }
            }
            _shouldNavigateBack.value = true
        }
    }
    
    private fun clearPaymentFromBackend() {
        viewModelScope.launch {
            paymentRepository.clearPayment()
        }
    }
    
    fun onBackPressed() {
        _shouldNavigateBack.value = true
    }
}

data class QrDisplayUiState(
    val qrBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
