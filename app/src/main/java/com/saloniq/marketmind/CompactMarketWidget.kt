package com.saloniq.marketmind

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.dp

class CompactMarketWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val settings = SettingsStore(context)
            val repository = MarketRepository(context)
            val asset = settings.loadAssets().firstOrNull()
            val quote = asset?.let { repository.cachedQuote(it.symbol) }
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text(asset?.symbol ?: "MarketMind")
                Text(quote?.price?.let { String.format("%.2f", it) } ?: "No market data")
                Text(quote?.change24h?.let { String.format("%+.2f%% today", it) } ?: "Open app to refresh")
            }
        }
    }
}

class CompactMarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactMarketWidget()
}
