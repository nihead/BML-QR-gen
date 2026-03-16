package com.paymv.posterminal.ui.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.paymv.posterminal.ui.theme.Gray

/**
 * Banner ad unit ID for PayMV POS Terminal
 */
private const val BANNER_AD_UNIT_ID = "ca-app-pub-6779206163307389/4875285949"

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    text: String = "Ad Placeholder"
) {
    val context = LocalContext.current
    var adLoaded by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }
    
    // Initialize Mobile Ads SDK once when composable mounts
    LaunchedEffect(Unit) {
        // Configure test device for development (remove or make conditional for production)
        val testDeviceIds = listOf(
            "1766C22CCEBFA1140B94735F532465A3" // Add your test device ID here
        )
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)
        
        MobileAds.initialize(context) { initializationStatus ->
            Log.d("AdBanner", "AdMob SDK initialized: ${initializationStatus.adapterStatusMap}")
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        // Show loading placeholder until ad loads
        if (!adLoaded && !adFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Gray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading ad...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // AdMob Banner
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = BANNER_AD_UNIT_ID
                    
                    // Add listener for debugging and state management
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            adLoaded = true
                            adFailed = false
                            Log.d("AdBanner", "Ad loaded successfully")
                        }
                        
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)
                            adLoaded = false
                            adFailed = true
                            Log.e("AdBanner", "Ad failed to load: ${error.message} (code: ${error.code})")
                        }
                        
                        override fun onAdOpened() {
                            super.onAdOpened()
                            Log.d("AdBanner", "Ad opened")
                        }
                        
                        override fun onAdClosed() {
                            super.onAdClosed()
                            Log.d("AdBanner", "Ad closed")
                        }
                    }
                    
                    // Load the ad
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
        
        // Show error message if ad failed to load
        if (adFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Gray.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$text (Ad not available)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    // Clean up when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            Log.d("AdBanner", "Banner disposed")
        }
    }
}
