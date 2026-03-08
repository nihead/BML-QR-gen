package com.paymv.posterminal.data.service

import com.paymv.posterminal.data.model.PaymentRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class WebhookPaymentSourceTest {
    
    private lateinit var webhookSource: WebhookPaymentSource
    private val testPort = 14646 // Use a non-standard port for testing
    
    @Before
    fun setup() {
        webhookSource = WebhookPaymentSource(testPort)
    }
    
    @After
    fun teardown() = runBlocking {
        webhookSource.stop()
    }
    
    @Test
    fun `start makes source active`() = runBlocking {
        webhookSource.start()
        assertTrue(webhookSource.isActive)
    }
    
    @Test
    fun `stop makes source inactive`() = runBlocking {
        webhookSource.start()
        webhookSource.stop()
        assertFalse(webhookSource.isActive)
    }
    
    @Test
    fun `health endpoint returns OK`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/health")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("ok"))
        
        conn.disconnect()
    }
    
    @Test
    fun `root endpoint returns info`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("PayMV"))
        
        conn.disconnect()
    }
    
    @Test
    fun `POST payment with valid JSON emits payment`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/payment")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        val json = """{"amount": "25.50", "timestamp": 1700000000}"""
        OutputStreamWriter(conn.outputStream).use { it.write(json) }
        
        assertEquals(200, conn.responseCode)
        
        val payment = withTimeoutOrNull(2000) {
            webhookSource.observePayments().first()
        }
        assertNotNull(payment)
        assertEquals("25.50", payment?.amount)
        
        conn.disconnect()
    }
    
    @Test
    fun `POST payment with missing amount returns error`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/payment")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        val json = """{"timestamp": "1700000000"}"""
        OutputStreamWriter(conn.outputStream).use { it.write(json) }
        
        val responseCode = conn.responseCode
        val responseBody = if (responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: "No error stream"
        }
        
        println("Response code: $responseCode")
        println("Response body: $responseBody")
        
        assertEquals(400, responseCode)
        conn.disconnect()
    }
    
    @Test
    fun `POST payment with invalid JSON returns error`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/payment")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        
        OutputStreamWriter(conn.outputStream).use { it.write("not json") }
        
        assertEquals(400, conn.responseCode)
        conn.disconnect()
    }
    
    @Test
    fun `GET on unknown path returns 404`() = runBlocking {
        webhookSource.start()
        
        val url = URL("http://localhost:$testPort/unknown")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }
}
