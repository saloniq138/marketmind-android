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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val widgetWhite = ColorProvider(Color.White)
private val widgetAccent = ColorProvider(Color(0xFF64B5F6))
private val widgetPositive = ColorProvider(Color(0xFF4CAF50))
private val widgetNegative = ColorProvider(Color(0xFFF44336))
private val widgetBackground = ColorProvider(Color(0xFF111111))

private fun baseWidget() = GlanceModifier
    .fillMaxSize()
    .padding(16.dp)
    .background(widgetBackground)
    .clickable(actionStartActivity<MainActivity>())

private fun nextSummerVacation(): LocalDate {
    val today = LocalDate.now()
    var year = today.year
    fun vacationStart(y: Int): LocalDate {
        var lastFriday = LocalDate.of(y, 6, 30)
        while (lastFriday.dayOfWeek != DayOfWeek.FRIDAY) lastFriday = lastFriday.minusDays(1)
        return lastFriday.plusDays(1)
    }
    var target = vacationStart(year)
    if (!today.isBefore(target)) target = vacationStart(++year)
    return target
}

class VacationCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), nextSummerVacation())
            Column(baseWidget()) {
                Text("☀️ WAKACJE", style = TextStyle(color = widgetAccent, fontSize = 18.sp))
                Spacer(GlanceModifier.padding(4.dp))
                Text("$days", style = TextStyle(color = widgetWhite, fontSize = 38.sp))
                Text(if (days == 1L) "dzień" else "dni", style = TextStyle(color = widgetWhite, fontSize = 16.sp))
                Text("do rozpoczęcia wakacji", style = TextStyle(color = widgetWhite, fontSize = 12.sp))
            }
        }
    }
}

class VacationCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VacationCountdownWidget()
}

private fun signalFor(quote: Quote?): String = when {
    quote?.change24h == null -> "BRAK DANYCH"
    quote.change24h >= 3.0 -> "KUP"
    quote.change24h <= -3.0 -> "SPRZEDAJ"
    else -> "OBSERWUJ"
}

class MarketSignalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val asset = SettingsStore(context).loadAssets().firstOrNull()
            val quote = asset?.let { MarketRepository(context).cachedQuote(it.symbol) }
            val signal = signalFor(quote)
            val signalColor = when (signal) {
                "KUP" -> widgetPositive
                "SPRZEDAJ" -> widgetNegative
                else -> widgetAccent
            }
            Column(baseWidget()) {
                Text("MARKET SIGNAL", style = TextStyle(color = widgetWhite, fontSize = 14.sp))
                Spacer(GlanceModifier.padding(4.dp))
                Text(asset?.symbol ?: "Dodaj aktywo", style = TextStyle(color = widgetAccent, fontSize = 20.sp))
                Text(signal, style = TextStyle(color = signalColor, fontSize = 30.sp))
                Text(quote?.change24h?.let { "24h: ${String.format("%+.2f%%", it)}" } ?: "Brak danych", style = TextStyle(color = widgetWhite, fontSize = 12.sp))
                Text("Sygnał heurystyczny — nie jest poradą inwestycyjną", style = TextStyle(color = widgetWhite, fontSize = 9.sp))
            }
        }
    }
}

class MarketSignalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketSignalWidget()
}

class MarketOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val assets = SettingsStore(context).loadAssets()
            val repo = MarketRepository(context)
            val quotes = assets.mapNotNull { asset -> repo.cachedQuote(asset.symbol)?.let { asset to it } }
            val up = quotes.count { (it.second.change24h ?: 0.0) > 0 }
            val down = quotes.count { (it.second.change24h ?: 0.0) < 0 }
            Column(baseWidget()) {
                Text("MARKET OVERVIEW", style = TextStyle(color = widgetAccent, fontSize = 17.sp))
                Spacer(GlanceModifier.padding(4.dp))
                Text("Aktywa: ${assets.size}", style = TextStyle(color = widgetWhite, fontSize = 16.sp))
                Row {
                    Text("▲ $up", style = TextStyle(color = widgetPositive, fontSize = 15.sp))
                    Spacer(GlanceModifier.width(14.dp))
                    Text("▼ $down", style = TextStyle(color = widgetNegative, fontSize = 15.sp))
                }
                Text("Dane z ostatniego odświeżenia", style = TextStyle(color = widgetWhite, fontSize = 10.sp))
            }
        }
    }
}

class MarketOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketOverviewWidget()
}

class TopMoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val repo = MarketRepository(context)
            val mover = SettingsStore(context).loadAssets().mapNotNull { asset ->
                repo.cachedQuote(asset.symbol)?.change24h?.let { asset to it }
            }.maxByOrNull { kotlin.math.abs(it.second) }
            Column(baseWidget()) {
                Text("TOP MOVER", style = TextStyle(color = widgetAccent, fontSize = 17.sp))
                Spacer(GlanceModifier.padding(4.dp))
                if (mover != null) {
                    Text(mover.first.symbol, style = TextStyle(color = widgetWhite, fontSize = 22.sp))
                    Text(String.format("%+.2f%%", mover.second), style = TextStyle(color = if (mover.second >= 0) widgetPositive else widgetNegative, fontSize = 28.sp))
                    Text("największa zmiana 24h na liście", style = TextStyle(color = widgetWhite, fontSize = 10.sp))
                } else {
                    Text("Brak danych", style = TextStyle(color = widgetWhite, fontSize = 16.sp))
                }
            }
        }
    }
}

class TopMoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopMoverWidget()
}
