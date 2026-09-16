package com.saloniq.marketmind

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("marketmind", Context.MODE_PRIVATE)
    private val keyAlias = "marketmind_nvidia_key"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(256)
        return generator.generateKey()
    }

    fun saveNvidiaApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove("nvidia_key").remove("nvidia_iv").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString("nvidia_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("nvidia_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun getNvidiaApiKey(): String {
        val encrypted = prefs.getString("nvidia_key", null) ?: return ""
        val iv = prefs.getString("nvidia_iv", null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun saveAssets(assets: List<Asset>) {
        prefs.edit().putStringSet("assets", assets.map { encode(it) }.toSet()).apply()
    }

    fun loadAssets(): List<Asset> {
        val saved = prefs.getStringSet("assets", null)
        if (saved.isNullOrEmpty()) {
            return listOf(
                Asset("BTC", "Bitcoin", "Crypto", coinId = "bitcoin"),
                Asset("ETH", "Ethereum", "Crypto", coinId = "ethereum"),
                Asset("TSLA", "Tesla", "Stock"),
                Asset("CDR", "CD Projekt", "Stock", marketSymbol = "CDR.WA")
            )
        }
        return saved.mapNotNull { decode(it) }.sortedBy { it.symbol }
    }

    fun refreshMinutes(): Long = prefs.getLong("refresh_minutes", 60L)
    fun setRefreshMinutes(value: Long) = prefs.edit().putLong("refresh_minutes", value).apply()
    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications", true)
    fun setNotificationsEnabled(value: Boolean) = prefs.edit().putBoolean("notifications", value).apply()
    fun currency(): String = prefs.getString("currency", "USD") ?: "USD"
    fun setCurrency(value: String) = prefs.edit().putString("currency", value).apply()
    fun model(): String = prefs.getString("nvidia_model", "meta/llama-3.1-8b-instruct") ?: "meta/llama-3.1-8b-instruct"
    fun setModel(value: String) = prefs.edit().putString("nvidia_model", value).apply()

    private fun encode(a: Asset) = listOf(a.symbol, a.name, a.type, a.marketSymbol, a.coinId ?: "").joinToString("|")
    private fun decode(value: String): Asset? {
        val p = value.split("|", limit = 5)
        return if (p.size == 5) Asset(p[0], p[1], p[2], p[3], p[4].ifBlank { null }) else null
    }
}
