package com.saloniq.marketmind

import android.content.Context
import android.graphics.Color as AndroidColor
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private fun compactColor(value: String, alphaPercent: Int? = null): Color = runCatching {
    val parsed = AndroidColor.parseColor(value)
    val alpha = alphaPercent?.let { (it.coerceIn(0, 100) * 255 / 100) } ?: AndroidColor.alpha(parsed)
    Color(AndroidColor.argb(alpha, AndroidColor.red(parsed), AndroidColor.green(parsed), AndroidColor.blue(parsed)))
}.getOrDefault(Color.White)

class CompactMarketWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val settings = WidgetSettingsStore(context).load()
            val store = SettingsStore(context)
            val repository = MarketRepository(context)
            val asset = store.loadAssets().firstOrNull()
            val quote = asset?.let { repository.cachedQuote(it.symbol) }
            val textColor = compactColor(settings.textColor)
            val accentColor = compactColor(settings.accentColor)
            val positiveColor = compactColor(settings.positiveColor)
            val negativeColor = compactColor(settings.negativeColor)
            val backgroundColor = compactColor(settings.backgroundColor, settings.backgroundAlpha)
            val change = quote?.change24h
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(settings.padding.dp)
                    .background(ColorProvider(backgroundColor))
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text(asset?.symbol ?: "MarketMind", style = TextStyle(color = ColorProvider(accentColor), fontSize = settings.textSize.sp))
                if (settings.showName && asset != null) Text(asset.name, style = TextStyle(color = ColorProvider(textColor), fontSize = settings.textSize.sp))
                if (settings.showPrice) Text(quote?.price?.let { String.format("%.2f", it) } ?: "No market data", style = TextStyle(color = ColorProvider(textColor), fontSize = settings.priceSize.sp))
                if (settings.showChange) Text(change?.let { String.format("%+.2f%% today", it) } ?: "Open app to refresh", style = TextStyle(color = ColorProvider(if ((change ?: 0.0) >= 0) positiveColor else negativeColor), fontSize = settings.changeSize.sp))
                if (settings.showSignal && !quote?.signal.isNullOrBlank()) Text(quote?.signal ?: "", style = TextStyle(color = ColorProvider(textColor), fontSize = settings.textSize.sp))
            }
        }
    }
}

class CompactMarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactMarketWidget()
}
