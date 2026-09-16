package com.saloniq.marketmind

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class MarketMindWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val settings = SettingsStore(context)
            val repository = MarketRepository(context)
            val assets = settings.loadAssets().take(5)
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text("MarketMind")
                assets.forEach { asset ->
                    val quote = repository.cachedQuote(asset.symbol)
                    Row {
                        Text(asset.symbol)
                        Spacer(GlanceModifier.width(8.dp))
                        Text(quote?.price?.let { String.format("%.2f", it) } ?: "—")
                        Spacer(GlanceModifier.width(8.dp))
                        Text(quote?.change24h?.let { String.format("%+.2f%%", it) } ?: "—")
                    }
                }
                if (assets.isEmpty()) Text("Add assets in the app")
            }
        }
    }
}

class MarketMindWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketMindWidget()
}
