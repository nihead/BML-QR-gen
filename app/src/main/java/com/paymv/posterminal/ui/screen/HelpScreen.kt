package com.paymv.posterminal.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Guide") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Getting Started
            SectionTitle("Getting Started")
            BulletPoint("Go to Settings and enter your Account Name and 13-digit Account Number")
            BulletPoint("The webhook server starts automatically on port 4646")
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Receiving Payments
            SectionTitle("Receiving Payments")
            SubSection("Via API")
            Text(
                "Send POST request to http://<device-ip>:4646/payment",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            SubSection("Via Web UI")
            Text(
                "Open http://<device-ip>:4646 in any browser to send test payments",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            SubSection("Manual Entry")
            Text(
                "Enable \"Manual QR Input\" in settings to enter amounts directly",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // QR Code Display
            SectionTitle("QR Code Display")
            BulletPoint("QR code auto-generates when payment is received")
            BulletPoint("Shows amount, account details, and auto-closes after timeout")
            BulletPoint("Customer scans with BML app to pay")
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Optional Features
            SectionTitle("Optional Features")
            SubSection("Viber Number")
            Text(
                "Add your Viber number to prompt customers to send payment slip",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            SubSection("Browser Mode")
            Text(
                "Replace idle screen with a custom webpage",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            SubSection("Hide Ads")
            Text(
                "Subscribe to remove advertisements",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Webhook API
            SectionTitle("Webhook API")
            
            Text(
                "POST /payment",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Code block for JSON
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = """
{
  "amount": "30.00",
  "timestamp": "2026-03-18T11:15:07.235Z",
  "completed": "false"
}
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // API Fields Table
            Text(
                "Fields:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ApiField("amount", "Required", "Payment amount (e.g., \"30.00\")")
            ApiField("timestamp", "Optional", "ISO 8601 timestamp")
            ApiField("completed", "Optional", "Set to \"true\" to close QR display early")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "GET /health → Server status check",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tip
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Tip: Send completed: \"true\" to dismiss the QR screen before the timeout expires (e.g., after payment confirmation).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SubSection(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun BulletPoint(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun ApiField(name: String, required: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 6.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = "($required) ",
            style = MaterialTheme.typography.bodySmall,
            color = if (required == "Required") 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.outline
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
