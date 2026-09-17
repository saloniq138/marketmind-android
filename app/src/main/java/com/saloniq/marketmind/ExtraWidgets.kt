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

private fun wc(value: String, alpha: Int? = null): Color = runCatching {
    val p = AndroidColor.parseColor(value)
    Color(AndroidColor.argb(alpha?.coerceIn(0,100)?.times(255)?.div(100) ?: AndroidColor.alpha(p), AndroidColor.red(p), AndroidColor.green(p), AndroidColor.blue(p)))
}.getOrDefault(Color.White)

private fun base(s: WidgetSettings) = GlanceModifier.fillMaxSize().padding(s.padding.dp).background(ColorProvider(wc(s.backgroundColor, s.backgroundAlpha))).clickable(actionStartActivity<MainActivity>())
private fun text(s: WidgetSettings) = TextStyle(color = ColorProvider(wc(s.textColor)), fontSize = s.textSize.sp)
private fun accent(s: WidgetSettings) = TextStyle(color = ColorProvider(wc(s.accentColor)), fontSize = s.textSize.sp)
private fun nextSummerVacation(): LocalDate {
    val today = LocalDate.now(); var year = today.year
    fun start(y: Int): LocalDate { var d = LocalDate.of(y, 6, 30); while (d.dayOfWeek != DayOfWeek.FRIDAY) d = d.minusDays(1); return d.plusDays(1) }
    var target = start(year); if (!today.isBefore(target)) target = start(++year); return target
}

class VacationCountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent {
        val s = WidgetSettingsStore(context).load("vacation"); val days = ChronoUnit.DAYS.between(LocalDate.now(), nextSummerVacation())
        Column(base(s)) {
            Text("☀️ WAKACJE", style = accent(s)); Text("$days", style = TextStyle(color = ColorProvider(wc(s.textColor)), fontSize = s.priceSize.sp))
            Text(if (days == 1L) "dzień" else "dni", style = text(s)); Text("do rozpoczęcia wakacji", style = TextStyle(color = ColorProvider(wc(s.textColor)), fontSize = s.changeSize.sp))
        }
    }}
}
class VacationCountdownWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = VacationCountdownWidget() }

private fun signalFor(q: Quote?): String = when { q?.change24h == null -> "BRAK DANYCH"; q.change24h >= 3.0 -> "KUP"; q.change24h <= -3.0 -> "SPRZEDAJ"; else -> "OBSERWUJ" }
class MarketSignalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent {
        val s = WidgetSettingsStore(context).load("signal"); val asset = SettingsStore(context).loadAssets().firstOrNull(); val q = asset?.let { MarketRepository(context).cachedQuote(it.symbol) }; val signal = signalFor(q)
        val signalColor = when (signal) { "KUP" -> wc(s.positiveColor); "SPRZEDAJ" -> wc(s.negativeColor); else -> wc(s.accentColor) }
        Column(base(s)) {
            Text("MARKET SIGNAL", style = accent(s)); Text(asset?.symbol ?: "Dodaj aktywo", style = text(s)); Text(signal, style = TextStyle(color = ColorProvider(signalColor), fontSize = s.priceSize.sp))
            if (s.showChange) Text(q?.change24h?.let { "24h: ${String.format("%+.2f%%", it)}" } ?: "Brak danych", style = text(s))
            Text("Sygnał heurystyczny — nie jest poradą inwestycyjną", style = TextStyle(color = ColorProvider(wc(s.textColor)), fontSize = 9.sp))
        }
    }}
}
class MarketSignalWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MarketSignalWidget() }

class MarketOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent {
        val s = WidgetSettingsStore(context).load("overview"); val assets = SettingsStore(context).loadAssets(); val repo = MarketRepository(context); val quotes = assets.mapNotNull { a -> repo.cachedQuote(a.symbol)?.let { a to it } }; val up = quotes.count { (it.second.change24h ?: 0.0) > 0 }; val down = quotes.count { (it.second.change24h ?: 0.0) < 0 }
        Column(base(s)) { Text("MARKET OVERVIEW", style = accent(s)); Text("Aktywa: ${assets.size}", style = text(s)); Row { Text("▲ $up", style = TextStyle(color = ColorProvider(wc(s.positiveColor)), fontSize = s.changeSize.sp)); Spacer(GlanceModifier.width(14.dp)); Text("▼ $down", style = TextStyle(color = ColorProvider(wc(s.negativeColor)), fontSize = s.changeSize.sp)) }; Text("Dane z ostatniego odświeżenia", style = text(s)) }
    }}
}
class MarketOverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MarketOverviewWidget() }

class TopMoverWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent {
        val s = WidgetSettingsStore(context).load("top_mover"); val repo = MarketRepository(context); val mover = SettingsStore(context).loadAssets().mapNotNull { a -> repo.cachedQuote(a.symbol)?.change24h?.let { a to it } }.maxByOrNull { kotlin.math.abs(it.second) }
        Column(base(s)) { Text("TOP MOVER", style = accent(s)); if (mover != null) { Text(mover.first.symbol, style = text(s)); Text(String.format("%+.2f%%", mover.second), style = TextStyle(color = ColorProvider(wc(if (mover.second >= 0) s.positiveColor else s.negativeColor)), fontSize = s.priceSize.sp)); Text("największa zmiana 24h na liście", style = text(s)) } else Text("Brak danych", style = text(s)) }
    }}
}
class TopMoverWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TopMoverWidget() }
