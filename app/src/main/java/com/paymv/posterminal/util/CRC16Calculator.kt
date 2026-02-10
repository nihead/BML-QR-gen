package com.paymv.posterminal.util

object CRC16Calculator {
    
    /**
     * Calculate CRC-16/CCITT-FALSE checksum
     * Polynomial: 0x1021
     * Initial value: 0xFFFF
     */
    fun calculate(data: String): String {
        var crc = 0xFFFF
        val polynomial = 0x1021
        
        for (char in data) {
            crc = crc xor (char.code shl 8)
            for (i in 0 until 8) {
                if (crc and 0x8000 != 0) {
                    crc = (crc shl 1) xor polynomial
                } else {
                    crc = crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
