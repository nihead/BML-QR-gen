package com.paymv.posterminal.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing subscription state.
 * Provides a clean interface for ViewModels to interact with billing.
 */
class SubscriptionRepository private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val billingManager = BillingManager(context.applicationContext, scope)

    /**
     * Flow indicating whether the user has an active subscription
     */
    val isSubscribed: StateFlow<Boolean> = billingManager.isSubscribed

    /**
     * Flow indicating the current billing connection state
     */
    val billingState: StateFlow<BillingState> = billingManager.billingState

    /**
     * Check if product details are available (subscription configured in Play Console)
     */
    val isProductAvailable: Boolean
        get() = billingManager.productDetails.value != null

    /**
     * Get the formatted subscription price (e.g., "$1.00/month")
     */
    fun getFormattedPrice(): String {
        return billingManager.getFormattedPrice() ?: "$1.00/month"
    }

    /**
     * Launch the subscription purchase flow
     * 
     * @param activity The activity to use for the purchase flow
     * @return true if the flow was launched successfully
     */
    fun purchaseSubscription(activity: Activity): Boolean {
        return billingManager.launchPurchaseFlow(activity)
    }

    /**
     * Refresh subscription status from Google Play
     */
    suspend fun refreshSubscriptionStatus() {
        billingManager.queryPurchases()
    }

    /**
     * Reconnect to billing service if disconnected
     */
    fun reconnect() {
        billingManager.startConnection()
    }

    companion object {
        @Volatile
        private var instance: SubscriptionRepository? = null

        /**
         * Get singleton instance of SubscriptionRepository
         */
        fun getInstance(context: Context): SubscriptionRepository {
            return instance ?: synchronized(this) {
                instance ?: SubscriptionRepository(context).also { instance = it }
            }
        }
    }
}
