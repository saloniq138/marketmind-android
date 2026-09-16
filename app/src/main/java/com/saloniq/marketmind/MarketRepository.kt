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

    suspend fun testNvidiaApi(): NvidiaApiResult = withContext(Dispatchers.IO) {
        val key = settings.getNvidiaApiKey().trim()
        if (key.isBlank()) return@withContext NvidiaApiResult(false, "No NVIDIA API key is saved.")

        val payload = JSONObject()
            .put("model", settings.model())
            .put("temperature", 0.0)
            .put("max_tokens", 1)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "user").put("content", "Reply with OK.")))

        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    NvidiaApiResult(true, "API OK — NVIDIA NIM is reachable and the key is valid.")
                } else {
                    NvidiaApiResult(false, formatNvidiaError(response.code, body))
                }
            }
        }.getOrElse { error ->
            NvidiaApiResult(false, "Connection error: ${error.message ?: "unknown network error"}")
        }
    }

    private fun formatNvidiaError(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return if (detail != null) "NVIDIA API error $code: $detail"
        else "NVIDIA API error $code: ${body.take(300).ifBlank { "no error details returned" }}"
    }

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
        return Quote(price = price, change24h = change, signal = signalFromChange(change))
    }

    private fun fetchStock(asset: Asset): Quote {
        val encoded = asset.marketSymbol.replace("/", "%2F")
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=5d"
        val body = request(url) ?: return Quote(signal = "Stock data unavailable")
        val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val price = meta.optDouble("regularMarketPrice").takeUnless { it.isNaN() }
        val previous = meta.optDouble("previousClose").takeUnless { it.isNaN() }
        val change = if (price != null && previous != null && previous != 0.0) (price - previous) / previous * 100.0 else null
        return Quote(price = price, change24h = change, signal = signalFromChange(change))
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
                if (!response.isSuccessful) formatNvidiaError(response.code, text)
                else JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        }.getOrElse { "NVIDIA analysis failed: ${it.message ?: "unknown error"}" }
    }

    suspend fun refreshAll() {
        val assets = settings.loadAssets()
        for (asset in assets) {
            val quote = fetchQuote(asset)
            saveQuote(asset.symbol, quote)
        }
        MarketMindWidget().updateAll(context)
    }

    fun cachedQuote(symbol: String): Quote? {
        val prefs = context.getSharedPreferences("market_quotes", Context.MODE_PRIVATE)
        val raw = prefs.getString(symbol, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Quote(
                price = if (json.has("price") && !json.isNull("price")) json.getDouble("price") else null,
                change24h = if (json.has("change24h") && !json.isNull("change24h")) json.getDouble("change24h") else null,
                signal = json.optString("signal", "Waiting for analysis"),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun saveQuote(symbol: String, quote: Quote) {
        val json = JSONObject()
            .put("price", quote.price)
            .put("change24h", quote.change24h)
            .put("signal", quote.signal)
            .put("updatedAt", quote.updatedAt)
        context.getSharedPreferences("market_quotes", Context.MODE_PRIVATE)
            .edit().putString(symbol, json.toString()).apply()
    }

    private fun request(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun signalFromChange(change: Double?): String = when {
        change == null -> "Waiting for analysis"
        change >= 3.0 -> "Strong upward move"
        change >= 0.5 -> "Upward move"
        change <= -3.0 -> "Strong downward move"
        change <= -0.5 -> "Downward move"
        else -> "Mostly unchanged"
    }
}

data class NvidiaApiResult(val success: Boolean, val message: String)
