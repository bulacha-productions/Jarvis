package com.jarvis.assistant.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.audio.TTSManager
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.repositories.ChatRepository
import com.jarvis.assistant.router.JarvisRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val attachedFile: Attachment? = null,
    val isRecordingVoice: Boolean = false,
    val isSending: Boolean = false,
    val perMessageCapacity: AILevel? = null,
    val isPlusMenuOpen: Boolean = false,
    val isCapacitySubmenuOpen: Boolean = false,
    val activeProviderName: String = "Jarvis Neural Core v${JarvisVersion.VERSION_NAME}",
    val lastDecision: SmartRoutingDecision? = null,
    val editingMessageTimestamp: Long? = null,
    val isSpeakingTTS: Boolean = false
)

class ChatViewModel(
    private val context: Context,
    private val repository: ChatRepository,
    private val router: JarvisRouter,
    private val preferencesManager: UserPreferencesManager,
    private val secureKeyStore: SecureKeyStoreManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null
    private var streamingJob: Job? = null

    val ttsManager = TTSManager(context)

    init {
        viewModelScope.launch {
            repository.cleanupEmptyConversations()
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeakingTTS = speaking)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }

    fun loadConversation(conversationId: String?) {
        val current = _uiState.value.currentConversationId
        if (conversationId != null && conversationId == current && messagesJob?.isActive == true) {
            return
        }

        if (conversationId.isNullOrBlank()) {
            startNewChat()
            return
        }

        _uiState.value = _uiState.value.copy(
            currentConversationId = conversationId,
            editingMessageTimestamp = null
        )
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(conversationId).collect { msgList ->
                _uiState.value = _uiState.value.copy(messages = msgList)
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun setAttachedFile(attachment: Attachment?) {
        _uiState.value = _uiState.value.copy(
            attachedFile = attachment,
            isPlusMenuOpen = false,
            isCapacitySubmenuOpen = false
        )
    }

    fun removeAttachedFile() {
        _uiState.value = _uiState.value.copy(attachedFile = null)
    }

    fun setRecordingVoice(isRecording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecordingVoice = isRecording)
    }

    fun togglePlusMenu(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(
            isPlusMenuOpen = isOpen,
            isCapacitySubmenuOpen = false
        )
    }

    fun toggleCapacitySubmenu(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isCapacitySubmenuOpen = isOpen)
    }

    fun setPerMessageCapacity(level: AILevel?) {
        _uiState.value = _uiState.value.copy(
            perMessageCapacity = level,
            isPlusMenuOpen = false,
            isCapacitySubmenuOpen = false
        )
    }

    /**
     * Criação Tardia (Lazy Creation):
     * Abrir nova conversa sem gravar imediatamente no banco.
     * O registro só é criado quando o usuário envia a primeira mensagem!
     */
    fun startNewChat() {
        messagesJob?.cancel()
        streamingJob?.cancel()
        ttsManager.stop()
        _uiState.value = _uiState.value.copy(
            currentConversationId = null,
            messages = emptyList(),
            inputText = "",
            attachedFile = null,
            editingMessageTimestamp = null
        )
    }

    fun cancelActiveRequest() {
        if (streamingJob?.isActive == true) {
            streamingJob?.cancel()
            val convId = _uiState.value.currentConversationId
            if (convId != null) {
                viewModelScope.launch {
                    repository.saveAssistantMessage(convId, "⛔ [Solicitação cancelada pelo usuário.]")
                }
            }
            _uiState.value = _uiState.value.copy(isSending = false)
        }
    }

    /**
     * Edição de mensagens: reabre o texto no input e prepara o repositório
     * para reescrever de forma consistente a partir daquele ponto.
     */
    fun editUserMessage(message: Message) {
        if (!message.isFromUser) return
        _uiState.value = _uiState.value.copy(
            inputText = message.content,
            attachedFile = message.attachment,
            editingMessageTimestamp = message.timestamp
        )
    }

    fun speakMessage(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachment = _uiState.value.attachedFile
        val targetConvId = _uiState.value.currentConversationId
        val overrideCapacity = _uiState.value.perMessageCapacity
        val editingTimestamp = _uiState.value.editingMessageTimestamp

        if ((text.isEmpty() && attachment == null) || _uiState.value.isSending) return

        val displayContent = if (text.isEmpty() && attachment != null) {
            "[Arquivo Anexado: ${attachment.name}]"
        } else {
            text
        }

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                inputText = "",
                attachedFile = null,
                perMessageCapacity = null,
                isPlusMenuOpen = false,
                isCapacitySubmenuOpen = false,
                editingMessageTimestamp = null,
                isSending = true
            )

            // Se for reenvio de mensagem editada, poda mensagens posteriores
            if (targetConvId != null && editingTimestamp != null) {
                repository.pruneMessagesFromTimestamp(targetConvId, editingTimestamp)
            }

            // Captura histórico antes da inserção da mensagem atual para evitar duplicação na chamada da API
            val historyBeforeCurrent = if (editingTimestamp != null) {
                _uiState.value.messages.filter { it.timestamp < editingTimestamp }
            } else {
                _uiState.value.messages
            }

            // Criação tardia: saveUserMessage cria a conversa no Room se targetConvId for nulo!
            val (convId, _) = repository.saveUserMessage(targetConvId, displayContent, attachment)
            if (_uiState.value.currentConversationId != convId) {
                _uiState.value = _uiState.value.copy(currentConversationId = convId)
                loadConversation(convId)
            }

            val taskAssignments = preferencesManager.taskAssignments.firstOrNull() ?: emptyMap()
            val isMultiAgent = preferencesManager.isMultiAgentEnabled.firstOrNull() ?: true
            val multiAgentMode = preferencesManager.multiAgentMode.firstOrNull() ?: MultiAgentMode.AUTOMATIC
            val routingMode = preferencesManager.routingMode.firstOrNull() ?: RoutingMode.AUTOMATIC
            val preferredProvider = preferencesManager.preferredProvider.firstOrNull()
            val enabledProviders = preferencesManager.enabledProviders.firstOrNull() ?: setOf("gemini", "openai", "claude", "openrouter")
            val manualOverride = preferencesManager.manualProviderOverride.firstOrNull()
            val selectedModels = preferencesManager.selectedModels.firstOrNull() ?: emptyMap()
            val financialPolicy = preferencesManager.financialPolicy.firstOrNull() ?: FinancialPolicy.FREE_ONLY
            val monthlyBudget = preferencesManager.monthlyBudget.firstOrNull() ?: 5.0
            val currentSpend = preferencesManager.currentSpend.firstOrNull() ?: 0.0
            val openAiKey = secureKeyStore?.getApiKey("openai")
            val isAutoSpeak = preferencesManager.isTtsAutoSpeak.firstOrNull() ?: false

            val history = historyBeforeCurrent
            val responseBuilder = StringBuilder()

            try {
                router.routeAndExecute(
                    prompt = displayContent,
                    history = history,
                    attachment = attachment,
                    overrideLevel = overrideCapacity,
                    taskAssignments = taskAssignments,
                    enabledProviderIds = enabledProviders,
                    multiAgentMode = multiAgentMode,
                    isMultiAgentEnabled = isMultiAgent,
                    routingMode = routingMode,
                    preferredProviderId = preferredProvider,
                    manualProviderId = manualOverride,
                    selectedModels = selectedModels,
                    financialPolicy = financialPolicy,
                    currentSpend = currentSpend,
                    monthlyBudget = monthlyBudget,
                    openAiApiKey = openAiKey
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                }

                val finalResponse = responseBuilder.toString().ifBlank {
                    "Nenhuma resposta foi gerada pelo modelo selecionado."
                }
                repository.saveAssistantMessage(convId, finalResponse)

                // Leitura TTS automática se ativada nas configurações
                if (isAutoSpeak) {
                    ttsManager.speak(finalResponse)
                }
            } catch (_: CancellationException) {
                // Cancelamento voluntário do usuário
            } catch (e: Exception) {
                repository.saveAssistantMessage(
                    convId,
                    "Falha ao processar solicitação: ${e.message ?: "Erro desconhecido"}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }
}
