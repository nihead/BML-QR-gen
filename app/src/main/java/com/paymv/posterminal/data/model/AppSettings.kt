package com.paymv.posterminal.data.model

enum class PaymentReceptionMode {
    POLLING,   // HTTP polling every 2 seconds (default)
    FIREBASE,  // Firebase Cloud Messaging push notifications
    WEBHOOK    // Local HTTP webhook server
}

data class AppSettings(
    val storeName: String = "PayMV Terminal",
    val storeLogo: String? = null, // File path or base64
    val accountName: String = "",
    val accountNumber: String = "", // 13 digits
    val mobileNumber: String? = null, // +960XXXXXXX
    val adminPassword: String = "", // Empty = no password protection
    val proMode: Boolean = false,
    // Payment reception settings (Pro mode only)
    val paymentReceptionMode: PaymentReceptionMode = PaymentReceptionMode.POLLING,
    val deviceId: String = "",       // Firebase Installation ID (auto-generated)
    val fcmToken: String = "",       // FCM registration token
    val webhookPort: Int = 4646      // Local webhook server port
)
