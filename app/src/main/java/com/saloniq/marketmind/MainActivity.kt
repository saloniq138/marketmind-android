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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarketMindApp()
        }
    }
}

@Composable
private fun MarketMindApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsStore(context) }

    var darkMode by remember {
        mutableStateOf(settings.darkMode())
    }

    MarketMindTheme(darkTheme = darkMode) {
        val repository = remember {
            MarketRepository(context)
        }

        val watchlist = remember {
            mutableStateListOf<Asset>().also {
                it.addAll(settings.loadAssets())
            }
        }

        var screen by remember {
            mutableStateOf("dashboard")
        }

        var editingAsset by remember {
            mutableStateOf<Asset?>(null)
        }

        var refreshKey by remember {
            mutableStateOf(0)
        }

        LaunchedEffect(refreshKey) {
            withContext(Dispatchers.IO) {
                repository.refreshAll()
            }
        }

        when (screen) {

            "add" -> {
                AddAssetScreen(
                    title = "Add asset",
                    initial = null,
                    onBack = {
                        screen = "dashboard"
                    }
                ) { asset ->

                    if (
                        watchlist.none {
                            it.symbol.equals(asset.symbol, ignoreCase = true)
                        }
                    ) {
                        watchlist.add(asset)
                        settings.saveAssets(watchlist)
                        refreshKey++
                    }

                    screen = "dashboard"
                }
            }

            "edit" -> {
                AddAssetScreen(
                    title = "Edit asset",
                    initial = editingAsset,
                    onBack = {
                        editingAsset = null
                        screen = "dashboard"
                    }
                ) { asset ->

                    val old = editingAsset

                    val index = old?.let {
                        watchlist.indexOf(it)
                    } ?: -1

                    val duplicate = watchlist.any {
                        it != old &&
                            it.symbol.equals(
                                asset.symbol,
                                ignoreCase = true
                            )
                    }

                    if (index >= 0 && !duplicate) {
                        watchlist[index] = asset
                        settings.saveAssets(watchlist)
                        refreshKey++
                    }

                    editingAsset = null
                    screen = "dashboard"
                }
            }

            "settings" -> {
                SettingsScreen(
                    settings = settings,
                    repository = repository,
                    darkMode = darkMode,
                    onDarkMode = { dark ->
                        darkMode = dark
                        settings.setDarkMode(dark)
                    },
                    onBack = {
                        screen = "dashboard"
                    },
                    onSaved = {
                        refreshKey++
                    }
                )
            }

            else -> {
                DashboardScreen(
                    assets = watchlist,
                    repository = repository,
                    onAdd = {
                        screen = "add"
                    },
                    onSettings = {
                        screen = "settings"
                    },
                    onEdit = { asset ->
                        editingAsset = asset
                        screen = "edit"
                    },
                    onRemove = { asset ->
                        watchlist.remove(asset)
                        settings.saveAssets(watchlist)
                    },
                    onRefresh = {
                        refreshKey++
                    }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    assets: List<Asset>,
    repository: MarketRepository,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onEdit: (Asset) -> Unit,
    onRemove: (Asset) -> Unit,
    onRefresh: () -> Unit
) {
    var aiText by remember {
        mutableStateOf("")
    }

    var aiLoading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    val aiSettings = remember {
        AiProviderSettings(context)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(
                    text = "MarketMind",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Stocks + crypto • ${aiSettings.provider().label}"
                )
            }

            OutlinedButton(
                onClick = onSettings
            ) {
                Text("Settings")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh market data")
                }
            }

            items(
                items = assets,
                key = { it.symbol }
            ) { asset ->

                AssetCard(
                    asset = asset,
                    quote = repository.cachedQuote(asset.symbol),

                    onAnalyze = {
                        aiLoading = true

                        scope.launch {

                            val quote = withContext(Dispatchers.IO) {
                                repository.fetchQuote(asset)
                            }

                            aiText = withContext(Dispatchers.IO) {
                                AiClient(context).analyze(
                                    asset,
                                    quote
                                )
                            }

                            aiLoading = false
                        }
                    },

                    onEdit = {
                        onEdit(asset)
                    },

                    onRemove = {
                        onRemove(asset)
                    }
                )
            }

            item {

                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add stock or crypto")
                }

                if (aiLoading) {
                    Text(
                        text = "AI is analyzing…",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (aiText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = aiText,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: Asset,
    quote: Quote?,
    onAnalyze: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Column {
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = asset.name
                    )
                }

                Text(
                    text = asset.type,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "Price: ${
                    quote?.price?.let {
                        String.format("%.4f", it)
                    } ?: "—"
                }"
            )

            Text(
                text = "24h: ${
                    quote?.change24h?.let {
                        String.format("%+.2f%%", it)
                    } ?: "—"
                }"
            )

            Text(
                text = quote?.signal
                    ?: "Waiting for market data",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AI analysis")
                }

                OutlinedButton(
                    onClick = onEdit
                ) {
                    Text("Edit")
                }

                OutlinedButton(
                    onClick = onRemove
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun AddAssetScreen(
    title: String,
    initial: Asset?,
    onBack: () -> Unit,
    onSave: (Asset) -> Unit
) {
    var symbol by remember(initial) {
        mutableStateOf(initial?.symbol ?: "")
    }

    var name by remember(initial) {
        mutableStateOf(initial?.name ?: "")
    }

    var type by remember(initial) {
        mutableStateOf(initial?.type ?: "Stock")
    }

    var marketSymbol by remember(initial) {
        mutableStateOf(initial?.marketSymbol ?: "")
    }

    var coinId by remember(initial) {
        mutableStateOf(initial?.coinId ?: "")
    }

    var error by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = if (initial == null) {
                "Add stocks or crypto to your personal watchlist."
            } else {
                "Change the name, ticker or market identifier. Your existing asset will be updated."
            },
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = symbol,
            onValueChange = {
                symbol = it.uppercase()
                error = ""
            },
            label = {
                Text("Ticker / symbol")
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
            },
            label = {
                Text("Name")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (type == "Stock") {
                Button(
                    onClick = {
                        type = "Stock"
                    }
                ) {
                    Text("Stock")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        type = "Stock"
                    }
                ) {
                    Text("Stock")
                }
            }

            if (type == "Crypto") {
                Button(
                    onClick = {
                        type = "Crypto"
                    }
                ) {
                    Text("Crypto")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        type = "Crypto"
                    }
                ) {
                    Text("Crypto")
                }
            }
        }

        if (type == "Stock") {

            OutlinedTextField(
                value = marketSymbol,
                onValueChange = {
                    marketSymbol = it
                },
                label = {
                    Text(
                        "Market ticker, e.g. TSLA or CDR.WA"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

        } else {

            OutlinedTextField(
                value = coinId,
                onValueChange = {
                    coinId = it.lowercase()
                },
                label = {
                    Text(
                        "CoinGecko ID, e.g. bitcoin"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error.isNotBlank()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = {

                when {
                    symbol.isBlank() -> {
                        error = "Enter a ticker / symbol."
                    }

                    type == "Crypto" && coinId.isBlank() -> {
                        error = "Enter the CoinGecko ID."
                    }

                    type == "Stock" && marketSymbol.isBlank() -> {
                        error = "Enter the market ticker."
                    }

                    else -> {
                        onSave(
                            Asset(
                                symbol = symbol.trim(),
                                name = name.trim()
                                    .ifBlank { symbol.trim() },
                                type = type,
                                marketSymbol = marketSymbol
                                    .trim()
                                    .ifBlank { symbol.trim() },
                                coinId = coinId
                                    .trim()
                                    .ifBlank { null }
                            )
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (initial == null) {
                    "Add to watchlist"
                } else {
                    "Save changes"
                }
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: SettingsStore,
    repository: MarketRepository,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val ai = remember {
        AiProviderSettings(context)
    }

    var provider by remember {
        mutableStateOf(ai.provider())
    }

    var apiKey by remember {
        mutableStateOf(ai.getKey())
    }

    var model by remember {
        mutableStateOf(ai.model())
    }

    var models by remember {
        mutableStateOf(listOf<String>())
    }

    var modelStatus by remember {
        mutableStateOf(
            "Load models after saving your API key."
        )
    }

    var apiStatus by remember {
        mutableStateOf(
            if (ai.hasKey()) {
                "Saved key(s) — not tested yet."
            } else {
                "No API key saved yet."
            }
        )
    }

    var testing by remember {
        mutableStateOf(false)
    }

    var loadingModels by remember {
        mutableStateOf(false)
    }

    var minutes by remember {
        mutableStateOf(
            settings.refreshMinutes().toString()
        )
    }

    var notifications by remember {
        mutableStateOf(
            settings.notificationsEnabled()
        )
    }

    fun switchProvider(next: AiProvider) {
        provider = next
        ai.setProvider(next)
        apiKey = ai.getKey()
        model = ai.model()
        models = emptyList()

        modelStatus =
            "Save the ${next.label} key, then load its models."

        apiStatus =
            if (ai.hasKey()) {
                "${ai.getKeys().size} saved key(s) — ready for automatic failover."
            } else {
                "No ${next.label} API key saved."
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        item {

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = if (darkMode) {
                        "Dark mode"
                    } else {
                        "Light mode"
                    }
                )

                Switch(
                    checked = darkMode,
                    onCheckedChange = onDarkMode
                )
            }

            Text(
                text = "Theme changes instantly and are saved on the device.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = "AI provider",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Choose which provider powers market analysis. Keys are encrypted on this device.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                AiProvider.values().forEach { option ->

                    if (option == provider) {

                        Button(
                            onClick = {
                                switchProvider(option)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(option.label)
                        }

                    } else {

                        OutlinedButton(
                            onClick = {
                                switchProvider(option)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }

            AiKeyPoolControls(
                ai = ai,
                provider = provider
            ) {
                apiStatus = it
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                },
                label = {
                    Text(
                        "Quick test / add ${provider.label} API key"
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = apiStatus,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Button(
                onClick = {

                    val key = apiKey.trim()

                    if (key.isEmpty()) {
                        apiStatus = "API key is empty."
                        return@Button
                    }

                    ai.setProvider(provider)

                    ai.setModel(
                        model.trim().ifBlank {
                            provider.defaultModel
                        }
                    )

                    val save = ai.saveKey(key)

                    if (save.isFailure) {
                        apiStatus =
                            "Save error: ${
                                save.exceptionOrNull()?.message
                                    ?: "unknown error"
                            }"

                        return@Button
                    }

                    testing = true

                    apiStatus =
                        "Key saved. Testing ${provider.label}…"

                    scope.launch {

                        val result =
                            withContext(Dispatchers.IO) {
                                AiClient(context).test()
                            }

                        testing = false
                        apiStatus = result.message
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (testing) {
                        "Testing API…"
                    } else {
                        "Save & test API"
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                },
                label = {
                    Text("Model ID")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Default: ${provider.defaultModel}",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {

                    if (!ai.hasKey()) {
                        modelStatus =
                            "Save the API key first."
                        return@Button
                    }

                    loadingModels = true

                    modelStatus =
                        "Loading available ${provider.label} models…"

                    scope.launch {

                        val result =
                            withContext(Dispatchers.IO) {
                                AiClient(context).models()
                            }

                        loadingModels = false
                        models = result.models
                        modelStatus = result.message
                    }
                },
                enabled = !loadingModels,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (loadingModels) {
                        "Loading models…"
                    } else {
                        "Load available models"
                    }
                )
            }

            Text(
                text = modelStatus,
                style = MaterialTheme.typography.bodySmall
            )

            if (models.isNotEmpty()) {

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    models.forEach { id ->

                        OutlinedButton(
                            onClick = {
                                model = id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (id == model) {
                                    "✓ $id"
                                } else {
                                    id
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Background refresh",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = minutes,
                onValueChange = {
                    minutes = it.filter(Char::isDigit)
                },
                label = {
                    Text("Minutes (15–1440)")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text("Market notifications")

                Switch(
                    checked = notifications,
                    onCheckedChange = {
                        notifications = it
                    }
                )
            }
        }

        item {
            WidgetAppearanceControls()
        }

        item {

            Button(
                onClick = {

                    ai.setProvider(provider)

                    ai.setModel(
                        model.trim().ifBlank {
                            provider.defaultModel
                        }
                    )

                    if (apiKey.isNotBlank()) {
                        ai.saveKey(apiKey.trim())
                    }

                    settings.setNotificationsEnabled(
                        notifications
                    )

                    val period =
                        minutes
                            .toLongOrNull()
                            ?.coerceIn(15L, 1440L)
                            ?: 60L

                    settings.setRefreshMinutes(period)

                    WorkManager
                        .getInstance(context)
                        .enqueueUniquePeriodicWork(
                            "marketmind_refresh",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<MarketRefreshWorker>(
                                period,
                                TimeUnit.MINUTES
                            ).build()
                        )

                    onSaved()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save settings & close")
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}
