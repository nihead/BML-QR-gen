package com.paymv.posterminal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.ui.theme.DarkPrimary

@Composable
fun ConnectionStatus(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isConnected) DarkPrimary else Color.Red,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isConnected) "Ready for payments" else "Offline",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected) DarkPrimary else Color.Red
        )
    }
}
