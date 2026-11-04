package com.jarvis.assistant.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gerenciador seguro de chaves de API utilizando Android Keystore com AES-256 GCM.
 * Não utiliza fallbacks em texto puro para garantir a segurança dos tokens do usuário.
 */
class SecureKeyStoreManager(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = try {
            createEncryptedPreferences(context)
        } catch (e: Exception) {
            // Em caso de corrupção ou reinicialização de Keystore após restauração de sistema,
            // limpa o arquivo corrompido e recria a MasterKey para prevenir travamentos
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(PREF_FILE_NAME)
                }
                createEncryptedPreferences(context)
            } catch (fatal: Exception) {
                throw SecurityException("Falha crítica ao inicializar Android Keystore AES-256: ${fatal.message}", fatal)
            }
        }

        // Migração pontual e exclusão de arquivo fallback legado se existir
        migrateLegacyKeysIfAny(context)
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREF_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateLegacyKeysIfAny(context: Context) {
        try {
            val legacyPref = context.getSharedPreferences("jarvis_fallback_keys", Context.MODE_PRIVATE)
            val allKeys = legacyPref.all
            if (allKeys.isNotEmpty()) {
                val editor = sharedPreferences.edit()
                for ((k, v) in allKeys) {
                    if (v is String && !sharedPreferences.contains(k)) {
                        editor.putString(k, v)
                    }
                }
                editor.apply()
                legacyPref.edit().clear().apply()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences("jarvis_fallback_keys")
                }
            }
        } catch (_: Exception) {
            // Silencioso se arquivo legado não existir
        }
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        sharedPreferences.edit().putString("key_$providerId", apiKey.trim()).apply()
    }

    fun getApiKey(providerId: String): String {
        return sharedPreferences.getString("key_$providerId", "") ?: ""
    }

    fun hasKey(providerId: String): Boolean {
        return getApiKey(providerId).isNotBlank()
    }

    fun removeApiKey(providerId: String) {
        sharedPreferences.edit().remove("key_$providerId").apply()
    }

    fun isKeystoreFunctional(): Boolean {
        return try {
            val testKey = "__keystore_healthcheck__"
            sharedPreferences.edit().putBoolean(testKey, true).commit()
            val ok = sharedPreferences.getBoolean(testKey, false)
            sharedPreferences.edit().remove(testKey).apply()
            ok
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREF_FILE_NAME = "jarvis_secure_keystore_v2"
    }
}
