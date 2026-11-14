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
    val maskedPreview: String = "",
    val length: Int = 0,
    val errorMessage: String? = null
)

sealed class CredentialLookupResult {
    data class Success(
        val apiKey: String,
        val fingerprint: String,
        val maskedPreview: String
    ) : CredentialLookupResult()

    data class Missing(val providerId: String) : CredentialLookupResult()

    data class DecryptionFailure(
        val providerId: String,
        val reason: String
    ) : CredentialLookupResult()
}

/**
 * Gerenciador Singleton de Credenciais com Android Keystore AES-256 GCM (V3.1.3).
 *
 * Princípios de estabilidade e segurança:
 * - Instância única por processo (getInstance) evitando concorrência no EncryptedSharedPreferences / Tink Keyset.
 * - Nunca inicializa com texto puro como fallback silencioso.
 * - Nunca apaga arquivos de credenciais em exceções genéricas.
 * - Migração idempotente das versões v1.1 ("jarvis_secure_keystore", "jarvis_fallback_keys", "jarvis_settings") sem sobrescrever valores válidos.
 * - Nunca retorna string vazia silenciosamente quando há falha de descriptografia: expõe CredentialLookupResult explícito.
 * - Nunca registra chaves em logs e nunca expõe chaves completas na interface.
 */
class SecureKeyStoreManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val lock = Any()
    private var sharedPreferences: SharedPreferences? = null

    var lastError: String? = null
        private set

    init {
        synchronized(lock) {
            initializeKeystoreLocked()
            if (sharedPreferences != null) {
                migrateAllLegacyStoresLocked()
            }
        }
    }

    private fun initializeKeystoreLocked() {
        sharedPreferences = try {
            createEncryptedPreferences(appContext)
        } catch (e: Exception) {
            lastError = e.message ?: "Erro ao acessar Android Keystore"
            Log.e(TAG, "Tentativa inicial do Android Keystore falhou (dados preservados): ${e.message}")
            try {
                createEncryptedPreferences(appContext)
            } catch (retryEx: Exception) {
                lastError = retryEx.message ?: "Falha persistente no Android Keystore"
                Log.e(TAG, "Keystore indisponível após nova tentativa (arquivos mantidos intactos): ${retryEx.message}")
                null
            }
        }
    }

    private fun ensureInitializedLocked(): SharedPreferences? {
        if (sharedPreferences == null) {
            initializeKeystoreLocked()
            if (sharedPreferences != null) {
                migrateAllLegacyStoresLocked()
            }
        }
        return sharedPreferences
    }

    private fun createEncryptedPreferences(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            PREF_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Migração Idempotente e Não-Destrutiva:
     * Preserva chaves válidas existentes no v2 e recupera credenciais de stores anteriores.
     */
    private fun migrateAllLegacyStoresLocked() {
        val prefs = sharedPreferences ?: return
        val knownProviders = listOf("gemini", "openai", "claude", "openrouter")

        // 1. Auto-cura no arquivo atual (chaves sem prefixo "key_")
        try {
            for (p in knownProviders) {
                val bare = prefs.getString(p, null)
                val prefixed = prefs.getString("key_$p", null)
                if (!bare.isNullOrBlank() && prefixed.isNullOrBlank()) {
                    prefs.edit().putString("key_$p", bare.trim()).commit()
                }
            }
        } catch (_: Exception) {}

        // 2. Migração da v1.1 ("jarvis_secure_keystore")
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val v1Encrypted = EncryptedSharedPreferences.create(
                appContext,
                "jarvis_secure_keystore",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            for (p in knownProviders) {
                val existing = prefs.getString("key_$p", null)
                if (existing.isNullOrBlank()) {
                    val v1Key = v1Encrypted.getString("key_$p", null) ?: v1Encrypted.getString(p, null)
                    if (!v1Key.isNullOrBlank()) {
                        prefs.edit().putString("key_$p", v1Key.trim()).commit()
                    }
                }
            }
        } catch (_: Exception) {
            // Store v1 ausente ou inacessível — não destrutivo
        }

        // 3. Migração de fallback v1.1 ("jarvis_fallback_keys")
        try {
            val fallbackPrefs = appContext.getSharedPreferences("jarvis_fallback_keys", Context.MODE_PRIVATE)
            val all = fallbackPrefs.all
            if (all.isNotEmpty()) {
                val editor = prefs.edit()
                var changed = false
                for ((k, v) in all) {
                    if (v is String && v.isNotBlank()) {
                        val cleanId = normalizeProviderId(k.removePrefix("key_"))
                        if (cleanId in knownProviders) {
                            val existing = prefs.getString("key_$cleanId", null)
                            if (existing.isNullOrBlank()) {
                                editor.putString("key_$cleanId", v.trim())
                                changed = true
                            }
                        }
                    }
                }
                if (changed) editor.commit()
            }
        } catch (_: Exception) {}

        // 4. Migração de "jarvis_settings" se houver
        try {
            val generalPrefs = appContext.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
            for (p in knownProviders) {
                val existing = prefs.getString("key_$p", null)
                if (existing.isNullOrBlank()) {
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

    fun saveApiKey(providerId: String, apiKey: String): Result<CredentialVerificationResult> = synchronized(lock) {
        val cleanId = normalizeProviderId(providerId)
        val trimmed = apiKey.trim()
        if (cleanId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de provedor inválido."))
        }
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Chave vazia não pode sobrescrever uma credencial válida. Use remover se desejar excluir."))
        }

        val prefs = ensureInitializedLocked()
            ?: return Result.failure(
                SecurityException("Armazenamento seguro do Android Keystore indisponível: ${lastError ?: "Keystore bloqueado"}")
            )

        return try {
            val saved = prefs.edit().putString("key_$cleanId", trimmed).commit()
            if (!saved) {
                return Result.failure(KeyStoreException("Falha ao persistir a chave criptografada de $cleanId."))
            }
            val readBack = prefs.getString("key_$cleanId", null)?.trim()
            if (readBack != trimmed) {
                return Result.failure(KeyStoreException("Falha de validação criptográfica na leitura pós-gravação de $cleanId."))
            }
            Result.success(
                CredentialVerificationResult(
                    isStored = true,
                    isDecrypted = true,
                    fingerprint = computeFingerprint(readBack),
                    maskedPreview = maskApiKey(readBack),
                    length = readBack.length
                )
            )
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(e)
        }
    }

    fun removeApiKey(providerId: String): Boolean = synchronized(lock) {
        val cleanId = normalizeProviderId(providerId)
        val prefs = ensureInitializedLocked() ?: return false
        return try {
            prefs.edit().remove("key_$cleanId").remove(cleanId).commit()
        } catch (e: Exception) {
            lastError = e.message
            false
        }
    }

    fun getCredential(providerId: String): CredentialLookupResult = synchronized(lock) {
        val cleanId = normalizeProviderId(providerId)
        val prefs = ensureInitializedLocked()
            ?: return CredentialLookupResult.DecryptionFailure(
                providerId = cleanId,
                reason = "Android Keystore indisponível: ${lastError ?: "Falha de inicialização"}"
            )

        val hasPrefixed = try { prefs.contains("key_$cleanId") } catch (_: Exception) { false }
        val hasBare = try { prefs.contains(cleanId) } catch (_: Exception) { false }

        if (!hasPrefixed && !hasBare) {
            return CredentialLookupResult.Missing(cleanId)
        }

        return try {
            val value = prefs.getString("key_$cleanId", null)?.trim()
                ?: prefs.getString(cleanId, null)?.trim()

            if (value.isNullOrBlank()) {
                CredentialLookupResult.DecryptionFailure(
                    providerId = cleanId,
                    reason = "A entrada no Keystore para $cleanId retornou vazia após descriptografia."
                )
            } else {
                if (hasBare && !hasPrefixed) {
                    prefs.edit().putString("key_$cleanId", value).commit()
                }
                CredentialLookupResult.Success(
                    apiKey = value,
                    fingerprint = computeFingerprint(value),
                    maskedPreview = maskApiKey(value)
                )
            }
        } catch (e: Exception) {
            lastError = e.message
            CredentialLookupResult.DecryptionFailure(
                providerId = cleanId,
                reason = "Erro ao descriptografar chave de $cleanId: ${e.message ?: "Falha de Keystore"}"
            )
        }
    }

    /**
     * Uso interno exclusivo pelos provedores HTTP após validação via verifyCredential/getCredential.
     */
    fun getApiKey(providerId: String): String {
        return when (val res = getCredential(providerId)) {
            is CredentialLookupResult.Success -> res.apiKey
            else -> ""
        }
    }

    fun getMaskedPreview(providerId: String): String {
        return when (val res = getCredential(providerId)) {
            is CredentialLookupResult.Success -> res.maskedPreview
            else -> ""
        }
    }

    fun hasKey(providerId: String): Boolean {
        return getCredential(providerId) is CredentialLookupResult.Success
    }

    fun verifyCredential(providerId: String): CredentialVerificationResult {
        return when (val res = getCredential(providerId)) {
            is CredentialLookupResult.Success -> CredentialVerificationResult(
                isStored = true,
                isDecrypted = true,
                fingerprint = res.fingerprint,
                maskedPreview = res.maskedPreview,
                length = res.apiKey.length
            )
            is CredentialLookupResult.Missing -> CredentialVerificationResult(
                isStored = false,
                isDecrypted = false,
                errorMessage = "Nenhuma credencial configurada para $providerId"
            )
            is CredentialLookupResult.DecryptionFailure -> CredentialVerificationResult(
                isStored = true,
                isDecrypted = false,
                errorMessage = res.reason
            )
        }
    }

    fun isKeystoreFunctional(): Boolean = synchronized(lock) {
        val prefs = ensureInitializedLocked() ?: return false
        return try {
            val testKey = "__keystore_healthcheck__"
            prefs.edit().putBoolean(testKey, true).commit()
            val ok = prefs.getBoolean(testKey, false)
            prefs.edit().remove(testKey).commit()
            ok
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "SecureKeyStoreManager"
        private const val PREF_FILE_NAME = "jarvis_secure_keystore_v2"

        @Volatile
        private var INSTANCE: SecureKeyStoreManager? = null

        fun getInstance(context: Context): SecureKeyStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureKeyStoreManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun normalizeProviderId(providerId: String): String {
            return providerId.trim().lowercase()
        }

        fun maskApiKey(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.length <= 6) return "••••"
            return "••••" + trimmed.takeLast(4)
        }

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
