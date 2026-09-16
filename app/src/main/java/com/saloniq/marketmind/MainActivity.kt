package com.saloniq.marketmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private const val DEFAULT_NVIDIA_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarketMindTheme { MarketMindApp() } }
    }
}

@Composable
private fun MarketMindApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsStore(context) }
    val repository = remember { MarketRepository(context) }
    val watchlist = remember { mutableStateListOf<Asset>().also { it.addAll(settings.loadAssets()) } }
    var screen by remember { mutableStateOf("dashboard") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) { withContext(Dispatchers.IO) { repository.refreshAll() } }

    when (screen) {
        "add" -> AddAssetScreen({ screen = "dashboard" }) { asset ->
            if (watchlist.none { it.symbol.equals(asset.symbol, true) }) {
                watchlist.add(asset)
                settings.saveAssets(watchlist)
                refreshKey++
            }
            screen = "dashboard"
        }
        "settings" -> SettingsScreen(settings, repository, { screen = "dashboard" }) { refreshKey++ }
        else -> DashboardScreen(watchlist, repository, { screen = "add" }, { screen = "settings" }, { asset ->
            watchlist.remove(asset)
            settings.saveAssets(watchlist)
        }) { refreshKey++ }
    }
}

@Composable
private fun DashboardScreen(
    assets: List<Asset>, repository: MarketRepository, onAdd: () -> Unit,
    onSettings: () -> Unit, onRemove: (Asset) -> Unit, onRefresh: () -> Unit
) {
    var aiText by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) {
            Column {
                Text("MarketMind", style = MaterialTheme.typography.headlineMedium)
                Text("Stocks + crypto • NVIDIA NIM")
            }
            OutlinedButton(onClick = onSettings) { Text("Settings") }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Button(onClick = onRefresh, Modifier.fillMaxWidth()) { Text("Refresh market data") } }
            items(assets, key = { it.symbol }) { asset ->
                AssetCard(asset, repository.cachedQuote(asset.symbol), {
                    aiLoading = true
                    scope.launch {
                        val quote = withContext(Dispatchers.IO) { repository.fetchQuote(asset) }
                        aiText = withContext(Dispatchers.IO) { repository.analyzeWithNvidia(asset, quote) }
                        aiLoading = false
                    }
                }, { onRemove(asset) })
            }
            item {
                Button(onClick = onAdd, Modifier.fillMaxWidth()) { Text("Add stock or crypto") }
                if (aiLoading) Text("NVIDIA is analyzing…", Modifier.padding(top = 8.dp))
                if (aiText.isNotBlank()) Card(Modifier.fillMaxWidth()) { Text(aiText, Modifier.padding(16.dp)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AssetCard(asset: Asset, quote: Quote?, onAnalyze: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text(asset.symbol, style = MaterialTheme.typography.titleLarge)
                    Text(asset.name)
                }
                Text(asset.type, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text("Price: ${quote?.price?.let { String.format("%.4f", it) } ?: "—"}")
            Text("24h: ${quote?.change24h?.let { String.format("%+.2f%%", it) } ?: "—"}")
            Text(quote?.signal ?: "Waiting for market data", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAnalyze, Modifier.weight(1f)) { Text("AI analysis") }
                OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun AddAssetScreen(onBack: () -> Unit, onAdd: (Asset) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Stock") }
    var marketSymbol by remember { mutableStateOf("") }
    var coinId by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Add asset", style = MaterialTheme.typography.headlineMedium)
        Text("Add stocks or crypto to your personal watchlist.", Modifier.padding(vertical = 8.dp))
        OutlinedTextField(symbol, { symbol = it.uppercase() }, label = { Text("Ticker / symbol") }, Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { type = "Stock" }) { Text("Stock") }
            OutlinedButton(onClick = { type = "Crypto" }) { Text("Crypto") }
        }
        if (type == "Stock") {
            OutlinedTextField(marketSymbol, { marketSymbol = it }, label = { Text("Market ticker, e.g. TSLA or CDR.WA") }, Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(coinId, { coinId = it.lowercase() }, label = { Text("CoinGecko ID, e.g. bitcoin") }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (symbol.isNotBlank()) onAdd(Asset(symbol, name.ifBlank { symbol }, type, marketSymbol.ifBlank { symbol }, coinId.ifBlank { null }))
        }, enabled = symbol.isNotBlank(), Modifier.fillMaxWidth()) { Text("Add to watchlist") }
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun SettingsScreen(settings: SettingsStore, repository: MarketRepository, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(settings.getNvidiaApiKey()) }
    var model by remember {
        mutableStateOf(settings.model().takeUnless { it == "meta/llama-3.1-8b-instruct" }.orEmpty().ifBlank { DEFAULT_NVIDIA_MODEL })
    }
    var models by remember { mutableStateOf(listOf<String>()) }
    var modelStatus by remember { mutableStateOf("Load models from NVIDIA after saving your API key.") }
    var loadingModels by remember { mutableStateOf(false) }
    var apiStatus by remember { mutableStateOf(if (settings.hasNvidiaApiKey()) "Saved key — not tested yet." else "No API key saved yet.") }
    var testing by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(settings.refreshMinutes().toString()) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("NVIDIA NIM", style = MaterialTheme.typography.titleLarge, Modifier.padding(top = 16.dp))
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("NVIDIA API key") }, visualTransformation = PasswordVisualTransformation(), Modifier.fillMaxWidth())
        Text(if (settings.hasNvidiaApiKey()) "API key is saved securely on this device." else "No API key saved yet.", style = MaterialTheme.typography.bodySmall, Modifier.padding(top = 6.dp))
        Text(apiStatus, Modifier.padding(vertical = 8.dp))

        Button(onClick = {
            val key = apiKey.trim()
            if (key.isEmpty()) { apiStatus = "Save error: NVIDIA API key is empty."; return@Button }
            settings.setModel(model.trim().ifBlank { DEFAULT_NVIDIA_MODEL })
            val result = settings.saveNvidiaApiKey(key)
            if (result.isFailure) {
                val e = result.exceptionOrNull()
                apiStatus = "Save error: ${e?.javaClass?.simpleName}: ${e?.message ?: "unknown error"}"
                return@Button
            }
            testing = true
            apiStatus = "API key saved. Testing NVIDIA API…"
            scope.launch {
                val result2 = withContext(Dispatchers.IO) { repository.testNvidiaApi() }
                testing = false
                apiStatus = result2.message
            }
        }, enabled = !testing, Modifier.fillMaxWidth()) { Text(if (testing) "Testing API…" else "Save & test NVIDIA API") }

        Spacer(Modifier.height(8.dp))
        Text("NIM model", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, Modifier.fillMaxWidth())
        Text("Current default: $DEFAULT_NVIDIA_MODEL", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            if (!settings.hasNvidiaApiKey()) { modelStatus = "Save an NVIDIA API key first."; return@Button }
            loadingModels = true
            modelStatus = "Loading available models from NVIDIA…"
            scope.launch {
                val result = withContext(Dispatchers.IO) { repository.fetchNvidiaModels() }
                loadingModels = false
                models = result.models
                modelStatus = result.message
            }
        }, enabled = !loadingModels, Modifier.fillMaxWidth()) { Text(if (loadingModels) "Loading models…" else "Load available NVIDIA models") }
        Text(modelStatus, style = MaterialTheme.typography.bodySmall, Modifier.padding(vertical = 4.dp))

        if (models.isNotEmpty()) {
            Text("Available chat models", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { id ->
                    OutlinedButton(onClick = { model = id }, Modifier.fillMaxWidth()) {
                        Text(if (id == model) "✓ $id" else id)
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Background refresh", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes (15–1440)") }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Market notifications")
            Switch(checked = notifications, onCheckedChange = { notifications = it })
        }
        Button(onClick = {
            val key = apiKey.trim()
            if (key.isNotEmpty()) {
                val save = settings.saveNvidiaApiKey(key)
                if (save.isFailure) {
                    val e = save.exceptionOrNull()
                    apiStatus = "Save error: ${e?.javaClass?.simpleName}: ${e?.message ?: "unknown error"}"
                    return@Button
                }
            }
            settings.setModel(model.trim().ifBlank { DEFAULT_NVIDIA_MODEL })
            settings.setNotificationsEnabled(notifications)
            val period = minutes.toLongOrNull()?.coerceIn(15L, 1440L) ?: 60L
            settings.setRefreshMinutes(period)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "marketmind_refresh", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MarketRefreshWorker>(period, TimeUnit.MINUTES).build()
            )
            onSaved()
            onBack()
        }, Modifier.fillMaxWidth()) { Text("Save settings & close") }
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("Back") }
    }
}
