package com.saloniq.marketmind

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private fun widgetColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.White)

class MarketMindWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val settings = WidgetSettingsStore(context).load()
            val repository = MarketRepository(context)
            val assets = SettingsStore(context).loadAssets().take(5)
            val textColor = widgetColor(settings.textColor)
            val accentColor = widgetColor(settings.accentColor)
            val positiveColor = widgetColor(settings.positiveColor)
            val negativeColor = widgetColor(settings.negativeColor)
            val backgroundColor = widgetColor(settings.backgroundColor)
            val base = GlanceModifier
                .fillMaxSize()
                .padding(settings.padding.dp)
                .background(ColorProvider(backgroundColor))
                .clickable(actionStartActivity<MainActivity>())
            val normalStyle = TextStyle(color = ColorProvider(textColor), fontSize = settings.textSize.sp)
            val priceStyle = TextStyle(color = ColorProvider(textColor), fontSize = settings.priceSize.sp)
            val changePositive = TextStyle(color = ColorProvider(positiveColor), fontSize = settings.changeSize.sp)
            val changeNegative = TextStyle(color = ColorProvider(negativeColor), fontSize = settings.changeSize.sp)
            val accentStyle = TextStyle(color = ColorProvider(accentColor), fontSize = settings.textSize.sp)

            Column(base) {
                Text("MarketMind", style = accentStyle)
                assets.forEach { asset ->
                    val quote = repository.cachedQuote(asset.symbol)
                    Column(GlanceModifier.padding(top = 6.dp)) {
                        Row {
                            Column {
                                if (settings.showName) Text(asset.symbol, style = normalStyle)
                                if (settings.showPrice) Text(quote?.price?.let { String.format("%.2f", it) } ?: "—", style = priceStyle)
                            }
                            Spacer(GlanceModifier.width(8.dp))
                            if (settings.showChange) {
                                val change = quote?.change24h
                                Text(change?.let { String.format("%+.2f%%", it) } ?: "—", style = if ((change ?: 0.0) >= 0) changePositive else changeNegative)
                            }
                        }
                        if (settings.showSignal && !quote?.signal.isNullOrBlank()) Text(quote?.signal ?: "", style = normalStyle)
                    }
                }
                if (assets.isEmpty()) Text("Add assets in the app", style = normalStyle)
                if (settings.showUpdatedAt) Text("Updated when market data refreshes", style = normalStyle)
            }
        }
    }
}

class MarketMindWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketMindWidget()
}
