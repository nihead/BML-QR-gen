package com.paymv.posterminal.util

import com.paymv.posterminal.data.model.AppSettings
import org.junit.Assert.*
import org.junit.Test

class PayMVQrGeneratorTest {
    
    @Test
    fun testCanGenerateQR_ValidSettings() {
        val settings = AppSettings(
            storeName = "Test Store",
            accountNumber = "1234567890123",
            accountName = "TEST.USER"
        )
        
        assertTrue(PayMVQrGenerator.canGenerateQR(settings))
    }
    
    @Test
    fun testCanGenerateQR_InvalidAccountNumber() {
        val settings = AppSettings(
            storeName = "Test Store",
            accountNumber = "123", // Too short
            accountName = "TEST.USER"
        )
        
        assertFalse(PayMVQrGenerator.canGenerateQR(settings))
    }
    
    @Test
    fun testCanGenerateQR_EmptyStoreName() {
        val settings = AppSettings(
            storeName = "",
            accountNumber = "1234567890123",
            accountName = "TEST.USER"
        )
        
        assertFalse(PayMVQrGenerator.canGenerateQR(settings))
    }
    
    @Test
    fun testQRGeneration_ValidSettings() {
        val settings = AppSettings(
            storeName = "Test Store",
            accountNumber = "7001234567890",
            accountName = "TEST.USER",
            mobileNumber = "+9607654321"
        )
        
        val qrString = PayMVQrGenerator.generate("250.00", settings, "POS001")
        
        // QR string should start with payload format indicator
        assertTrue(qrString.startsWith("000201"))
        
        // Should contain point of initiation method
        assertTrue(qrString.contains("010212"))
        
        // Should contain currency code (MVR = 462)
        assertTrue(qrString.contains("5303462"))
        
        // Should contain country code
        assertTrue(qrString.contains("5802MV"))
        
        // Should end with checksum tag and 4-digit checksum
        assertTrue(qrString.contains("6304"))
        assertTrue(qrString.length > 100) // Reasonable length check
    }
    
    @Test
    fun testQRGeneration_WithoutMobileNumber() {
        val settings = AppSettings(
            storeName = "Test Store",
            accountNumber = "7001234567890",
            accountName = "TEST.USER",
            mobileNumber = null
        )
        
        val qrString = PayMVQrGenerator.generate("100.00", settings)
        
        // Should still generate valid QR without mobile number
        assertTrue(qrString.startsWith("000201"))
        assertTrue(qrString.contains("6304"))
    }
}
