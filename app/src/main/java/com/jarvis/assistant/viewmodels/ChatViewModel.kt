package com.jarvis.assistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.TaskAssignment
import com.jarvis.assistant.repositories.ChatRepository
import com.jarvis.assistant.router.JarvisRouter
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
    val activeProviderName: String = "Jarvis Neural Core"
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val router: JarvisRouter,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null
    private var streamingJob: Job? = null

    fun loadConversation(conversationId: String?) {
        val current = _uiState.value.currentConversationId
        if (conversationId != null && conversationId == current && messagesJob?.isActive == true) {
            return
        }

        if (conversationId == null) {
            if (current == null) {
                startNewChat()
            }
            return
        }

        _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
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

    fun startNewChat() {
        messagesJob?.cancel()
        streamingJob?.cancel()
        viewModelScope.launch {
            val id = repository.createNewConversation()
            loadConversation(id)
        }
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

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachment = _uiState.value.attachedFile
        val convId = _uiState.value.currentConversationId
        val overrideCapacity = _uiState.value.perMessageCapacity

        if ((text.isEmpty() && attachment == null) || convId == null || _uiState.value.isSending) return

        val displayContent = if (text.isEmpty() && attachment != null) {
            "[Arquivo Anexado: ${attachment.name}]"
        } else {
            text
        }

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            // Reset per-message capacity after capturing it (Rule: only applies to this single message!)
            _uiState.value = _uiState.value.copy(
                inputText = "",
                attachedFile = null,
                perMessageCapacity = null,
                isPlusMenuOpen = false,
                isCapacitySubmenuOpen = false,
                isSending = true
            )

            // Save user message immediately in Room
            repository.saveUserMessage(convId, displayContent, attachment)

            val taskAssignments = preferencesManager.taskAssignments.firstOrNull() ?: emptyMap()
            val history = _uiState.value.messages

            val responseBuilder = StringBuilder()

            try {
                router.routeAndExecute(
                    prompt = displayContent,
                    history = history,
                    attachment = attachment,
                    overrideLevel = overrideCapacity,
                    taskAssignments = taskAssignments
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                }

                val finalResponse = responseBuilder.toString().ifBlank {
                    "Nenhuma resposta foi gerada pelo modelo selecionado."
                }
                repository.saveAssistantMessage(convId, finalResponse)
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
