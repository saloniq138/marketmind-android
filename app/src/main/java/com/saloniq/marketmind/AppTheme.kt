package com.saloniq.marketmind

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MarketMindDarkColors = darkColorScheme()
private val MarketMindLightColors = lightColorScheme()

@Composable
fun MarketMindTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) MarketMindDarkColors else MarketMindLightColors,
        content = content
    )
}
