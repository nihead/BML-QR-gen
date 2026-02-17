package com.paymv.posterminal.data.service

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.paymv.posterminal.data.model.AppSettings
import com.paymv.posterminal.data.model.PaymentRequest
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileInputStream
import java.time.Instant

/**
 * Local webhook HTTP server payment source.
 * Runs an embedded HTTP server on the device that receives
 * POST /payment requests from the local network.
 * Also serves a web UI at root for manual payment testing.
 */
class WebhookPaymentSource(
    private val port: Int = 4646,
    private val settingsProvider: (() -> AppSettings)? = null
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
            server = WebhookHttpServer(port, settingsProvider) { payment ->
                _incomingPayments.tryEmit(payment)
            }
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            _isActive = true
            val hostname = server?.hostname ?: "unknown"
            val listeningPort = server?.listeningPort ?: -1
            val isAlive = server?.isAlive ?: false
            Log.d(TAG, "Webhook server started - Host: $hostname, Port: $listeningPort, Alive: $isAlive")
            Log.d(TAG, "Server should be accessible at: http://<device-ip>:$port/health")
        } catch (e: Exception) {
            _isActive = false
            Log.e(TAG, "Failed to start webhook server on port $port: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
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
     * Embedded NanoHTTPD server that handles incoming payment webhooks
     * and serves a web UI for manual payment testing.
     */
    private class WebhookHttpServer(
        private val serverPort: Int,
        private val settingsProvider: (() -> AppSettings)?,
        private val onPayment: (PaymentRequest) -> Unit
    ) : NanoHTTPD("0.0.0.0", serverPort) {
        
        private val gson = Gson()
        
        override fun serve(session: IHTTPSession): Response {
            Log.d(TAG, "Incoming request: ${session.method} ${session.uri} from ${session.remoteIpAddress}")
            
            // Handle CORS preflight
            if (session.method == Method.OPTIONS) {
                return newCorsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
            }
            
            val response = when {
                session.uri == "/" && session.method == Method.GET -> handleRoot()
                session.uri == "/payment" && session.method == Method.POST -> handlePayment(session)
                session.uri == "/send" && session.method == Method.POST -> handleSendFromUI(session)
                session.uri == "/complete" && session.method == Method.POST -> handleComplete()
                session.uri == "/health" && session.method == Method.GET -> handleHealth()
                session.uri == "/info" && session.method == Method.GET -> handleInfo()
                session.uri == "/logo" && session.method == Method.GET -> handleLogo()
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
        
        // ── Web UI ──────────────────────────────────────────────
        
        private fun handleRoot(): Response {
            val settings = settingsProvider?.invoke()
            val storeName = settings?.storeName ?: "PayMV Terminal"
            val logoSrc = getLogoDataUri(settings)
            
            val html = buildWebUI(storeName, logoSrc)
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }
        
        private fun handleSendFromUI(session: IHTTPSession): Response {
            return try {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val postData = body["postData"] ?: ""
                
                // Try JSON first, then form-encoded
                val amount = try {
                    val parsed = gson.fromJson(postData, Map::class.java)
                    parsed["amount"]?.toString()
                } catch (e: Exception) {
                    session.parms["amount"]
                }
                
                if (amount.isNullOrBlank()) {
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, MIME_JSON,
                        """{"error": "Amount is required"}"""
                    )
                }
                
                val payment = PaymentRequest(
                    amount = amount,
                    timestamp = Instant.now().toString()
                )
                onPayment(payment)
                Log.d(TAG, "Payment sent from web UI: amount=$amount")
                
                newFixedLengthResponse(
                    Response.Status.OK, MIME_JSON,
                    """{"status": "ok", "message": "Payment sent", "amount": "$amount"}"""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error processing web UI payment: ${e.message}")
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_JSON,
                    """{"error": "${e.message}"}"""
                )
            }
        }
        
        private fun handleComplete(): Response {
            val payment = PaymentRequest(
                amount = "",
                timestamp = Instant.now().toString(),
                completed = true
            )
            onPayment(payment)
            Log.d(TAG, "Completion signal sent from web UI")
            return newFixedLengthResponse(
                Response.Status.OK, MIME_JSON,
                """{"status": "ok", "message": "Completion signal sent"}"""
            )
        }
        
        private fun handleLogo(): Response {
            val settings = settingsProvider?.invoke()
            val logoPath = settings?.storeLogo
            
            if (logoPath.isNullOrEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No logo configured"
                )
            }
            
            return try {
                val file = File(logoPath)
                if (!file.exists()) {
                    return newFixedLengthResponse(
                        Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Logo file not found"
                    )
                }
                val mimeType = when {
                    logoPath.endsWith(".png", true) -> "image/png"
                    logoPath.endsWith(".jpg", true) || logoPath.endsWith(".jpeg", true) -> "image/jpeg"
                    logoPath.endsWith(".webp", true) -> "image/webp"
                    else -> "image/png"
                }
                newChunkedResponse(Response.Status.OK, mimeType, FileInputStream(file))
            } catch (e: Exception) {
                Log.e(TAG, "Error serving logo: ${e.message}")
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error loading logo"
                )
            }
        }
        
        private fun getLogoDataUri(settings: AppSettings?): String? {
            val logoPath = settings?.storeLogo ?: return null
            return try {
                val file = File(logoPath)
                if (!file.exists()) return null
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mimeType = when {
                    logoPath.endsWith(".png", true) -> "image/png"
                    logoPath.endsWith(".jpg", true) || logoPath.endsWith(".jpeg", true) -> "image/jpeg"
                    logoPath.endsWith(".webp", true) -> "image/webp"
                    else -> "image/png"
                }
                "data:$mimeType;base64,$base64"
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding logo to base64: ${e.message}")
                null
            }
        }
        
        // ── API Endpoints ───────────────────────────────────────
        
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
                    val completedRegex = """"completed"\s*:\s*(true|false)""".toRegex()
                    val amountMatch = amountRegex.find(bodyStr)
                    val completedMatch = completedRegex.find(bodyStr)
                    if (amountMatch != null) {
                        PaymentRequest(
                            amount = amountMatch.groupValues[1],
                            timestamp = Instant.now().toString(),
                            completed = completedMatch?.groupValues?.get(1)?.toBoolean()
                        )
                    } else {
                        null
                    }
                }
                
                if (payment != null && (payment.amount.isNotEmpty() || payment.completed == true)) {
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
                """{"service": "PayMV POS Terminal", "endpoints": [{"method": "GET", "path": "/", "description": "Web UI for manual testing"}, {"method": "POST", "path": "/payment", "body": {"amount": "string", "timestamp": "string", "completed": "boolean (optional)"}}, {"method": "POST", "path": "/send", "body": {"amount": "string"}, "description": "Send payment from web UI"}, {"method": "POST", "path": "/complete", "description": "Send completion signal"}, {"method": "GET", "path": "/health"}, {"method": "GET", "path": "/logo"}]}"""
            )
        }
        
        // ── HTML Builder ────────────────────────────────────────
        
        private fun buildWebUI(storeName: String, logoDataUri: String?): String {
            val logoHtml = if (logoDataUri != null) {
                """<img src="$logoDataUri" alt="$storeName" class="logo">"""
            } else {
                """<div class="logo-placeholder">
                    <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <rect x="2" y="3" width="20" height="18" rx="2"/><path d="M8 21V7l8-4v18"/>
                        <path d="M12 11h.01"/><path d="M12 15h.01"/>
                    </svg>
                </div>"""
            }
            
            return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$storeName - Payment Terminal</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: #0f172a; color: #e2e8f0;
    min-height: 100vh; display: flex; align-items: center; justify-content: center;
  }
  .container {
    width: 100%; max-width: 420px; padding: 24px;
  }
  .card {
    background: #1e293b; border-radius: 16px; padding: 32px 24px;
    box-shadow: 0 4px 24px rgba(0,0,0,0.3);
  }
  .header { text-align: center; margin-bottom: 32px; }
  .logo { width: 80px; height: 80px; border-radius: 16px; object-fit: cover; margin-bottom: 16px; }
  .logo-placeholder {
    width: 80px; height: 80px; border-radius: 16px; margin: 0 auto 16px;
    background: #334155; display: flex; align-items: center; justify-content: center;
    color: #64748b;
  }
  h1 { font-size: 1.25rem; font-weight: 600; color: #f1f5f9; }
  .subtitle { font-size: 0.8rem; color: #64748b; margin-top: 4px; }
  .form-group { margin-bottom: 20px; }
  label { display: block; font-size: 0.8rem; color: #94a3b8; margin-bottom: 8px; font-weight: 500; }
  .input-wrap {
    display: flex; align-items: center; background: #0f172a;
    border: 2px solid #334155; border-radius: 12px; padding: 0 16px;
    transition: border-color 0.2s;
  }
  .input-wrap:focus-within { border-color: #3b82f6; }
  .currency { font-size: 1.1rem; color: #64748b; font-weight: 600; margin-right: 8px; }
  input[type="number"] {
    flex: 1; background: transparent; border: none; outline: none;
    color: #f1f5f9; font-size: 1.5rem; font-weight: 700; padding: 14px 0;
    -moz-appearance: textfield;
  }
  input::-webkit-outer-spin-button, input::-webkit-inner-spin-button { -webkit-appearance: none; }
  .btn {
    width: 100%; padding: 14px; border: none; border-radius: 12px;
    font-size: 1rem; font-weight: 600; cursor: pointer;
    transition: all 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px;
  }
  .btn-primary { background: #3b82f6; color: white; margin-bottom: 12px; }
  .btn-primary:hover { background: #2563eb; }
  .btn-primary:active { transform: scale(0.98); }
  .btn-success { background: #059669; color: white; }
  .btn-success:hover { background: #047857; }
  .btn-success:active { transform: scale(0.98); }
  .btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
  .toast {
    position: fixed; top: 20px; left: 50%; transform: translateX(-50%);
    padding: 12px 24px; border-radius: 10px; font-size: 0.875rem; font-weight: 500;
    opacity: 0; transition: opacity 0.3s; pointer-events: none; z-index: 100;
  }
  .toast.show { opacity: 1; }
  .toast.success { background: #059669; color: white; }
  .toast.error { background: #dc2626; color: white; }
  .divider { border: none; border-top: 1px solid #334155; margin: 20px 0; }
  .status {
    text-align: center; font-size: 0.75rem; color: #475569; margin-top: 24px;
  }
  .status .dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #22c55e; margin-right: 6px; vertical-align: middle; }
</style>
</head>
<body>
<div class="container">
  <div class="card">
    <div class="header">
      $logoHtml
      <h1>$storeName</h1>
      <div class="subtitle">Payment Terminal</div>
    </div>
    
    <div class="form-group">
      <label>Payment Amount</label>
      <div class="input-wrap">
        <span class="currency">MVR</span>
        <input type="number" id="amount" placeholder="0.00" step="0.01" min="0.01" autofocus>
      </div>
    </div>
    
    <button class="btn btn-primary" id="sendBtn" onclick="sendPayment()">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 2L11 13"/><path d="M22 2L15 22L11 13L2 9L22 2Z"/></svg>
      Send Payment
    </button>
    
    <hr class="divider">
    
    <button class="btn btn-success" id="completeBtn" onclick="markComplete()">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6L9 17L4 12"/></svg>
      Mark as Paid
    </button>
  </div>
  
  <div class="status">
    <span class="dot"></span> Server running on port $serverPort
  </div>
</div>

<div class="toast" id="toast"></div>

<script>
function showToast(msg, type) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = 'toast show ' + type;
  setTimeout(() => t.className = 'toast', 2500);
}

async function sendPayment() {
  const amount = document.getElementById('amount').value.trim();
  if (!amount || parseFloat(amount) <= 0) {
    showToast('Enter a valid amount', 'error');
    return;
  }
  const btn = document.getElementById('sendBtn');
  btn.disabled = true;
  try {
    const res = await fetch('/send', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({amount: amount})
    });
    const data = await res.json();
    if (res.ok) {
      showToast('Payment of MVR ' + amount + ' sent', 'success');
      document.getElementById('amount').value = '';
    } else {
      showToast(data.error || 'Failed to send', 'error');
    }
  } catch(e) {
    showToast('Connection error', 'error');
  }
  btn.disabled = false;
}

async function markComplete() {
  const btn = document.getElementById('completeBtn');
  btn.disabled = true;
  try {
    const res = await fetch('/complete', {method: 'POST'});
    const data = await res.json();
    if (res.ok) {
      showToast('Completion signal sent', 'success');
    } else {
      showToast(data.error || 'Failed', 'error');
    }
  } catch(e) {
    showToast('Connection error', 'error');
  }
  btn.disabled = false;
}

document.getElementById('amount').addEventListener('keydown', function(e) {
  if (e.key === 'Enter') sendPayment();
});
</script>
</body>
</html>"""
        }
        
        companion object {
            private const val TAG = "WebhookServer"
            private const val MIME_JSON = "application/json"
        }
    }
}
