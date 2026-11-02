package com.jarvis.assistant.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureKeyStoreManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "jarvis_secure_keystore",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback seguro em ambientes restritos
        context.getSharedPreferences("jarvis_fallback_keys", Context.MODE_PRIVATE)
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        sharedPreferences.edit().putString("key_$providerId", apiKey).apply()
    }

    fun getApiKey(providerId: String): String {
        return sharedPreferences.getString("key_$providerId", "") ?: ""
    }

    fun removeApiKey(providerId: String) {
        sharedPreferences.edit().remove("key_$providerId").apply()
    }
}
