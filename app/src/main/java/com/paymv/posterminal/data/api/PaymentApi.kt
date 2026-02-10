package com.paymv.posterminal.data.api

import com.paymv.posterminal.data.model.PaymentRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PaymentApi {
    
    @GET("api/payment/latest")
    suspend fun getLatestPayment(): Response<PaymentRequest>
    
    @POST("api/payment/clear")
    suspend fun clearPayment(): Response<Unit>
    
    @POST("api/pay")
    suspend fun sendTestPayment(@Body payment: Map<String, String>): Response<Unit>
}
