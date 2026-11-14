package com.jarvis.assistant.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.ai.*
import com.jarvis.assistant.audio.TTSManager
import com.jarvis.assistant.audio.VoiceDiagnosticsManager
import com.jarvis.assistant.data.local.preferences.CredentialLookupResult
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.repositories.ModelCatalogRepository
import com.jarvis.assistant.service.JarvisVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val secureKeyStore: SecureKeyStoreManager,
    private val catalogRepository: ModelCatalogRepository = ModelCatalogRepository.getInstance(context)
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

    private val runtimeStatuses = mutableMapOf<String, ProviderStatus>()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            val enabledSet = preferencesManager.enabledProviders.firstOrNull()
                ?: setOf("gemini", "openai", "claude", "openrouter")
            val chosenModels = preferencesManager.selectedModels.firstOrNull() ?: emptyMap()
            val keystoreOk = secureKeyStore.isKeystoreFunctional()

            val descriptors = listOf(
                Triple("gemini", ProviderType.GEMINI, "Google Gemini"),
                Triple("openai", ProviderType.OPENAI, "OpenAI"),
                Triple("claude", ProviderType.CLAUDE, "Anthropic Claude"),
                Triple("openrouter", ProviderType.OPENROUTER, "OpenRouter")
            )

            val providerConfigs = descriptors.map { (id, type, displayName) ->
                val credResult = secureKeyStore.getCredential(id)
                val persistedModels = catalogRepository.getModelsForProvider(id)
                val catalogError = catalogRepository.getLastError(id)

                val (status, masked, fingerprint, errMsg, isKeySaved, isDecryptionFailed) = when (credResult) {
                    is CredentialLookupResult.Success -> {
                        val effectiveStatus = runtimeStatuses[id]
                            ?: if (persistedModels.isNotEmpty()) ProviderStatus.CREDENCIAL_SALVA else ProviderStatus.CREDENCIAL_SALVA
                        ConfigProjection(
                            status = effectiveStatus,
                            masked = credResult.maskedPreview,
                            fingerprint = credResult.fingerprint,
                            error = catalogError,
                            isSaved = true,
                            decryptionFailed = false
                        )
                    }
                    is CredentialLookupResult.Missing -> {
                        ConfigProjection(
                            status = ProviderStatus.SEM_CREDENCIAL,
                            masked = "",
                            fingerprint = null,
                            error = catalogError,
                            isSaved = false,
                            decryptionFailed = false
                        )
                    }
                    is CredentialLookupResult.DecryptionFailure -> {
                        ConfigProjection(
                            status = ProviderStatus.FALHA_DESCRIPTOGRAFIA,
                            masked = "",
                            fingerprint = null,
                            error = credResult.reason,
                            isSaved = false,
                            decryptionFailed = true
                        )
                    }
                }

                val rawSelected = chosenModels[id]?.trim().orEmpty()
                val sanitizedSelected = if (rawSelected == "claude-3-5-sonnet-20241022") "" else rawSelected

                AIProviderConfig(
                    id = id,
                    type = type,
                    displayName = displayName,
                    apiKey = "", // Nunca expõe chave em texto puro no estado da UI
                    isEnabled = id in enabledSet,
                    availableModels = persistedModels,
                    selectedModel = sanitizedSelected,
                    status = status,
                    lastError = errMsg,
                    maskedKey = masked,
                    keyFingerprint = fingerprint,
                    isKeySaved = isKeySaved,
                    isDecryptionFailed = isDecryptionFailed,
                    isConnectionValidated = status.isConnected,
                    isCatalogLoaded = persistedModels.isNotEmpty()
                )
            }

            _uiState.update {
                it.copy(
                    providers = providerConfigs,
                    keystoreHealthy = keystoreOk
                )
            }
        }
    }

    private data class ConfigProjection(
        val status: ProviderStatus,
        val masked: String,
        val fingerprint: String?,
        val error: String?,
        val isSaved: Boolean,
        val decryptionFailed: Boolean
    )

    fun updateApiKey(providerId: String, apiKey: String) = saveProviderKey(providerId, apiKey)
    fun saveApiKey(providerId: String, apiKey: String) = saveProviderKey(providerId, apiKey)

    fun saveProviderKey(providerId: String, rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    testResultMessage = "Informe uma chave de API válida antes de salvar. Para apagar, use o botão Remover.",
                    lastTestSuccess = false
                )
            }
            return
        }

        val saveResult = secureKeyStore.saveApiKey(providerId, trimmed)
        saveResult.fold(
            onSuccess = { verification ->
                runtimeStatuses[providerId] = ProviderStatus.CREDENCIAL_SALVA
                catalogRepository.setCatalogError(providerId, null)
                _uiState.update {
                    it.copy(
                        testResultMessage = "✓ Chave salva com segurança no Android Keystore (${verification.maskedPreview} | ${verification.fingerprint}).",
                        lastTestSuccess = true
                    )
                }
                loadProviders()
            },
            onFailure = { error ->
                val msg = error.message ?: "Falha ao criptografar chave no Android Keystore."
                runtimeStatuses[providerId] = ProviderStatus.FALHA_DESCRIPTOGRAFIA
                catalogRepository.setCatalogError(providerId, msg)
                _uiState.update {
                    it.copy(
                        testResultMessage = "✗ Erro ao salvar credencial: $msg",
                        lastTestSuccess = false,
                        keystoreHealthy = secureKeyStore.isKeystoreFunctional()
                    )
                }
                loadProviders()
            }
        )
    }

    fun removeApiKey(providerId: String) {
        val removed = secureKeyStore.removeApiKey(providerId)
        runtimeStatuses[providerId] = ProviderStatus.SEM_CREDENCIAL
        catalogRepository.clearProviderCatalog(providerId)
        catalogRepository.setCatalogError(providerId, null)
        viewModelScope.launch {
            preferencesManager.setSelectedModel(providerId, "")
            loadProviders()
            _uiState.update {
                it.copy(
                    testResultMessage = if (removed) "Chave removida do Android Keystore." else "Nenhuma chave removida.",
                    lastTestSuccess = removed
                )
            }
        }
    }

    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) = toggleProvider(providerId, isEnabled)

    fun toggleProvider(providerId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setProviderEnabled(providerId, isEnabled)
            loadProviders()
        }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedModel(providerId, modelId.trim())
            loadProviders()
        }
    }

    fun toggleModelEnabled(providerId: String, modelId: String, isEnabled: Boolean) {
        catalogRepository.setModelEnabled(providerId, modelId, isEnabled)
        loadProviders()
    }

    fun addCustomModel(
        providerId: String,
        modelId: String,
        modelName: String,
        level: AILevel,
        capabilities: Set<ModelCapability>
    ) {
        val cleanId = modelId.trim()
        if (cleanId.isBlank()) return
        val cleanName = modelName.trim().ifBlank { cleanId }
        val customModel = AIModelInfo(
            id = cleanId,
            displayName = cleanName,
            providerId = providerId,
            defaultLevel = level,
            capabilities = capabilities.ifEmpty { setOf(ModelCapability.TEXT) },
            description = "Modelo cadastrado manualmente pelo usuário",
            isFree = providerId == "gemini" || cleanId.endsWith(":free"),
            promptPricePerMillion = if (providerId == "gemini" || cleanId.endsWith(":free")) 0.0 else null,
            completionPricePerMillion = if (providerId == "gemini" || cleanId.endsWith(":free")) 0.0 else null
        )
        catalogRepository.addCustomModel(providerId, customModel)
        selectModel(providerId, cleanId)
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            preferencesManager.setRoutingMode(mode)
            if (mode != RoutingMode.MANUAL) {
                preferencesManager.setManualProvider(null)
            }
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
            preferencesManager.setRoutingMode(
                if (providerId.isNullOrBlank()) RoutingMode.AUTOMATIC else RoutingMode.MANUAL
            )
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

    fun refreshModels(providerId: String) {
        testConnection(providerId)
    }

    fun testConnection(providerId: String, candidateKey: String? = null) {
        val providerConfig = _uiState.value.providers.find { it.id == providerId } ?: return

        if (!candidateKey.isNullOrBlank()) {
            val saveRes = secureKeyStore.saveApiKey(providerId, candidateKey)
            if (saveRes.isFailure) {
                val errMsg = saveRes.exceptionOrNull()?.message ?: "Falha ao salvar chave no Android Keystore."
                runtimeStatuses[providerId] = ProviderStatus.FALHA_DESCRIPTOGRAFIA
                catalogRepository.setCatalogError(providerId, errMsg)
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testResultMessage = "✗ $errMsg",
                        lastTestSuccess = false
                    )
                }
                loadProviders()
                return
            }
        }

        val credResult = secureKeyStore.getCredential(providerId)
        val rawKey: String
        val fingerprint: String
        when (credResult) {
            is CredentialLookupResult.Missing -> {
                runtimeStatuses[providerId] = ProviderStatus.SEM_CREDENCIAL
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testResultMessage = "Insira e salve uma chave de API para testar ${providerConfig.displayName}.",
                        lastTestSuccess = false
                    )
                }
                loadProviders()
                return
            }
            is CredentialLookupResult.DecryptionFailure -> {
                runtimeStatuses[providerId] = ProviderStatus.FALHA_DESCRIPTOGRAFIA
                catalogRepository.setCatalogError(providerId, credResult.reason)
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testResultMessage = "✗ Falha ao descriptografar credencial no Android Keystore: ${credResult.reason}",
                        lastTestSuccess = false
                    )
                }
                loadProviders()
                return
            }
            is CredentialLookupResult.Success -> {
                rawKey = credResult.apiKey
                fingerprint = credResult.fingerprint
            }
        }

        runtimeStatuses[providerId] = ProviderStatus.TESTING
        _uiState.update { state ->
            state.copy(
                isTestingConnection = true,
                testResultMessage = null,
                lastTestSuccess = null,
                providers = state.providers.map { p ->
                    if (p.id == providerId) p.copy(status = ProviderStatus.TESTING, lastError = null) else p
                }
            )
        }

        viewModelScope.launch {
            val providerInstance: AIProvider = when (providerConfig.type) {
                ProviderType.GEMINI -> GeminiProvider(context) { rawKey }
                ProviderType.OPENAI -> OpenAIProvider(context, { rawKey }, { providerConfig.baseUrl })
                ProviderType.CLAUDE -> ClaudeProvider(context) { rawKey }
                ProviderType.OPENROUTER -> OpenRouterProvider(context) { rawKey }
                ProviderType.MOCK, ProviderType.CUSTOM -> MockSystemProvider()
            }

            val testResult = providerInstance.testConnection(rawKey, providerConfig.baseUrl)
            runtimeStatuses[providerId] = testResult.status

            if (testResult.discoveredModels.isNotEmpty()) {
                catalogRepository.saveDiscoveredModels(providerId, testResult.discoveredModels)
            }
            catalogRepository.setCatalogError(
                providerId,
                if (testResult.isSuccess) null else testResult.message
            )

            val updatedModels = catalogRepository.getModelsForProvider(providerId)
            val currentSelected = preferencesManager.selectedModels.firstOrNull()?.get(providerId)?.trim().orEmpty()
            val preservedSelected = when {
                updatedModels.isEmpty() -> currentSelected
                currentSelected.isNotBlank() && updatedModels.any { it.id == currentSelected } -> currentSelected
                else -> updatedModels.first().id
            }

            if (preservedSelected != currentSelected && preservedSelected.isNotBlank()) {
                preferencesManager.setSelectedModel(providerId, preservedSelected)
            }

            loadProviders()
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    testResultMessage = if (testResult.isSuccess) {
                        "✓ ${testResult.message} [$fingerprint]"
                    } else {
                        "✗ ${testResult.message}"
                    },
                    lastTestSuccess = testResult.isSuccess
                )
            }
        }
    }

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
        tts.speak("Teste do sistema de síntese de voz do Jarvis ${JarvisVersion.VERSION_NAME} concluído com sucesso.")
    }
}
