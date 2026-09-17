package com.saloniq.marketmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MarketMindApp() } }
}

@Composable
private fun MarketMindApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsStore(context) }
    var darkMode by remember { mutableStateOf(settings.darkMode()) }
    MarketMindTheme(darkTheme = darkMode) {
        val repository = remember { MarketRepository(context) }
        val watchlist = remember { mutableStateListOf<Asset>().also { it.addAll(settings.loadAssets()) } }
        var screen by remember { mutableStateOf("dashboard") }
        var editingAsset by remember { mutableStateOf<Asset?>(null) }
        var refreshKey by remember { mutableStateOf(0) }
        LaunchedEffect(refreshKey) { withContext(Dispatchers.IO) { repository.refreshAll() } }
        when (screen) {
            "add" -> AddAssetScreen("Add asset", null, { screen = "dashboard" }) { asset -> if (watchlist.none { it.symbol.equals(asset.symbol,true) }) { watchlist.add(asset); settings.saveAssets(watchlist); refreshKey++ }; screen="dashboard" }
            "edit" -> AddAssetScreen("Edit asset", editingAsset, { screen = "dashboard" }) { asset ->
                val old = editingAsset
                val index = old?.let { watchlist.indexOf(it) } ?: -1
                val duplicate = watchlist.any { it != old && it.symbol.equals(asset.symbol, true) }
                if (index >= 0 && !duplicate) {
                    watchlist[index] = asset
                    settings.saveAssets(watchlist)
                    refreshKey++
                }
                editingAsset = null
                screen = "dashboard"
            }
            "settings" -> SettingsScreen(settings, repository, darkMode, { dark -> darkMode=dark; settings.setDarkMode(dark) }, { screen="dashboard" }) { refreshKey++ }
            else -> DashboardScreen(watchlist, repository, { screen="add" }, { screen="settings" }, { asset -> editingAsset=asset; screen="edit" }, { asset -> watchlist.remove(asset); settings.saveAssets(watchlist) }) { refreshKey++ }
        }
    }
}

@Composable private fun DashboardScreen(assets:List<Asset>, repository:MarketRepository, onAdd:()->Unit, onSettings:()->Unit, onEdit:(Asset)->Unit, onRemove:(Asset)->Unit, onRefresh:()->Unit) {
    var aiText by remember{mutableStateOf("")}; var aiLoading by remember{mutableStateOf(false)}; val scope=rememberCoroutineScope(); val context=androidx.compose.ui.platform.LocalContext.current; val aiSettings=remember{AiProviderSettings(context)}
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(16.dp),Arrangement.SpaceBetween){ Column{Text("MarketMind",style=MaterialTheme.typography.headlineMedium);Text("Stocks + crypto • ${aiSettings.provider().label}")};OutlinedButton(onClick=onSettings){Text("Settings")} }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){ item{Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth()){Text("Refresh market data")}};items(assets,key={it.symbol}){asset->AssetCard(asset,repository.cachedQuote(asset.symbol),{aiLoading=true;scope.launch{val q=withContext(Dispatchers.IO){repository.fetchQuote(asset)};aiText=withContext(Dispatchers.IO){AiClient(context).analyze(asset,q)};aiLoading=false}},{onEdit(asset)},{onRemove(asset)})};item{Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("Add stock or crypto")};if(aiLoading)Text("AI is analyzing…",Modifier.padding(top=8.dp));if(aiText.isNotBlank())Card(Modifier.fillMaxWidth()){Text(aiText,Modifier.padding(16.dp))};Spacer(Modifier.height(24.dp))}}
    }
}

@Composable private fun AssetCard(asset:Asset,quote:Quote?,onAnalyze:()->Unit,onEdit:()->Unit,onRemove:()->Unit){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Column{Text(asset.symbol,style=MaterialTheme.typography.titleLarge);Text(asset.name)};Text(asset.type,style=MaterialTheme.typography.labelMedium)};Spacer(Modifier.height(10.dp));Text("Price: ${quote?.price?.let{String.format("%.4f",it)}?:"—"}");Text("24h: ${quote?.change24h?.let{String.format("%+.2f%%",it)}?:"—"}");Text(quote?.signal?:"Waiting for market data",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),Arrangement.spacedBy(8.dp)){Button(onClick=onAnalyze,modifier=Modifier.weight(1f)){Text("AI analysis")};OutlinedButton(onClick=onEdit){Text("Edit")};OutlinedButton(onClick=onRemove){Text("Remove")}}}}}

@Composable private fun AddAssetScreen(title:String,initial:Asset?,onBack:()->Unit,onSave:(Asset)->Unit){var symbol by remember(initial){mutableStateOf(initial?.symbol?:"")};var name by remember(initial){mutableStateOf(initial?.name?:"")};var type by remember(initial){mutableStateOf(initial?.type?:"Stock")};var marketSymbol by remember(initial){mutableStateOf(initial?.marketSymbol?:"")};var coinId by remember(initial){mutableStateOf(initial?.coinId?:"")};var error by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(16.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);Text(if(initial==null)"Add stocks or crypto to your personal watchlist." else "Change the name, ticker or market identifier. Your existing asset will be updated.",Modifier.padding(vertical=8.dp));OutlinedTextField(value=symbol,onValueChange={symbol=it.uppercase();error=""},label={Text("Ticker / symbol")},modifier=Modifier.fillMaxWidth());OutlinedTextField(value=name,onValueChange={name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(type=="Stock")Button(onClick={type="Stock"}){Text("Stock")}else OutlinedButton(onClick={type="Stock"}){Text("Stock")};if(type=="Crypto")Button(onClick={type="Crypto"}){Text("Crypto")}else OutlinedButton(onClick={type="Crypto"}){Text("Crypto")}};if(type=="Stock")OutlinedTextField(value=marketSymbol,onValueChange={marketSymbol=it},label={Text("Market ticker, e.g. TSLA or CDR.WA")},modifier=Modifier.fillMaxWidth()) else OutlinedTextField(value=coinId,onValueChange={coinId=it.lowercase()},label={Text("CoinGecko ID, e.g. bitcoin")},modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(16.dp));Button(onClick={if(symbol.isBlank()){error="Enter a ticker / symbol."}else if(type=="Crypto"&&coinId.isBlank()){error="Enter the CoinGecko ID."}else if(type=="Stock"&&marketSymbol.isBlank()){error="Enter the market ticker."}else onSave(Asset(symbol.trim(),name.trim().ifBlank{symbol.trim()},type,marketSymbol.trim().ifBlank{symbol.trim()},coinId.trim().ifBlank{null}))},modifier=Modifier.fillMaxWidth()){Text(if(initial==null)"Add to watchlist" else "Save changes")};OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Back")}}
}

@Composable private fun SettingsScreen(settings:SettingsStore,repository:MarketRepository,darkMode:Boolean,onDarkMode:(Boolean)->Unit,onBack:()->Unit,onSaved:()->Unit){val context=androidx.compose.ui.platform.LocalContext.current;val scope=rememberCoroutineScope();val ai=remember{AiProviderSettings(context)};var provider by remember{mutableStateOf(ai.provider())};var apiKey by remember{mutableStateOf(ai.getKey())};var model by remember{mutableStateOf(ai.model())};var models by remember{mutableStateOf(listOf<String>())};var modelStatus by remember{mutableStateOf("Load models after saving your API key.")};var apiStatus by remember{mutableStateOf(if(ai.hasKey())"Saved key(s) — not tested yet." else "No API key saved yet.")};var testing by remember{mutableStateOf(false)};var loadingModels by remember{mutableStateOf(false)};var minutes by remember{mutableStateOf(settings.refreshMinutes().toString())};var notifications by remember{mutableStateOf(settings.notificationsEnabled())}
    fun switchProvider(next:AiProvider){provider=next;ai.setProvider(next);apiKey=ai.getKey();model=ai.model();models=emptyList();modelStatus="Save the ${next.label} key, then load its models.";apiStatus=if(ai.hasKey())"${ai.getKeys().size} saved key(s) — ready for automatic failover." else "No ${next.label} API key saved."}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Settings",style=MaterialTheme.typography.headlineMedium);Text("Appearance",style=MaterialTheme.typography.titleLarge,Modifier.padding(top=16.dp));Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text(if(darkMode)"Dark mode" else "Light mode");Switch(checked=darkMode,onCheckedChange=onDarkMode)};Text("Theme changes instantly and are saved on the device.",style=MaterialTheme.typography.bodySmall);HorizontalDivider(Modifier.padding(vertical=12.dp));Text("AI provider",style=MaterialTheme.typography.titleLarge);Text("Choose which provider powers market analysis. Keys are encrypted on this device.",style=MaterialTheme.typography.bodySmall);Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){AiProvider.values().forEach{option->if(option==provider)Button(onClick={switchProvider(option)},modifier=Modifier.weight(1f)){Text(option.label)}else OutlinedButton(onClick={switchProvider(option)},modifier=Modifier.weight(1f)){Text(option.label)}}};AiKeyPoolControls(ai,provider){apiStatus=it};OutlinedTextField(value=apiKey,onValueChange={apiKey=it},label={Text("Quick test / add ${provider.label} API key")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Text(apiStatus,Modifier.padding(vertical=8.dp));Button(onClick={val key=apiKey.trim();if(key.isEmpty()){apiStatus="API key is empty.";return@Button};ai.setProvider(provider);ai.setModel(model.trim().ifBlank{provider.defaultModel});val save=ai.saveKey(key);if(save.isFailure){apiStatus="Save error: ${save.exceptionOrNull()?.message?:"unknown error"}";return@Button};testing=true;apiStatus="Key saved. Testing ${provider.label}…";scope.launch{val r=withContext(Dispatchers.IO){AiClient(context).test()};testing=false;apiStatus=r.message}},enabled=!testing,modifier=Modifier.fillMaxWidth()){Text(if(testing)"Testing API…" else "Save & test API")};Spacer(Modifier.height(8.dp));Text("Model",style=MaterialTheme.typography.titleMedium);OutlinedTextField(value=model,onValueChange={model=it},label={Text("Model ID")},modifier=Modifier.fillMaxWidth());Text("Default: ${provider.defaultModel}",style=MaterialTheme.typography.bodySmall);Button(onClick={if(!ai.hasKey()){modelStatus="Save the API key first.";return@Button};loadingModels=true;modelStatus="Loading available ${provider.label} models…";scope.launch{val r=withContext(Dispatchers.IO){AiClient(context).models()};loadingModels=false;models=r.models;modelStatus=r.message}},enabled=!loadingModels,modifier=Modifier.fillMaxWidth()){Text(if(loadingModels)"Loading models…" else "Load available models")};Text(modelStatus,style=MaterialTheme.typography.bodySmall);if(models.isNotEmpty())Column(verticalArrangement=Arrangement.spacedBy(6.dp)){models.forEach{id->OutlinedButton(onClick={model=id},modifier=Modifier.fillMaxWidth()){Text(if(id==model)"✓ $id" else id)}}};HorizontalDivider(Modifier.padding(vertical=16.dp));Text("Background refresh",style=MaterialTheme.typography.titleLarge);OutlinedTextField(value=minutes,onValueChange={minutes=it.filter(Char::isDigit)},label={Text("Minutes (15–1440)")},modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text("Market notifications");Switch(checked=notifications,onCheckedChange={notifications=it})}}
        item{WidgetAppearanceControls()};item{Button(onClick={ai.setProvider(provider);ai.setModel(model.trim().ifBlank{provider.defaultModel});if(apiKey.isNotBlank())ai.saveKey(apiKey.trim());settings.setNotificationsEnabled(notifications);val period=minutes.toLongOrNull()?.coerceIn(15L,1440L)?:60L;settings.setRefreshMinutes(period);WorkManager.getInstance(context).enqueueUniquePeriodicWork("marketmind_refresh",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<MarketRefreshWorker>(period,TimeUnit.MINUTES).build());onSaved();onBack()},modifier=Modifier.fillMaxWidth()){Text("Save settings & close")};OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Back")}}
    }
}
