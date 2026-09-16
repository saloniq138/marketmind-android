package com.saloniq.marketmind

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("marketmind", Context.MODE_PRIVATE)
    private val keyAlias = "marketmind_nvidia_key_v4"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(128)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun saveNvidiaApiKey(value: String): Result<Unit> {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            val committed = prefs.edit()
                .remove(KEY_ENCRYPTED).remove(KEY_IV).remove(KEY_HAS_VALUE).commit()
            return if (committed) Result.success(Unit)
            else Result.failure(IllegalStateException("SharedPreferences commit returned false"))
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putBoolean(KEY_HAS_VALUE, true)
                .commit()
            check(committed) { "SharedPreferences commit returned false" }
        }
    }

    fun hasNvidiaApiKey(): Boolean =
        prefs.getBoolean(KEY_HAS_VALUE, false) &&
            !prefs.getString(KEY_ENCRYPTED, null).isNullOrBlank() &&
            !prefs.getString(KEY_IV, null).isNullOrBlank()

    fun getNvidiaApiKey(): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED, null) ?: return ""
        val iv = prefs.getString(KEY_IV, null) ?: return ""
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

    fun model(): String {
        val saved = prefs.getString("nvidia_model", null).orEmpty()
        return if (saved.isBlank() || saved == "meta/llama-3.1-8b-instruct") DEFAULT_NVIDIA_MODEL else saved
    }

    fun setModel(value: String) = prefs.edit().putString("nvidia_model", value).apply()

    private fun encode(a: Asset) =
        listOf(a.symbol, a.name, a.type, a.marketSymbol, a.coinId ?: "").joinToString("|")

    private fun decode(value: String): Asset? {
        val p = value.split("|", limit = 5)
        return if (p.size == 5) Asset(p[0], p[1], p[2], p[3], p[4].ifBlank { null }) else null
    }

    private companion object {
        const val DEFAULT_NVIDIA_MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"
        const val KEY_ENCRYPTED = "nvidia_key_v4"
        const val KEY_IV = "nvidia_iv_v4"
        const val KEY_HAS_VALUE = "nvidia_key_saved_v4"
    }
}
