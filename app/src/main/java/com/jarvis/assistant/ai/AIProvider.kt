package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface AIProvider {
    val id: String
    val displayName: String

    suspend fun isAvailable(): Boolean
    suspend fun getAvailableModels(level: AILevel): List<String>
    
    fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel
    ): Flow<String>
}
