package com.paymv.posterminal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paymv.posterminal.ui.theme.Gray

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    text: String = "Ad Placeholder"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Gray.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Gray,
            textAlign = TextAlign.Center
        )
    }
}
