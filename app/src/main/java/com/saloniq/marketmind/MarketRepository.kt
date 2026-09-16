package com.saloniq.marketmind

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MarketRepository(private val context: Context) {
    private val settings = SettingsStore(context)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun fetchQuote(asset: Asset): Quote = withContext(Dispatchers.IO) {
        runCatching {
            if (asset.type == "Crypto") fetchCrypto(asset) else fetchStock(asset)
        }.getOrElse { Quote(signal = "Data unavailable") }
    }

    private fun fetchCrypto(asset: Asset): Quote {
        val id = asset.coinId ?: asset.symbol.lowercase()
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=$id&vs_currencies=usd&include_24hr_change=true"
        val body = request(url) ?: return Quote(signal = "Crypto data unavailable")
        val item = JSONObject(body).optJSONObject(id) ?: return Quote(signal = "Crypto not found")
        val price = item.optDouble("usd").takeUnless { it.isNaN() }
        val change = item.optDouble("usd_24h_change").takeUnless { it.isNaN() }
        return Quote(price, change, signalFromChange(change))
    }

    private fun fetchStock(asset: Asset): Quote {
        val encoded = asset.marketSymbol.replace("/", "%2F")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=5d"
        val body = request(url) ?: return Quote(signal = "Stock data unavailable")
        val result = JSONObject(body).getJSONArray("chart").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val price = meta.optDouble("regularMarketPrice").takeUnless { it.isNaN() }
        val previous = meta.optDouble("previousClose").takeUnless { it.isNaN() }
        val change = if (price != null && previous != null && previous != 0.0) (price - previous) / previous * 100.0 else null
        return Quote(price, change, signalFromChange(change))
    }

    suspend fun analyzeWithNvidia(asset: Asset, quote: Quote): String = withContext(Dispatchers.IO) {
        val key = settings.getNvidiaApiKey()
        if (key.isBlank()) return@withContext "Add your NVIDIA API key in Settings to enable AI analysis."
        val prompt = "Analyze ${asset.name} (${asset.symbol}). Current price: ${quote.price ?: "unknown"}. 24h change: ${quote.change24h ?: "unknown"}%. Give a short factual market summary, risks, and technical considerations. Do not present it as guaranteed investment advice."
        val payload = JSONObject()
            .put("model", settings.model())
            .put("temperature", 0.2)
            .put("max_tokens", 300)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are a financial market analysis assistant. Be concise, evidence-aware and clearly state uncertainty."))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) "NVIDIA API error ${response.code}: ${JSONObject(text).optString("detail", "request failed")}" else
                    JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No analysis returned.")
            }
        }.getOrElse { "NVIDIA request failed: ${it.message ?: "unknown error"}" }
    }

    suspend fun refreshAll() {
        val assets = settings.loadAssets()
        val quotes = assets.associate { it.symbol to fetchQuote(it) }
        context.getSharedPreferences("market_quotes", Context.MODE_PRIVATE).edit().apply {
            quotes.forEach { (symbol, quote) ->
                putString(symbol, JSONObject().put("price", quote.price).put("change", quote.change24h).put("signal", quote.signal).toString())
            }
        }.apply()
        runCatching { MarketMindWidget().updateAll(context) }
    }

    fun cachedQuote(symbol: String): Quote? {
        val raw = context.getSharedPreferences("market_quotes", Context.MODE_PRIVATE).getString(symbol, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Quote(json.optDouble("price").takeUnless { it.isNaN() }, json.optDouble("change").takeUnless { it.isNaN() }, signal = json.optString("signal"))
        }.getOrNull()
    }

    private fun request(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).addHeader("User-Agent", "MarketMind/1.0").build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun signalFromChange(change: Double?): String = when {
        change == null -> "Waiting for data"
        change <= -5.0 -> "Large move down — review risk"
        change <= -2.0 -> "Down — watch technicals"
        change >= 5.0 -> "Large move up — avoid chasing"
        change >= 2.0 -> "Up — watch momentum"
        else -> "Neutral — monitor"
    }
}
