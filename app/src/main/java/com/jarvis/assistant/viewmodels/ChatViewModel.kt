package com.jarvis.assistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.repositories.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val attachedFile: Attachment? = null,
    val isRecordingVoice: Boolean = false,
    val isSending: Boolean = false,
    val activeProviderName: String = "Jarvis Core"
)

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

    fun loadConversation(conversationId: String?) {
        val current = _uiState.value.currentConversationId

        // Don't restart collection if already listening to the same conversation
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
        _uiState.value = _uiState.value.copy(attachedFile = attachment)
    }

    fun setRecordingVoice(isRecording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecordingVoice = isRecording)
    }

    fun startNewChat() {
        messagesJob?.cancel()
        viewModelScope.launch {
            val id = repository.createNewConversation()
            loadConversation(id)
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachment = _uiState.value.attachedFile
        val convId = _uiState.value.currentConversationId

        if ((text.isEmpty() && attachment == null) || convId == null) return

        val messageText = if (text.isEmpty() && attachment != null) "[Arquivo Anexado: ${attachment.name}]" else text

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                inputText = "",
                attachedFile = null,
                isSending = true
            )

            // Save immediately in database
            repository.saveUserMessage(convId, messageText, attachment)

            // Simulated Jarvis Core response (Fase 1/1.1)
            kotlinx.coroutines.delay(450)
            repository.saveAssistantMessage(
                convId,
                "Recebido. Operando com persistência Room local e integridade referencial ativa."
            )

            _uiState.value = _uiState.value.copy(isSending = false)
        }
    }
}
