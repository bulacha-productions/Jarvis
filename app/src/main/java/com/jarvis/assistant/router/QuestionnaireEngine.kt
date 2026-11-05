package com.jarvis.assistant.router

import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.domain.model.TaskAssignment

object QuestionnaireEngine {

    fun generateOptimalAssignments(
        providers: List<AIProvider>,
        enabledProviderIds: Set<String>
    ): List<TaskAssignment> {
        val activeProviders = providers.filter { it.id in enabledProviderIds }
        if (activeProviders.isEmpty()) {
            return emptyList()
        }

        val gemini = activeProviders.find { it.id == "gemini" }
        val openai = activeProviders.find { it.id == "openai" }
        val claude = activeProviders.find { it.id == "claude" }
        val openrouter = activeProviders.find { it.id == "openrouter" }

        val fallback = activeProviders.first()
        val fallbackModel = when (fallback.id) {
            "gemini" -> "gemini-2.5-flash"
            "openai" -> "gpt-4o"
            "claude" -> "claude-3-5-sonnet-20241022"
            "openrouter" -> "meta-llama/llama-3.3-70b-instruct:free"
            else -> "default"
        }

        return ModelTask.values().map { task ->
            when (task) {
                ModelTask.GENERAL_CHAT -> {
                    when {
                        gemini != null -> TaskAssignment(
                            task = task,
                            providerId = "gemini",
                            modelName = "gemini-2.5-flash",
                            level = AILevel.MEDIUM,
                            recommendationReason = "Padrão do Jarvis Central: velocidade e resposta natural para conversação geral."
                        )
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.MEDIUM,
                            recommendationReason = "Meta Llama 3.3 70B via OpenRouter para conversação fluida."
                        )
                        openai != null -> TaskAssignment(
                            task = task,
                            providerId = "openai",
                            modelName = "gpt-4o",
                            level = AILevel.MEDIUM,
                            recommendationReason = "GPT-4o para conversação geral e diálogo contínuo."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = claude?.id ?: fallback.id,
                            modelName = if (claude != null) "claude-3-5-sonnet-20241022" else fallbackModel,
                            level = AILevel.MEDIUM,
                            recommendationReason = "Claude para conversação e assistência geral."
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
                            recommendationReason = "Baixa latência para consultas instantâneas e comandos diretos."
                        )
                        openai != null -> TaskAssignment(
                            task = task,
                            providerId = "openai",
                            modelName = "gpt-4o-mini",
                            level = AILevel.LIGHT,
                            recommendationReason = "Respostas curtas e rápidas para dúvidas pontuais."
                        )
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.LIGHT,
                            recommendationReason = "Execução rápida e gratuita via OpenRouter."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = claude?.id ?: fallback.id,
                            modelName = if (claude != null) "claude-3-5-haiku-20241022" else fallbackModel,
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
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.HIGH,
                            recommendationReason = "Agente de Programação: Llama 3.3 70B com forte proficiência técnica."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = gemini?.id ?: fallback.id,
                            modelName = if (gemini != null) "gemini-2.5-flash" else fallbackModel,
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
                            modelName = "gemini-2.5-flash",
                            level = AILevel.HIGH,
                            recommendationReason = "Agente Analítico: raciocínio multifacetado com contextualização profunda."
                        )
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.HIGH,
                            recommendationReason = "Agente Analítico: modelo de 70B parâmetros para dedução lógica."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = claude?.id ?: fallback.id,
                            modelName = if (claude != null) "claude-3-5-sonnet-20241022" else fallbackModel,
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
                            modelName = "gemini-2.5-flash",
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
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.MEDIUM,
                            recommendationReason = "Agente de Pesquisa: busca e organização estruturada de informações."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = openai?.id ?: fallback.id,
                            modelName = if (openai != null) "gpt-4o" else fallbackModel,
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
                        openrouter != null -> TaskAssignment(
                            task = task,
                            providerId = "openrouter",
                            modelName = "meta-llama/llama-3.3-70b-instruct:free",
                            level = AILevel.MEDIUM,
                            recommendationReason = "Agente Criativo: composição dinâmica e expressiva via Llama 3.3."
                        )
                        gemini != null -> TaskAssignment(
                            task = task,
                            providerId = "gemini",
                            modelName = "gemini-2.5-flash",
                            level = AILevel.MEDIUM,
                            recommendationReason = "Agente Criativo: geração dinâmica de textos, roteiros e redação publicitária."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = openai?.id ?: fallback.id,
                            modelName = if (openai != null) "gpt-4o" else fallbackModel,
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
                            modelName = "gemini-2.5-flash",
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
                        claude != null -> TaskAssignment(
                            task = task,
                            providerId = "claude",
                            modelName = "claude-3-5-sonnet-20241022",
                            level = AILevel.HIGH,
                            recommendationReason = "Análise estruturada de conteúdo visual e diagramas."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = fallback.id,
                            modelName = fallbackModel,
                            level = AILevel.HIGH,
                            recommendationReason = "Processamento visual via modelo ativo disponível."
                        )
                    }
                }
                ModelTask.DOCUMENT_ANALYSIS -> {
                    when {
                        gemini != null -> TaskAssignment(
                            task = task,
                            providerId = "gemini",
                            modelName = "gemini-2.5-flash",
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
                        claude != null -> TaskAssignment(
                            task = task,
                            providerId = "claude",
                            modelName = "claude-3-5-sonnet-20241022",
                            level = AILevel.HIGH,
                            recommendationReason = "Leitura e síntese detalhada de textos densos."
                        )
                        else -> TaskAssignment(
                            task = task,
                            providerId = fallback.id,
                            modelName = fallbackModel,
                            level = AILevel.HIGH,
                            recommendationReason = "Análise documental com modelo disponível."
                        )
                    }
                }
            }
        }
    }
}
