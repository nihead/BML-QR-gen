package com.paymv.posterminal.data.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.paymv.posterminal.data.model.PaymentRequest
import java.time.Instant

/**
 * Firebase Cloud Messaging service that receives payment push notifications.
 * 
 * Each device subscribes to a topic based on its Firebase Installation ID:
 *   device_{installationId}
 * 
 * The backend sends payment requests to this topic.
 * 
 * Expected FCM data payload:
 * {
 *   "amount": "250.00",
 *   "timestamp": "2026-02-10T10:30:00Z"
 * }
 */
class PayMVFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "PayMVFCMService"
        
        /** Callback to save new FCM tokens to settings */
        var onTokenRefreshed: ((String) -> Unit)? = null
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from: ${message.from}")
        
        val data = message.data
        if (data.isEmpty()) {
            Log.w(TAG, "FCM message has no data payload")
            return
        }
        
        val amount = data["amount"]
        val timestamp = data["timestamp"] ?: Instant.now().toString()
        
        if (amount.isNullOrEmpty()) {
            Log.w(TAG, "FCM message missing 'amount' field")
            return
        }
        
        Log.d(TAG, "Payment received via FCM: amount=$amount, timestamp=$timestamp")
        
        val payment = PaymentRequest(
            amount = amount,
            timestamp = timestamp
        )
        
        // Post to shared flow that FirebasePaymentSource observes
        FirebasePaymentSource.onPaymentReceived(payment)
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")
        onTokenRefreshed?.invoke(token)
    }
}
