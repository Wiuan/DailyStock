package com.personal.portfolio.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * API Key 仅存于 Android Keystore 保护的 EncryptedSharedPreferences。
 * 禁止写入源码、日志、Room、普通 SharedPreferences。
 */
class SecureApiKeyStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_API).apply()
        } else {
            prefs.edit().putString(KEY_API, trimmed).apply()
        }
    }

    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    companion object {
        private const val FILE_NAME = "secure_ai_prefs"
        private const val KEY_API = "openai_compatible_api_key"
    }
}
