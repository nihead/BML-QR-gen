package com.paymv.posterminal.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Google Play Billing for subscriptions.
 * 
 * Product ID to create in Play Console: "hide_ads_monthly"
 * Base Plan: Monthly subscription at $1/month
 */
class BillingManager(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingManager"
        
        // Product ID - must match exactly what you create in Play Console
        const val PRODUCT_ID_HIDE_ADS = "hide_ads_monthly"
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Disconnected)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    init {
        startConnection()
    }

    /**
     * Start connection to Google Play Billing service
     */
    fun startConnection() {
        if (billingClient.isReady) {
            Log.d(TAG, "BillingClient already connected")
            return
        }
        
        Log.d(TAG, "Starting BillingClient connection...")
        _billingState.value = BillingState.Connecting
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "BillingClient connected successfully")
            _billingState.value = BillingState.Connected
            
            // Query existing purchases and product details
            scope.launch {
                queryPurchases()
                queryProductDetails()
            }
        } else {
            Log.e(TAG, "BillingClient connection failed: ${billingResult.debugMessage}")
            _billingState.value = BillingState.Error(billingResult.debugMessage)
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "BillingClient disconnected")
        _billingState.value = BillingState.Disconnected
        // Try to reconnect
        startConnection()
    }

    /**
     * Query existing purchases to check subscription status
     */
    suspend fun queryPurchases() {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient not ready, cannot query purchases")
            return
        }

        withContext(Dispatchers.IO) {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val result = billingClient.queryPurchasesAsync(params)
            
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasValidSubscription = result.purchasesList.any { purchase ->
                    purchase.products.contains(PRODUCT_ID_HIDE_ADS) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                
                Log.d(TAG, "Subscription status: $hasValidSubscription")
                _isSubscribed.value = hasValidSubscription
                
                // Acknowledge any unacknowledged purchases
                result.purchasesList.forEach { purchase ->
                    if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                    }
                }
            } else {
                Log.e(TAG, "Failed to query purchases: ${result.billingResult.debugMessage}")
            }
        }
    }

    /**
     * Query product details for the subscription
     */
    private suspend fun queryProductDetails() {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient not ready, cannot query products")
            return
        }

        withContext(Dispatchers.IO) {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_HIDE_ADS)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val result = billingClient.queryProductDetails(params)
            
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = result.productDetailsList?.firstOrNull()
                _productDetails.value = details
                
                if (details != null) {
                    Log.d(TAG, "Product details loaded: ${details.name}")
                } else {
                    Log.w(TAG, "No product details found for $PRODUCT_ID_HIDE_ADS. " +
                            "Make sure the subscription is created and active in Play Console.")
                }
            } else {
                Log.e(TAG, "Failed to query product details: ${result.billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launch the purchase flow for the subscription
     */
    fun launchPurchaseFlow(activity: Activity): Boolean {
        val details = _productDetails.value
        if (details == null) {
            Log.e(TAG, "Cannot launch purchase: product details not available")
            return false
        }

        // Get the first offer (base plan)
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "Cannot launch purchase: no offer token available")
            return false
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /**
     * Called when a purchase is updated (completed, canceled, etc.)
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(TAG, "Purchase successful")
                purchases?.forEach { purchase ->
                    scope.launch {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Purchase canceled by user")
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Handle a successful purchase
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.products.contains(PRODUCT_ID_HIDE_ADS)) {
                _isSubscribed.value = true
            }
            
            // Acknowledge the purchase if not already acknowledged
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }
    }

    /**
     * Acknowledge a purchase (required for subscriptions)
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        withContext(Dispatchers.IO) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            val result = billingClient.acknowledgePurchase(params)
            
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
            }
        }
    }

    /**
     * Get formatted price string for display
     */
    fun getFormattedPrice(): String? {
        return _productDetails.value?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice
    }

    /**
     * End the billing connection
     */
    fun endConnection() {
        Log.d(TAG, "Ending BillingClient connection")
        billingClient.endConnection()
    }
}

/**
 * Represents the current state of the billing connection
 */
sealed class BillingState {
    data object Disconnected : BillingState()
    data object Connecting : BillingState()
    data object Connected : BillingState()
    data class Error(val message: String) : BillingState()
}
