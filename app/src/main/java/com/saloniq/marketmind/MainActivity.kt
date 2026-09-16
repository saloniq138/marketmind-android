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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarketMindApp() }
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
        "add" -> AddAssetScreen(onBack = { screen = "dashboard" }) { asset ->
            if (watchlist.none { it.symbol.equals(asset.symbol, true) }) {
                watchlist.add(asset)
                settings.saveAssets(watchlist)
                refreshKey++
            }
            screen = "dashboard"
        }
        "settings" -> SettingsScreen(settings, repository, onBack = { screen = "dashboard" }) { refreshKey++ }
        else -> DashboardScreen(
            assets = watchlist,
            repository = repository,
            onAdd = { screen = "add" },
            onSettings = { screen = "settings" },
            onRemove = { asset ->
                watchlist.remove(asset)
                settings.saveAssets(watchlist)
            },
            onRefresh = { refreshKey++ }
        )
    }
}

@Composable
private fun DashboardScreen(
    assets: List<Asset>,
    repository: MarketRepository,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onRemove: (Asset) -> Unit,
    onRefresh: () -> Unit
) {
    var aiText by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("MarketMind", style = MaterialTheme.typography.headlineMedium)
                Text("Stocks + crypto • NVIDIA NIM")
            }
            OutlinedButton(onClick = onSettings) { Text("Settings") }
        }
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh market data")
                }
            }
            items(assets, key = { it.symbol }) { asset ->
                AssetCard(
                    asset = asset,
                    quote = repository.cachedQuote(asset.symbol),
                    onAnalyze = {
                        aiLoading = true
                        scope.launch {
                            val quote = withContext(Dispatchers.IO) { repository.fetchQuote(asset) }
                            aiText = withContext(Dispatchers.IO) {
                                repository.analyzeWithNvidia(asset, quote)
                            }
                            aiLoading = false
                        }
                    },
                    onRemove = { onRemove(asset) }
                )
            }
            item {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Add stock or crypto")
                }
                if (aiLoading) {
                    Text("NVIDIA is analyzing…", Modifier.padding(top = 8.dp))
                }
                if (aiText.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(aiText, Modifier.padding(16.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: Asset,
    quote: Quote?,
    onAnalyze: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(asset.symbol, style = MaterialTheme.typography.titleLarge)
                    Text(asset.name)
                }
                Text(asset.type, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Price: ${quote?.price?.let { String.format("%.4f", it) } ?: "—"}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("24h: ${quote?.change24h?.let { String.format("%+.2f%%", it) } ?: "—"}")
            Text(
                quote?.signal ?: "Waiting for market data",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAnalyze, modifier = Modifier.weight(1f)) {
                    Text("AI analysis")
                }
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
        OutlinedTextField(
            symbol,
            { symbol = it.uppercase() },
            label = { Text("Ticker / symbol") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { type = "Stock" }) { Text("Stock") }
            OutlinedButton(onClick = { type = "Crypto" }) { Text("Crypto") }
        }
        if (type == "Stock") {
            OutlinedTextField(
                marketSymbol,
                { marketSymbol = it },
                label = { Text("Market ticker, e.g. TSLA or CDR.WA") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                coinId,
                { coinId = it.lowercase() },
                label = { Text("CoinGecko ID, e.g. bitcoin") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (symbol.isNotBlank()) {
                    onAdd(
                        Asset(
                            symbol,
                            name.ifBlank { symbol },
                            type,
                            marketSymbol.ifBlank { symbol },
                            coinId.ifBlank { null }
                        )
                    )
                }
            },
            enabled = symbol.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add to watchlist") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: SettingsStore,
    repository: MarketRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(settings.getNvidiaApiKey()) }
    var model by remember { mutableStateOf(settings.model()) }
    var minutes by remember { mutableStateOf(settings.refreshMinutes().toString()) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled()) }
    var apiStatus by remember { mutableStateOf(if (settings.hasNvidiaApiKey()) "Saved key — not tested yet." else "No API key saved yet.") }
    var testing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "NVIDIA NIM",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        OutlinedTextField(
            apiKey,
            { apiKey = it },
            label = { Text("NVIDIA API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (settings.hasNvidiaApiKey()) "API key is saved securely on this device." else "No API key saved yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            apiStatus,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Button(
            onClick = {
                val normalized = apiKey.trim()
                val saved = settings.saveNvidiaApiKey(normalized)
                if (!saved) {
                    apiStatus = "Save error: could not persist the NVIDIA API key."
                    return@Button
                }
                testing = true
                apiStatus = "Testing NVIDIA API…"
                scope.launch {
                    val result = repository.testNvidiaApi()
                    testing = false
                    apiStatus = result.message
                }
            },
            enabled = !testing,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (testing) "Testing API…" else "Save & test NVIDIA API") }

        OutlinedTextField(
            model,
            { model = it },
            label = { Text("NIM model") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Default: meta/llama-3.1-8b-instruct",
            style = MaterialTheme.typography.bodySmall
        )
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Background refresh", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            minutes,
            { minutes = it.filter(Char::isDigit) },
            label = { Text("Minutes (15–1440)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Market notifications")
            Switch(
                checked = notifications,
                onCheckedChange = { notifications = it }
            )
        }
        Button(
            onClick = {
                val normalized = apiKey.trim()
                val saved = settings.saveNvidiaApiKey(normalized)
                if (!saved) {
                    apiStatus = "Save error: could not persist the NVIDIA API key."
                    return@Button
                }
                settings.setModel(model.trim().ifBlank { "meta/llama-3.1-8b-instruct" })
                settings.setNotificationsEnabled(notifications)
                val period = minutes.toLongOrNull()?.coerceIn(15L, 1440L) ?: 60L
                settings.setRefreshMinutes(period)
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "marketmind_refresh",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<MarketRefreshWorker>(period, TimeUnit.MINUTES).build()
                )
                onSaved()
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save settings & close") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
