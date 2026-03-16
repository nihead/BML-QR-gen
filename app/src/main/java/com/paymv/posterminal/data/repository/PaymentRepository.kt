package com.paymv.posterminal.data.repository

import android.util.Log
import com.paymv.posterminal.data.api.PaymentApi
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentRequest
import com.paymv.posterminal.data.service.WebhookPaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PaymentRepository(
    private val api: PaymentApi,
    private val settingsProvider: (() -> AppSettings)? = null
) {
    
    companion object {
        private const val TAG = "PaymentRepository"
    }
    
    // The webhook server instance
    private var webhookServer: WebhookPaymentSource? = null
    
    // StateFlow to track webhook server status for UI updates
    private val _webhookServerStatus = MutableStateFlow(false)
    val webhookServerStatus: StateFlow<Boolean> = _webhookServerStatus.asStateFlow()
    
    /**
     * Check if webhook server is currently running.
     */
    val isWebhookServerRunning: Boolean 
        get() = webhookServer?.isActive == true
    
    /**
     * Start the webhook server on the specified port.
     */
    suspend fun startWebhookServer(port: Int = 4646): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Stop any existing server first
                if (webhookServer != null) {
                    Log.d(TAG, "Stopping existing webhook server before starting new one")
                    webhookServer?.stop()
                    webhookServer = null
                }
                
                Log.d(TAG, "Creating webhook server on port $port")
                val source = WebhookPaymentSource(port, settingsProvider)
                source.start()
                
                // Verify server is actually active
                if (source.isActive) {
                    webhookServer = source
                    _webhookServerStatus.value = true
                    Log.d(TAG, "Webhook server started successfully on port $port")
                    true
                } else {
                    _webhookServerStatus.value = false
                    Log.e(TAG, "Webhook server reported not active after start")
                    source.stop()
                    false
                }
            } catch (e: Exception) {
                _webhookServerStatus.value = false
                Log.e(TAG, "Failed to start webhook server: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Observe payments from the webhook server.
     * Returns null if webhook server is not running.
     */
    fun observePayments(): Flow<PaymentRequest?>? {
        val server = webhookServer
        return if (server?.isActive == true) {
            Log.d(TAG, "Providing webhook payment observations")
            server.observePayments()
        } else null
    }
    
    /**
     * Stop the webhook server.
     */
    suspend fun stopWebhookServer() {
        try {
            webhookServer?.stop()
            webhookServer = null
            _webhookServerStatus.value = false
            Log.d(TAG, "Webhook server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping webhook server: ${e.message}")
        }
    }
    
    /**
     * Consume the current webhook payment to prevent replay.
     */
    suspend fun consumePayment() {
        try {
            webhookServer?.consumePayment()
            Log.d(TAG, "Webhook payment consumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error consuming webhook payment: ${e.message}")
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
