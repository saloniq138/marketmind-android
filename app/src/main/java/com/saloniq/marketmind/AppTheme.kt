package com.saloniq.marketmind

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MarketMindDarkColors = darkColorScheme()

@Composable
fun MarketMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarketMindDarkColors,
        content = content
    )
}
