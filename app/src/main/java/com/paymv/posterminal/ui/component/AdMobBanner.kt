package com.paymv.posterminal.ui.component

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds

/**
 * AdMob Banner Ad component for Jetpack Compose.
 * 
 * @param adUnitId The AdMob ad unit ID for this banner
 * @param modifier Optional modifier for the banner container
 */
@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize Mobile Ads SDK (safe to call multiple times)
    remember {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d("AdMobBanner", "AdMob SDK initialized: ${initializationStatus.adapterStatusMap}")
        }
    }
    
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                
                // Add listener for debugging
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        Log.d("AdMobBanner", "Ad loaded successfully")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        super.onAdFailedToLoad(error)
                        Log.e("AdMobBanner", "Ad failed to load: ${error.message} (code: ${error.code})")
                    }
                    
                    override fun onAdOpened() {
                        super.onAdOpened()
                        Log.d("AdMobBanner", "Ad opened")
                    }
                    
                    override fun onAdClosed() {
                        super.onAdClosed()
                        Log.d("AdMobBanner", "Ad closed")
                    }
                }
                
                // Load the ad
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            // Refresh ad when composition updates
            adView.loadAd(AdRequest.Builder().build())
        }
    )
    
    // Clean up when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            Log.d("AdMobBanner", "Banner disposed")
        }
    }
}
