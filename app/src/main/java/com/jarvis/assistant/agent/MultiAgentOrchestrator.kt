package com.jarvis.assistant.agent

import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.router.JarvisSmartRouter
import com.jarvis.assistant.router.RequestAnalysis
import com.jarvis.assistant.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MultiAgentOrchestrator(
    private val providers: List<AIProvider>,
    private val smartRouter: JarvisSmartRouter = JarvisSmartRouter(providers)
) {

    companion object {
        const val MAX_AGENT_STEPS = 4
    }

    fun orchestrate(
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
    ): Flow<String> = flow {
        val analysis = smartRouter.analyzeRequest(prompt, history, attachment)

        val shouldUseMultiAgent: Boolean = when {
            !isMultiAgentEnabled -> false
            multiAgentMode == MultiAgentMode.ONE_AGENT -> false
            multiAgentMode == MultiAgentMode.TWO_AGENTS -> true
            else -> analysis.recommendedAgentCount > 1
        }

        // Cenário A: Agente Único
        if (!shouldUseMultiAgent) {
            val targetTask = analysis.task
            val targetLevel = overrideLevel ?: analysis.complexity

            val decision = smartRouter.selectModelAndProvider(
                task = targetTask,
                targetLevel = targetLevel,
                routingMode = routingMode,
                preferredProviderId = preferredProviderId,
                manualProviderId = manualProviderId,
                manualModelId = selectedModels[manualProviderId ?: ""],
                enabledProviders = enabledProviderIds,
                selectedModels = selectedModels,
                financialPolicy = financialPolicy,
                currentSpend = currentSpend,
                monthlyBudget = monthlyBudget,
                promptLength = prompt.length
            )

            if (!decision.isSuccess) {
                emit(
                    decision.failureReason
                        ?: "Nenhum provedor/modelo elegível disponível. Acesse Configurações para ativar um provedor e testar a conexão."
                )
                return@flow
            }

            val provider = providers.find { it.id == decision.providerId }
            if (provider == null) {
                emit("Provedor '${decision.providerId}' não encontrado na arquitetura do aplicativo.")
                return@flow
            }

            emit("> 🔀 **Rota:** ${decision.providerDisplayName} • `${decision.modelId}`\n\n")

            val buffer = StringBuilder()
            provider.generateStreamResponse(
                prompt = prompt,
                history = history,
                level = targetLevel,
                modelName = decision.modelId,
                attachment = attachment
            ).collect { chunk ->
                buffer.append(chunk)
                emit(chunk)
            }

            ToolRegistry.processOrSanitizeOutput(buffer.toString(), openAiApiKey)
            return@flow
        }

        // Cenário B: Execução Multiagente
        val plan = createExecutionPlan(prompt, analysis, multiAgentMode)
        val agentBadgesHeader = plan.participatingAgents.joinToString(" ") { "[${it.iconEmoji} ${it.shortBadge}]" }

        emit("🤖 **JARVIS v${JarvisVersion.VERSION_NAME} — Coordenação Multiagente**\n")
        emit("Modo: **${multiAgentMode.displayName}** | Especialistas: $agentBadgesHeader\n\n")

        val intermediateResults = mutableListOf<AgentStepResult>()
        var accumulatedContext = ""
        var stepCounter = 0

        for (step in plan.steps) {
            stepCounter++
            if (stepCounter > MAX_AGENT_STEPS) break

            val decision = smartRouter.selectModelAndProvider(
                task = step.assignedTask,
                targetLevel = step.targetLevel,
                routingMode = routingMode,
                preferredProviderId = preferredProviderId,
                manualProviderId = manualProviderId,
                manualModelId = selectedModels[manualProviderId ?: ""],
                enabledProviders = enabledProviderIds,
                selectedModels = selectedModels,
                financialPolicy = financialPolicy,
                currentSpend = currentSpend,
                monthlyBudget = monthlyBudget,
                promptLength = step.promptInstruction.length
            )

            if (!decision.isSuccess) {
                val failMsg = decision.failureReason ?: "Roteamento indisponível para ${step.agent.displayName}."
                emit("⚠️ *[Passo $stepCounter/${plan.steps.size}: $failMsg]*\n\n")
                intermediateResults.add(
                    AgentStepResult(
                        stepIndex = stepCounter,
                        agent = step.agent,
                        summary = "Falha de roteamento",
                        content = failMsg,
                        isSuccess = false
                    )
                )
                continue
            }

            val provider = providers.find { it.id == decision.providerId }
            if (provider == null) {
                emit("⚠️ *[Aviso: Provedor '${decision.providerId}' indisponível. Etapa omitida.]*\n\n")
                continue
            }

            emit("⏳ *[Passo $stepCounter/${plan.steps.size}: ${step.agent.displayName} via ${decision.providerDisplayName} • `${decision.modelId}`]*\n\n")

            val enrichedPrompt = if (accumulatedContext.isNotBlank()) {
                "${step.promptInstruction}\n\n[Contexto e Entregas das Etapas Anteriores]:\n$accumulatedContext"
            } else {
                step.promptInstruction
            }

            val stepOutputBuilder = StringBuilder()
            try {
                provider.generateStreamResponse(
                    prompt = enrichedPrompt,
                    history = if (stepCounter == 1) history else emptyList(),
                    level = step.targetLevel,
                    modelName = decision.modelId,
                    attachment = if (stepCounter == 1) attachment else null
                ).collect { chunk ->
                    stepOutputBuilder.append(chunk)
                }

                val resultText = stepOutputBuilder.toString().trim()
                if (resultText.startsWith("Erro ") || resultText.startsWith("Falha ") || resultText.startsWith("Erro:")) {
                    intermediateResults.add(
                        AgentStepResult(
                            stepIndex = stepCounter,
                            agent = step.agent,
                            summary = "Falha na etapa",
                            content = resultText,
                            isSuccess = false
                        )
                    )
                } else {
                    val sanitized = ToolRegistry.processOrSanitizeOutput(resultText, openAiApiKey)
                    intermediateResults.add(
                        AgentStepResult(
                            stepIndex = stepCounter,
                            agent = step.agent,
                            summary = step.specificGoal,
                            content = sanitized,
                            isSuccess = true
                        )
                    )
                    accumulatedContext += "\n--- [Entrega do ${step.agent.displayName}] ---\n" + sanitized.take(1800)
                }
            } catch (e: Exception) {
                intermediateResults.add(
                    AgentStepResult(
                        stepIndex = stepCounter,
                        agent = step.agent,
                        summary = "Exceção na etapa",
                        content = e.message ?: "Erro desconhecido",
                        isSuccess = false
                    )
                )
            }
        }

        val successfulResults = intermediateResults.filter { it.isSuccess }
        if (successfulResults.isEmpty()) {
            val errorsSummary = intermediateResults.joinToString("\n") { "- ${it.agent.displayName}: ${it.content}" }
            emit("Não foi possível concluir a colaboração multiagente devido a falhas nos provedores ativados:\n$errorsSummary")
            return@flow
        }

        val synthDecision = smartRouter.selectModelAndProvider(
            task = ModelTask.GENERAL_CHAT,
            targetLevel = AILevel.HIGH,
            routingMode = routingMode,
            preferredProviderId = preferredProviderId,
            manualProviderId = manualProviderId,
            manualModelId = selectedModels[manualProviderId ?: ""],
            enabledProviders = enabledProviderIds,
            selectedModels = selectedModels,
            financialPolicy = financialPolicy,
            currentSpend = currentSpend,
            monthlyBudget = monthlyBudget,
            promptLength = 300
        )

        val synthProvider = if (synthDecision.isSuccess) providers.find { it.id == synthDecision.providerId } else null
        if (synthProvider != null) {
            emit("> 🔀 **Rota (Síntese):** ${synthDecision.providerDisplayName} • `${synthDecision.modelId}`\n\n")
            emit("✨ **[Jarvis Central: Consolidando Síntese Final Integrada...]**\n\n")
            val synthesisPrompt = buildString {
                append("Você é o JARVIS Central (Coordenador). Seus agentes especialistas executaram as tarefas da solicitação do usuário:\n\n")
                successfulResults.forEach { res ->
                    append("### Entrega de ${res.agent.displayName}:\n${res.content}\n\n")
                }
                append("Solicitação original do usuário: \"$prompt\"\n\n")
                append("Instrução de Síntese:\n")
                append("1. Elabore a resposta final unificada, elegante e coesa, incorporando as entregas dos especialistas sem repetições.\n")
                append("2. Verifique se existem eventuais contradições entre os especialistas e resolva-as logicamente.\n")
                append("3. Formate a resposta com excelente Markdown, blocos de código com linguagem indicada quando aplicável e clareza absoluta.")
            }

            try {
                synthProvider.generateStreamResponse(
                    prompt = synthesisPrompt,
                    history = history,
                    level = AILevel.HIGH,
                    modelName = synthDecision.modelId,
                    attachment = null
                ).collect { chunk ->
                    emit(chunk)
                }
            } catch (_: Exception) {
                successfulResults.forEach { res ->
                    emit("### ${res.agent.displayName}\n${res.content}\n\n")
                }
            }
        } else {
            successfulResults.forEach { res ->
                emit("### ${res.agent.displayName}\n${res.content}\n\n")
            }
        }
    }

    private fun createExecutionPlan(
        prompt: String,
        analysis: RequestAnalysis,
        mode: MultiAgentMode
    ): AgentPlan {
        val steps = mutableListOf<AgentTaskStep>()

        if (mode == MultiAgentMode.TWO_AGENTS || (mode == MultiAgentMode.AUTOMATIC && analysis.recommendedAgentCount == 2)) {
            val firstAgent = analysis.primaryAgent
            val secondAgent = analysis.secondaryAgent ?: when (firstAgent) {
                AgentRole.PROGRAMMER -> AgentRole.ANALYST
                AgentRole.RESEARCHER -> AgentRole.PROGRAMMER
                AgentRole.CREATIVE -> AgentRole.ANALYST
                AgentRole.ANALYST -> AgentRole.PROGRAMMER
                else -> AgentRole.RESEARCHER
            }

            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = firstAgent,
                    assignedTask = analysis.task,
                    specificGoal = "Investigação preliminar e fundamentação estruturada.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Você é o ${firstAgent.displayName}. Analise minuciosamente a solicitação: \"$prompt\". Forneça sua análise técnica, dados essenciais e base estrutural para a próxima etapa."
                )
            )

            val secondTask = when (secondAgent) {
                AgentRole.PROGRAMMER -> ModelTask.CODING
                AgentRole.ANALYST -> ModelTask.COMPLEX_REASONING
                AgentRole.CREATIVE -> ModelTask.CREATIVE_WRITING
                else -> ModelTask.RESEARCH
            }
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = secondAgent,
                    assignedTask = secondTask,
                    specificGoal = "Execução, desenvolvimento e consolidação da solução.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Você é o ${secondAgent.displayName}. Com base na solicitação original (\"$prompt\") e nas descobertas preliminares da etapa anterior, desenvolva a solução final detalhada, implementando os requisitos com máxima precisão."
                )
            )

            return AgentPlan(
                isMultiAgentNeeded = true,
                executionGoal = "Colaboração entre dois especialistas com síntese central.",
                participatingAgents = listOf(firstAgent, secondAgent, AgentRole.COORDINATOR),
                steps = steps,
                explanation = "Modo de dois especialistas ativado."
            )
        }

        val singleAgent = analysis.primaryAgent
        steps.add(
            AgentTaskStep(
                stepIndex = 1,
                agent = singleAgent,
                assignedTask = analysis.task,
                specificGoal = analysis.explanation,
                targetLevel = analysis.complexity,
                promptInstruction = prompt
            )
        )

        return AgentPlan(
            isMultiAgentNeeded = false,
            executionGoal = analysis.explanation,
            participatingAgents = listOf(singleAgent),
            steps = steps,
            explanation = analysis.explanation
        )
    }
}
