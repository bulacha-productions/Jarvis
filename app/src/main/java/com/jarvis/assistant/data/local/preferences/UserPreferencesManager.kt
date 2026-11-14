package com.jarvis.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

class UserPreferencesManager(private val context: Context) {

    private val KEY_DARK_THEME = booleanPreferencesKey("pref_dark_theme")
    private val KEY_AI_LEVEL = stringPreferencesKey("pref_ai_level")
    private val KEY_TASK_ASSIGNMENTS = stringPreferencesKey("pref_task_assignments_v2_0_2")
    private val KEY_MULTI_AGENT_ENABLED = booleanPreferencesKey("pref_multi_agent_enabled_v2_0_2")
    private val KEY_ENABLED_PROVIDERS = stringSetPreferencesKey("pref_enabled_providers_v2_0_2")
    private val KEY_MANUAL_PROVIDER = stringPreferencesKey("pref_manual_provider_v2_0_2")
    private val KEY_SELECTED_MODELS = stringPreferencesKey("pref_selected_models_v2_0_2")

    // Novas chaves da V3.0.0
    private val KEY_ROUTING_MODE = stringPreferencesKey("pref_routing_mode_v3_0_0")
    private val KEY_PREFERRED_PROVIDER = stringPreferencesKey("pref_preferred_provider_v3_0_0")
    private val KEY_MULTI_AGENT_MODE = stringPreferencesKey("pref_multi_agent_mode_v3_0_0")
    private val KEY_FINANCIAL_POLICY = stringPreferencesKey("pref_financial_policy_v3_0_0")
    private val KEY_MONTHLY_BUDGET = doublePreferencesKey("pref_monthly_budget_v3_0_0")
    private val KEY_CURRENT_SPEND = doublePreferencesKey("pref_current_spend_v3_0_0")
    private val KEY_TTS_ENABLED = booleanPreferencesKey("pref_tts_enabled_v3_0_0")
    private val KEY_TTS_AUTO_SPEAK = booleanPreferencesKey("pref_tts_auto_speak_v3_0_0")
    private val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("pref_wake_word_enabled_v3_1_0")
    private val KEY_WAKE_WORD_SPOKEN_FEEDBACK = booleanPreferencesKey("pref_wake_word_spoken_feedback_v3_1_0")


    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true
    }

    val isMultiAgentEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MULTI_AGENT_ENABLED] ?: true
    }

    val multiAgentMode: Flow<MultiAgentMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_MULTI_AGENT_MODE] ?: MultiAgentMode.AUTOMATIC.name
        try { MultiAgentMode.valueOf(raw) } catch (_: Exception) { MultiAgentMode.AUTOMATIC }
    }

    val routingMode: Flow<RoutingMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_ROUTING_MODE] ?: RoutingMode.AUTOMATIC.name
        try { RoutingMode.valueOf(raw) } catch (_: Exception) { RoutingMode.AUTOMATIC }
    }

    val preferredProvider: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PREFERRED_PROVIDER]
    }

    val financialPolicy: Flow<FinancialPolicy> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_FINANCIAL_POLICY] ?: FinancialPolicy.FREE_ONLY.name
        try { FinancialPolicy.valueOf(raw) } catch (_: Exception) { FinancialPolicy.FREE_ONLY }
    }

    val monthlyBudget: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_MONTHLY_BUDGET] ?: 5.0
    }

    val currentSpend: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENT_SPEND] ?: 0.0
    }

    val isTtsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_ENABLED] ?: true
    }

    val isTtsAutoSpeak: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TTS_AUTO_SPEAK] ?: false
    }

    val isWakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAKE_WORD_ENABLED] ?: false
    }

    val isWakeWordSpokenFeedback: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAKE_WORD_SPOKEN_FEEDBACK] ?: true
    }


    val manualProviderOverride: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_MANUAL_PROVIDER]
    }

    val enabledProviders: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_PROVIDERS] ?: setOf("gemini", "openai", "claude", "openrouter")
    }

    val selectedModels: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_SELECTED_MODELS] ?: return@map emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = json.getString(k)
            }
        } catch (_: Exception) {}
        result
    }

    suspend fun setSelectedModel(providerId: String, modelId: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[KEY_SELECTED_MODELS] ?: "{}"
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            json.put(providerId, modelId)
            preferences[KEY_SELECTED_MODELS] = json.toString()
        }
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ROUTING_MODE] = mode.name
        }
    }

    suspend fun setPreferredProvider(providerId: String?) {
        context.dataStore.edit { preferences ->
            if (providerId.isNullOrBlank()) preferences.remove(KEY_PREFERRED_PROVIDER)
            else preferences[KEY_PREFERRED_PROVIDER] = providerId
        }
    }

    suspend fun setMultiAgentMode(mode: MultiAgentMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MULTI_AGENT_MODE] = mode.name
        }
    }

    suspend fun setFinancialPolicy(policy: FinancialPolicy) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FINANCIAL_POLICY] = policy.name
        }
    }

    suspend fun setMonthlyBudget(budget: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_BUDGET] = budget
        }
    }

    suspend fun addSpend(amount: Double) {
        context.dataStore.edit { preferences ->
            val cur = preferences[KEY_CURRENT_SPEND] ?: 0.0
            preferences[KEY_CURRENT_SPEND] = (cur + amount).coerceAtLeast(0.0)
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_ENABLED] = enabled
        }
    }

    suspend fun setTtsAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TTS_AUTO_SPEAK] = enabled
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun setWakeWordSpokenFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WAKE_WORD_SPOKEN_FEEDBACK] = enabled
        }
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
            val current = preferences[KEY_ENABLED_PROVIDERS] ?: setOf("gemini", "openai", "claude", "openrouter")
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
// Preserva o modelo selecionado pelo usuário sem substituição forçada
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
            ModelTask.GENERAL_CHAT, "gemini", "gemini-2.5-flash", AILevel.MEDIUM,
            "Padrão do Jarvis Central com alta velocidade e naturalidade."
        )
        map[ModelTask.QUICK_QUESTIONS.id] = TaskAssignment(
            ModelTask.QUICK_QUESTIONS, "gemini", "gemini-2.0-flash", AILevel.LIGHT,
            "Baixa latência para perguntas diretas e comandos instantâneos."
        )
        map[ModelTask.CODING.id] = TaskAssignment(
            ModelTask.CODING, "claude", "claude-sonnet-4-20250514", AILevel.HIGH,
            "Agente de Programação: alta precisão técnica e depuração robusta."
        )
        map[ModelTask.COMPLEX_REASONING.id] = TaskAssignment(
            ModelTask.COMPLEX_REASONING, "openai", "o3-mini", AILevel.HIGH,
            "Agente Analítico: encadeamento dedutivo, lógica e matemática avançada."
        )
        map[ModelTask.RESEARCH.id] = TaskAssignment(
            ModelTask.RESEARCH, "gemini", "gemini-2.5-flash", AILevel.MEDIUM,
            "Agente de Pesquisa: síntese ampla e investigação comparativa."
        )
        map[ModelTask.CREATIVE_WRITING.id] = TaskAssignment(
            ModelTask.CREATIVE_WRITING, "claude", "claude-sonnet-4-20250514", AILevel.MEDIUM,
            "Agente Criativo: estilo narrativo refinado e ideação expressiva."
        )
        map[ModelTask.IMAGE_ANALYSIS.id] = TaskAssignment(
            ModelTask.IMAGE_ANALYSIS, "gemini", "gemini-2.5-flash", AILevel.HIGH,
            "Visão computacional e OCR nativo com alta sensibilidade visual."
        )
        map[ModelTask.DOCUMENT_ANALYSIS.id] = TaskAssignment(
            ModelTask.DOCUMENT_ANALYSIS, "gemini", "gemini-2.5-flash", AILevel.HIGH,
            "Janela de contexto longa para análise aprofundada de documentos e relatórios."
        )
        return map
    }
}
