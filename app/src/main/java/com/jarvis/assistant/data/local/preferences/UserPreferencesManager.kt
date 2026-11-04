package com.jarvis.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

class UserPreferencesManager(private val context: Context) {

    private val KEY_DARK_THEME = booleanPreferencesKey("pref_dark_theme")
    private val KEY_AI_LEVEL = stringPreferencesKey("pref_ai_level")
    private val KEY_TASK_ASSIGNMENTS = stringPreferencesKey("pref_task_assignments_v2_0_1")
    private val KEY_MULTI_AGENT_ENABLED = booleanPreferencesKey("pref_multi_agent_enabled_v2_0_1")
    private val KEY_ENABLED_PROVIDERS = stringSetPreferencesKey("pref_enabled_providers_v2_0_1")
    private val KEY_MANUAL_PROVIDER = stringPreferencesKey("pref_manual_provider_v2_0_1")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true
    }

    val isMultiAgentEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MULTI_AGENT_ENABLED] ?: true
    }

    val manualProviderOverride: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_MANUAL_PROVIDER]
    }

    val enabledProviders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_PROVIDERS] ?: setOf("gemini", "openai", "claude")
    }

    val aiLevel: Flow<AILevel> = context.dataStore.data.map { preferences ->
        val levelString = preferences[KEY_AI_LEVEL] ?: AILevel.MEDIUM.name
        try {
            AILevel.valueOf(levelString)
        } catch (_: Exception) {
            AILevel.MEDIUM
        }
    }

    val taskAssignments: Flow<Map<String, TaskAssignment>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[KEY_TASK_ASSIGNMENTS]
        if (!jsonStr.isNullOrBlank()) {
            parseAssignments(jsonStr)
        } else {
            getDefaultAssignments()
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = enabled
        }
    }

    suspend fun setMultiAgentEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MULTI_AGENT_ENABLED] = enabled
        }
    }

    suspend fun setManualProvider(providerId: String?) {
        context.dataStore.edit { preferences ->
            if (providerId.isNullOrBlank() || providerId == "auto") {
                preferences.remove(KEY_MANUAL_PROVIDER)
            } else {
                preferences[KEY_MANUAL_PROVIDER] = providerId
            }
        }
    }

    suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_ENABLED_PROVIDERS] ?: setOf("gemini", "openai", "claude")
            val updated = if (isEnabled) {
                current + providerId
            } else {
                current - providerId
            }
            preferences[KEY_ENABLED_PROVIDERS] = updated
        }
    }

    suspend fun setAILevel(level: AILevel) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_LEVEL] = level.name
        }
    }

    suspend fun saveTaskAssignments(assignments: Map<String, TaskAssignment>) {
        val json = JSONObject()
        assignments.forEach { (taskId, assign) ->
            val obj = JSONObject().apply {
                put("taskId", assign.task.id)
                put("providerId", assign.providerId)
                put("modelName", assign.modelName)
                put("level", assign.level.name)
                put("reason", assign.recommendationReason)
            }
            json.put(taskId, obj)
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_TASK_ASSIGNMENTS] = json.toString()
        }
    }

    private fun parseAssignments(jsonStr: String): Map<String, TaskAssignment> {
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, TaskAssignment>()
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                val taskId = obj.getString("taskId")
                val task = ModelTask.values().find { it.id == taskId } ?: ModelTask.GENERAL_CHAT
                val providerId = obj.getString("providerId")
                var modelName = obj.getString("modelName")

                // Remove completamente qualquer referência a gemini-1.5 e gemini-2.5
                if (modelName.contains("gemini-1.5") || modelName.contains("gemini-2.5")) {
                    modelName = "gemini-2.0-flash"
                }

                val level = try { AILevel.valueOf(obj.getString("level")) } catch (_: Exception) { AILevel.MEDIUM }
                val reason = obj.optString("reason", "")
                map[taskId] = TaskAssignment(task, providerId, modelName, level, reason)
            }
            map
        } catch (_: Exception) {
            getDefaultAssignments()
        }
    }

    private fun getDefaultAssignments(): Map<String, TaskAssignment> {
        val map = mutableMapOf<String, TaskAssignment>()
        map[ModelTask.GENERAL_CHAT.id] = TaskAssignment(
            ModelTask.GENERAL_CHAT, "gemini", "gemini-2.0-flash", AILevel.MEDIUM,
            "Padrão do Jarvis Central com alta velocidade e naturalidade."
        )
        map[ModelTask.QUICK_QUESTIONS.id] = TaskAssignment(
            ModelTask.QUICK_QUESTIONS, "gemini", "gemini-2.0-flash", AILevel.LIGHT,
            "Baixa latência para perguntas diretas e comandos instantâneos."
        )
        map[ModelTask.CODING.id] = TaskAssignment(
            ModelTask.CODING, "claude", "claude-3-5-sonnet-20241022", AILevel.HIGH,
            "Agente de Programação: alta precisão técnica e depuração robusta."
        )
        map[ModelTask.COMPLEX_REASONING.id] = TaskAssignment(
            ModelTask.COMPLEX_REASONING, "openai", "o3-mini", AILevel.HIGH,
            "Agente Analítico: encadeamento dedutivo, lógica e matemática avançada."
        )
        map[ModelTask.RESEARCH.id] = TaskAssignment(
            ModelTask.RESEARCH, "gemini", "gemini-2.0-flash", AILevel.MEDIUM,
            "Agente de Pesquisa: síntese ampla e investigação comparativa."
        )
        map[ModelTask.CREATIVE_WRITING.id] = TaskAssignment(
            ModelTask.CREATIVE_WRITING, "claude", "claude-3-5-sonnet-20241022", AILevel.MEDIUM,
            "Agente Criativo: estilo narrativo refinado e ideação expressiva."
        )
        map[ModelTask.IMAGE_ANALYSIS.id] = TaskAssignment(
            ModelTask.IMAGE_ANALYSIS, "gemini", "gemini-2.0-flash", AILevel.HIGH,
            "Visão computacional e OCR nativo com alta sensibilidade visual."
        )
        map[ModelTask.DOCUMENT_ANALYSIS.id] = TaskAssignment(
            ModelTask.DOCUMENT_ANALYSIS, "gemini", "gemini-2.0-flash", AILevel.HIGH,
            "Janela de contexto longa para análise aprofundada de documentos e relatórios."
        )
        return map
    }
}
