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
import com.jarvis.assistant.audio.JarvisVoiceState
import com.jarvis.assistant.audio.TTSManager
import com.jarvis.assistant.audio.VoiceDiagnosticsManager
import com.jarvis.assistant.service.JarvisVoiceService

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val keystoreHealthy: Boolean = true,
    val isVoiceDiagnosticsOpen: Boolean = false
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

    // V3.1.0: Sistema de Voz e Wake Word
    val voiceState = JarvisVoiceService.voiceState
    val isVoiceServiceRunning = JarvisVoiceService.isServiceRunning

    val isWakeWordEnabled = preferencesManager.isWakeWordEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isWakeWordSpokenFeedback = preferencesManager.isWakeWordSpokenFeedback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val diagnosticsManager = VoiceDiagnosticsManager(context)
    val diagnosticsItems = diagnosticsManager.items

    private val _isVoiceDiagnosticsOpen = MutableStateFlow(false)
    val isVoiceDiagnosticsOpen = _isVoiceDiagnosticsOpen.asStateFlow()

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

    private val runtimeStatuses = mutableMapOf<String, ProviderStatus>()
    private val runtimeDiscoveredModels = mutableMapOf<String, List<AIModelInfo>>()

    fun loadProviders() {
        viewModelScope.launch {
            val enabledSet = preferencesManager.enabledProviders.firstOrNull() ?: setOf("gemini", "openai", "claude", "openrouter")
            val chosenModels = preferencesManager.selectedModels.firstOrNull() ?: emptyMap()

            fun getProviderStatus(id: String, key: String): ProviderStatus {
                val verification = secureKeyStore.verifyCredential(id)
                if (!verification.isStored || key.isBlank()) return ProviderStatus.SEM_CREDENCIAL
                if (!verification.isDecrypted) return ProviderStatus.FALHA_DESCRIPTOGRAFIA
                return runtimeStatuses[id] ?: ProviderStatus.CREDENCIAL_SALVA
            }

            val providerConfigs = listOf(
                AIProviderConfig(
                    id = "gemini",
                    type = ProviderType.GEMINI,
                    displayName = "Google Gemini",
                    apiKey = secureKeyStore.getApiKey("gemini"),
                    isEnabled = "gemini" in enabledSet,
                    selectedModel = chosenModels["gemini"] ?: "",
                    status = getProviderStatus("gemini", secureKeyStore.getApiKey("gemini")),
                    availableModels = runtimeDiscoveredModels["gemini"] ?: emptyList()
                ),
                AIProviderConfig(
                    id = "openai",
                    type = ProviderType.OPENAI,
                    displayName = "OpenAI",
                    apiKey = secureKeyStore.getApiKey("openai"),
                    isEnabled = "openai" in enabledSet,
                    selectedModel = chosenModels["openai"] ?: "gpt-4o",
                    status = getProviderStatus("openai", secureKeyStore.getApiKey("openai")),
                    availableModels = runtimeDiscoveredModels["openai"] ?: emptyList()
                ),
                AIProviderConfig(
                    id = "claude",
                    type = ProviderType.CLAUDE,
                    displayName = "Anthropic Claude",
                    apiKey = secureKeyStore.getApiKey("claude"),
                    isEnabled = "claude" in enabledSet,
                    selectedModel = chosenModels["claude"] ?: "claude-3-5-sonnet-20241022",
                    status = getProviderStatus("claude", secureKeyStore.getApiKey("claude")),
                    availableModels = runtimeDiscoveredModels["claude"] ?: emptyList()
                ),
                AIProviderConfig(
                    id = "openrouter",
                    type = ProviderType.OPENROUTER,
                    displayName = "OpenRouter",
                    apiKey = secureKeyStore.getApiKey("openrouter"),
                    isEnabled = "openrouter" in enabledSet,
                    selectedModel = chosenModels["openrouter"] ?: OpenRouterProvider.DEFAULT_FREE_MODEL,
                    status = getProviderStatus("openrouter", secureKeyStore.getApiKey("openrouter")),
                    availableModels = runtimeDiscoveredModels["openrouter"] ?: emptyList()
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
        if (apiKey.isBlank()) {
            runtimeStatuses[providerId] = ProviderStatus.SEM_CREDENCIAL
        } else {
            runtimeStatuses[providerId] = ProviderStatus.CREDENCIAL_SALVA
        }
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


    fun updateAILevel(level: AILevel) = setAILevel(level)
    fun setManualProvider(providerId: String?) = setManualProviderOverride(providerId)
    fun toggleTheme(enabled: Boolean) = setDarkTheme(enabled)

    fun saveTaskAssignments(assignments: Map<String, TaskAssignment>) {
        viewModelScope.launch {
            preferencesManager.saveTaskAssignments(assignments)
        }
    }

    fun setWizardOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isWizardOpen = isOpen) }
    }

    fun addCustomModel(providerId: String, modelId: String, modelName: String, level: AILevel, capabilities: Set<ModelCapability>) {
        viewModelScope.launch {
            selectModel(providerId, modelId)
        }
    }

    fun refreshModels(providerId: String) {
        testConnection(providerId)
    }

    fun testConnection(providerId: String) {
        val providerConfig = _uiState.value.providers.find { it.id == providerId } ?: return
        val verification = secureKeyStore.verifyCredential(providerId)
        if (verification.isStored && !verification.isDecrypted) {
            runtimeStatuses[providerId] = ProviderStatus.FALHA_DESCRIPTOGRAFIA
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testResultMessage = "✗ Falha ao descriptografar credencial no Android Keystore: ${verification.errorMessage}",
                lastTestSuccess = false,
                providers = _uiState.value.providers.map { p ->
                    if (p.id == providerId) p.copy(status = ProviderStatus.FALHA_DESCRIPTOGRAFIA, lastError = verification.errorMessage) else p
                }
            )
            return
        }

        if (providerConfig.apiKey.isBlank()) {
            runtimeStatuses[providerId] = ProviderStatus.SEM_CREDENCIAL
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testResultMessage = "Insira uma chave de API para testar.",
                lastTestSuccess = false,
                providers = _uiState.value.providers.map { p ->
                    if (p.id == providerId) p.copy(status = ProviderStatus.SEM_CREDENCIAL) else p
                }
            )
            return
        }

        runtimeStatuses[providerId] = ProviderStatus.TESTING
        _uiState.value = _uiState.value.copy(
            isTestingConnection = true,
            testResultMessage = null,
            lastTestSuccess = null,
            providers = _uiState.value.providers.map { p ->
                if (p.id == providerId) p.copy(status = ProviderStatus.TESTING) else p
            }
        )

        viewModelScope.launch {
            val providerInstance: AIProvider = when (providerConfig.type) {
                ProviderType.GEMINI -> GeminiProvider(context) { providerConfig.apiKey }
                ProviderType.OPENAI -> OpenAIProvider(context, { providerConfig.apiKey }, { providerConfig.baseUrl })
                ProviderType.CLAUDE -> ClaudeProvider(context) { providerConfig.apiKey }
                ProviderType.OPENROUTER -> OpenRouterProvider(context) { providerConfig.apiKey }
                ProviderType.MOCK, ProviderType.CUSTOM -> MockSystemProvider()
            }

            val testResult: ConnectionTestResult = providerInstance.testConnection(providerConfig.apiKey, providerConfig.baseUrl)
            runtimeStatuses[providerId] = testResult.status

            if (testResult.discoveredModels.isNotEmpty()) {
                runtimeDiscoveredModels[providerId] = testResult.discoveredModels
            }

            val updatedModels = runtimeDiscoveredModels[providerId] ?: providerConfig.availableModels
            val currentSelected = providerConfig.selectedModel
            val newSelectedModel = if (updatedModels.isNotEmpty() && (currentSelected.isBlank() || updatedModels.none { it.id == currentSelected })) {
                updatedModels.first().id
            } else {
                currentSelected
            }

            if (newSelectedModel != currentSelected && newSelectedModel.isNotBlank()) {
                preferencesManager.setSelectedModel(providerId, newSelectedModel)
            }

            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testResultMessage = if (testResult.isSuccess) "✓ ${testResult.message} [Fingerprint: ${verification.fingerprint ?: "OK"}]" else "✗ ${testResult.message}",
                lastTestSuccess = testResult.isSuccess,
                providers = _uiState.value.providers.map { p ->
                    if (p.id == providerId) {
                        p.copy(
                            status = testResult.status,
                            availableModels = updatedModels,
                            selectedModel = newSelectedModel,
                            lastError = if (testResult.isSuccess) null else testResult.message
                        )
                    } else p
                }
            )
        }
    }

    // V3.1.0: Métodos de Controle do Sistema de Voz
    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWakeWordEnabled(enabled)
            if (enabled) {
                JarvisVoiceService.startService(context)
            } else {
                JarvisVoiceService.stopService(context)
            }
        }
    }

    fun setWakeWordSpokenFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWakeWordSpokenFeedback(enabled)
        }
    }

    fun startVoiceListening() {
        viewModelScope.launch {
            preferencesManager.setWakeWordEnabled(true)
            JarvisVoiceService.startService(context)
        }
    }

    fun pauseVoiceListening() {
        JarvisVoiceService.pauseService(context)
    }

    fun resumeVoiceListening() {
        JarvisVoiceService.resumeService(context)
    }

    fun stopVoiceListening() {
        viewModelScope.launch {
            preferencesManager.setWakeWordEnabled(false)
            JarvisVoiceService.stopService(context)
        }
    }

    fun runVoiceDiagnostics() {
        viewModelScope.launch {
            diagnosticsManager.runAllDiagnostics()
        }
    }

    fun setVoiceDiagnosticsOpen(isOpen: Boolean) {
        _isVoiceDiagnosticsOpen.value = isOpen
    }

    fun testTtsQuick() {
        val tts = TTSManager(context)
        tts.speak("Teste do sistema de síntese de voz do Jarvis 3.1.0 concluído com sucesso.")
    }

}