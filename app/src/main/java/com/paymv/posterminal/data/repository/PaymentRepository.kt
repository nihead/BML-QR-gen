package com.paymv.posterminal.data.repository

import android.util.Log
import com.paymv.posterminal.data.api.PaymentApi
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentReceptionMode
import com.paymv.posterminal.data.model.PaymentRequest
import com.paymv.posterminal.data.service.FirebasePaymentSource
import com.paymv.posterminal.data.service.PaymentSource
import com.paymv.posterminal.data.service.PollingPaymentSource
import com.paymv.posterminal.data.service.WebhookPaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PaymentRepository(
    private val api: PaymentApi,
    private val settingsProvider: (() -> AppSettings)? = null
) {
    
    companion object {
        private const val TAG = "PaymentRepository"
    }
    
    private var currentSource: PaymentSource? = null
    private var currentMode: PaymentReceptionMode? = null
    
    // Separate tracking for manually-started webhook server from Settings
    private var manualWebhookServer: WebhookPaymentSource? = null
    
    /**
     * Get the currently active payment source, or null if none.
     */
    val activeSource: PaymentSource? get() = currentSource ?: manualWebhookServer
    
    /**
     * Check if a webhook server is currently running (manual or active).
     */
    val isWebhookServerRunning: Boolean 
        get() = manualWebhookServer?.isActive == true || 
                (currentSource is WebhookPaymentSource && currentSource?.isActive == true)
    
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
        
        // For WEBHOOK mode, if manual server is already running, reuse it
        if (mode == PaymentReceptionMode.WEBHOOK && manualWebhookServer?.isActive == true) {
            // Use manual server as the current source but keep tracking it
            currentSource = manualWebhookServer
            currentMode = mode
            Log.d(TAG, "Reusing manual webhook server as active payment source")
            return currentSource!!.observePayments()
        }
        
        // For WEBHOOK mode, if server is already running on that port, reuse it
        if (mode == PaymentReceptionMode.WEBHOOK && currentSource is WebhookPaymentSource && currentSource?.isActive == true) {
            currentMode = mode
            Log.d(TAG, "Reusing existing webhook server for active payment source")
            return currentSource!!.observePayments()
        }
        
        // Create new source for the requested mode
        val source = when (mode) {
            PaymentReceptionMode.POLLING -> PollingPaymentSource(api)
            PaymentReceptionMode.FIREBASE -> FirebasePaymentSource()
            PaymentReceptionMode.WEBHOOK -> WebhookPaymentSource(webhookPort, settingsProvider)
        }
        
        currentSource = source
        currentMode = mode
        Log.d(TAG, "Payment source switched to: $mode")
        
        return source.observePayments()
    }
    
    /**
     * Start the webhook server (only needed for WEBHOOK mode).
     * Starts as a manual server that won't be stopped by payment source changes.
     */
    suspend fun startWebhookServer(port: Int = 4646): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Stop any existing manual webhook server first
                if (manualWebhookServer != null) {
                    Log.d(TAG, "Stopping existing manual webhook server before starting new one")
                    manualWebhookServer?.stop()
                    manualWebhookServer = null
                }
                
                Log.d(TAG, "Creating manual webhook server on port $port")
                val source = WebhookPaymentSource(port, settingsProvider)
                source.start()
                
                // Verify server is actually active
                if (source.isActive) {
                    manualWebhookServer = source
                    Log.d(TAG, "Manual webhook server started successfully on port $port")
                    true
                } else {
                    Log.e(TAG, "Manual webhook server reported not active after start")
                    source.stop()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start manual webhook server: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Observe payments from the manual webhook server.
     * Returns null flow if no manual webhook server is running.
     */
    fun observeWebhookPayments(): Flow<PaymentRequest?>? {
        val server = manualWebhookServer
        return if (server?.isActive == true) {
            Log.d(TAG, "Providing webhook payment observations from manual server")
            server.observePayments()
        } else null
    }
    
    /**
     * Stop only the webhook server (if it's running).
     */
    suspend fun stopWebhookServer() {
        try {
            // If currentSource is the same as manualWebhookServer, clear both
            if (currentSource === manualWebhookServer) {
                currentSource = null
                currentMode = null
            }
            manualWebhookServer?.stop()
            manualWebhookServer = null
            Log.d(TAG, "Manual webhook server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping manual webhook server: ${e.message}")
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
