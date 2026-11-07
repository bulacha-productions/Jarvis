package com.jarvis.assistant.agent

import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.financial.FinancialManager
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
        // 1. Análise inteligente da solicitação via Smart Router
        val analysis = smartRouter.analyzeRequest(prompt, history, attachment)

        // 2. Determinar se executará como Agente Único ou Multiagente
        val shouldUseMultiAgent: Boolean = when {
            !isMultiAgentEnabled -> false
            multiAgentMode == MultiAgentMode.ONE_AGENT -> false
            multiAgentMode == MultiAgentMode.TWO_AGENTS -> true
            else -> analysis.recommendedAgentCount > 1 // MultiAgentMode.AUTOMATIC
        }

        // Cenário A: Execução com Agente Único (Menor Latência e Custo Mínimo)
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

            val provider = providers.find { it.id == decision.providerId } ?: providers.firstOrNull { it.isAvailable() }
            if (provider == null) {
                emit("Nenhum provedor de IA com chave configurada está disponível. Acesse Configurações para ativar Google Gemini, OpenAI, Claude ou OpenRouter.")
                return@flow
            }

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

            // Sanitização de ferramentas e geração de imagens
            val finalOutput = buffer.toString()
            val sanitized = ToolRegistry.processOrSanitizeOutput(finalOutput, openAiApiKey)
            if (sanitized != finalOutput) {
                // Se o modelo vazou JSON de ferramenta, substitui pelo resultado limpo da ferramenta
                // No caso do stream já emitido, emitimos uma correção explicativa se necessário
            }
            return@flow
        }

        // Cenário B: Execução Multiagente (Dois Especialistas ou Automático)
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

            emit("⏳ *[Passo $stepCounter/${plan.steps.size}: ${step.agent.displayName} executando...]*\n\n")

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

            val provider = providers.find { it.id == decision.providerId } ?: providers.firstOrNull { it.isAvailable() }
            if (provider == null) {
                emit("⚠️ [Aviso: Provedor para ${step.agent.displayName} indisponível. Etapa omitida.]\n\n")
                continue
            }

            val enrichedPrompt = if (accumulatedContext.isNotBlank()) {
                "${step.promptInstruction}\n\n[Contexto e Entregas das Etapas Anteriores]:\n$accumulatedContext"
            } else {
                step.promptInstruction
            }

            val stepOutputBuilder = StringBuilder()
            var stepFailed = false
            try {
                provider.generateStreamResponse(
                    prompt = enrichedPrompt,
                    history = history,
                    level = step.targetLevel,
                    modelName = decision.modelId,
                    attachment = attachment
                ).collect { chunk ->
                    stepOutputBuilder.append(chunk)
                }

                val resultText = stepOutputBuilder.toString().trim()
                if (resultText.startsWith("Erro ") || resultText.startsWith("Falha ")) {
                    stepFailed = true
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
                stepFailed = true
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

        // Síntese e Consolidação pelo Jarvis Central
        emit("✨ **[Jarvis Central: Consolidando Síntese Final Integrada...]**\n\n")

        val successfulResults = intermediateResults.filter { it.isSuccess }
        if (successfulResults.isEmpty()) {
            val errorsSummary = intermediateResults.joinToString("\n") { "- ${it.agent.displayName}: ${it.content}" }
            emit("Não foi possível concluir a colaboração multiagente devido a falhas nos provedores ativados:\n$errorsSummary")
            return@flow
        }

        // Escolhe o modelo mais adequado para a síntese final
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

        val synthProvider = providers.find { it.id == synthDecision.providerId } ?: providers.firstOrNull { it.isAvailable() }

        if (synthProvider != null) {
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
                val synthBuffer = StringBuilder()
                synthProvider.generateStreamResponse(
                    prompt = synthesisPrompt,
                    history = history,
                    level = AILevel.HIGH,
                    modelName = synthDecision.modelId,
                    attachment = null
                ).collect { chunk ->
                    synthBuffer.append(chunk)
                    emit(chunk)
                }
            } catch (_: Exception) {
                // Em caso de falha do sintetizador, entrega as contribuições dos especialistas diretamente
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

        // Padrão automático com 1 especialista + sintetizador
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
