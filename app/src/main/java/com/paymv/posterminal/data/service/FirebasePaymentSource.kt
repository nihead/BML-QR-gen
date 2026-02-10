package com.paymv.posterminal.data.service

import android.util.Log
import com.paymv.posterminal.data.model.PaymentRequest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Firebase Cloud Messaging payment source.
 * Receives payment requests via FCM push notifications.
 * 
 * The FCM service (PayMVFirebaseMessagingService) posts payments
 * to the shared [incomingPayments] flow that this source observes.
 */
class FirebasePaymentSource : PaymentSource {
    
    companion object {
        private const val TAG = "FirebasePaymentSource"
        
        /**
         * Shared flow for receiving payments from the FCM service.
         * The FirebaseMessagingService posts to this flow,
         * and the source observes it.
         */
        private val _incomingPayments = MutableSharedFlow<PaymentRequest>(
            replay = 1,
            extraBufferCapacity = 5,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        
        /**
         * Called by PayMVFirebaseMessagingService when a payment message is received.
         */
        fun onPaymentReceived(payment: PaymentRequest) {
            Log.d(TAG, "Payment received via FCM: amount=${payment.amount}")
            _incomingPayments.tryEmit(payment)
        }
    }
    
    private var _isActive = false
    override val isActive: Boolean get() = _isActive
    
    override fun observePayments(): Flow<PaymentRequest?> {
        return _incomingPayments.asSharedFlow()
    }
    
    override suspend fun start() {
        _isActive = true
        Log.d(TAG, "Firebase payment source started")
    }
    
    override suspend fun stop() {
        _isActive = false
        Log.d(TAG, "Firebase payment source stopped")
    }
}
