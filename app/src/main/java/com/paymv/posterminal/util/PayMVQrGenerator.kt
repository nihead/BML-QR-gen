package com.paymv.posterminal.util

import com.paymv.posterminal.data.model.AppSettings

object PayMVQrGenerator {
    
    /**
     * Generate PayMV QR code string with BML TLV protocol
     */
    fun generate(amount: String, settings: AppSettings, transactionRef: String = "POS001"): String {
        // Tag 00: Payload Format Indicator
        val tag00 = "00" + "02" + "01"
        
        // Tag 01: Point of Initiation Method (dynamic QR)
        val tag01 = "01" + "02" + "12"
        
        // Tag 26: Merchant Account Information (nested TLVs)
        val tag26Content = buildTag26Content(settings, transactionRef)
        val tag26 = "26" + tag26Content.length.toString().padStart(2, '0') + tag26Content
        
        // Tag 52: Merchant Category Code
        val tag52 = "52" + "04" + "0000"
        
        // Tag 53: Currency Code (MVR)
        val tag53 = "53" + "03" + "462"
        
        // Tag 54: Transaction Amount
        val formattedAmount = formatAmount(amount)
        val tag54 = "54" + formattedAmount.length.toString().padStart(2, '0') + formattedAmount
        
        // Tag 58: Country Code
        val tag58 = "58" + "02" + "MV"
        
        // Tag 59: Merchant Name
        val merchantName = settings.storeName
        val tag59 = "59" + merchantName.length.toString().padStart(2, '0') + merchantName
        
        // Tag 62: Additional Data
        val tag62Content = "05" + transactionRef.length.toString().padStart(2, '0') + transactionRef
        val tag62 = "62" + tag62Content.length.toString().padStart(2, '0') + tag62Content
        
        // Build QR string without checksum
        val qrWithoutChecksum = tag00 + tag01 + tag26 + tag52 + tag53 + tag54 + tag58 + tag59 + tag62 + "6304"
        
        // Calculate CRC-16 checksum
        val checksum = CRC16Calculator.calculate(qrWithoutChecksum)
        
        // Return complete QR string
        return qrWithoutChecksum + checksum
    }
    
    private fun buildTag26Content(settings: AppSettings, transactionRef: String): String {
        // Sub-tag 00: Domain
        val subTag00 = "00" + "14" + "mv.favara.mpqr"
        
        // Sub-tag 01: Merchant ID
        val subTag01 = "01" + "08" + "MALBMVMV"
        
        // Sub-tag 02: Merchant ID (duplicate)
        val subTag02 = "02" + "08" + "MALBMVMV"
        
        // Sub-tag 03: Account Number (13 digits)
        val accountNumber = settings.accountNumber
        val subTag03 = "03" + "13" + accountNumber
        
        // Sub-tag 05: Mobile Number (optional)
        val subTag05 = if (!settings.mobileNumber.isNullOrEmpty()) {
            val mobile = settings.mobileNumber
            "05" + mobile.length.toString().padStart(2, '0') + mobile
        } else {
            ""
        }
        
        // Sub-tag 10: Payment Type
        val subTag10 = "10" + "04" + "IPAY"
        
        // Sub-tag 60: Transaction Reference
        val subTag60 = "60" + transactionRef.length.toString().padStart(2, '0') + transactionRef
        
        return subTag00 + subTag01 + subTag02 + subTag03 + subTag05 + subTag10 + subTag60
    }
    
    private fun formatAmount(amount: String): String {
        val cleanAmount = amount.replace(",", "").trim()
        val doubleAmount = cleanAmount.toDoubleOrNull() ?: 0.0
        return String.format("%.2f", doubleAmount)
    }
    
    /**
     * Validate if settings are complete enough to generate QR
     */
    fun canGenerateQR(settings: AppSettings): Boolean {
        return settings.accountNumber.isNotEmpty() &&
               settings.accountNumber.length == 13 &&
               settings.storeName.isNotEmpty()
    }
}
