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
    private val prefs = context.applicationContext.getSharedPreferences("marketmind_ai", Context.MODE_PRIVATE)
    private val keyAlias = "marketmind_ai_keys_v1"

    fun provider(): AiProvider = runCatching {
        AiProvider.valueOf(prefs.getString("provider", AiProvider.NVIDIA.name) ?: AiProvider.NVIDIA.name)
    }.getOrDefault(AiProvider.NVIDIA)

    fun setProvider(value: AiProvider) = prefs.edit().putString("provider", value.name).apply()

    fun model(): String = prefs.getString("model_${provider().name}", null).orEmpty().ifBlank { provider().defaultModel }

    fun setModel(value: String) = prefs.edit().putString("model_${provider().name}", value.trim()).apply()

    fun hasKey(): Boolean = getKey().isNotBlank()

    fun getKey(): String = getEncrypted("key_${provider().name}")

    fun saveKey(value: String): Result<Unit> {
        val name = "key_${provider().name}"
        if (value.trim().isEmpty()) return runCatching { prefs.edit().remove(name).commit(); Unit }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.trim().toByteArray(StandardCharsets.UTF_8))
            check(prefs.edit()
                .putString("${name}_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("${name}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit())
        }
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
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(128)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
