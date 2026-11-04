package com.jarvis.assistant.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.ConnectionTestResult
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.ai.ProviderType
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.domain.model.TaskAssignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<AIProviderConfig> = emptyList(),
    val isTestingConnection: Boolean = false,
    val testResultMessage: String? = null,
    val lastTestSuccess: Boolean? = null,
    val isWizardOpen: Boolean = false,
    val taskAssignments: Map<String, TaskAssignment> = emptyMap(),
    val isDiagnosticsOpen: Boolean = false,
    val keystoreHealthy: Boolean = true
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesManager: UserPreferencesManager,
    private val secureKeyStore: SecureKeyStoreManager
) : ViewModel() {

    val isDarkTheme = preferencesManager.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isMultiAgentEnabled = preferencesManager.isMultiAgentEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val aiLevel = preferencesManager.aiLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AILevel.MEDIUM
    )

    val taskAssignments = preferencesManager.taskAssignments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val manualProviderOverride = preferencesManager.manualProviderOverride.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val enabledProviders = preferencesManager.enabledProviders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = setOf("gemini", "openai", "claude")
    )

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            val geminiKey = secureKeyStore.getApiKey("gemini")
            val openaiKey = secureKeyStore.getApiKey("openai")
            val claudeKey = secureKeyStore.getApiKey("claude")
            val enabledSet = preferencesManager.enabledProviders.firstOrNull() ?: setOf("gemini", "openai", "claude")

            val geminiProvider = GeminiProvider(context) { secureKeyStore.getApiKey("gemini") }
            val openaiProvider = OpenAIProvider(context, { secureKeyStore.getApiKey("openai") }, { "" })
            val claudeProvider = ClaudeProvider { secureKeyStore.getApiKey("claude") }

            val geminiModels = geminiProvider.getAvailableModels(AILevel.MEDIUM) +
                    geminiProvider.getAvailableModels(AILevel.LIGHT) +
                    geminiProvider.getAvailableModels(AILevel.HIGH)

            val openaiModels = openaiProvider.getAvailableModels(AILevel.MEDIUM) +
                    openaiProvider.getAvailableModels(AILevel.LIGHT) +
                    openaiProvider.getAvailableModels(AILevel.HIGH)

            val claudeModels = claudeProvider.getAvailableModels(AILevel.MEDIUM) +
                    claudeProvider.getAvailableModels(AILevel.LIGHT) +
                    claudeProvider.getAvailableModels(AILevel.HIGH)

            val list = listOf(
                AIProviderConfig(
                    id = "gemini",
                    type = ProviderType.GEMINI,
                    displayName = "Google Gemini",
                    apiKey = geminiKey,
                    isEnabled = enabledSet.contains("gemini"),
                    availableModels = geminiModels.distinctBy { it.id },
                    selectedModel = "gemini-2.0-flash",
                    status = if (geminiKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                ),
                AIProviderConfig(
                    id = "openai",
                    type = ProviderType.OPENAI,
                    displayName = "OpenAI",
                    apiKey = openaiKey,
                    isEnabled = enabledSet.contains("openai"),
                    availableModels = openaiModels.distinctBy { it.id },
                    selectedModel = "gpt-4o",
                    status = if (openaiKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                ),
                AIProviderConfig(
                    id = "claude",
                    type = ProviderType.CLAUDE,
                    displayName = "Anthropic Claude",
                    apiKey = claudeKey,
                    isEnabled = enabledSet.contains("claude"),
                    availableModels = claudeModels.distinctBy { it.id },
                    selectedModel = "claude-3-5-sonnet-20241022",
                    status = if (claudeKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                )
            )

            _settingsState.value = _settingsState.value.copy(
                providers = list,
                keystoreHealthy = secureKeyStore.isKeystoreFunctional()
            )
        }
    }

    fun updateApiKey(providerId: String, apiKey: String) {
        secureKeyStore.saveApiKey(providerId, apiKey.trim())
        val updated = _settingsState.value.providers.map {
            if (it.id == providerId) {
                it.copy(
                    apiKey = apiKey.trim(),
                    status = if (apiKey.isNotBlank()) ProviderStatus.TESTING else ProviderStatus.UNCONFIGURED
                )
            } else it
        }
        _settingsState.value = _settingsState.value.copy(providers = updated)
    }

    fun removeApiKey(providerId: String) {
        secureKeyStore.removeApiKey(providerId)
        val updated = _settingsState.value.providers.map {
            if (it.id == providerId) {
                it.copy(apiKey = "", status = ProviderStatus.UNCONFIGURED, lastError = null)
            } else it
        }
        _settingsState.value = _settingsState.value.copy(providers = updated, testResultMessage = null)
    }

    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setProviderEnabled(providerId, isEnabled)
            val updated = _settingsState.value.providers.map {
                if (it.id == providerId) it.copy(isEnabled = isEnabled) else it
            }
            _settingsState.value = _settingsState.value.copy(providers = updated)
        }
    }

    fun setManualProvider(providerId: String?) {
        viewModelScope.launch {
            preferencesManager.setManualProvider(providerId)
        }
    }

    fun toggleDiagnostics(isOpen: Boolean) {
        _settingsState.value = _settingsState.value.copy(
            isDiagnosticsOpen = isOpen,
            keystoreHealthy = secureKeyStore.isKeystoreFunctional()
        )
    }

    fun testConnection(providerId: String) {
        viewModelScope.launch {
            _settingsState.value = _settingsState.value.copy(
                isTestingConnection = true,
                testResultMessage = null,
                lastTestSuccess = null
            )

            val providerConfig = _settingsState.value.providers.find { it.id == providerId }
            if (providerConfig == null || providerConfig.apiKey.isBlank()) {
                _settingsState.value = _settingsState.value.copy(
                    isTestingConnection = false,
                    lastTestSuccess = false,
                    testResultMessage = "Por favor, informe a chave de API antes de testar a conexão."
                )
                return@launch
            }

            val providerInstance: AIProvider = when (providerConfig.type) {
                ProviderType.GEMINI -> GeminiProvider(context) { providerConfig.apiKey }
                ProviderType.OPENAI -> OpenAIProvider(context, { providerConfig.apiKey }, { providerConfig.baseUrl })
                ProviderType.CLAUDE -> ClaudeProvider { providerConfig.apiKey }
                else -> GeminiProvider(context) { providerConfig.apiKey }
            }

            val testResult: ConnectionTestResult = providerInstance.testConnection(providerConfig.apiKey, providerConfig.baseUrl)

            if (testResult.isSuccess) {
                val updatedModels = if (testResult.discoveredModels.isNotEmpty()) {
                    testResult.discoveredModels
                } else {
                    providerConfig.availableModels
                }

                val updatedProviders = _settingsState.value.providers.map {
                    if (it.id == providerId) {
                        it.copy(
                            status = ProviderStatus.CONNECTED,
                            availableModels = updatedModels,
                            lastError = null
                        )
                    } else it
                }
                _settingsState.value = _settingsState.value.copy(
                    providers = updatedProviders,
                    isTestingConnection = false,
                    lastTestSuccess = true,
                    testResultMessage = testResult.message
                )
            } else {
                val updatedProviders = _settingsState.value.providers.map {
                    if (it.id == providerId) {
                        it.copy(
                            status = ProviderStatus.ERROR,
                            lastError = testResult.message
                        )
                    } else it
                }
                _settingsState.value = _settingsState.value.copy(
                    providers = updatedProviders,
                    isTestingConnection = false,
                    lastTestSuccess = false,
                    testResultMessage = testResult.message
                )
            }
        }
    }

    fun addCustomModel(
        providerId: String,
        modelId: String,
        displayName: String,
        defaultLevel: AILevel,
        capabilities: Set<ModelCapability>
    ) {
        val newModel = AIModelInfo(
            id = modelId.trim(),
            displayName = displayName.ifBlank { modelId }.trim(),
            providerId = providerId,
            defaultLevel = defaultLevel,
            capabilities = capabilities,
            description = "Modelo cadastrado manualmente pelo usuário."
        )

        val updated = _settingsState.value.providers.map { p ->
            if (p.id == providerId) {
                val existing = p.availableModels.filter { it.id != modelId }
                p.copy(availableModels = existing + newModel)
            } else p
        }
        _settingsState.value = _settingsState.value.copy(providers = updated)
    }

    fun toggleModelEnabled(providerId: String, modelId: String, isEnabled: Boolean) {
        val updated = _settingsState.value.providers.map { p ->
            if (p.id == providerId) {
                val models = p.availableModels.map { m ->
                    if (m.id == modelId) m.copy(isEnabled = isEnabled) else m
                }
                p.copy(availableModels = models)
            } else p
        }
        _settingsState.value = _settingsState.value.copy(providers = updated)
    }

    fun saveTaskAssignments(assignments: Map<String, TaskAssignment>) {
        viewModelScope.launch {
            preferencesManager.saveTaskAssignments(assignments)
            _settingsState.value = _settingsState.value.copy(taskAssignments = assignments)
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

    fun toggleMultiAgent(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMultiAgentEnabled(enabled)
        }
    }

    fun updateAILevel(level: AILevel) {
        viewModelScope.launch {
            preferencesManager.setAILevel(level)
        }
    }
}
