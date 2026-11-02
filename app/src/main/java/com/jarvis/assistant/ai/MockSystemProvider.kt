package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockSystemProvider : AIProvider {
    override val id: String = "unconfigured_system"
    override val displayName: String = "Jarvis Core (Desconectado)"

    override suspend fun isAvailable(): Boolean = false

    override suspend fun getAvailableModels(level: AILevel): List<String> = emptyList()

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel
    ): Flow<String> = flow {
        emit("Os provedores de IA serão configurados na próxima etapa. Nenhuma conexão remota ativa.")
    }
}
