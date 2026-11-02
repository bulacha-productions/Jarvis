package com.jarvis.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarvis.assistant.domain.model.AILevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

class UserPreferencesManager(private val context: Context) {

    private val KEY_DARK_THEME = booleanPreferencesKey("pref_dark_theme")
    private val KEY_AI_LEVEL = stringPreferencesKey("pref_ai_level")

    // Padrão: Tema Escuro ativado
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: true
    }

    // Padrão: Nível MEDIUM
    val aiLevel: Flow<AILevel> = context.dataStore.data.map { preferences ->
        val levelString = preferences[KEY_AI_LEVEL] ?: AILevel.MEDIUM.name
        try {
            AILevel.valueOf(levelString)
        } catch (_: Exception) {
            AILevel.MEDIUM
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
}
