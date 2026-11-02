package com.jarvis.assistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.repositories.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isProviderConnected: Boolean = false
)

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun loadConversation(conversationId: String?) {
        if (conversationId == null) {
            startNewChat()
            return
        }

        _uiState.value = _uiState.value.copy(currentConversationId = conversationId)
        viewModelScope.launch {
            repository.getMessages(conversationId).collect { msgList ->
                _uiState.value = _uiState.value.copy(messages = msgList)
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun startNewChat() {
        viewModelScope.launch {
            val id = repository.createNewConversation()
            loadConversation(id)
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val convId = _uiState.value.currentConversationId

        if (text.isEmpty() || convId == null) return

        viewModelScope.launch {
            repository.saveUserMessage(convId, text)
            _uiState.value = _uiState.value.copy(inputText = "")
        }
    }
}
