package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockSystemProvider : AIProvider {
    override val id: String = "unconfigured_system"
    override val type: ProviderType = ProviderType.MOCK
    override val displayName: String = "Jarvis Core (Offline)"

    override suspend fun isAvailable(): Boolean = false

    override suspend fun testConnection(apiKey: String, baseUrl: String): Result<List<String>> {
        return Result.success(listOf("mock-offline-v1"))
    }

    override suspend fun getAvailableModels(level: AILevel): List<String> = listOf("jarvis-core-offline")

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String
    ): Flow<String> = flow {
        emit("Os provedores de IA serão configurados na próxima etapa. Nenhuma conexão remota ativa.")
    }
}
