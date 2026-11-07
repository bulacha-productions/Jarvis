package com.jarvis.assistant.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.ConnectionTestResult
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.MockSystemProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.ai.OpenRouterProvider
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.ai.ProviderType
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.*
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

    val multiAgentMode = preferencesManager.multiAgentMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MultiAgentMode.AUTOMATIC
    )

    val routingMode = preferencesManager.routingMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoutingMode.AUTOMATIC
    )

    val preferredProvider = preferencesManager.preferredProvider.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val financialPolicy = preferencesManager.financialPolicy.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinancialPolicy.FREE_ONLY
    )

    val monthlyBudget = preferencesManager.monthlyBudget.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 5.0
    )

    val currentSpend = preferencesManager.currentSpend.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val isTtsEnabled = preferencesManager.isTtsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isTtsAutoSpeak = preferencesManager.isTtsAutoSpeak.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
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
        initialValue = setOf("gemini", "openai", "claude", "openrouter")
    )

    val selectedModels = preferencesManager.selectedModels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    val settingsState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            val enabledSet = preferencesManager.enabledProviders.firstOrNull() ?: setOf("gemini", "openai", "claude", "openrouter")
            val chosenModels = preferencesManager.selectedModels.firstOrNull() ?: emptyMap()

            val providerConfigs = listOf(
                AIProviderConfig(
                    id = "gemini",
                    type = ProviderType.GEMINI,
                    displayName = "Google Gemini",
                    apiKey = secureKeyStore.getApiKey("gemini"),
                    isEnabled = "gemini" in enabledSet,
                    selectedModel = chosenModels["gemini"] ?: "gemini-2.5-flash",
                    status = if (secureKeyStore.getApiKey("gemini").isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                ),
                AIProviderConfig(
                    id = "openai",
                    type = ProviderType.OPENAI,
                    displayName = "OpenAI",
                    apiKey = secureKeyStore.getApiKey("openai"),
                    isEnabled = "openai" in enabledSet,
                    selectedModel = chosenModels["openai"] ?: "gpt-4o",
                    status = if (secureKeyStore.getApiKey("openai").isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                ),
                AIProviderConfig(
                    id = "claude",
                    type = ProviderType.CLAUDE,
                    displayName = "Anthropic Claude",
                    apiKey = secureKeyStore.getApiKey("claude"),
                    isEnabled = "claude" in enabledSet,
                    selectedModel = chosenModels["claude"] ?: "claude-3-5-sonnet-20241022",
                    status = if (secureKeyStore.getApiKey("claude").isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                ),
                AIProviderConfig(
                    id = "openrouter",
                    type = ProviderType.OPENROUTER,
                    displayName = "OpenRouter",
                    apiKey = secureKeyStore.getApiKey("openrouter"),
                    isEnabled = "openrouter" in enabledSet,
                    selectedModel = chosenModels["openrouter"] ?: OpenRouterProvider.DEFAULT_FREE_MODEL,
                    status = if (secureKeyStore.getApiKey("openrouter").isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.UNCONFIGURED
                )
            )
            _uiState.value = _uiState.value.copy(providers = providerConfigs)
        }
    }


    fun updateApiKey(providerId: String, apiKey: String) = saveApiKey(providerId, apiKey)
    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) = toggleProvider(providerId, isEnabled)
    fun removeApiKey(providerId: String) = saveApiKey(providerId, "")
    fun toggleModelEnabled(providerId: String, modelId: String, isEnabled: Boolean) {}

    fun saveApiKey(providerId: String, apiKey: String) {
        secureKeyStore.saveApiKey(providerId, apiKey)
        loadProviders()
    }

    fun toggleProvider(providerId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setProviderEnabled(providerId, isEnabled)
            loadProviders()
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedModel(providerId, modelId)
            loadProviders()
        }
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            preferencesManager.setRoutingMode(mode)
        }
    }

    fun setPreferredProvider(providerId: String?) {
        viewModelScope.launch {
            preferencesManager.setPreferredProvider(providerId)
        }
    }

    fun setMultiAgentMode(mode: MultiAgentMode) {
        viewModelScope.launch {
            preferencesManager.setMultiAgentMode(mode)
        }
    }

    fun setFinancialPolicy(policy: FinancialPolicy) {
        viewModelScope.launch {
            preferencesManager.setFinancialPolicy(policy)
        }
    }

    fun setMonthlyBudget(budget: Double) {
        viewModelScope.launch {
            preferencesManager.setMonthlyBudget(budget)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTtsEnabled(enabled)
        }
    }

    fun setTtsAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTtsAutoSpeak(enabled)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkTheme(enabled)
        }
    }

    fun toggleMultiAgent(enabled: Boolean) = setMultiAgentEnabled(enabled)

    fun setMultiAgentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMultiAgentEnabled(enabled)
        }
    }

    fun setAILevel(level: AILevel) {
        viewModelScope.launch {
            preferencesManager.setAILevel(level)
        }
    }

    fun setManualProviderOverride(providerId: String?) {
        viewModelScope.launch {
            preferencesManager.setManualProvider(providerId)
        }
    }

    fun refreshModels(providerId: String) {
        testConnection(providerId)
    }

    fun testConnection(providerId: String) {
        val providerConfig = _uiState.value.providers.find { it.id == providerId } ?: return
        _uiState.value = _uiState.value.copy(
            isTestingConnection = true,
            testResultMessage = null,
            lastTestSuccess = null
        )

        viewModelScope.launch {
            if (providerConfig.apiKey.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    testResultMessage = "Insira uma chave de API para testar.",
                    lastTestSuccess = false
                )
                return@launch
            }

            val providerInstance: AIProvider = when (providerConfig.type) {
                ProviderType.GEMINI -> GeminiProvider(context) { providerConfig.apiKey }
                ProviderType.OPENAI -> OpenAIProvider(context, { providerConfig.apiKey }, { providerConfig.baseUrl })
                ProviderType.CLAUDE -> ClaudeProvider(context) { providerConfig.apiKey }
                ProviderType.OPENROUTER -> OpenRouterProvider(context) { providerConfig.apiKey }
                ProviderType.MOCK, ProviderType.CUSTOM -> MockSystemProvider()
            }

            val testResult: ConnectionTestResult = providerInstance.testConnection(providerConfig.apiKey, providerConfig.baseUrl)

            if (testResult.isSuccess) {
                val updatedModels = if (testResult.discoveredModels.isNotEmpty()) {
                    testResult.discoveredModels
                } else {
                    providerConfig.availableModels
                }

                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    testResultMessage = "✓ " + testResult.message,
                    lastTestSuccess = true,
                    providers = _uiState.value.providers.map { p ->
                        if (p.id == providerId) {
                            p.copy(
                                status = ProviderStatus.CONNECTED,
                                availableModels = updatedModels,
                                lastError = null
                            )
                        } else p
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    testResultMessage = "✗ " + testResult.message,
                    lastTestSuccess = false,
                    providers = _uiState.value.providers.map { p ->
                        if (p.id == providerId) {
                            p.copy(
                                status = ProviderStatus.ERROR,
                                lastError = testResult.message
                            )
                        } else p
                    }
                )
            }
        }
    }
}
