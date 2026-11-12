package com.jarvis.assistant.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStoreException
import java.security.MessageDigest

data class CredentialVerificationResult(
    val isStored: Boolean,
    val isDecrypted: Boolean,
    val fingerprint: String? = null,
    val length: Int = 0,
    val errorMessage: String? = null
)

/**
 * Gerenciador seguro de chaves de API utilizando Android Keystore com AES-256 GCM.
 * V3.1.2: Migração transparente de todas as versões anteriores (jarvis_secure_keystore,
 * jarvis_fallback_keys, jarvis_settings), persistência garantida com commit síncrono,
 * teste interno de descriptografia com fingerprint segura (sem expor segredos) e
 * tolerância a falhas sem exclusão destrutiva.
 */
class SecureKeyStoreManager(private val context: Context) {

    private var sharedPreferences: SharedPreferences? = null
    var lastError: String? = null
        private set

    init {
        initializeKeystore()
        if (sharedPreferences != null) {
            migrateAllLegacyStores()
        }
    }

    private fun initializeKeystore() {
        sharedPreferences = try {
            createEncryptedPreferences(context)
        } catch (e: Exception) {
            lastError = e.message ?: "Erro ao acessar Android Keystore"
            Log.e(TAG, "Tentativa inicial de inicialização do Keystore falhou: ${e.message}")
            try {
                // Segunda tentativa defensiva sem apagar arquivos
                createEncryptedPreferences(context)
            } catch (retryEx: Exception) {
                lastError = retryEx.message
                Log.e(TAG, "Falha ao inicializar EncryptedSharedPreferences (dados preservados para recuperação): ${retryEx.message}")
                null
            }
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

    /**
     * Migração Idempotente e Segura:
     * Varre todos os arquivos de versões anteriores (v1.1, v2.0.0, fallbacks) e preserva
     * qualquer credencial válida existente sem sobrescrever valores novos.
     */
    @Synchronized
    fun migrateAllLegacyStores() {
        val prefs = sharedPreferences ?: return
        val knownProviders = listOf("gemini", "openai", "claude", "openrouter")

        // 1. Auto-cura dentro do arquivo atual: se salvou sem o prefixo "key_"
        try {
            for (p in knownProviders) {
                val currentKey = prefs.getString("key_$p", null)
                if (currentKey.isNullOrBlank()) {
                    val bareKey = prefs.getString(p, null)
                    if (!bareKey.isNullOrBlank()) {
                        prefs.edit().putString("key_$p", bareKey.trim()).commit()
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Migração do arquivo v1.1 EncryptedSharedPreferences ("jarvis_secure_keystore")
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val v1Encrypted = EncryptedSharedPreferences.create(
                context,
                "jarvis_secure_keystore",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            for (p in knownProviders) {
                if (getApiKey(p).isBlank()) {
                    val v1Key = v1Encrypted.getString("key_$p", null) ?: v1Encrypted.getString(p, null)
                    if (!v1Key.isNullOrBlank()) {
                        prefs.edit().putString("key_$p", v1Key.trim()).commit()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignora se o arquivo v1 não existir ou não puder ser aberto
        }

        // 3. Migração do arquivo de fallback legado ("jarvis_fallback_keys")
        try {
            val fallbackPrefs = context.getSharedPreferences("jarvis_fallback_keys", Context.MODE_PRIVATE)
            val all = fallbackPrefs.all
            if (all.isNotEmpty()) {
                val editor = prefs.edit()
                var migratedAny = false
                for ((k, v) in all) {
                    if (v is String && v.isNotBlank()) {
                        val cleanId = if (k.startsWith("key_")) k.removePrefix("key_") else k
                        if (getApiKey(cleanId).isBlank()) {
                            editor.putString("key_$cleanId", v.trim())
                            migratedAny = true
                        }
                    }
                }
                if (migratedAny) {
                    editor.commit()
                }
            }
        } catch (_: Exception) {}

        // 4. Migração de SharedPreferences gerais se houver ("jarvis_settings")
        try {
            val generalPrefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
            for (p in knownProviders) {
                if (getApiKey(p).isBlank()) {
                    val candidate = generalPrefs.getString("key_$p", null)
                        ?: generalPrefs.getString(p, null)
                        ?: generalPrefs.getString("${p}_api_key", null)
                    if (!candidate.isNullOrBlank()) {
                        prefs.edit().putString("key_$p", candidate.trim()).commit()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun saveApiKey(providerId: String, apiKey: String) {
        val cleanId = providerId.trim().lowercase()
        val trimmed = apiKey.trim()
        val prefs = sharedPreferences
            ?: throw SecurityException("Armazenamento seguro do Android Keystore indisponível: ${lastError ?: "Keystore bloqueado"}")

        if (trimmed.isEmpty()) {
            removeApiKey(cleanId)
            return
        }

        // Grava com commit síncrono para garantir persistência imediata em disco
        val saved = prefs.edit().putString("key_$cleanId", trimmed).commit()
        if (!saved) {
            throw KeyStoreException("Falha ao salvar a chave criptografada para $providerId no Keystore.")
        }

        // Valida imediatamente a integridade da leitura (roundtrip de descriptografia)
        val readBack = prefs.getString("key_$cleanId", null)
        if (readBack != trimmed) {
            throw KeyStoreException("Falha de validação criptográfica: a chave lida não corresponde ao valor gravado.")
        }
    }

    @Synchronized
    fun getApiKey(providerId: String): String {
        val cleanId = providerId.trim().lowercase()
        val prefs = sharedPreferences ?: return ""
        val key = prefs.getString("key_$cleanId", null)
        if (!key.isNullOrBlank()) return key.trim()

        // Auto-cura de chaves legadas armazenadas sem o prefixo
        val bareKey = prefs.getString(cleanId, null)
        if (!bareKey.isNullOrBlank()) {
            prefs.edit().putString("key_$cleanId", bareKey.trim()).apply()
            return bareKey.trim()
        }

        return ""
    }

    fun hasKey(providerId: String): Boolean {
        return getApiKey(providerId).isNotBlank()
    }

    @Synchronized
    fun removeApiKey(providerId: String) {
        val cleanId = providerId.trim().lowercase()
        val prefs = sharedPreferences ?: return
        prefs.edit().remove("key_$cleanId").remove(cleanId).apply()
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

    /**
     * Validação real da recuperação segura de credencial.
     * Gera uma fingerprint segura sem expor a chave original em logs ou na UI.
     */
    fun verifyCredential(providerId: String): CredentialVerificationResult {
        val prefs = sharedPreferences
        if (prefs == null) {
            return CredentialVerificationResult(
                isStored = false,
                isDecrypted = false,
                errorMessage = "Android Keystore indisponível (${lastError ?: "Falha de inicialização"})"
            )
        }

        val cleanId = providerId.trim().lowercase()
        val hasEntry = prefs.contains("key_$cleanId") || prefs.contains(cleanId)
        if (!hasEntry) {
            return CredentialVerificationResult(
                isStored = false,
                isDecrypted = false,
                errorMessage = "Nenhuma credencial encontrada no Keystore para $providerId"
            )
        }

        return try {
            val decrypted = getApiKey(cleanId)
            if (decrypted.isNotBlank()) {
                val fp = computeFingerprint(decrypted)
                CredentialVerificationResult(
                    isStored = true,
                    isDecrypted = true,
                    fingerprint = fp,
                    length = decrypted.length
                )
            } else {
                CredentialVerificationResult(
                    isStored = true,
                    isDecrypted = false,
                    errorMessage = "Credencial armazenada está vazia ou corrompida"
                )
            }
        } catch (e: Exception) {
            CredentialVerificationResult(
                isStored = true,
                isDecrypted = false,
                errorMessage = "Erro ao descriptografar credencial: ${e.message}"
            )
        }
    }

    companion object {
        private const val TAG = "SecureKeyStoreManager"
        private const val PREF_FILE_NAME = "jarvis_secure_keystore_v2"

        /**
         * Gera uma fingerprint segura (hash SHA-256) dos primeiros 8 caracteres hexadecimais.
         * Permite comprovar a integridade da chave sem expor seu conteúdo.
         */
        fun computeFingerprint(key: String): String {
            if (key.isBlank()) return "(vazio)"
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(key.trim().toByteArray(Charsets.UTF_8))
                "SHA256:" + hash.take(4).joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                "Tamanho:${key.length}"
            }
        }
    }
}
