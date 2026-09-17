package com.saloniq.marketmind

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiModelsResult(val success: Boolean, val models: List<String>, val message: String)
data class AiApiResult(val success: Boolean, val message: String)

class AiClient(context: Context) {
    private val settings = AiProviderSettings(context)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun test(): AiApiResult = withContext(Dispatchers.IO) {
        val provider = settings.provider()
        val key = settings.getKey().trim()
        if (key.isBlank()) return@withContext AiApiResult(false, "No ${provider.label} API key is saved.")
        val payload = JSONObject().put("model", settings.model()).put("temperature", 0.0).put("max_tokens", 1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with OK.")))
        val request = Request.Builder().url("${provider.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $key").addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        runCatching { client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) AiApiResult(true, "API OK — ${provider.label} works with ${settings.model()}.")
            else AiApiResult(false, "${provider.label} API error ${response.code}: ${errorMessage(body)}")
        }}.getOrElse { AiApiResult(false, "Connection error: ${it.message ?: "unknown network error"}") }
    }

    suspend fun models(): AiModelsResult = withContext(Dispatchers.IO) {
        val provider = settings.provider()
        val key = settings.getKey().trim()
        if (key.isBlank()) return@withContext AiModelsResult(false, emptyList(), "Save an API key first.")
        val request = Request.Builder().url("${provider.baseUrl}/models").addHeader("Authorization", "Bearer $key").get().build()
        runCatching { client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@use AiModelsResult(false, emptyList(), "${provider.label} API error ${response.code}: ${errorMessage(body)}")
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            val models = buildList {
                for (i in 0 until data.length()) {
                    val id = data.optJSONObject(i)?.optString("id").orEmpty()
                    if (id.isNotBlank() && isTextModel(id)) add(id)
                }
            }.distinct().sorted()
            if (models.isEmpty()) AiModelsResult(false, emptyList(), "No compatible text models returned.")
            else AiModelsResult(true, models, "Found ${models.size} compatible models.")
        }}.getOrElse { AiModelsResult(false, emptyList(), "Connection error: ${it.message ?: "unknown network error"}") }
    }

    suspend fun analyze(asset: Asset, quote: Quote): String = withContext(Dispatchers.IO) {
        val provider = settings.provider()
        val key = settings.getKey().trim()
        if (key.isBlank()) return@withContext "Add a ${provider.label} API key in Settings to enable AI analysis."
        val prompt = "Analyze ${asset.name} (${asset.symbol}). Current price: ${quote.price ?: "unknown"}. 24h change: ${quote.change24h ?: "unknown"}%. Give a short factual market summary, risks, and technical considerations. Do not present it as guaranteed investment advice."
        val payload = JSONObject().put("model", settings.model()).put("temperature", 0.2).put("max_tokens", 300)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "You are a concise, evidence-aware market analysis assistant. Clearly state uncertainty."))
                .put(JSONObject().put("role", "user").put("content", prompt)))
        val request = Request.Builder().url("${provider.baseUrl}/chat/completions").addHeader("Authorization", "Bearer $key").addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        runCatching { client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) "${provider.label} error ${response.code}: ${errorMessage(body)}"
            else JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }}.getOrElse { "${provider.label} analysis failed: ${it.message ?: "unknown error"}" }
    }

    private fun isTextModel(id: String): Boolean {
        val v = id.lowercase()
        return listOf("embed", "rerank", "tts", "asr", "ocr", "translate", "safety", "guard", "image", "video").none { v.contains(it) }
    }

    private fun errorMessage(body: String): String = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: body.take(300)
    }.getOrDefault(body.take(300).ifBlank { "no error details returned" })
}
