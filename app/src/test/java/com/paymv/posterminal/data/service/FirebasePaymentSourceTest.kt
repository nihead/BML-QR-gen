package com.paymv.posterminal.data.service

import com.paymv.posterminal.data.model.PaymentRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class FirebasePaymentSourceTest {
    
    @Test
    fun `initially not active`() {
        val source = FirebasePaymentSource()
        assertFalse(source.isActive)
    }
    
    @Test
    fun `start makes source active`() = runBlocking {
        val source = FirebasePaymentSource()
        source.start()
        assertTrue(source.isActive)
    }
    
    @Test
    fun `stop makes source inactive`() = runBlocking {
        val source = FirebasePaymentSource()
        source.start()
        source.stop()
        assertFalse(source.isActive)
    }
    
    @Test
    fun `onPaymentReceived emits to flow`() = runBlocking {
        val source = FirebasePaymentSource()
        source.start()
        
        // Simulate receiving a payment via companion object
        val testPayment = PaymentRequest(
            amount = "42.00",
            timestamp = System.currentTimeMillis().toString()
        )
        FirebasePaymentSource.onPaymentReceived(testPayment)
        
        val payment = withTimeoutOrNull(2000) {
            source.observePayments().first()
        }
        
        assertNotNull(payment)
        assertEquals("42.00", payment?.amount)
    }
}
