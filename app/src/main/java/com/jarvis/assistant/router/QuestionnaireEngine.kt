package com.jarvis.assistant.router

import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment

object QuestionnaireEngine {

    fun generateOptimalAssignments(
        providers: List<AIProvider>,
        enabledProviderIds: Set<String> = setOf("gemini", "openai", "claude"),
        userPreferences: Map<ModelTask, AILevel> = emptyMap()
    ): List<TaskAssignment> {
        val activeProviders = providers.filter { it.id in enabledProviderIds && it.type.name != "MOCK" }
        return ModelTask.values().map { task ->
            val preferredLevel = userPreferences[task] ?: task.defaultLevel
            pickOptimalModelForTask(task, preferredLevel, activeProviders, enabledProviderIds)
        }
    }

    private fun pickOptimalModelForTask(
        task: ModelTask,
        level: AILevel,
        activeProviders: List<AIProvider>,
        enabledProviderIds: Set<String>
    ): TaskAssignment {
        val gemini = activeProviders.find { it.id == "gemini" && "gemini" in enabledProviderIds }
        val openai = activeProviders.find { it.id == "openai" && "openai" in enabledProviderIds }
        val claude = activeProviders.find { it.id == "claude" && "claude" in enabledProviderIds }

        // Heurística de distribuição respeitando rigorosamente os provedores ATIVADOS
        return when (task) {
            ModelTask.GENERAL_CHAT -> {
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = level,
                        recommendationReason = "Equilíbrio ideal entre velocidade, naturalidade de diálogo e fluência."
                    )
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o-mini",
                        level = level,
                        recommendationReason = "Excelente custo-benefício e respostas rápidas para diálogos rotineiros."
                    )
                    claude != null -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-haiku-20241022",
                        level = level,
                        recommendationReason = "Respostas claras e estruturadas com baixa latência."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = activeProviders.firstOrNull()?.id ?: "gemini",
                        modelName = "gemini-2.0-flash",
                        level = level,
                        recommendationReason = "Padrão recomendado para conversas diárias."
                    )
                }
            }
            ModelTask.QUICK_QUESTIONS -> {
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.LIGHT,
                        recommendationReason = "Baixíssima latência para consultas instantâneas e comandos diretos."
                    )
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o-mini",
                        level = AILevel.LIGHT,
                        recommendationReason = "Respostas curtas e rápidas para dúvidas pontuais."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = claude?.id ?: "gemini",
                        modelName = "claude-3-5-haiku-20241022",
                        level = AILevel.LIGHT,
                        recommendationReason = "Alta velocidade para consultas diretas."
                    )
                }
            }
            ModelTask.CODING -> {
                when {
                    claude != null -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente de Programação: líder em engenharia de software, código limpo e depuração."
                    )
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente de Programação: alta precisão em múltiplas linguagens e algoritmos."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente de Programação: capacidade analítica avançada para arquitetura de software."
                    )
                }
            }
            ModelTask.COMPLEX_REASONING -> {
                when {
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "o3-mini",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente Analítico: arquitetura especializada em encadeamento dedutivo, lógica e matemática."
                    )
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente Analítico: raciocínio multifacetado com contextualização profunda."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente Analítico: profundidade analítica para problemas complexos e deduções lógicas."
                    )
                }
            }
            ModelTask.RESEARCH -> {
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente de Pesquisa: janela de contexto ampla para síntese e investigação de tópicos densos."
                    )
                    claude != null -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente de Pesquisa: síntese abrangente e análise comparativa de fontes."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente de Pesquisa: extração de dados e síntese de informações."
                    )
                }
            }
            ModelTask.CREATIVE_WRITING -> {
                when {
                    claude != null -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente Criativo: refinamento estilístico superior, vocabulário rico e prosa expressiva."
                    )
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente Criativo: geração dinâmica de textos, roteiros e redação publicitária."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente Criativo: composição criativa e fluência expressiva."
                    )
                }
            }
            ModelTask.IMAGE_ANALYSIS -> {
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.HIGH,
                        recommendationReason = "Processamento multimodal nativo com alta sensibilidade para identificação visual e OCR."
                    )
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.HIGH,
                        recommendationReason = "Visão computacional e descrição detalhada de imagens."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.HIGH,
                        recommendationReason = "Análise estruturada de conteúdo visual e diagramas."
                    )
                }
            }
            ModelTask.DOCUMENT_ANALYSIS -> {
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.HIGH,
                        recommendationReason = "Excelente compreensão de documentos extensos, relatórios e tabelas."
                    )
                    openai != null -> TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.HIGH,
                        recommendationReason = "Capacidade consistente de leitura e extração estruturada de dados."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet-20241022",
                        level = AILevel.HIGH,
                        recommendationReason = "Leitura e síntese detalhada de textos densos."
                    )
                }
            }
        }
    }
}
