package com.paymv.posterminal.data.model

data class PaymentRequest(
    val amount: String, // Format: "250.00"
    val timestamp: String,
    val completed: Boolean? = null // True when payment transaction is completed
)
