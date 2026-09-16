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

@Composable
fun WidgetAppearanceControls() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = remember { WidgetSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var backgroundHex by remember { mutableStateOf(settings.backgroundColor) }
    var textHex by remember { mutableStateOf(settings.textColor) }
    var accentHex by remember { mutableStateOf(settings.accentColor) }
    var positiveHex by remember { mutableStateOf(settings.positiveColor) }
    var negativeHex by remember { mutableStateOf(settings.negativeColor) }
    var status by remember { mutableStateOf("") }

    fun validHex(value: String): Boolean = runCatching { AndroidColor.parseColor(value) }.isSuccess
    fun save() {
        if (!listOf(backgroundHex, textHex, accentHex, positiveHex, negativeHex).all(::validHex)) {
            status = "Use #RRGGBB or #AARRGGBB for colors."
            return
        }
        val updated = settings.copy(
            backgroundColor = backgroundHex,
            textColor = textHex,
            accentColor = accentHex,
            positiveColor = positiveHex,
            negativeColor = negativeHex
        )
        store.save(updated)
        settings = updated
        status = "Widget appearance saved."
        scope.launch {
            MarketMindWidget().updateAll(context)
            CompactMarketWidget().updateAll(context)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Widget appearance", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Customize colors, transparency, sizes and which information is visible.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live preview", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                val previewBg = runCatching { Color(AndroidColor.parseColor(backgroundHex)) }.getOrDefault(Color(0xFF111111))
                val previewText = runCatching { Color(AndroidColor.parseColor(textHex)) }.getOrDefault(Color.White)
                val previewAccent = runCatching { Color(AndroidColor.parseColor(accentHex)) }.getOrDefault(Color(0xFF64B5F6))
                Column(
                    Modifier.fillMaxWidth().height(150.dp).background(previewBg, RoundedCornerShape(settings.cornerRadius.dp)).padding(settings.padding.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("MarketMind", color = previewAccent, fontSize = settings.textSize.sp)
                    Text("BTC", color = previewText, fontSize = settings.textSize.sp)
                    if (settings.showPrice) Text("68,420.25", color = previewText, fontSize = settings.priceSize.sp)
                    if (settings.showChange) Text("+2.41%", color = previewText, fontSize = settings.changeSize.sp)
                    if (settings.showSignal) Text("Signal: HOLD", color = previewText, fontSize = settings.textSize.sp)
                }
            }
        }

        OutlinedTextField(value = backgroundHex, onValueChange = { backgroundHex = it }, label = { Text("Background color (#AARRGGBB)") }, modifier = Modifier.fillMaxWidth())
        Text("Background transparency: ${settings.backgroundAlpha}%")
        Slider(value = settings.backgroundAlpha / 100f, onValueChange = { settings = settings.copy(backgroundAlpha = (it * 100).toInt()) }, valueRange = 0f..100f)
        OutlinedTextField(value = textHex, onValueChange = { textHex = it }, label = { Text("Text color") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = accentHex, onValueChange = { accentHex = it }, label = { Text("Accent color") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = positiveHex, onValueChange = { positiveHex = it }, label = { Text("Positive / gain color") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = negativeHex, onValueChange = { negativeHex = it }, label = { Text("Negative / loss color") }, modifier = Modifier.fillMaxWidth())

        Text("General text: ${settings.textSize.toInt()} sp")
        Slider(value = settings.textSize, onValueChange = { settings = settings.copy(textSize = it) }, valueRange = 10f..24f)
        Text("Price: ${settings.priceSize.toInt()} sp")
        Slider(value = settings.priceSize, onValueChange = { settings = settings.copy(priceSize = it) }, valueRange = 14f..32f)
        Text("Change %: ${settings.changeSize.toInt()} sp")
        Slider(value = settings.changeSize, onValueChange = { settings = settings.copy(changeSize = it) }, valueRange = 10f..22f)
        Text("Corner radius: ${settings.cornerRadius} dp")
        Slider(value = settings.cornerRadius.toFloat(), onValueChange = { settings = settings.copy(cornerRadius = it.toInt()) }, valueRange = 0f..32f)
        Text("Padding: ${settings.padding} dp")
        Slider(value = settings.padding.toFloat(), onValueChange = { settings = settings.copy(padding = it.toInt()) }, valueRange = 4f..24f)

        WidgetSwitch("Show price", settings.showPrice) { settings = settings.copy(showPrice = it) }
        WidgetSwitch("Show 24h change", settings.showChange) { settings = settings.copy(showChange = it) }
        WidgetSwitch("Show asset name", settings.showName) { settings = settings.copy(showName = it) }
        WidgetSwitch("Show AI signal", settings.showSignal) { settings = settings.copy(showSignal = it) }
        WidgetSwitch("Show update text", settings.showUpdatedAt) { settings = settings.copy(showUpdatedAt = it) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::save, modifier = Modifier.weight(1f)) { Text("Save widget") }
            OutlinedButton(onClick = {
                store.reset()
                settings = store.load()
                backgroundHex = settings.backgroundColor
                textHex = settings.textColor
                accentHex = settings.accentColor
                positiveHex = settings.positiveColor
                negativeHex = settings.negativeColor
                status = "Widget appearance reset."
                scope.launch {
                    MarketMindWidget().updateAll(context)
                    CompactMarketWidget().updateAll(context)
                }
            }, modifier = Modifier.weight(1f)) { Text("Reset") }
        }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun WidgetSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
