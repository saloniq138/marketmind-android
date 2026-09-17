package com.saloniq.marketmind

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

private fun applyAlpha(hex:String,a:Int)=runCatching{val c=AndroidColor.parseColor(hex);String.format("#%02X%02X%02X%02X",a.coerceIn(0,100)*255/100,AndroidColor.red(c),AndroidColor.green(c),AndroidColor.blue(c))}.getOrDefault(hex)

@Composable fun WidgetAppearanceControls(){
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val store=remember{WidgetSettingsStore(context)}
    val widgets=listOf("market" to "MarketMind","compact" to "Compact","vacation" to "Wakacje","signal" to "Signal","overview" to "Overview","top_mover" to "Top Mover")
    var selected by remember{mutableStateOf("market")}; var settings by remember{mutableStateOf(store.load("market"))}; var bg by remember{mutableStateOf(settings.backgroundColor)};var tx by remember{mutableStateOf(settings.textColor)};var ac by remember{mutableStateOf(settings.accentColor)};var po by remember{mutableStateOf(settings.positiveColor)};var ne by remember{mutableStateOf(settings.negativeColor)};var status by remember{mutableStateOf("")}
    fun load(id:String){selected=id;settings=store.load(id);bg=settings.backgroundColor;tx=settings.textColor;ac=settings.accentColor;po=settings.positiveColor;ne=settings.negativeColor;status=""}
    fun refreshAll(){scope.launch{MarketMindWidget().updateAll(context);CompactMarketWidget().updateAll(context);VacationCountdownWidget().updateAll(context);MarketSignalWidget().updateAll(context);MarketOverviewWidget().updateAll(context);TopMoverWidget().updateAll(context)}}
    fun save(){if(!listOf(bg,tx,ac,po,ne).all{runCatching{AndroidColor.parseColor(it)}.isSuccess}){status="Use #RRGGBB or #AARRGGBB.";return};val u=settings.copy(backgroundColor=applyAlpha(bg,settings.backgroundAlpha),textColor=tx,accentColor=ac,positiveColor=po,negativeColor=ne);store.save(selected,u);settings=u;refreshAll();status="${widgets.first{it.first==selected}.second} saved."}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
        Text("Widget appearance",style=MaterialTheme.typography.titleLarge);Text("Every widget can now have its own design.",style=MaterialTheme.typography.bodySmall)
        widgets.chunked(3).forEach{r->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){r.forEach{(id,label)->if(selected==id)Button(onClick={load(id)},modifier=Modifier.weight(1f)){Text(label)}else OutlinedButton(onClick={load(id)},modifier=Modifier.weight(1f)){Text(label)}};repeat(3-r.size){Spacer(Modifier.weight(1f))}}}
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Preview: ${widgets.first{it.first==selected}.second}",style=MaterialTheme.typography.titleMedium);val b=runCatching{Color(AndroidColor.parseColor(applyAlpha(bg,settings.backgroundAlpha)))}.getOrDefault(Color(0xFF111111));val t=runCatching{Color(AndroidColor.parseColor(tx))}.getOrDefault(Color.White);val a=runCatching{Color(AndroidColor.parseColor(ac))}.getOrDefault(Color(0xFF64B5F6));Column(Modifier.fillMaxWidth().height(120.dp).background(b,RoundedCornerShape(settings.cornerRadius.dp)).padding(settings.padding.dp)){Text("MarketMind",color=a,fontSize=settings.textSize.sp);Text(if(selected=="vacation")"☀️ 286 dni do wakacji" else if(selected=="signal")"TSLA • KUP" else "BTC 68,420.25",color=t,fontSize=settings.priceSize.sp);if(settings.showChange)Text("+2.41%",color=t,fontSize=settings.changeSize.sp)}}}
        OutlinedTextField(value=bg,onValueChange={bg=it},label={Text("Background HEX")},modifier=Modifier.fillMaxWidth());Text("Transparency: ${settings.backgroundAlpha}%");Slider(value=settings.backgroundAlpha.toFloat(),onValueChange={settings=settings.copy(backgroundAlpha=it.toInt())},valueRange=0f..100f);OutlinedTextField(value=tx,onValueChange={tx=it},label={Text("Text HEX")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=ac,onValueChange={ac=it},label={Text("Accent HEX")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=po,onValueChange={po=it},label={Text("Positive HEX")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=ne,onValueChange={ne=it},label={Text("Negative HEX")},modifier=Modifier.fillMaxWidth())
        Text("Text size: ${settings.textSize.toInt()}sp");Slider(value=settings.textSize,onValueChange={settings=settings.copy(textSize=it)},valueRange=10f..24f);Text("Price size: ${settings.priceSize.toInt()}sp");Slider(value=settings.priceSize,onValueChange={settings=settings.copy(priceSize=it)},valueRange=14f..32f);Text("Change size: ${settings.changeSize.toInt()}sp");Slider(value=settings.changeSize,onValueChange={settings=settings.copy(changeSize=it)},valueRange=10f..22f);Text("Corner radius: ${settings.cornerRadius}dp");Slider(value=settings.cornerRadius.toFloat(),onValueChange={settings=settings.copy(cornerRadius=it.toInt())},valueRange=0f..32f);Text("Padding: ${settings.padding}dp");Slider(value=settings.padding.toFloat(),onValueChange={settings=settings.copy(padding=it.toInt())},valueRange=4f..24f)
        WidgetSwitch("Show price",settings.showPrice){settings=settings.copy(showPrice=it)};WidgetSwitch("Show 24h change",settings.showChange){settings=settings.copy(showChange=it)};WidgetSwitch("Show name",settings.showName){settings=settings.copy(showName=it)};WidgetSwitch("Show signal",settings.showSignal){settings=settings.copy(showSignal=it)};WidgetSwitch("Show update text",settings.showUpdatedAt){settings=settings.copy(showUpdatedAt=it)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=::save,modifier=Modifier.weight(1f)){Text("Save widget")};OutlinedButton(onClick={store.reset(selected);load(selected);refreshAll();status="Reset."},modifier=Modifier.weight(1f)){Text("Reset")}};if(status.isNotBlank())Text(status)
    }
}
@Composable private fun WidgetSwitch(label:String,checked:Boolean,onCheckedChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Switch(checked=checked,onCheckedChange=onCheckedChange)}}
