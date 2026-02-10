package com.paymv.posterminal.data.repository

import android.util.Log
import com.paymv.posterminal.data.api.PaymentApi
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.data.model.PaymentRequest
import com.paymv.posterminal.data.service.FirebasePaymentSource
import com.paymv.posterminal.data.service.PaymentSource
import com.paymv.posterminal.data.service.PollingPaymentSource
import com.paymv.posterminal.data.service.WebhookPaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PaymentRepository(private val api: PaymentApi) {
    
    companion object {
        private const val TAG = "PaymentRepository"
    }
    
    private var currentSource: PaymentSource? = null
    private var currentMode: PaymentReceptionMode? = null
    
    /**
     * Get the currently active payment source, or null if none.
     */
    val activeSource: PaymentSource? get() = currentSource
    
    /**
     * Observe payments from the specified reception mode.
     * Automatically creates and starts the appropriate payment source.
     * Reuses an already-active source if the mode and source match.
     */
    fun observePayments(mode: PaymentReceptionMode, webhookPort: Int = 4646): Flow<PaymentRequest?> {
        // If mode hasn't changed and source is active, return existing flow
        if (mode == currentMode && currentSource?.isActive == true) {
            return currentSource!!.observePayments()
        }
        
        // For WEBHOOK mode, if server is already running on that port, reuse it
        if (mode == PaymentReceptionMode.WEBHOOK && currentSource is WebhookPaymentSource && currentSource?.isActive == true) {
            currentMode = mode
            return currentSource!!.observePayments()
        }
        
        // Create new source for the requested mode
        val source = when (mode) {
            PaymentReceptionMode.POLLING -> PollingPaymentSource(api)
            PaymentReceptionMode.FIREBASE -> FirebasePaymentSource()
            PaymentReceptionMode.WEBHOOK -> WebhookPaymentSource(webhookPort)
        }
        
        currentSource = source
        currentMode = mode
        Log.d(TAG, "Payment source switched to: $mode")
        
        return source.observePayments()
    }
    
    /**
     * Start the webhook server (only needed for WEBHOOK mode).
     */
    suspend fun startWebhookServer(port: Int = 4646): Boolean {
        return try {
            val source = WebhookPaymentSource(port)
            source.start()
            currentSource = source
            currentMode = PaymentReceptionMode.WEBHOOK
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start webhook server: ${e.message}")
            false
        }
    }
    
    /**
     * Stop the current payment source.
     */
    suspend fun stopCurrentSource() {
        try {
            currentSource?.stop()
            currentSource = null
            currentMode = null
            Log.d(TAG, "Payment source stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping payment source: ${e.message}")
        }
    }
    
    /**
     * Legacy polling method (kept for backward compatibility).
     */
    fun pollForPayments(): Flow<PaymentRequest?> {
        return observePayments(PaymentReceptionMode.POLLING)
    }
    
    suspend fun clearPayment(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.clearPayment()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun sendTestPayment(amount: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendTestPayment(mapOf("amount" to amount))
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
}
