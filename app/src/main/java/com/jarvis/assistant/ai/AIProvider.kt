package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow

enum class ProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI"),
    CLAUDE("Anthropic Claude"),
    OPENROUTER("OpenRouter"),
    CUSTOM("Provedor Personalizado (OpenAI Compatível)"),
    MOCK("Jarvis Core (Offline)")
}

enum class ProviderStatus {
    CONNECTED,
    TESTING,
    ERROR,
    UNCONFIGURED
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val discoveredModels: List<AIModelInfo> = emptyList()
)

data class AIProviderConfig(
    val id: String,
    val type: ProviderType,
    val displayName: String,
    val apiKey: String,
    val isEnabled: Boolean = true,
    val baseUrl: String = "",
    val availableModels: List<AIModelInfo> = emptyList(),
    val selectedModel: String = "",
    val status: ProviderStatus = ProviderStatus.UNCONFIGURED,
    val lastError: String? = null
)

interface AIProvider {
    val id: String
    val type: ProviderType
    val displayName: String

    suspend fun isAvailable(): Boolean
    suspend fun testConnection(apiKey: String, baseUrl: String = ""): ConnectionTestResult
    suspend fun queryRemoteModels(apiKey: String, baseUrl: String = ""): Result<List<AIModelInfo>>
    suspend fun getAvailableModels(level: AILevel): List<AIModelInfo>
    fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String = "",
        attachment: Attachment? = null
    ): Flow<String>
}
