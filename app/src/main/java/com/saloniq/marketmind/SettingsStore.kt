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
    private fun secretKey(): SecretKey { val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }; val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"); g.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(128).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return g.generateKey() }
    fun saveNvidiaApiKey(value: String): Result<Unit> { val n=value.trim(); if(n.isEmpty()){ val c=prefs.edit().remove(KEY_ENCRYPTED).remove(KEY_IV).remove(KEY_HAS_VALUE).commit(); return if(c) Result.success(Unit) else Result.failure(IllegalStateException("SharedPreferences commit returned false")) }; return runCatching { val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,secretKey()); val e=c.doFinal(n.toByteArray(StandardCharsets.UTF_8)); check(prefs.edit().putString(KEY_ENCRYPTED,Base64.encodeToString(e,Base64.NO_WRAP)).putString(KEY_IV,Base64.encodeToString(c.iv,Base64.NO_WRAP)).putBoolean(KEY_HAS_VALUE,true).commit()) }
    }
    fun hasNvidiaApiKey() = prefs.getBoolean(KEY_HAS_VALUE,false) && !prefs.getString(KEY_ENCRYPTED,null).isNullOrBlank() && !prefs.getString(KEY_IV,null).isNullOrBlank()
    fun getNvidiaApiKey(): String { val e=prefs.getString(KEY_ENCRYPTED,null)?:return ""; val iv=prefs.getString(KEY_IV,null)?:return ""; return runCatching { val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP))); String(c.doFinal(Base64.decode(e,Base64.NO_WRAP)),StandardCharsets.UTF_8) }.getOrDefault("") }
    fun saveAssets(assets: List<Asset>) = prefs.edit().putStringSet("assets",assets.map{encode(it)}.toSet()).apply()
    fun loadAssets(): List<Asset> { val saved=prefs.getStringSet("assets",null); if(saved.isNullOrEmpty()) return listOf(Asset("BTC","Bitcoin","Crypto",coinId="bitcoin"),Asset("ETH","Ethereum","Crypto",coinId="ethereum"),Asset("TSLA","Tesla","Stock"),Asset("CDR","CD Projekt","Stock",marketSymbol="CDR.WA")); return saved.mapNotNull{decode(it)}.sortedBy{it.symbol} }
    fun refreshMinutes()=prefs.getLong("refresh_minutes",60L)
    fun setRefreshMinutes(value:Long)=prefs.edit().putLong("refresh_minutes",value).apply()
    fun notificationsEnabled()=prefs.getBoolean("notifications",true)
    fun setNotificationsEnabled(value:Boolean)=prefs.edit().putBoolean("notifications",value).apply()
    fun currency()=prefs.getString("currency","USD")?:"USD"
    fun setCurrency(value:String)=prefs.edit().putString("currency",value).apply()
    fun model():String { val saved=prefs.getString("nvidia_model",null).orEmpty(); return if(saved.isBlank()||saved=="meta/llama-3.1-8b-instruct") DEFAULT_NVIDIA_MODEL else saved }
    fun setModel(value:String)=prefs.edit().putString("nvidia_model",value).apply()
    fun darkMode()=prefs.getBoolean("dark_mode",true)
    fun setDarkMode(value:Boolean)=prefs.edit().putBoolean("dark_mode",value).apply()
    private fun encode(a:Asset)=listOf(a.symbol,a.name,a.type,a.marketSymbol,a.coinId?:"").joinToString("|")
    private fun decode(value:String):Asset? { val p=value.split("|",limit=5); return if(p.size==5) Asset(p[0],p[1],p[2],p[3],p[4].ifBlank{null}) else null }
    private companion object { const val DEFAULT_NVIDIA_MODEL="nvidia/nemotron-3.5-lightning-30b-a3b"; const val KEY_ENCRYPTED="nvidia_key_v4"; const val KEY_IV="nvidia_iv_v4"; const val KEY_HAS_VALUE="nvidia_key_saved_v4" }
}
