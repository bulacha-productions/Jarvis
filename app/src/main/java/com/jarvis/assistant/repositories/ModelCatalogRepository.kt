package com.jarvis.assistant.repositories

import android.content.Context
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Repositório independente e persistente do catálogo real de modelos por provedor (V3.1.3).
 *
 * Requisitos atendidos:
 * - Catálogo independente por provedor (gemini, openai, claude, openrouter).
 * - Persiste modelos descobertos via API e modelos customizados entre reinicializações.
 * - Remove duplicatas mantendo o ID real do modelo.
 * - Preserva estado habilitado/desabilitado individual por modelo.
 * - Nunca inventa modelos quando a consulta falha: expõe o erro exato em lastErrors.
 */
class ModelCatalogRepository private constructor(
    private val storageDir: File?
) {
    private val lock = Any()
    private val knownProviders = listOf("gemini", "openai", "claude", "openrouter")

    private val discoveredMap = mutableMapOf<String, List<AIModelInfo>>()
    private val customMap = mutableMapOf<String, List<AIModelInfo>>()
    private val disabledMap = mutableMapOf<String, MutableSet<String>>()

    private val _catalogs = MutableStateFlow<Map<String, List<AIModelInfo>>>(emptyMap())
    val catalogs: StateFlow<Map<String, List<AIModelInfo>>> = _catalogs.asStateFlow()

    private val _lastErrors = MutableStateFlow<Map<String, String?>>(emptyMap())
    val lastErrors: StateFlow<Map<String, String?>> = _lastErrors.asStateFlow()

    init {
        if (storageDir != null) {
            try {
                if (!storageDir.exists()) storageDir.mkdirs()
                loadAllFromDisk()
            } catch (_: Exception) {}
        }
    }

    private fun loadAllFromDisk() = synchronized(lock) {
        for (providerId in knownProviders) {
            discoveredMap[providerId] = readModelsFile("${providerId}_discovered.json")
            customMap[providerId] = readModelsFile("${providerId}_custom.json")
            disabledMap[providerId] = readDisabledFile("${providerId}_disabled.json")
        }
        publishCombinedLocked()
    }

    fun getModelsForProvider(providerId: String): List<AIModelInfo> = synchronized(lock) {
        _catalogs.value[providerId] ?: emptyList()
    }

    fun getLastError(providerId: String): String? = _lastErrors.value[providerId]

    fun saveDiscoveredModels(providerId: String, models: List<AIModelInfo>) = synchronized(lock) {
        val cleanList = models
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id.trim() }
            .map { it.copy(id = it.id.trim(), providerId = providerId) }

        discoveredMap[providerId] = cleanList
        writeModelsFile("${providerId}_discovered.json", cleanList)

        val currentErrors = _lastErrors.value.toMutableMap()
        currentErrors[providerId] = null
        _lastErrors.value = currentErrors

        publishCombinedLocked()
    }

    fun setCatalogError(providerId: String, errorMessage: String?) = synchronized(lock) {
        val currentErrors = _lastErrors.value.toMutableMap()
        currentErrors[providerId] = errorMessage
        _lastErrors.value = currentErrors
    }

    fun addCustomModel(providerId: String, model: AIModelInfo) = synchronized(lock) {
        val cleanId = model.id.trim()
        if (cleanId.isBlank()) return
        val existingCustom = customMap[providerId]?.filter { it.id != cleanId } ?: emptyList()
        val updatedCustom = existingCustom + model.copy(id = cleanId, providerId = providerId)
        customMap[providerId] = updatedCustom
        writeModelsFile("${providerId}_custom.json", updatedCustom)

        // Garante que modelo adicionado manualmente começa habilitado
        disabledMap[providerId]?.remove(cleanId)
        writeDisabledFile("${providerId}_disabled.json", disabledMap[providerId] ?: emptySet())

        publishCombinedLocked()
    }

    fun setModelEnabled(providerId: String, modelId: String, isEnabled: Boolean) = synchronized(lock) {
        val cleanId = modelId.trim()
        val disabled = disabledMap.getOrPut(providerId) { mutableSetOf() }
        if (isEnabled) {
            disabled.remove(cleanId)
        } else {
            disabled.add(cleanId)
        }
        writeDisabledFile("${providerId}_disabled.json", disabled)
        publishCombinedLocked()
    }

    fun clearProviderCatalog(providerId: String) = synchronized(lock) {
        discoveredMap[providerId] = emptyList()
        writeModelsFile("${providerId}_discovered.json", emptyList())
        publishCombinedLocked()
    }

    private fun publishCombinedLocked() {
        val allIds = (knownProviders + discoveredMap.keys + customMap.keys).distinct()
        val combined = mutableMapOf<String, List<AIModelInfo>>()
        for (pid in allIds) {
            val disc = discoveredMap[pid] ?: emptyList()
            val custom = customMap[pid] ?: emptyList()
            val disabled = disabledMap[pid] ?: emptySet()

            // Combina custom + descobertos sem duplicatas (descobertos têm metadados oficiais prioritários, custom complementa)
            val merged = (disc + custom)
                .distinctBy { it.id }
                .map { model ->
                    model.copy(isEnabled = model.id !in disabled)
                }
            combined[pid] = merged
        }
        _catalogs.value = combined
    }

    private fun readModelsFile(fileName: String): List<AIModelInfo> {
        val dir = storageDir ?: return emptyList()
        val file = File(dir, fileName)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText(Charsets.UTF_8)
            val arr = JSONArray(text)
            val list = mutableListOf<AIModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                if (id.isBlank()) continue
                val displayName = obj.optString("displayName", id)
                val providerId = obj.optString("providerId", "")
                val level = try {
                    AILevel.valueOf(obj.optString("defaultLevel", AILevel.MEDIUM.name))
                } catch (_: Exception) {
                    AILevel.MEDIUM
                }
                val capsArr = obj.optJSONArray("capabilities")
                val caps = mutableSetOf<ModelCapability>()
                if (capsArr != null) {
                    for (j in 0 until capsArr.length()) {
                        try {
                            caps.add(ModelCapability.valueOf(capsArr.getString(j)))
                        } catch (_: Exception) {}
                    }
                }
                if (caps.isEmpty()) caps.add(ModelCapability.TEXT)

                val promptPrice = if (obj.has("promptPricePerMillion") && !obj.isNull("promptPricePerMillion")) {
                    obj.optDouble("promptPricePerMillion").takeIf { !it.isNaN() && it >= 0.0 }
                } else null

                val completionPrice = if (obj.has("completionPricePerMillion") && !obj.isNull("completionPricePerMillion")) {
                    obj.optDouble("completionPricePerMillion").takeIf { !it.isNaN() && it >= 0.0 }
                } else null

                val contextLen = if (obj.has("contextLength") && !obj.isNull("contextLength")) {
                    obj.optInt("contextLength").takeIf { it > 0 }
                } else null

                list.add(
                    AIModelInfo(
                        id = id,
                        displayName = displayName,
                        providerId = providerId,
                        defaultLevel = level,
                        capabilities = caps,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isAvailable = obj.optBoolean("isAvailable", true),
                        description = obj.optString("description", ""),
                        contextLength = contextLen,
                        promptPricePerMillion = promptPrice,
                        completionPricePerMillion = completionPrice,
                        isFree = obj.optBoolean("isFree", false),
                        supportsMultiturn = obj.optBoolean("supportsMultiturn", true),
                        supportsTools = obj.optBoolean("supportsTools", false),
                        organization = obj.optString("organization", "").ifBlank { null }
                    )
                )
            }
            list.distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeModelsFile(fileName: String, models: List<AIModelInfo>) {
        val dir = storageDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val arr = JSONArray()
            for (m in models.distinctBy { it.id }) {
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("providerId", m.providerId)
                    put("defaultLevel", m.defaultLevel.name)
                    put("capabilities", JSONArray(m.capabilities.map { it.name }))
                    put("isEnabled", m.isEnabled)
                    put("isAvailable", m.isAvailable)
                    put("description", m.description)
                    if (m.contextLength != null) put("contextLength", m.contextLength)
                    if (m.promptPricePerMillion != null) put("promptPricePerMillion", m.promptPricePerMillion)
                    if (m.completionPricePerMillion != null) put("completionPricePerMillion", m.completionPricePerMillion)
                    put("isFree", m.isFree)
                    put("supportsMultiturn", m.supportsMultiturn)
                    put("supportsTools", m.supportsTools)
                    if (m.organization != null) put("organization", m.organization)
                }
                arr.put(obj)
            }
            File(dir, fileName).writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun readDisabledFile(fileName: String): MutableSet<String> {
        val dir = storageDir ?: return mutableSetOf()
        val file = File(dir, fileName)
        if (!file.exists()) return mutableSetOf()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "").trim()
                if (id.isNotBlank()) set.add(id)
            }
            set
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun writeDisabledFile(fileName: String, disabled: Set<String>) {
        val dir = storageDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeText(JSONArray(disabled.toList()).toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var INSTANCE: ModelCatalogRepository? = null

        fun getInstance(context: Context): ModelCatalogRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelCatalogRepository(
                    File(context.applicationContext.filesDir, "model_catalogs")
                ).also { INSTANCE = it }
            }
        }

        fun inMemoryForTests(): ModelCatalogRepository {
            return ModelCatalogRepository(null)
        }
    }
}
