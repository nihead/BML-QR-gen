package com.paymv.posterminal.data.service

import com.paymv.posterminal.data.api.PaymentApi
import com.paymv.posterminal.data.model.PaymentRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Original polling-based payment source.
 * Polls the backend API every 2 seconds for new payment requests.
 */
class PollingPaymentSource(
    private val api: PaymentApi
) : PaymentSource {
    
    private var _isActive = false
    override val isActive: Boolean get() = _isActive
    
    override fun observePayments(): Flow<PaymentRequest?> = flow {
        _isActive = true
        while (_isActive) {
            try {
                val response = api.getLatestPayment()
                if (response.isSuccessful && response.body() != null) {
                    emit(response.body())
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
            delay(2000)
        }
    }
    
    override suspend fun start() {
        _isActive = true
    }
    
    override suspend fun stop() {
        _isActive = false
    }
}
