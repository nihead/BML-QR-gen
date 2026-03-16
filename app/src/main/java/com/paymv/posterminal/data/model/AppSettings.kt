package com.paymv.posterminal.data.model

data class AppSettings(
    val storeName: String = "PayMV Terminal",
    val storeLogo: String? = null, // File path or base64
    val accountName: String = "",
    val accountNumber: String = "", // 13 digits
    val mobileNumber: String? = null, // +960XXXXXXX
    val adminPassword: String = "", // Empty = no password protection
    val proMode: Boolean = false,
    val showManualQrInput: Boolean = false, // Show manual amount input on idle screen
    val hideAds: Boolean = false, // Hide advertisement banners
    // Browser settings
    val browserEnabled: Boolean = false, // Replace idle screen with browser
    val browserUrl: String = "", // URL to display in browser
    val browserAutoReload: Boolean = false, // Auto reload browser page periodically
    // Webhook server settings
    val webhookPort: Int = 4646      // Local webhook server port
)
