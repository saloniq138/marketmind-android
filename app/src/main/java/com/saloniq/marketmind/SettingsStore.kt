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
 * NVIDIA API key:
 * - encrypted with AES/GCM
 * - AES key stored in Android Keystore
 * - plaintext API key is never stored in SharedPreferences
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        "marketmind",
        Context.MODE_PRIVATE
    )

    /**
     * Version 4 uses a completely new Keystore alias.
     * This avoids conflicts with keys created by previous versions.
     */
    private val keyAlias = "marketmind_nvidia_key_v4"

    /**
     * Gets existing AES key or creates a new one.
     */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey

        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(128)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .build()

        keyGenerator.init(keySpec)

        return keyGenerator.generateKey()
    }

    /**
     * Saves NVIDIA API key.
     *
     * Result.success() = saved correctly.
     *
     * Result.failure() = contains the real exception, which is useful
     * for displaying the actual problem in the UI.
     */
    fun saveNvidiaApiKey(value: String): Result<Unit> {
        val normalized = value.trim()

        // Empty key = remove saved key.
        if (normalized.isEmpty()) {
            val committed = prefs.edit()
                .remove(KEY_ENCRYPTED)
                .remove(KEY_IV)
                .remove(KEY_HAS_VALUE)
                .commit()

            return if (committed) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(
                        "SharedPreferences commit returned false"
                    )
                )
            }
        }

        return runCatching {

            /*
             * Create AES/GCM cipher.
             */
            val cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            /*
             * Generate a random IV automatically.
             */
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey()
            )

            /*
             * Encrypt the API key.
             */
            val encrypted = cipher.doFinal(
                normalized.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

            /*
             * Convert encrypted data and IV to Base64.
             */
            val encryptedBase64 = Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP
            )

            val ivBase64 = Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )

            /*
             * Persist encrypted key.
             *
             * commit() is intentional here because the UI should know
             * immediately whether saving actually succeeded.
             */
            val committed = prefs.edit()
                .putString(
                    KEY_ENCRYPTED,
                    encryptedBase64
                )
                .putString(
                    KEY_IV,
                    ivBase64
                )
                .putBoolean(
                    KEY_HAS_VALUE,
                    true
                )
                .commit()

            check(committed) {
                "SharedPreferences commit returned false"
            }
        }
    }

    /**
     * Returns true when an encrypted NVIDIA API key exists.
     */
    fun hasNvidiaApiKey(): Boolean {
        return prefs.getBoolean(
            KEY_HAS_VALUE,
            false
        ) &&
            !prefs.getString(
                KEY_ENCRYPTED,
                null
            ).isNullOrBlank() &&
            !prefs.getString(
                KEY_IV,
                null
            ).isNullOrBlank()
    }

    /**
     * Decrypts the NVIDIA API key.
     *
     * Returns empty string if the key cannot be decrypted.
     */
    fun getNvidiaApiKey(): String {

        val encrypted = prefs.getString(
            KEY_ENCRYPTED,
            null
        ) ?: return ""

        val iv = prefs.getString(
            KEY_IV,
            null
        ) ?: return ""

        return runCatching {

            val cipher = Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(
                    128,
                    Base64.decode(
                        iv,
                        Base64.NO_WRAP
                    )
                )
            )

            val decrypted = cipher.doFinal(
                Base64.decode(
                    encrypted,
                    Base64.NO_WRAP
                )
            )

            String(
                decrypted,
                StandardCharsets.UTF_8
            )

        }.getOrDefault("")
    }

    /*
     * ------------------------------------------------------------
     * Assets
     * ------------------------------------------------------------
     */

    fun saveAssets(assets: List<Asset>) {
        prefs.edit()
            .putStringSet(
                "assets",
                assets.map {
                    encode(it)
                }.toSet()
            )
            .apply()
    }

    fun loadAssets(): List<Asset> {

        val saved = prefs.getStringSet(
            "assets",
            null
        )

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
            .mapNotNull {
                decode(it)
            }
            .sortedBy {
                it.symbol
            }
    }

    /*
     * ------------------------------------------------------------
     * Other settings
     * ------------------------------------------------------------
     */

    fun refreshMinutes(): Long {
        return prefs.getLong(
            "refresh_minutes",
            60L
        )
    }

    fun setRefreshMinutes(value: Long) {
        prefs.edit()
            .putLong(
                "refresh_minutes",
                value
            )
            .apply()
    }

    fun notificationsEnabled(): Boolean {
        return prefs.getBoolean(
            "notifications",
            true
        )
    }

    fun setNotificationsEnabled(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "notifications",
                value
            )
            .apply()
    }

    fun currency(): String {
        return prefs.getString(
            "currency",
            "USD"
        ) ?: "USD"
    }

    fun setCurrency(value: String) {
        prefs.edit()
            .putString(
                "currency",
                value
            )
            .apply()
    }

    fun model(): String {
        return prefs.getString(
            "nvidia_model",
            "meta/llama-3.1-8b-instruct"
        ) ?: "meta/llama-3.1-8b-instruct"
    }

    fun setModel(value: String) {
        prefs.edit()
            .putString(
                "nvidia_model",
                value
            )
            .apply()
    }

    /*
     * ------------------------------------------------------------
     * Asset serialization
     * ------------------------------------------------------------
     */

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

        val parts = value.split(
            "|",
            limit = 5
        )

        return if (parts.size == 5) {

            Asset(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4].ifBlank {
                    null
                }
            )

        } else {
            null
        }
    }

    /*
     * ------------------------------------------------------------
     * NVIDIA encrypted storage keys
     * ------------------------------------------------------------
     */

    private companion object {

        const val KEY_ENCRYPTED =
            "nvidia_key_v4"

        const val KEY_IV =
            "nvidia_iv_v4"

        const val KEY_HAS_VALUE =
            "nvidia_key_saved_v4"
    }
}
