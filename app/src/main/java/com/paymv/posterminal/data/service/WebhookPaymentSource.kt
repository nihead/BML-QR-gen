package com.paymv.posterminal.data.service

import android.util.Log
import com.google.gson.Gson
import com.paymv.posterminal.data.model.PaymentRequest
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/**
 * Local webhook HTTP server payment source.
 * Runs an embedded HTTP server on the device that receives
 * POST /payment requests from the local network.
 */
class WebhookPaymentSource(
    private val port: Int = 4646
) : PaymentSource {
    
    companion object {
        private const val TAG = "WebhookPaymentSource"
    }
    
    private var server: WebhookHttpServer? = null
    private var _isActive = false
    override val isActive: Boolean get() = _isActive
    
    private val _incomingPayments = MutableSharedFlow<PaymentRequest>(
        replay = 1,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    override fun observePayments(): Flow<PaymentRequest?> {
        return _incomingPayments.asSharedFlow()
    }
    
    override suspend fun start() {
        try {
            if (server != null) {
                stop()
            }
            server = WebhookHttpServer(port) { payment ->
                _incomingPayments.tryEmit(payment)
            }
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            _isActive = true
            Log.d(TAG, "Webhook server started on port $port")
        } catch (e: Exception) {
            _isActive = false
            Log.e(TAG, "Failed to start webhook server on port $port: ${e.message}")
            throw e
        }
    }
    
    override suspend fun stop() {
        try {
            server?.stop()
            server = null
            _isActive = false
            Log.d(TAG, "Webhook server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping webhook server: ${e.message}")
        }
    }
    
    /**
     * Embedded NanoHTTPD server that handles incoming payment webhooks.
     */
    private class WebhookHttpServer(
        port: Int,
        private val onPayment: (PaymentRequest) -> Unit
    ) : NanoHTTPD("0.0.0.0", port) {
        
        private val gson = Gson()
        
        override fun serve(session: IHTTPSession): Response {
            // Handle CORS preflight
            if (session.method == Method.OPTIONS) {
                return newCorsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
            }
            
            val response = when {
                session.uri == "/payment" && session.method == Method.POST -> handlePayment(session)
                session.uri == "/health" && session.method == Method.GET -> handleHealth()
                session.uri == "/" && session.method == Method.GET -> handleInfo()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, 
                    MIME_JSON, 
                    """{"error": "Not found"}"""
                )
            }
            return newCorsResponse(response)
        }
        
        private fun newCorsResponse(response: Response): Response {
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            response.addHeader("Access-Control-Max-Age", "86400")
            return response
        }
        
        private fun handlePayment(session: IHTTPSession): Response {
            return try {
                // Read request body
                val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(contentLength)
                session.inputStream.read(body, 0, contentLength)
                val bodyStr = String(body)
                
                Log.d(TAG, "Webhook received: $bodyStr")
                
                // Parse payment request
                val payment = try {
                    gson.fromJson(bodyStr, PaymentRequest::class.java)
                } catch (e: Exception) {
                    // If parsing fails, try to extract amount manually
                    val amountRegex = """"amount"\s*:\s*"([^"]+)"""".toRegex()
                    val match = amountRegex.find(bodyStr)
                    if (match != null) {
                        PaymentRequest(
                            amount = match.groupValues[1],
                            timestamp = Instant.now().toString()
                        )
                    } else {
                        null
                    }
                }
                
                if (payment != null && payment.amount.isNotEmpty()) {
                    onPayment(payment)
                    newFixedLengthResponse(
                        Response.Status.OK, 
                        MIME_JSON, 
                        """{"status": "ok", "message": "Payment received", "amount": "${payment.amount}"}"""
                    )
                } else {
                    newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, 
                        MIME_JSON, 
                        """{"error": "Invalid payment data. Expected JSON with 'amount' field."}"""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing webhook: ${e.message}")
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, 
                    MIME_JSON, 
                    """{"error": "${e.message}"}"""
                )
            }
        }
        
        private fun handleHealth(): Response {
            return newFixedLengthResponse(
                Response.Status.OK, 
                MIME_JSON, 
                """{"status": "ok", "service": "PayMV POS Webhook"}"""
            )
        }
        
        private fun handleInfo(): Response {
            return newFixedLengthResponse(
                Response.Status.OK, 
                MIME_JSON, 
                """{"service": "PayMV POS Terminal", "endpoints": [{"method": "POST", "path": "/payment", "body": {"amount": "string", "timestamp": "string"}}, {"method": "GET", "path": "/health"}]}"""
            )
        }
        
        companion object {
            private const val TAG = "WebhookServer"
            private const val MIME_JSON = "application/json"
        }
    }
}
