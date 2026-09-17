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
        val keys = settings.getKeys()
        if (keys.isEmpty()) return@withContext AiApiResult(false, "No ${provider.label} API keys are saved.")
        var last = "No API response."
        keys.forEachIndexed { index, key ->
            if (index > 0 && !shouldFailover(last)) return@withContext AiApiResult(false, last)
            val response = execute(requestFor("Reply with OK.", true, provider, key))
            if (response.success) return@withContext AiApiResult(true, if (index == 0) "API OK — ${provider.label} works with ${settings.model()}." else "API OK — automatically switched to backup key #${index + 1}.")
            last = "${provider.label} API error ${response.code}: ${response.message}"
        }
        AiApiResult(false, "All ${provider.label} API keys failed. Last error: $last")
    }

    suspend fun models(): AiModelsResult = withContext(Dispatchers.IO) {
        val provider = settings.provider()
        val keys = settings.getKeys()
        if (keys.isEmpty()) return@withContext AiModelsResult(false, emptyList(), "Save at least one API key first.")
        var last = "No API response."
        keys.forEachIndexed { index, key ->
            val response = execute(Request.Builder().url("${provider.baseUrl}/models").addHeader("Authorization", "Bearer $key").get().build())
            if (response.success) {
                val data = JSONObject(response.body).optJSONArray("data") ?: JSONArray()
                val models = buildList {
                    for (i in 0 until data.length()) {
                        val id = data.optJSONObject(i)?.optString("id").orEmpty()
                        if (id.isNotBlank() && isTextModel(id)) add(id)
                    }
                }.distinct().sorted()
                return@withContext if (models.isEmpty()) AiModelsResult(false, emptyList(), "No compatible text models returned.")
                else AiModelsResult(true, models, if (index == 0) "Found ${models.size} compatible models." else "Found ${models.size} models using backup API key #${index + 1}.")
            }
            last = "${provider.label} API error ${response.code}: ${response.message}"
            if (!shouldFailover(last)) return@withContext AiModelsResult(false, emptyList(), last)
        }
        AiModelsResult(false, emptyList(), "All ${provider.label} API keys failed. Last error: $last")
    }

    suspend fun analyze(asset: Asset, quote: Quote): String = withContext(Dispatchers.IO) {
        val provider = settings.provider()
        val keys = settings.getKeys()
        if (keys.isEmpty()) return@withContext "Add at least one ${provider.label} API key in Settings to enable AI analysis."
        val prompt = "Analyze ${asset.name} (${asset.symbol}). Current price: ${quote.price ?: "unknown"}. 24h change: ${quote.change24h ?: "unknown"}%. Give a short factual market summary, risks, and technical considerations. Do not present it as guaranteed investment advice."
        var last = "No API response."
        keys.forEachIndexed { index, key ->
            val response = execute(requestFor(prompt, false, provider, key))
            if (response.success) {
                return@withContext runCatching { parseText(response.body, provider) }.getOrElse { "${provider.label} returned an unreadable response." } +
                    if (index > 0) "\n\n(Auto-switched to backup API key #${index + 1}.)" else ""
            }
            last = "${provider.label} error ${response.code}: ${response.message}"
            if (!shouldFailover(last)) return@withContext last
        }
        "All ${provider.label} API keys failed. Last error: $last"
    }

    private data class HttpResult(val success: Boolean, val code: Int, val body: String, val message: String)

    private fun execute(request: Request): HttpResult = runCatching {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            HttpResult(response.isSuccessful, response.code, body, if (response.isSuccessful) "" else errorMessage(body))
        }
    }.getOrElse { HttpResult(false, -1, "", it.message ?: "unknown network error") }

    private fun requestFor(prompt: String, test: Boolean, provider: AiProvider, key: String): Request {
        return if (provider == AiProvider.OPENAI) {
            val payload = JSONObject().put("model", settings.model()).put("input", prompt)
            Request.Builder().url("${provider.baseUrl}/responses").addHeader("Authorization", "Bearer $key").addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        } else {
            val payload = JSONObject().put("model", settings.model()).put("temperature", if (test) 0.0 else 0.2).put("max_tokens", if (test) 1 else 300)
                .put("messages", JSONArray().apply {
                    if (!test) put(JSONObject().put("role", "system").put("content", "You are a concise, evidence-aware market analysis assistant. Clearly state uncertainty."))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            Request.Builder().url("${provider.baseUrl}/chat/completions").addHeader("Authorization", "Bearer $key").addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        }
    }

    private fun parseText(body: String, provider: AiProvider): String {
        if (provider == AiProvider.OPENAI) {
            val output = JSONObject(body).optJSONArray("output") ?: return "OpenAI returned no text."
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val text = content.optJSONObject(j)?.optString("text").orEmpty()
                    if (text.isNotBlank()) return text
                }
            }
            return "OpenAI returned no text."
        }
        return JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun isTextModel(id: String): Boolean {
        val v = id.lowercase()
        return listOf("embed", "rerank", "tts", "asr", "ocr", "translate", "safety", "guard", "image", "video").none { v.contains(it) }
    }

    private fun shouldFailover(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("api error 401", "api error 403", "api error 408", "api error 409", "api error 429", "api error 500", "api error 502", "api error 503", "api error 504", "connection error", "timeout").any { lower.contains(it) }
    }

    private fun errorMessage(body: String): String = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: body.take(300)
    }.getOrDefault(body.take(300).ifBlank { "no error details returned" })
}
