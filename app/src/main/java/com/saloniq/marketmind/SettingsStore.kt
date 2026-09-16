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

/**
 * Persistent app settings.
 *
 * The NVIDIA API key is encrypted with AES/GCM using a key stored in
 * Android Keystore. SharedPreferences only stores the encrypted value
 * and IV, never the plaintext API key.
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        "marketmind",
        Context.MODE_PRIVATE
    )

    /*
     * v3 intentionally uses a new Keystore alias so that installations
     * containing the previous v2 key are not affected by the new format.
     */
    private val keyAlias = "marketmind_nvidia_key_v3"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(128)
            .build()

        generator.init(spec)

        return generator.generateKey()
    }

    /**
     * Saves the NVIDIA API key synchronously.
     *
     * Returns true only when SharedPreferences successfully commits.
     */
    fun saveNvidiaApiKey(value: String): Boolean {
        val normalized = value.trim()

        // Empty value means remove the saved API key.
        if (normalized.isEmpty()) {
            return prefs.edit()
                .remove(KEY_ENCRYPTED)
                .remove(KEY_IV)
                .remove(KEY_HAS_VALUE)
                .commit()
        }

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey()
            )

            val encrypted = cipher.doFinal(
                normalized.toByteArray(StandardCharsets.UTF_8)
            )

            val encryptedBase64 = Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP
            )

            val ivBase64 = Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )

            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED, encryptedBase64)
                .putString(KEY_IV, ivBase64)
                .putBoolean(KEY_HAS_VALUE, true)
                .commit()

            if (!committed) {
                throw IllegalStateException(
                    "SharedPreferences commit returned false"
                )
            }

            true
        }.getOrElse {
            false
        }
    }

    fun hasNvidiaApiKey(): Boolean {
        return prefs.getBoolean(KEY_HAS_VALUE, false) &&
            !prefs.getString(KEY_ENCRYPTED, null).isNullOrBlank() &&
            !prefs.getString(KEY_IV, null).isNullOrBlank()
    }

    /**
     * Decrypts the NVIDIA API key.
     *
     * Returns an empty string when the key cannot be decrypted.
     */
    fun getNvidiaApiKey(): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED, null)
            ?: return ""

        val iv = prefs.getString(KEY_IV, null)
            ?: return ""

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(
                    128,
                    Base64.decode(iv, Base64.NO_WRAP)
                )
            )

            val decrypted = cipher.doFinal(
                Base64.decode(encrypted, Base64.NO_WRAP)
            )

            String(
                decrypted,
                StandardCharsets.UTF_8
            )
        }.getOrDefault("")
    }

    fun saveAssets(assets: List<Asset>) {
        prefs.edit()
            .putStringSet(
                "assets",
                assets.map { encode(it) }.toSet()
            )
            .apply()
    }

    fun loadAssets(): List<Asset> {
        val saved = prefs.getStringSet("assets", null)

        if (saved.isNullOrEmpty()) {
            return listOf(
                Asset(
                    "BTC",
                    "Bitcoin",
                    "Crypto",
                    coinId = "bitcoin"
                ),
                Asset(
                    "ETH",
                    "Ethereum",
                    "Crypto",
                    coinId = "ethereum"
                ),
                Asset(
                    "TSLA",
                    "Tesla",
                    "Stock"
                ),
                Asset(
                    "CDR",
                    "CD Projekt",
                    "Stock",
                    marketSymbol = "CDR.WA"
                )
            )
        }

        return saved
            .mapNotNull { decode(it) }
            .sortedBy { it.symbol }
    }

    fun refreshMinutes(): Long =
        prefs.getLong("refresh_minutes", 60L)

    fun setRefreshMinutes(value: Long) =
        prefs.edit()
            .putLong("refresh_minutes", value)
            .apply()

    fun notificationsEnabled(): Boolean =
        prefs.getBoolean("notifications", true)

    fun setNotificationsEnabled(value: Boolean) =
        prefs.edit()
            .putBoolean("notifications", value)
            .apply()

    fun currency(): String =
        prefs.getString("currency", "USD") ?: "USD"

    fun setCurrency(value: String) =
        prefs.edit()
            .putString("currency", value)
            .apply()

    fun model(): String =
        prefs.getString(
            "nvidia_model",
            "meta/llama-3.1-8b-instruct"
        ) ?: "meta/llama-3.1-8b-instruct"

    fun setModel(value: String) =
        prefs.edit()
            .putString("nvidia_model", value)
            .apply()

    private fun encode(a: Asset): String {
        return listOf(
            a.symbol,
            a.name,
            a.type,
            a.marketSymbol,
            a.coinId ?: ""
        ).joinToString("|")
    }

    private fun decode(value: String): Asset? {
        val parts = value.split("|", limit = 5)

        return if (parts.size == 5) {
            Asset(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4].ifBlank { null }
            )
        } else {
            null
        }
    }

    private companion object {
        /*
         * v3 prevents conflicts with the old v2 encrypted format.
         */
        const val KEY_ENCRYPTED = "nvidia_key_v3"
        const val KEY_IV = "nvidia_iv_v3"
        const val KEY_HAS_VALUE = "nvidia_key_saved_v3"
    }
}
