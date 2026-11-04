package com.jarvis.assistant.router

import com.jarvis.assistant.agent.MultiAgentOrchestrator
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.TaskAssignment
import kotlinx.coroutines.flow.Flow

class JarvisRouter(
    private val providers: List<AIProvider>
) {
    private val orchestrator = MultiAgentOrchestrator(providers)

    fun routeAndExecute(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        taskAssignments: Map<String, TaskAssignment>,
        enabledProviderIds: Set<String>,
        isMultiAgentEnabled: Boolean = true,
        manualProviderId: String? = null
    ): Flow<String> {
        return orchestrator.orchestrate(
            prompt = prompt,
            history = history,
            attachment = attachment,
            overrideLevel = overrideLevel,
            taskAssignments = taskAssignments,
            enabledProviderIds = enabledProviderIds,
            isMultiAgentEnabled = isMultiAgentEnabled,
            manualProviderId = manualProviderId
        )
    }
}
