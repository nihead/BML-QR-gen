package com.paymv.posterminal.data.model

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    
    @Test
    fun `default webhook port is 4646`() {
        val settings = AppSettings()
        assertEquals(4646, settings.webhookPort)
    }
    
    @Test
    fun `default storeName is PayMV Terminal`() {
        val settings = AppSettings()
        assertEquals("PayMV Terminal", settings.storeName)
    }
    
    @Test
    fun `default proMode is false`() {
        val settings = AppSettings()
        assertFalse(settings.proMode)
    }
    
    @Test
    fun `copy preserves webhook port`() {
        val settings = AppSettings(webhookPort = 8080)
        
        val copy = settings.copy(storeName = "New Store")
        
        assertEquals(8080, copy.webhookPort)
        assertEquals("New Store", copy.storeName)
    }
}
