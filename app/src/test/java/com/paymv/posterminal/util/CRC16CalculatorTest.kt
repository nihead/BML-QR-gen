package com.paymv.posterminal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CRC16CalculatorTest {
    
    @Test
    fun testCRC16Calculation() {
        // Test basic CRC-16/CCITT-FALSE calculation
        val testData = "000201010212"
        val result = CRC16Calculator.calculate(testData)
        
        // Result should be 4 uppercase hex characters
        assertEquals(4, result.length)
        assert(result.matches(Regex("[0-9A-F]{4}")))
    }
    
    @Test
    fun testCRC16Consistency() {
        // Same input should always produce same output
        val testData = "5303462"
        val result1 = CRC16Calculator.calculate(testData)
        val result2 = CRC16Calculator.calculate(testData)
        
        assertEquals(result1, result2)
    }
    
    @Test
    fun testCRC16EmptyString() {
        val result = CRC16Calculator.calculate("")
        assertEquals("FFFF", result) // Initial value for empty string
    }
}
