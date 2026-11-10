package com.jarvis.assistant.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStoreException

/**
 * Gerenciador seguro de chaves de API utilizando Android Keystore com AES-256 GCM.
 * Não utiliza fallbacks em texto puro para garantir a segurança dos tokens do usuário.
 * 
 * V3.1.1: Proteção contra exclusão acidental. Exceções genéricas ou de indisponibilidade
 * transitória do Keystore nunca provocam a eliminação do arquivo de chaves salvas.
 */
class SecureKeyStoreManager(context: Context) {
    private var sharedPreferences: SharedPreferences? = null
    var lastError: String? = null
        private set

    init {
        sharedPreferences = try {
            createEncryptedPreferences(context)
        } catch (e: Exception) {
            lastError = e.message ?: "Erro ao acessar Android Keystore"
            Log.e(TAG, "Aviso ao inicializar EncryptedSharedPreferences (dados preservados): ${e.message}")
            // Segunda tentativa segura sem apagar o arquivo
            try {
                createEncryptedPreferences(context)
            } catch (retryEx: Exception) {
                lastError = retryEx.message
                Log.e(TAG, "Falha persistente no Keystore. Chaves preservadas para recuperação posterior.", retryEx)
                null
            }
        }

        // Migração pontual de chaves legadas se houver
        if (sharedPreferences != null) {
            migrateLegacyKeysIfAny(context)
        }
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
        val prefs = sharedPreferences ?: return
        try {
            val legacyPref = context.getSharedPreferences("jarvis_fallback_keys", Context.MODE_PRIVATE)
            val allKeys = legacyPref.all
            if (allKeys.isNotEmpty()) {
                val editor = prefs.edit()
                for ((k, v) in allKeys) {
                    if (v is String && !prefs.contains(k)) {
                        editor.putString(k, v)
                    }
                }
                editor.apply()
                // Limpa legado somente após importação segura
                legacyPref.edit().clear().apply()
            }
        } catch (_: Exception) {
            // Silencioso se arquivo legado não existir ou estiver inacessível
        }
    }

    @Synchronized
    fun saveApiKey(providerId: String, apiKey: String) {
        val prefs = sharedPreferences
            ?: throw SecurityException("Armazenamento seguro temporariamente indisponível: ${lastError ?: "Keystore bloqueado"}")
        prefs.edit().putString("key_$providerId", apiKey.trim()).apply()
    }

    @Synchronized
    fun getApiKey(providerId: String): String {
        val prefs = sharedPreferences ?: return ""
        return prefs.getString("key_$providerId", "") ?: ""
    }

    fun hasKey(providerId: String): Boolean {
        return getApiKey(providerId).isNotBlank()
    }

    @Synchronized
    fun removeApiKey(providerId: String) {
        val prefs = sharedPreferences ?: return
        prefs.edit().remove("key_$providerId").apply()
    }

    fun isKeystoreFunctional(): Boolean {
        val prefs = sharedPreferences ?: return false
        return try {
            val testKey = "__keystore_healthcheck__"
            prefs.edit().putBoolean(testKey, true).commit()
            val ok = prefs.getBoolean(testKey, false)
            prefs.edit().remove(testKey).apply()
            ok
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "SecureKeyStoreManager"
        private const val PREF_FILE_NAME = "jarvis_secure_keystore_v2"
    }
}
