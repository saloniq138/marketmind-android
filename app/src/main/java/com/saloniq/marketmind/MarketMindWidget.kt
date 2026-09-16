package com.saloniq.marketmind

import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.dp

class MarketMindWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: android.content.Context) {
        val settings = SettingsStore(context)
        val repository = MarketRepository(context)
        val assets = settings.loadAssets().take(3)
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Text("MarketMind")
            assets.forEach { asset ->
                val quote = repository.cachedQuote(asset.symbol)
                Text("${asset.symbol}: ${quote?.price?.let { String.format("%.2f", it) } ?: "—"}  ${quote?.change24h?.let { String.format("%+.2f%%", it) } ?: ""}")
            }
            if (assets.isEmpty()) Text("Add assets in MarketMind")
        }
    }
}

class MarketMindWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketMindWidget()
}
