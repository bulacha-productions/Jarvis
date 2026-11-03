package com.jarvis.assistant.agent

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

    /**
     * Limite rígido anti-loop de iterações consecutivas entre agentes
     */
    private val MAX_AGENT_STEPS = 3

    /**
     * Analisa o prompt e determina se a requisição se beneficia de colaboração multiagente
     */
    fun createExecutionPlan(
        prompt: String,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        isMultiAgentEnabled: Boolean
    ): AgentPlan {
        if (!isMultiAgentEnabled) {
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
                explanation = "Modo multiagente desativado ou consulta pontual."
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

        // Complex collaboration conditions
        val isComplex = (isCodingIntent && isResearchIntent) ||
                (isCodingIntent && isAnalyticIntent) ||
                (isResearchIntent && isCreativeIntent) ||
                (isResearchIntent && isAnalyticIntent) ||
                (hasAttachment && (isCodingIntent || isAnalyticIntent)) ||
                (cleanPrompt.length > 220 && (isCodingIntent || isAnalyticIntent || isResearchIntent))

        if (!isComplex) {
            // Single agent direct route
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

        // Multi-agent workflow synthesis
        val steps = mutableListOf<AgentTaskStep>()
        val participants = mutableListOf<AgentRole>()

        if (isResearchIntent && isCodingIntent) {
            // Pipeline: Pesquisador levanta requisitos -> Programador implementa -> Jarvis consolida
            participants.add(AgentRole.RESEARCHER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.RESEARCHER,
                    assignedTask = ModelTask.RESEARCH,
                    specificGoal = "Mapear padrões, especificações técnicas e melhores práticas.",
                    targetLevel = AILevel.MEDIUM,
                    promptInstruction = "Atue como o Agente de Pesquisa do Jarvis. Identifique especificações técnicas, bibliotecas recomendadas e melhores práticas para: $prompt"
                )
            )

            participants.add(AgentRole.PROGRAMMER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = AgentRole.PROGRAMMER,
                    assignedTask = ModelTask.CODING,
                    specificGoal = "Implementar código estruturado, limpo e testável com base no plano.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente de Programação do Jarvis. Gere o código completo, robusto e documentado para atender à solicitação do usuário."
                )
            )
        } else if (isCodingIntent && isAnalyticIntent) {
            // Pipeline: Programador desenvolve -> Analítico valida complexidade matemática
            participants.add(AgentRole.PROGRAMMER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.PROGRAMMER,
                    assignedTask = ModelTask.CODING,
                    specificGoal = "Construir algoritmo e arquitetura de software.",
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
                    specificGoal = "Analisar complexidade assintótica O(n), casos de borda e validação lógica.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente Analítico do Jarvis. Valide o raciocínio matemático, complexidade algorítmica e casos limite da solução proposta."
                )
            )
        } else if (isResearchIntent && isCreativeIntent) {
            // Pipeline: Pesquisa fatos -> Criativo compõe narrativa
            participants.add(AgentRole.RESEARCHER)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.RESEARCHER,
                    assignedTask = ModelTask.RESEARCH,
                    specificGoal = "Coletar referências e contexto factual.",
                    targetLevel = AILevel.MEDIUM,
                    promptInstruction = "Atue como o Agente de Pesquisa do Jarvis. Colete fatos verídicos e pontos-chave para: $prompt"
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
                    promptInstruction = "Atue como o Agente Criativo do Jarvis. Elabore a redação criativa final com base nos fatos mapeados."
                )
            )
        } else {
            // Complex problem: Analítico estrutura o plano -> Jarvis executa e refina
            participants.add(AgentRole.ANALYST)
            steps.add(
                AgentTaskStep(
                    stepIndex = 1,
                    agent = AgentRole.ANALYST,
                    assignedTask = ModelTask.COMPLEX_REASONING,
                    specificGoal = "Decomposição lógica e análise crítica de variáveis.",
                    targetLevel = AILevel.HIGH,
                    promptInstruction = "Atue como o Agente Analítico do Jarvis. Estruture uma análise lógica detalhada com prós, contras e deduções sobre: $prompt"
                )
            )

            participants.add(AgentRole.COORDINATOR)
            steps.add(
                AgentTaskStep(
                    stepIndex = 2,
                    agent = AgentRole.COORDINATOR,
                    assignedTask = ModelTask.GENERAL_CHAT,
                    specificGoal = "Consolidar conclusões acionáveis e plano de execução.",
                    targetLevel = overrideLevel ?: AILevel.MEDIUM,
                    promptInstruction = "Atue como o Jarvis Central. Sintetize as conclusões analíticas em uma resposta final coesa, elegante e direta."
                )
            )
        }

        // Loop prevention safeguard: ensure steps count <= MAX_AGENT_STEPS
        val safeSteps = steps.take(MAX_AGENT_STEPS)

        return AgentPlan(
            isMultiAgentNeeded = true,
            executionGoal = "Colaboração multiagente coordenada.",
            participatingAgents = participants.distinct(),
            steps = safeSteps,
            explanation = "Tarefa multifacetada distribuída entre ${participants.joinToString { it.displayName }}."
        )
    }

    /**
     * Executa a orquestração multiagente completa com streaming de progresso e síntese unificada
     */
    fun orchestrate(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?,
        overrideLevel: AILevel?,
        taskAssignments: Map<String, TaskAssignment>,
        isMultiAgentEnabled: Boolean
    ): Flow<String> = flow {
        val plan = createExecutionPlan(prompt, attachment, overrideLevel, isMultiAgentEnabled)

        // Se for execução direta em agente único
        if (!plan.isMultiAgentNeeded || plan.steps.size <= 1) {
            val step = plan.steps.first()
            val (provider, modelName) = resolveProviderAndModel(step.assignedTask, step.targetLevel, taskAssignments)

            if (provider == null) {
                emit("Nenhum provedor de IA com credencial ativa está configurado. Cadastre sua chave do Google Gemini, OpenAI ou Claude nas Configurações.")
                return@flow
            }

            provider.generateStreamResponse(
                prompt = prompt,
                history = history,
                level = step.targetLevel,
                modelName = modelName,
                attachment = attachment
            ).collect { chunk ->
                emit(chunk)
            }
            return@flow
        }

        // Execução Multiagente Colaborativa (V2.0.0)
        val agentBadgesHeader = plan.participatingAgents.joinToString(separator = " ") { agent ->
            "[${agent.iconEmoji} ${agent.shortBadge}]"
        }

        // Notifica o usuário no stream sobre os agentes ativados
        emit("🤖 **JARVIS V2 — Coordenação Multiagente Ativada**\n")
        emit("Equipe de Agentes: $agentBadgesHeader\n\n")

        val intermediateResults = mutableListOf<AgentStepResult>()
        var previousContext = ""

        var stepCounter = 0
        for (step in plan.steps) {
            stepCounter++
            // Loop prevention check
            if (stepCounter > MAX_AGENT_STEPS) {
                emit("\n⚠️ [Limite de segurança anti-loop atingido: finalizando síntese.]\n")
                break
            }

            emit("⏳ *[Passo $stepCounter/${plan.steps.size}: ${step.agent.displayName} trabalhando...]*\n\n")

            val (provider, modelName) = resolveProviderAndModel(step.assignedTask, step.targetLevel, taskAssignments)
            if (provider == null) {
                emit("⚠️ [Aviso: Provedor para ${step.agent.displayName} não configurado. Prosseguindo com fallback.]\n\n")
                continue
            }

            // Injeta dados intermediários dos passos anteriores sem vazar informações privadas
            val enrichedPrompt = if (previousContext.isNotBlank()) {
                "${step.promptInstruction}\n\n[Contexto Intermediário da Equipe de Agentes]:\n$previousContext"
            } else {
                step.promptInstruction
            }

            val stepOutputBuilder = StringBuilder()
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
                intermediateResults.add(
                    AgentStepResult(
                        stepIndex = stepCounter,
                        agent = step.agent,
                        summary = step.specificGoal,
                        content = resultText,
                        isSuccess = true
                    )
                )

                previousContext = "Descoberta de ${step.agent.shortBadge}:\n" + resultText.take(1200)
            } catch (e: Exception) {
                intermediateResults.add(
                    AgentStepResult(
                        stepIndex = stepCounter,
                        agent = step.agent,
                        summary = "Erro no passo",
                        content = "Falha: ${e.message}",
                        isSuccess = false
                    )
                )
            }
        }

        // Síntese final unificada pelo Jarvis Central
        emit("✨ **Resposta Integrada Final:**\n\n")

        val finalSuccessfulOutput = intermediateResults.lastOrNull { it.isSuccess }?.content
        if (!finalSuccessfulOutput.isNullOrBlank()) {
            // Emite o conteúdo completo consolidado
            emit(finalSuccessfulOutput)
        } else {
            emit("Não foi possível consolidar a resposta multiagente. Verifique a conectividade com seus provedores de IA.")
        }
    }

    private suspend fun resolveProviderAndModel(
        task: ModelTask,
        level: AILevel,
        taskAssignments: Map<String, TaskAssignment>
    ): Pair<AIProvider?, String> {
        val assignment = taskAssignments[task.id]
        val targetProviderId = assignment?.providerId ?: "gemini"
        var targetModelName = assignment?.modelName ?: ""

        var provider = providers.find { it.id == targetProviderId && it.isAvailable() }
        if (provider == null) {
            // Fallback para qualquer provedor disponível com chave ativa
            provider = providers.firstOrNull { it.id != "unconfigured_system" && it.isAvailable() }
            targetModelName = ""
        }

        return Pair(provider, targetModelName)
    }
}
