package com.saloniq.marketmind

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

private fun applyAlpha(hex: String, alphaPercent: Int): String = runCatching {
    val color = AndroidColor.parseColor(hex)
    String.format("#%02X%02X%02X%02X", alphaPercent.coerceIn(0, 100) * 255 / 100, AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
}.getOrDefault(hex)

@Composable
fun WidgetAppearanceControls() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = remember { WidgetSettingsStore(context) }
    val widgets = listOf("market" to "MarketMind", "compact" to "Compact", "vacation" to "Wakacje", "signal" to "Signal", "overview" to "Overview", "top_mover" to "Top Mover")
    var selected by remember { mutableStateOf("market") }
    var settings by remember { mutableStateOf(store.load("market")) }
    var backgroundHex by remember { mutableStateOf(settings.backgroundColor) }
    var textHex by remember { mutableStateOf(settings.textColor) }
    var accentHex by remember { mutableStateOf(settings.accentColor) }
    var positiveHex by remember { mutableStateOf(settings.positiveColor) }
    var negativeHex by remember { mutableStateOf(settings.negativeColor) }
    var status by remember { mutableStateOf("") }

    fun loadWidget(id: String) {
        selected = id
        settings = store.load(id)
        backgroundHex = settings.backgroundColor
        textHex = settings.textColor
        accentHex = settings.accentColor
        positiveHex = settings.positiveColor
        negativeHex = settings.negativeColor
        status = ""
    }
    fun validHex(value: String) = runCatching { AndroidColor.parseColor(value) }.isSuccess
    fun save() {
        if (!listOf(backgroundHex, textHex, accentHex, positiveHex, negativeHex).all(::validHex)) { status = "Use #RRGGBB or #AARRGGBB."; return }
        val updated = settings.copy(backgroundColor = applyAlpha(backgroundHex, settings.backgroundAlpha), textColor = textHex, accentColor = accentHex, positiveColor = positiveHex, negativeColor = negativeHex)
        store.save(selected, updated)
        settings = updated
        scope.launch { MarketMindWidget().updateAll(context); CompactMarketWidget().updateAll(context) }
        status = "${widgets.first { it.first == selected }.second} saved."
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Widget appearance", style = MaterialTheme.typography.titleLarge)
        Text("Each widget now has its own independent design.", style = MaterialTheme.typography.bodySmall)
        widgets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (id, label) ->
                    if (selected == id) Button(onClick = { loadWidget(id) }, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(onClick = { loadWidget(id) }, modifier = Modifier.weight(1f)) { Text(label) }
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Preview: ${widgets.first { it.first == selected }.second}", style = MaterialTheme.typography.titleMedium)
                val bg = runCatching { Color(AndroidColor.parseColor(applyAlpha(backgroundHex, settings.backgroundAlpha))) }.getOrDefault(Color(0xFF111111))
                val text = runCatching { Color(AndroidColor.parseColor(textHex)) }.getOrDefault(Color.White)
                val accent = runCatching { Color(AndroidColor.parseColor(accentHex)) }.getOrDefault(Color(0xFF64B5F6))
                Column(Modifier.fillMaxWidth().height(120.dp).background(bg, RoundedCornerShape(settings.cornerRadius.dp)).padding(settings.padding.dp)) {
                    Text("MarketMind", color = accent, fontSize = settings.textSize.sp)
                    Text(if (selected == "vacation") "☀️ 286 dni do wakacji" else if (selected == "signal") "TSLA  •  KUP" else "BTC  68,420.25", color = text, fontSize = settings.priceSize.sp)
                    if (settings.showChange) Text("+2.41%", color = text, fontSize = settings.changeSize.sp)
                }
            }
        }
        OutlinedTextField(value = backgroundHex, onValueChange = { backgroundHex = it }, label = { Text("Background HEX") }, modifier = Modifier.fillMaxWidth())
        Text("Transparency: ${settings.backgroundAlpha}%")
        Slider(value = settings.backgroundAlpha.toFloat(), onValueChange = { settings = settings.copy(backgroundAlpha = it.toInt()) }, valueRange = 0f..100f)
        OutlinedTextField(value = textHex, onValueChange = { textHex = it }, label = { Text("Text HEX") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = accentHex, onValueChange = { accentHex = it }, label = { Text("Accent HEX") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = positiveHex, onValueChange = { positiveHex = it }, label = { Text("Positive HEX") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = negativeHex, onValueChange = { negativeHex = it }, label = { Text("Negative HEX") }, modifier = Modifier.fillMaxWidth())
        Text("Text size: ${settings.textSize.toInt()}sp")
        Slider(value = settings.textSize, onValueChange = { settings = settings.copy(textSize = it) }, valueRange = 10f..24f)
        Text("Price size: ${settings.priceSize.toInt()}sp")
        Slider(value = settings.priceSize, onValueChange = { settings = settings.copy(priceSize = it) }, valueRange = 14f..32f)
        Text("Change size: ${settings.changeSize.toInt()}sp")
        Slider(value = settings.changeSize, onValueChange = { settings = settings.copy(changeSize = it) }, valueRange = 10f..22f)
        Text("Corner radius: ${settings.cornerRadius}dp")
        Slider(value = settings.cornerRadius.toFloat(), onValueChange = { settings = settings.copy(cornerRadius = it.toInt()) }, valueRange = 0f..32f)
        Text("Padding: ${settings.padding}dp")
        Slider(value = settings.padding.toFloat(), onValueChange = { settings = settings.copy(padding = it.toInt()) }, valueRange = 4f..24f)
        WidgetSwitch("Show price", settings.showPrice) { settings = settings.copy(showPrice = it) }
        WidgetSwitch("Show 24h change", settings.showChange) { settings = settings.copy(showChange = it) }
        WidgetSwitch("Show name", settings.showName) { settings = settings.copy(showName = it) }
        WidgetSwitch("Show signal", settings.showSignal) { settings = settings.copy(showSignal = it) }
        WidgetSwitch("Show update text", settings.showUpdatedAt) { settings = settings.copy(showUpdatedAt = it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::save, modifier = Modifier.weight(1f)) { Text("Save widget") }
            OutlinedButton(onClick = { store.reset(selected); loadWidget(selected); status = "Reset." }, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun WidgetSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}
