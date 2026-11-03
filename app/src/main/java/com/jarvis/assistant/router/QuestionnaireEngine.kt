package com.jarvis.assistant.router

import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment

object QuestionnaireEngine {

    fun generateOptimalAssignments(
        providers: List<AIProvider>,
        userPreferences: Map<ModelTask, AILevel> = emptyMap()
    ): List<TaskAssignment> {
        return ModelTask.values().map { task ->
            val preferredLevel = userPreferences[task] ?: task.defaultLevel
            val assignment = pickOptimalModelForTask(task, preferredLevel, providers)
            assignment
        }
    }

    private fun pickOptimalModelForTask(
        task: ModelTask,
        level: AILevel,
        providers: List<AIProvider>
    ): TaskAssignment {
        val availableProviders = providers.filter { it.type.name != "MOCK" }

        return when (task) {
            ModelTask.GENERAL_CHAT -> {
                val gemini = availableProviders.find { it.id == "gemini" }
                val openai = availableProviders.find { it.id == "openai" }
                val claude = availableProviders.find { it.id == "claude" }
                when {
                    gemini != null -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = level,
                        recommendationReason = "Equilíbrio ideal entre velocidade, naturalidade de diálogo e fluência em português."
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
                        modelName = "claude-3-5-haiku",
                        level = level,
                        recommendationReason = "Respostas claras e estruturadas com baixa latência."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = level,
                        recommendationReason = "Padrão recomendado para conversas diárias (configure a chave em Configurações)."
                    )
                }
            }
            ModelTask.QUICK_QUESTIONS -> {
                TaskAssignment(
                    task = task,
                    providerId = "gemini",
                    modelName = "gemini-1.5-flash",
                    level = AILevel.LIGHT,
                    recommendationReason = "Baixíssima latência para consultas instantâneas e comandos diretos."
                )
            }
            ModelTask.CODING -> {
                val claude = availableProviders.find { it.id == "claude" }
                val openai = availableProviders.find { it.id == "openai" }
                when {
                    claude != null -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet",
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
                        modelName = "gemini-1.5-pro",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente de Programação: capacidade analítica avançada para arquitetura de software."
                    )
                }
            }
            ModelTask.COMPLEX_REASONING -> {
                val openai = availableProviders.find { it.id == "openai" }
                val gemini = availableProviders.find { it.id == "gemini" }
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
                        modelName = "gemini-1.5-pro",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente Analítico: raciocínio multifacetado com contextualização profunda."
                    )
                    else -> TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet",
                        level = AILevel.HIGH,
                        recommendationReason = "Agente Analítico: profundidade analítica para problemas complexos e deduções lógicas."
                    )
                }
            }
            ModelTask.RESEARCH -> {
                TaskAssignment(
                    task = task,
                    providerId = "gemini",
                    modelName = "gemini-2.0-flash",
                    level = AILevel.MEDIUM,
                    recommendationReason = "Agente de Pesquisa: janela de contexto ampla para síntese e investigação de tópicos densos."
                )
            }
            ModelTask.CREATIVE_WRITING -> {
                val claude = availableProviders.find { it.id == "claude" }
                if (claude != null) {
                    TaskAssignment(
                        task = task,
                        providerId = "claude",
                        modelName = "claude-3-5-sonnet",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente Criativo: refinamento estilístico superior, vocabulário rico e prosa expressiva."
                    )
                } else {
                    TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-2.0-flash",
                        level = AILevel.MEDIUM,
                        recommendationReason = "Agente Criativo: geração dinâmica de textos, roteiros e redação publicitária."
                    )
                }
            }
            ModelTask.IMAGE_ANALYSIS -> {
                TaskAssignment(
                    task = task,
                    providerId = "gemini",
                    modelName = "gemini-2.0-flash",
                    level = AILevel.HIGH,
                    recommendationReason = "Processamento multimodal nativo com alta sensibilidade para identificação visual e OCR."
                )
            }
            ModelTask.DOCUMENT_ANALYSIS -> {
                val gemini = availableProviders.find { it.id == "gemini" }
                if (gemini != null) {
                    TaskAssignment(
                        task = task,
                        providerId = "gemini",
                        modelName = "gemini-1.5-pro",
                        level = AILevel.HIGH,
                        recommendationReason = "Excelente compreensão de documentos extensos, relatórios e tabelas."
                    )
                } else {
                    TaskAssignment(
                        task = task,
                        providerId = "openai",
                        modelName = "gpt-4o",
                        level = AILevel.HIGH,
                        recommendationReason = "Capacidade consistente de leitura e extração estruturada de dados."
                    )
                }
            }
        }
    }
}
