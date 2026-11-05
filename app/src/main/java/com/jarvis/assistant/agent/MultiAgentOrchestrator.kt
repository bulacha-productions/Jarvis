package com.jarvis.assistant.agent

import com.jarvis.assistant.JarvisVersion
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment
import com.jarvis.assistant.router.CapabilityDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

class MultiAgentOrchestrator(
    private val providers: List<AIProvider>
) {
    private val MAX_AGENT_STEPS = 3

    fun createExecutionPlan(
        prompt: String,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        isMultiAgentEnabled: Boolean,
        manualProviderId: String?
    ): AgentPlan {
        if (!isMultiAgentEnabled || !manualProviderId.isNullOrBlank()) {
            val detectedTask = CapabilityDetector.detectTask(prompt, attachment)
            val role = AgentRole.fromTaskId(detectedTask.id)
            return AgentPlan(
                isMultiAgentNeeded = false,
                executionGoal = "Execução direta em agente único.",
                participatingAgents = listOf(role),
                steps = listOf(
                    AgentTaskStep(
                        stepIndex = 1,
                        agent = role,
                        assignedTask = detectedTask,
                        specificGoal = "Executar resposta direta.",
                        targetLevel = overrideLevel ?: detectedTask.defaultLevel,
                        promptInstruction = prompt
                    )
                ),
                explanation = if (!manualProviderId.isNullOrBlank()) "Seleção manual do provedor '$manualProviderId'." else "Modo multiagente desativado."
            )
        }

        val cleanPrompt = prompt.lowercase(Locale.ROOT)
        val hasAttachment = attachment != null

        val isCodingIntent = cleanPrompt.contains("código") || cleanPrompt.contains("codigo") ||
                cleanPrompt.contains("função") || cleanPrompt.contains("script") ||
                cleanPrompt.contains("kotlin") || cleanPrompt.contains("python") ||
                cleanPrompt.contains("java") || cleanPrompt.contains("program") ||
                cleanPrompt.contains("bug") || cleanPrompt.contains("refatore") ||
                cleanPrompt.contains("algoritmo") || cleanPrompt.contains("classe")

        val isResearchIntent = cleanPrompt.contains("pesquise") || cleanPrompt.contains("pesquisa") ||
                cleanPrompt.contains("busque") || cleanPrompt.contains("compare") ||
                cleanPrompt.contains("história de") || cleanPrompt.contains("quais são os") ||
                cleanPrompt.contains("diferença entre") || cleanPrompt.contains("artigo sobre")

        val isCreativeIntent = cleanPrompt.contains("história") || cleanPrompt.contains("poema") ||
                cleanPrompt.contains("slogan") || cleanPrompt.contains("roteiro") ||
                cleanPrompt.contains("conto") || cleanPrompt.contains("criativo") ||
                cleanPrompt.contains("escreva uma")

        val isAnalyticIntent = cleanPrompt.contains("calcule") || cleanPrompt.contains("probabilidade") ||
                cleanPrompt.contains("estatística") || cleanPrompt.contains("lógica") ||
                cleanPrompt.contains("matemática") || cleanPrompt.contains("resolva o problema") ||
                cleanPrompt.contains("otimize") || cleanPrompt.contains("equação")

        val isComplex = (isCodingIntent && isResearchIntent) ||
                (isCodingIntent && isAnalyticIntent) ||
                (isResearchIntent && isCreativeIntent) ||
                (isResearchIntent && isAnalyticIntent) ||
                (hasAttachment && (isCodingIntent || isAnalyticIntent)) ||
                (cleanPrompt.length > 200 && (isCodingIntent || isAnalyticIntent || isResearchIntent))

        if (!isComplex) {
            val task = CapabilityDetector.detectTask(prompt, attachment)
            val agent = AgentRole.fromTaskId(task.id)
            return AgentPlan(
                isMultiAgentNeeded = false,
                executionGoal = "Execução direta padrão pelo ${agent.displayName}",
                participatingAgents = listOf(agent),
                steps = listOf(
                    AgentTaskStep(
                        stepIndex = 1,
                        agent = agent,
                        assignedTask = task,
                        specificGoal = "Atendimento direto com alta eficiência.",
                        targetLevel = overrideLevel ?: task.defaultLevel,
                        promptInstruction = prompt
                    )
                ),
                explanation = "Solicitação concisa ou de domínio único."
            )
        }

        val steps = mutableListOf<AgentTaskStep>()
        val participants = mutableListOf<AgentRole>()

        if (isResearchIntent && isCodingIntent) {
            participants.add(AgentRole.RESEARCHER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.RESEARCHER,
                    assignedTask = ModelTask.RESEARCH,
                    specificGoal = "Levantar especificações técnicas e melhores práticas.",
                    targetLevel = AILevel.MEDIUM,
                    promptInstruction = "Atue como o Agente de Pesquisa do Jarvis. Identifique bibliotecas e requisitos técnicos para: $prompt"
                )
            )
            participants.add(AgentRole.PROGRAMMER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = AgentRole.PROGRAMMER,
                    assignedTask = ModelTask.CODING,
                    specificGoal = "Implementar o código limpo e arquitetado.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente de Programação do Jarvis. Gere o código completo e estruturado para: $prompt"
                )
            )
        } else if (isCodingIntent && isAnalyticIntent) {
            participants.add(AgentRole.PROGRAMMER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.PROGRAMMER,
                    assignedTask = ModelTask.CODING,
                    specificGoal = "Construir algoritmo e solução técnica.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente de Programação do Jarvis. Construa a implementação de código para: $prompt"
                )
            )
            participants.add(AgentRole.ANALYST)
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = AgentRole.ANALYST,
                    assignedTask = ModelTask.COMPLEX_REASONING,
                    specificGoal = "Validar complexidade algorítmica, casos de borda e integridade matemática.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente Analítico do Jarvis. Valide a consistência lógica e casos limites de: $prompt"
                )
            )
        } else if (isResearchIntent && isCreativeIntent) {
            participants.add(AgentRole.RESEARCHER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.RESEARCHER,
                    assignedTask = ModelTask.RESEARCH,
                    specificGoal = "Coletar referências factuais verídicas.",
                    targetLevel = AILevel.MEDIUM,
                    promptInstruction = "Atue como o Agente de Pesquisa do Jarvis. Mapeie fatos-chave para: $prompt"
                )
            )
            participants.add(AgentRole.CREATIVE)
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = AgentRole.CREATIVE,
                    assignedTask = ModelTask.CREATIVE_WRITING,
                    specificGoal = "Compor texto cativante e estilisticamente refinado.",
                    targetLevel = AILevel.MEDIUM,
                    promptInstruction = "Atue como o Agente Criativo do Jarvis. Elabore a redação com base nos fatos levantados para: $prompt"
                )
            )
        } else {
            participants.add(AgentRole.ANALYST)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.ANALYST,
                    assignedTask = ModelTask.COMPLEX_REASONING,
                    specificGoal = "Análise analítica estruturada de variáveis e deduções.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente Analítico do Jarvis. Estruture uma análise lógica detalhada sobre: $prompt"
                )
            )
        }

        participants.add(AgentRole.COORDINATOR)
        val safeSteps = steps.take(MAX_AGENT_STEPS)

        return AgentPlan(
            isMultiAgentNeeded = true,
            executionGoal = "Colaboração multiagente com síntese final obrigatória pelo Jarvis Central.",
            participatingAgents = participants.distinct(),
            steps = safeSteps,
            explanation = "Tarefa multifacetada distribuída entre especialistas com consolidação central."
        )
    }

    fun orchestrate(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        taskAssignments: Map<String, TaskAssignment>,
        enabledProviderIds: Set<String>,
        isMultiAgentEnabled: Boolean,
        manualProviderId: String? = null,
        selectedModels: Map<String, String> = emptyMap()
    ): Flow<String> = flow {

        if (!manualProviderId.isNullOrBlank()) {
            if (manualProviderId !in enabledProviderIds) {
                emit("O provedor '$manualProviderId' foi selecionado manualmente, mas está desativado nas Configurações. Ative-o para utilizá-lo.")
                return@flow
            }
            val manualProvider = providers.find { it.id == manualProviderId }
            if (manualProvider == null || !manualProvider.isAvailable()) {
                emit("O provedor '$manualProviderId' foi selecionado manualmente, mas sua chave de API não está configurada ou válida. Acesse as Configurações.")
                return@flow
            }
            val activeModel = selectedModels[manualProvider.id] ?: when (manualProvider.id) {
                "gemini" -> "gemini-2.5-flash"
                "openai" -> "gpt-4o"
                "claude" -> "claude-3-5-sonnet-20241022"
                "openrouter" -> "meta-llama/llama-3.3-70b-instruct:free"
                else -> ""
            }
            manualProvider.generateStreamResponse(
                prompt = prompt,
                history = history,
                level = overrideLevel ?: AILevel.MEDIUM,
                modelName = activeModel,
                attachment = attachment
            ).collect { chunk ->
                emit(chunk)
            }
            return@flow
        }

        val plan = createExecutionPlan(prompt, attachment, overrideLevel, isMultiAgentEnabled, manualProviderId)

        if (!plan.isMultiAgentNeeded || plan.steps.isEmpty()) {
            val task = CapabilityDetector.detectTask(prompt, attachment)
            val (provider, modelName) = resolveProviderAndModel(task, overrideLevel ?: task.defaultLevel, taskAssignments, enabledProviderIds, selectedModels)
            if (provider == null) {
                emit("Nenhum provedor de IA ativado com chave configurada está disponível. Acesse as Configurações para ativar o Google Gemini, OpenAI, Claude ou OpenRouter.")
                return@flow
            }
            provider.generateStreamResponse(
                prompt = prompt,
                history = history,
                level = overrideLevel ?: task.defaultLevel,
                modelName = modelName,
                attachment = attachment
            ).collect { chunk ->
                emit(chunk)
            }
            return@flow
        }

        val agentBadgesHeader = plan.participatingAgents.joinToString(separator = " ") { agent ->
            "[${agent.iconEmoji} ${agent.shortBadge}]"
        }

        emit("🤖 **JARVIS v${JarvisVersion.VERSION_NAME} — Coordenação Multiagente**\n")
        emit("Equipe Ativa: $agentBadgesHeader\n\n")

        val intermediateResults = mutableListOf<AgentStepResult>()
        var accumulatedContext = ""
        var stepCounter = 0

        for (step in plan.steps) {
            stepCounter++
            if (stepCounter > MAX_AGENT_STEPS) break
            emit("⏳ *[Passo $stepCounter/${plan.steps.size}: ${step.agent.displayName} executando...]*\n\n")

            val (provider, modelName) = resolveProviderAndModel(step.assignedTask, step.targetLevel, taskAssignments, enabledProviderIds, selectedModels)
            if (provider == null) {
                emit("⚠️ [Aviso: Nenhum provedor ativado para ${step.agent.displayName}. Etapa omitida.]\n\n")
                continue
            }

            val enrichedPrompt = if (accumulatedContext.isNotBlank()) {
                "${step.promptInstruction}\n\n[Contexto e Descobertas das Etapas Anteriores]:\n$accumulatedContext"
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
                    modelName = modelName,
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
                    intermediateResults.add(
                        AgentStepResult(
                            stepIndex = stepCounter,
                            agent = step.agent,
                            summary = step.specificGoal,
                            content = resultText,
                            isSuccess = true
                        )
                    )
                    accumulatedContext += "\n--- [Entrega do ${step.agent.displayName}] ---\n" + resultText.take(1500)
                }
            } catch (e: Exception) {
                stepFailed = true
                intermediateResults.add(
                    AgentStepResult(
                        stepIndex = stepCounter,
                        agent = step.agent,
                        summary = "Exceção",
                        content = e.message ?: "Erro desconhecido",
                        isSuccess = false
                    )
                )
            }
        }

        emit("✨ **[Jarvis Central: Consolidando Síntese Final Integrada...]**\n\n")

        val successfulResults = intermediateResults.filter { it.isSuccess }
        if (successfulResults.isEmpty()) {
            val errorsSummary = intermediateResults.joinToString("\n") { "- ${it.agent.displayName}: ${it.content}" }
            emit("Não foi possível concluir a colaboração multiagente devido a falhas nos provedores ativados:\n$errorsSummary")
            return@flow
        }

        val (synthProvider, synthModel) = resolveProviderAndModel(ModelTask.GENERAL_CHAT, AILevel.HIGH, taskAssignments, enabledProviderIds, selectedModels)
        if (synthProvider != null) {
            val synthesisPrompt = buildString {
                append("Você é o JARVIS Central (Coordenador). Os agentes especialistas da sua equipe executaram as seguintes etapas para a solicitação do usuário:\n\n")
                successfulResults.forEach { res ->
                    append("### ${res.agent.displayName}:\n${res.content}\n\n")
                }
                append("Solicitação original do usuário: \"$prompt\"\n\n")
                append("Instrução: Elabore a resposta final unificada, elegante e coesa, incorporando as entregas dos especialistas sem repetições desnecessárias.")
            }

            try {
                synthProvider.generateStreamResponse(
                    prompt = synthesisPrompt,
                    history = history,
                    level = AILevel.HIGH,
                    modelName = synthModel,
                    attachment = null
                ).collect { chunk ->
                    emit(chunk)
                }
            } catch (_: Exception) {
                emit(successfulResults.last().content)
            }
        } else {
            emit(successfulResults.last().content)
        }
    }

    private suspend fun resolveProviderAndModel(
        task: ModelTask,
        level: AILevel,
        taskAssignments: Map<String, TaskAssignment>,
        enabledProviderIds: Set<String>,
        selectedModels: Map<String, String> = emptyMap()
    ): Pair<AIProvider?, String> {
        val assignment = taskAssignments[task.id]
        val targetProviderId = assignment?.providerId ?: "gemini"
        var targetModelName = assignment?.modelName ?: ""

        var provider: AIProvider? = null
        if (targetProviderId in enabledProviderIds) {
            val candidate = providers.find { it.id == targetProviderId }
            if (candidate != null && candidate.isAvailable()) {
                provider = candidate
            }
        }

        if (provider == null) {
            provider = providers.firstOrNull { it.id in enabledProviderIds && it.id != "unconfigured_system" && it.isAvailable() }
            targetModelName = ""
        }

        if (provider != null) {
            val userSelected = selectedModels[provider.id]
            if (!userSelected.isNullOrBlank()) {
                targetModelName = userSelected
            } else if (targetModelName.isBlank()) {
                targetModelName = when (provider.id) {
                    "gemini" -> "gemini-2.5-flash"
                    "openai" -> "gpt-4o"
                    "claude" -> "claude-3-5-sonnet-20241022"
                    "openrouter" -> "meta-llama/llama-3.3-70b-instruct:free"
                    else -> ""
                }
            }
        }

        return Pair(provider, targetModelName)
    }
}
