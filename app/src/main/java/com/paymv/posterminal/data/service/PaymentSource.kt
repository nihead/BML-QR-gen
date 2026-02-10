package com.paymv.posterminal.data.service

import com.paymv.posterminal.data.model.PaymentRequest
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for different payment reception sources.
 * Each source emits PaymentRequest when a payment is received.
 */
interface PaymentSource {
    /** Flow of incoming payment requests. Emits null when no payment is available. */
    fun observePayments(): Flow<PaymentRequest?>
    
    /** Start the payment source (e.g., start server, subscribe to topic) */
    suspend fun start()
    
    /** Stop the payment source (e.g., stop server, unsubscribe) */
    suspend fun stop()
    
    /** Whether this source is currently active */
    val isActive: Boolean
}
