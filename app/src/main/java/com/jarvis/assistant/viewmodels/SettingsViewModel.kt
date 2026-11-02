package com.jarvis.assistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.ai.ProviderType
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<AIProviderConfig> = emptyList(),
    val isTestingConnection: Boolean = false,
    val testResultMessage: String? = null,
    val isWizardOpen: Boolean = false,
    val taskAssignments: Map<ModelTask, TaskAssignment> = emptyMap()
)

class SettingsViewModel(
    private val preferencesManager: UserPreferencesManager,
    private val secureKeyStore: SecureKeyStoreManager
) : ViewModel() {

    val isDarkTheme = preferencesManager.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val aiLevel = preferencesManager.aiLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AILevel.MEDIUM
    )

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        val geminiKey = secureKeyStore.getApiKey("gemini")
        val openaiKey = secureKeyStore.getApiKey("openai")
        val claudeKey = secureKeyStore.getApiKey("claude")

        val initialList = listOf(
            AIProviderConfig(
                id = "gemini",
                type = ProviderType.GEMINI,
                displayName = "Google Gemini",
                apiKey = geminiKey,
                isEnabled = true,
                availableModels = listOf("gemini-3.8-flash", "gemini-3.1-flash-lite", "gemini-4-argon"),
                selectedModel = "gemini-3.8-flash",
                status = if (geminiKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
            ),
            AIProviderConfig(
                id = "openai",
                type = ProviderType.OPENAI,
                displayName = "OpenAI",
                apiKey = openaiKey,
                isEnabled = openaiKey.isNotBlank(),
                availableModels = listOf("gpt-4o", "gpt-4o-mini", "o1"),
                selectedModel = "gpt-4o",
                status = if (openaiKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
            ),
            AIProviderConfig(
                id = "claude",
                type = ProviderType.CLAUDE,
                displayName = "Anthropic Claude",
                apiKey = claudeKey,
                isEnabled = claudeKey.isNotBlank(),
                availableModels = listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022"),
                selectedModel = "claude-3-5-sonnet-20241022",
                status = if (claudeKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
            )
        )
        _settingsState.value = _settingsState.value.copy(providers = initialList)
    }

    fun updateApiKey(providerId: String, apiKey: String) {
        secureKeyStore.saveApiKey(providerId, apiKey)
        val updated = _settingsState.value.providers.map {
            if (it.id == providerId) it.copy(apiKey = apiKey) else it
        }
        _settingsState.value = _settingsState.value.copy(providers = updated)
    }

    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) {
        val updated = _settingsState.value.providers.map {
            if (it.id == providerId) it.copy(isEnabled = isEnabled) else it
        }
        _settingsState.value = _settingsState.value.copy(providers = updated)
    }

    fun testConnection(providerId: String) {
        viewModelScope.launch {
            _settingsState.value = _settingsState.value.copy(isTestingConnection = true, testResultMessage = null)
            val provider = _settingsState.value.providers.find { it.id == providerId }
            if (provider == null || provider.apiKey.isBlank()) {
                _settingsState.value = _settingsState.value.copy(
                    isTestingConnection = false,
                    testResultMessage = "Insira uma chave de API válida para testar."
                )
                return@launch
            }

            val result = when (provider.type) {
                ProviderType.GEMINI -> GeminiProvider { provider.apiKey }.testConnection(provider.apiKey)
                ProviderType.OPENAI -> OpenAIProvider({ provider.apiKey }).testConnection(provider.apiKey, provider.baseUrl)
                ProviderType.CLAUDE -> ClaudeProvider { provider.apiKey }.testConnection(provider.apiKey)
                else -> Result.success(listOf("mock-model"))
            }

            result.fold(
                onSuccess = { models ->
                    val updated = _settingsState.value.providers.map {
                        if (it.id == providerId) it.copy(
                            status = ProviderStatus.CONNECTED,
                            availableModels = models,
                            lastError = null
                        ) else it
                    }
                    _settingsState.value = _settingsState.value.copy(
                        providers = updated,
                        isTestingConnection = false,
                        testResultMessage = "Conexão bem-sucedida! ${models.size} modelos identificados."
                    )
                },
                onFailure = { err ->
                    val updated = _settingsState.value.providers.map {
                        if (it.id == providerId) it.copy(
                            status = ProviderStatus.ERROR,
                            lastError = err.message
                        ) else it
                    }
                    _settingsState.value = _settingsState.value.copy(
                        providers = updated,
                        isTestingConnection = false,
                        testResultMessage = "Falha: ${err.message}"
                    )
                }
            )
        }
    }

    fun setWizardOpen(isOpen: Boolean) {
        _settingsState.value = _settingsState.value.copy(isWizardOpen = isOpen)
    }

    fun toggleTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkTheme(enabled)
        }
    }

    fun updateAILevel(level: AILevel) {
        viewModelScope.launch {
            preferencesManager.setAILevel(level)
        }
    }
}
