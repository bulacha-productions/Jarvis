package com.jarvis.assistant.router

import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Preparação arquitetural para o sistema multiagente da V2
data class ConversationContext(
    val conversationId: String,
    val detectedTask: ModelTask,
    val effectiveLevel: AILevel,
    val targetProviderId: String,
    val targetModelName: String,
    val wasSubstituted: Boolean = false,
    val substitutionReason: String? = null
)

interface SpecializedAgent {
    val agentId: String
    val name: String
    val supportedTasks: Set<ModelTask>
    fun canHandle(task: ModelTask, attachment: Attachment?): Boolean
}

class JarvisRouter(
    private val providers: List<AIProvider>
) {

    fun routeAndExecute(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        taskAssignments: Map<String, TaskAssignment>
    ): Flow<String> = flow {
        // 1. Detect required capability & task
        val task = CapabilityDetector.detectTask(prompt, attachment)

        // 2. Determine target provider and model
        val assignment = taskAssignments[task.id]
        val effectiveLevel = overrideLevel ?: assignment?.level ?: task.defaultLevel

        var targetProviderId = assignment?.providerId ?: "gemini"
        var targetModelName = assignment?.modelName ?: ""

        // If overrideLevel was specified by user on this message
        if (overrideLevel != null) {
            when (overrideLevel) {
                AILevel.LIGHT -> {
                    targetModelName = if (targetProviderId == "gemini") "gemini-1.5-flash" else "gpt-4o-mini"
                }
                AILevel.MEDIUM -> {
                    targetModelName = if (targetProviderId == "gemini") "gemini-2.5-flash" else "gpt-4o-mini"
                }
                AILevel.HIGH -> {
                    targetModelName = if (targetProviderId == "gemini") "gemini-2.5-pro" else if (targetProviderId == "claude") "claude-3-5-sonnet" else "gpt-4o"
                }
            }
        }

        // 3. Check if target provider is available
        var provider = providers.find { it.id == targetProviderId && it.isAvailable() }
        var substitutionNotice: String? = null

        if (provider == null) {
            // Find another provider that is actually available
            val fallbackProvider = providers.firstOrNull { it.id != "unconfigured_system" && it.isAvailable() }
            if (fallbackProvider != null) {
                val originalName = providers.find { it.id == targetProviderId }?.displayName ?: targetProviderId
                substitutionNotice = "⚡ [Roteador Jarvis: Provedor '$originalName' sem chave ativa. Encaminhando solicitação para '${fallbackProvider.displayName}'.]\n\n"
                provider = fallbackProvider
                targetProviderId = fallbackProvider.id
                targetModelName = ""
            }
        }

        if (provider == null) {
            emit("Nenhum provedor de IA com chave de API ativa está configurado. Acesse as Configurações para cadastrar sua chave do Google Gemini, OpenAI ou Claude.")
            return@flow
        }

        if (substitutionNotice != null) {
            emit(substitutionNotice)
        }

        // 4. Delegate to provider
        provider.generateStreamResponse(
            prompt = prompt,
            history = history,
            level = effectiveLevel,
            modelName = targetModelName,
            attachment = attachment
        ).collect { chunk ->
            emit(chunk)
        }
    }
}
