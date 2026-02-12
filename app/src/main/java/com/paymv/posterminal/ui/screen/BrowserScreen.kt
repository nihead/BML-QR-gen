package com.paymv.posterminal.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateToQR: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val appSettings by viewModel.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // Extract URL for use in AndroidView
    val browserUrl = appSettings.browserUrl
    val autoReload = appSettings.browserAutoReload
    
    // Remember a reference to the WebView so we can reload it
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Clear QR-active flag when BrowserScreen enters composition (returning from QR)
    LaunchedEffect(Unit) {
        viewModel.onReturnedFromQr()
    }
    
    // Navigate to QR screen when payment is detected (highest priority)
    LaunchedEffect(uiState.pendingPayment) {
        uiState.pendingPayment?.let { payment ->
            onNavigateToQR(payment.amount)
            viewModel.onPaymentNavigated()
        }
    }
    
    // Show error if URL is empty
    LaunchedEffect(browserUrl) {
        if (browserUrl.isEmpty()) {
            viewModel.onPageError("Browser URL is not configured. Please configure it in Settings.")
        }
    }
    
    // Auto-reload every 5 minutes when enabled
    LaunchedEffect(autoReload) {
        if (autoReload) {
            while (true) {
                delay(5 * 60 * 1000L) // 5 minutes
                webViewRef?.reload()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PayMV Terminal") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // WebView
            if (browserUrl.isNotEmpty()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(false)
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    viewModel.onPageStartLoading()
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    viewModel.onPageFinishLoading()
                                }
                                
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        viewModel.onPageError(
                                            error?.description?.toString() ?: "Error loading page"
                                        )
                                    }
                                }
                            }
                            
                            loadUrl(browserUrl)
                            webViewRef = this
                        }
                    },
                    // Remove the update block — the factory loads the URL once.
                    // Auto-reload is handled by LaunchedEffect above.
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Loading indicator
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Error display
            if (uiState.hasError) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .fillMaxWidth(0.9f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Failed to load page",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "URL: $browserUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
