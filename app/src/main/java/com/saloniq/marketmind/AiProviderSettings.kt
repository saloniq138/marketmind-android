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

enum class AiProvider(val label: String, val defaultModel: String, val baseUrl: String) {
    NVIDIA("NVIDIA NIM", "nvidia/nemotron-3.5-lightning-30b-a3b", "https://integrate.api.nvidia.com/v1"),
    OPENAI("OpenAI", "gpt-5.6-luna", "https://api.openai.com/v1"),
    GROQ("Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1")
}

class AiProviderSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("marketmind_ai", Context.MODE_PRIVATE)
    private val keyAlias = "marketmind_ai_keys_v1"

    fun provider(): AiProvider = runCatching { AiProvider.valueOf(prefs.getString("provider", AiProvider.NVIDIA.name) ?: AiProvider.NVIDIA.name) }.getOrDefault(AiProvider.NVIDIA)
    fun setProvider(value: AiProvider) = prefs.edit().putString("provider", value.name).apply()
    fun model(): String = prefs.getString("model_${provider().name}", null).orEmpty().ifBlank { provider().defaultModel }
    fun setModel(value: String) = prefs.edit().putString("model_${provider().name}", value.trim()).apply()

    fun getKeys(): List<String> {
        val p = provider()
        migrateLegacyIfNeeded(p)
        val count = prefs.getInt("key_count_${p.name}", 0)
        return (0 until count).mapNotNull { index -> getEncrypted("key_${p.name}_$index").takeIf { it.isNotBlank() } }
    }

    fun hasKey(): Boolean = getKeys().isNotEmpty()
    fun getKey(): String = getKeys().firstOrNull().orEmpty()

    fun addKey(value: String): Result<Unit> {
        val clean = value.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("API key is empty"))
        val p = provider()
        migrateLegacyIfNeeded(p)
        val keys = getKeys()
        if (keys.any { it == clean }) return Result.success(Unit)
        return runCatching {
            val index = keys.size
            encryptInto("key_${p.name}_$index", clean)
            check(prefs.edit().putInt("key_count_${p.name}", index + 1).commit())
        }
    }

    fun removeKey(index: Int): Boolean {
        val p = provider()
        val keys = getKeys().toMutableList()
        if (index !in keys.indices) return false
        keys.removeAt(index)
        val oldCount = prefs.getInt("key_count_${p.name}", 0)
        val editor = prefs.edit()
        (0 until oldCount).forEach { i ->
            editor.remove("key_${p.name}_$i_data").remove("key_${p.name}_$i_iv")
        }
        keys.forEachIndexed { i, key ->
            val encrypted = encryptValue(key)
            editor.putString("key_${p.name}_${i}_data", encrypted.first)
            editor.putString("key_${p.name}_${i}_iv", encrypted.second)
        }
        return editor.putInt("key_count_${p.name}", keys.size).commit()
    }

    private fun migrateLegacyIfNeeded(p: AiProvider) {
        if (p != AiProvider.NVIDIA || prefs.getInt("key_count_${p.name}", 0) > 0) return
        val legacy = SettingsStore(appContext).getNvidiaApiKey().trim()
        if (legacy.isBlank()) return
        runCatching {
            encryptInto("key_${p.name}_0", legacy)
            prefs.edit().putInt("key_count_${p.name}", 1).commit()
        }
    }

    private fun encryptValue(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun encryptInto(name: String, value: String) {
        val encrypted = encryptValue(value)
        check(prefs.edit().putString("${name}_data", encrypted.first).putString("${name}_iv", encrypted.second).commit())
    }

    private fun getEncrypted(name: String): String {
        val data = prefs.getString("${name}_data", null) ?: return ""
        val iv = prefs.getString("${name}_iv", null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(128).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}
