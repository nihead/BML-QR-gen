package com.paymv.posterminal.data.model

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    
    @Test
    fun `default settings have POLLING mode`() {
        val settings = AppSettings()
        assertEquals(PaymentReceptionMode.POLLING, settings.paymentReceptionMode)
    }
    
    @Test
    fun `default webhook port is 4646`() {
        val settings = AppSettings()
        assertEquals(4646, settings.webhookPort)
    }
    
    @Test
    fun `default deviceId is empty`() {
        val settings = AppSettings()
        assertEquals("", settings.deviceId)
    }
    
    @Test
    fun `default fcmToken is empty`() {
        val settings = AppSettings()
        assertEquals("", settings.fcmToken)
    }
    
    @Test
    fun `PaymentReceptionMode values are correct`() {
        assertEquals(3, PaymentReceptionMode.values().size)
        assertNotNull(PaymentReceptionMode.valueOf("POLLING"))
        assertNotNull(PaymentReceptionMode.valueOf("FIREBASE"))
        assertNotNull(PaymentReceptionMode.valueOf("WEBHOOK"))
    }
    
    @Test
    fun `copy preserves payment reception fields`() {
        val settings = AppSettings(
            paymentReceptionMode = PaymentReceptionMode.FIREBASE,
            deviceId = "test-device-123",
            fcmToken = "test-token-456",
            webhookPort = 8080
        )
        
        val copy = settings.copy(storeName = "New Store")
        
        assertEquals(PaymentReceptionMode.FIREBASE, copy.paymentReceptionMode)
        assertEquals("test-device-123", copy.deviceId)
        assertEquals("test-token-456", copy.fcmToken)
        assertEquals(8080, copy.webhookPort)
        assertEquals("New Store", copy.storeName)
    }
}
