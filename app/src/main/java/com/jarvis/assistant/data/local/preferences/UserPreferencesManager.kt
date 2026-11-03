package com.jarvis.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val KEY_TASK_ASSIGNMENTS = stringPreferencesKey("pref_task_assignments_v1_1")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true
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
                val modelName = obj.getString("modelName")
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
        map[ModelTask.GENERAL_CHAT.id] = TaskAssignment(ModelTask.GENERAL_CHAT, "gemini", "gemini-2.5-flash", AILevel.MEDIUM, "Padrão recomendado para conversas diárias.")
        map[ModelTask.QUICK_QUESTIONS.id] = TaskAssignment(ModelTask.QUICK_QUESTIONS, "gemini", "gemini-1.5-flash", AILevel.LIGHT, "Baixa latência para perguntas diretas.")
        map[ModelTask.CODING.id] = TaskAssignment(ModelTask.CODING, "claude", "claude-3-5-sonnet", AILevel.HIGH, "Alta precisão em engenharia de software e depuração.")
        map[ModelTask.COMPLEX_REASONING.id] = TaskAssignment(ModelTask.COMPLEX_REASONING, "openai", "o3-mini", AILevel.HIGH, "Raciocínio dedutivo e matemático avançado.")
        map[ModelTask.RESEARCH.id] = TaskAssignment(ModelTask.RESEARCH, "gemini", "gemini-2.5-flash", AILevel.MEDIUM, "Contexto longo para síntese de informações.")
        map[ModelTask.CREATIVE_WRITING.id] = TaskAssignment(ModelTask.CREATIVE_WRITING, "claude", "claude-3-5-sonnet", AILevel.MEDIUM, "Estilo narrativo refinado.")
        map[ModelTask.IMAGE_ANALYSIS.id] = TaskAssignment(ModelTask.IMAGE_ANALYSIS, "gemini", "gemini-2.5-flash", AILevel.HIGH, "Visão computacional e OCR nativo.")
        map[ModelTask.DOCUMENT_ANALYSIS.id] = TaskAssignment(ModelTask.DOCUMENT_ANALYSIS, "gemini", "gemini-2.5-pro", AILevel.HIGH, "Leitura estruturada de PDFs e relatórios.")
        return map
    }
}
