package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow

enum class ProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI"),
    CLAUDE("Anthropic Claude"),
    CUSTOM("Provedor Personalizado (OpenAI Compatível)"),
    MOCK("Jarvis Core (Offline)")
}

enum class ProviderStatus {
    UNCONFIGURED,
    CONNECTED,
    TESTING,
    ERROR
}

data class AIProviderConfig(
    val id: String,
    val type: ProviderType,
    val displayName: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val isEnabled: Boolean = true,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "",
    val status: ProviderStatus = ProviderStatus.UNCONFIGURED,
    val lastError: String? = null
)

interface AIProvider {
    val id: String
    val type: ProviderType
    val displayName: String

    suspend fun isAvailable(): Boolean
    suspend fun testConnection(apiKey: String, baseUrl: String = ""): Result<List<String>>
    suspend fun getAvailableModels(level: AILevel): List<String>
    
    fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String = ""
    ): Flow<String>
}
