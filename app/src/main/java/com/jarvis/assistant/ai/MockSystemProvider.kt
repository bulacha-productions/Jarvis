package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.ModelCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockSystemProvider : AIProvider {
    override val id: String = "unconfigured_system"
    override val type: ProviderType = ProviderType.MOCK
    override val displayName: String = "Jarvis Core (Offline)"

    private val defaultModels = listOf(
        AIModelInfo(
            id = "jarvis-core-offline",
            displayName = "Jarvis Core (Offline)",
            providerId = id,
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(ModelCapability.TEXT),
            description = "Núcleo de inicialização do Jarvis operando localmente no dispositivo."
        )
    )

    override suspend fun isAvailable(): Boolean = false

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult {
        return ConnectionTestResult(
            isSuccess = true,
            statusCode = 200,
            message = "Núcleo Jarvis Core offline validado localmente.",
            discoveredModels = defaultModels
        )
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> {
        return Result.success(defaultModels)
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> = defaultModels

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String,
        attachment: Attachment?
    ): Flow<String> = flow {
        emit("Nenhum provedor de IA conectado. Acesse as Configurações para cadastrar sua chave do Google Gemini, OpenAI ou Claude.")
    }
}
