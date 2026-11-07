package com.jarvis.assistant.router

import com.jarvis.assistant.agent.MultiAgentOrchestrator
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.*
import kotlinx.coroutines.flow.Flow

class JarvisRouter(
    val providers: List<AIProvider>
) {
    val smartRouter = JarvisSmartRouter(providers)
    val orchestrator = MultiAgentOrchestrator(providers, smartRouter)

    fun routeAndExecute(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        taskAssignments: Map<String, TaskAssignment>,
        enabledProviderIds: Set<String>,
        multiAgentMode: MultiAgentMode = MultiAgentMode.AUTOMATIC,
        isMultiAgentEnabled: Boolean = true,
        routingMode: RoutingMode = RoutingMode.AUTOMATIC,
        preferredProviderId: String? = null,
        manualProviderId: String? = null,
        selectedModels: Map<String, String> = emptyMap(),
        financialPolicy: FinancialPolicy = FinancialPolicy.FREE_ONLY,
        currentSpend: Double = 0.0,
        monthlyBudget: Double = 5.0,
        openAiApiKey: String? = null
    ): Flow<String> {
        return orchestrator.orchestrate(
            prompt = prompt,
            history = history,
            attachment = attachment,
            overrideLevel = overrideLevel,
            taskAssignments = taskAssignments,
            enabledProviderIds = enabledProviderIds,
            multiAgentMode = multiAgentMode,
            isMultiAgentEnabled = isMultiAgentEnabled,
            routingMode = routingMode,
            preferredProviderId = preferredProviderId,
            manualProviderId = manualProviderId,
            selectedModels = selectedModels,
            financialPolicy = financialPolicy,
            currentSpend = currentSpend,
            monthlyBudget = monthlyBudget,
            openAiApiKey = openAiApiKey
        )
    }
}
