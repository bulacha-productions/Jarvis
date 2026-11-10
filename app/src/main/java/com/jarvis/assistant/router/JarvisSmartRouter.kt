package com.jarvis.assistant.router

import com.jarvis.assistant.agent.AgentRole
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.financial.FinancialManager
import java.util.concurrent.ConcurrentHashMap

data class RequestAnalysis(
    val task: ModelTask,
    val complexity: AILevel,
    val primaryAgent: AgentRole,
    val secondaryAgent: AgentRole?,
    val recommendedAgentCount: Int,
    val isSimpleQuery: Boolean,
    val requiresVision: Boolean,
    val requiresLongContext: Boolean,
    val explanation: String
)

class JarvisSmartRouter(
    val providers: List<AIProvider>
) {
    // Catálogo dinâmico de modelos por provedor (preserva modelos descobertos)
    private val modelCatalogCache = ConcurrentHashMap<String, List<AIModelInfo>>()

    /**
     * Atualiza o catálogo de modelos consultados via API.
     */
    fun updateCatalog(providerId: String, models: List<AIModelInfo>) {
        if (models.isNotEmpty()) {
            modelCatalogCache[providerId] = models
        }
    }

    fun getKnownModels(providerId: String): List<AIModelInfo> {
        return modelCatalogCache[providerId] ?: emptyList()
    }

    /**
     * Análise inteligente da solicitação sem acionamento de modelos caros.
     */
    fun analyzeRequest(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?
    ): RequestAnalysis {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

        // 1. Verificação de anexos
        val hasImageAttachment = attachment?.mimeType?.startsWith("image/") == true ||
                attachment?.name?.endsWith(".jpg", ignoreCase = true) == true ||
                attachment?.name?.endsWith(".png", ignoreCase = true) == true ||
                attachment?.name?.endsWith(".webp", ignoreCase = true) == true

        val hasDocAttachment = attachment?.mimeType?.contains("pdf") == true ||
                attachment?.mimeType?.contains("document") == true ||
                attachment?.name?.endsWith(".pdf", ignoreCase = true) == true ||
                attachment?.name?.endsWith(".docx", ignoreCase = true) == true

        if (hasImageAttachment) {
            return RequestAnalysis(
                task = ModelTask.IMAGE_ANALYSIS,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = false,
                requiresVision = true,
                requiresLongContext = false,
                explanation = "Análise visual de imagem anexada."
            )
        }

        if (hasDocAttachment) {
            return RequestAnalysis(
                task = ModelTask.DOCUMENT_ANALYSIS,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = AgentRole.ANALYST,
                recommendedAgentCount = 2,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = true,
                explanation = "Análise aprofundada de documento estruturado."
            )
        }

        // 2. Indicadores semânticos de Programação
        val codingKeywords = listOf(
            "fun ", "def ", "class ", "import ", "const ", "let ", "val ", "var ",
            "interface ", "public static", "select ", "from ", "where ", "código", "codigo",
            "algoritmo", "debug", "refatore", "compile", "script", "api", "endpoint",
            "json", "sql", "react", "compose", "kotlin", "python", "typescript", "bug"
        )
        val hasCodingIntent = codingKeywords.any { lower.contains(it) }

        // 3. Indicadores de Raciocínio Complexo e Lógica
        val reasoningKeywords = listOf(
            "calcule", "resolva", "prove que", "deduza", "passo a passo", "paradoxo",
            "teorema", "lógica", "raciocínio", "raciocinio", "matemática", "probabilidade"
        )
        val hasReasoningIntent = reasoningKeywords.any { lower.contains(it) }

        // 4. Indicadores de Pesquisa e Comparação
        val researchKeywords = listOf(
            "pesquise", "compare", "diferença entre", "vantagens e desvantagens",
            "história de", "artigo científico", "estudo de mercado", "investigue"
        )
        val hasResearchIntent = researchKeywords.any { lower.contains(it) }

        // 5. Indicadores de Escrita Criativa
        val creativeKeywords = listOf(
            "crie uma história", "poema", "redija", "escreva um conto", "roteiro",
            "copywriting", "artigo criativo", "slogan", "ideias para"
        )
        val hasCreativeIntent = creativeKeywords.any { lower.contains(it) }

        // 6. Consultas Simples e Rápidas
        val isShort = trimmed.length < 40
        val isDirectQuestion = lower.startsWith("o que é") || lower.startsWith("quem foi") ||
                lower.startsWith("quanto é") || lower.startsWith("qual a capital") ||
                lower.startsWith("como se diz")

        if (isShort && isDirectQuestion && !hasCodingIntent && !hasReasoningIntent) {
            return RequestAnalysis(
                task = ModelTask.QUICK_QUESTIONS,
                complexity = AILevel.LIGHT,
                primaryAgent = AgentRole.COORDINATOR,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = true,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Consulta direta com baixa complexidade."
            )
        }

        // Decomposição de tarefas compostas (Dois especialistas)
        if (hasCodingIntent && hasResearchIntent) {
            return RequestAnalysis(
                task = ModelTask.CODING,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = AgentRole.PROGRAMMER,
                recommendedAgentCount = 2,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = true,
                explanation = "Pesquisa arquitetural + desenvolvimento de código."
            )
        }

        if (hasCodingIntent && hasReasoningIntent) {
            return RequestAnalysis(
                task = ModelTask.CODING,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.PROGRAMMER,
                secondaryAgent = AgentRole.ANALYST,
                recommendedAgentCount = 2,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Desenvolvimento de código + auditoria lógica."
            )
        }

        if (hasResearchIntent && hasReasoningIntent) {
            return RequestAnalysis(
                task = ModelTask.RESEARCH,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = AgentRole.ANALYST,
                recommendedAgentCount = 2,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = true,
                explanation = "Pesquisa analítica + validação comparativa."
            )
        }

        if (hasResearchIntent && hasCreativeIntent) {
            return RequestAnalysis(
                task = ModelTask.CREATIVE_WRITING,
                complexity = AILevel.MEDIUM,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = AgentRole.CREATIVE,
                recommendedAgentCount = 2,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Pesquisa de fatos + composição criativa."
            )
        }

        if (hasCodingIntent) {
            return RequestAnalysis(
                task = ModelTask.CODING,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.PROGRAMMER,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Geração ou depuração especializada de código."
            )
        }

        if (hasReasoningIntent) {
            return RequestAnalysis(
                task = ModelTask.COMPLEX_REASONING,
                complexity = AILevel.HIGH,
                primaryAgent = AgentRole.ANALYST,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Raciocínio dedutivo e resolução matemática."
            )
        }

        if (hasCreativeIntent) {
            return RequestAnalysis(
                task = ModelTask.CREATIVE_WRITING,
                complexity = AILevel.MEDIUM,
                primaryAgent = AgentRole.CREATIVE,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = false,
                explanation = "Criação de conteúdo e ideação narrativa."
            )
        }

        if (hasResearchIntent) {
            return RequestAnalysis(
                task = ModelTask.RESEARCH,
                complexity = AILevel.MEDIUM,
                primaryAgent = AgentRole.RESEARCHER,
                secondaryAgent = null,
                recommendedAgentCount = 1,
                isSimpleQuery = false,
                requiresVision = false,
                requiresLongContext = true,
                explanation = "Pesquisa e investigação aprofundada."
            )
        }

        // Padrão Geral
        return RequestAnalysis(
            task = ModelTask.GENERAL_CHAT,
            complexity = AILevel.MEDIUM,
            primaryAgent = AgentRole.COORDINATOR,
            secondaryAgent = null,
            recommendedAgentCount = 1,
            isSimpleQuery = false,
            requiresVision = false,
            requiresLongContext = false,
            explanation = "Conversação contínua padrão com o Jarvis Central."
        )
    }

    /**
     * Seleção inteligente de modelo respeitando o modo de roteamento e política financeira.
     */
    suspend fun selectModelAndProvider(
        task: ModelTask,
        targetLevel: AILevel,
        routingMode: RoutingMode,
        preferredProviderId: String?,
        manualProviderId: String?,
        manualModelId: String?,
        enabledProviders: Set<String>,
        selectedModels: Map<String, String>,
        financialPolicy: FinancialPolicy,
        currentSpend: Double,
        monthlyBudget: Double,
        promptLength: Int = 100
    ): SmartRoutingDecision {
        val activeProviders = providers.filter { it.id in enabledProviders && it.isAvailable() }

        // MODO 1: MANUAL (Usuário escolheu o provedor e modelo explicitamente)
        if (routingMode == RoutingMode.MANUAL && !manualProviderId.isNullOrBlank()) {
            val provider = activeProviders.find { it.id == manualProviderId }
            if (provider != null) {
                val modelId = manualModelId?.ifBlank { selectedModels[provider.id] } ?: ""
                val resolvedModelId = modelId.ifBlank { getDefaultModelForProvider(provider.id) }
                return SmartRoutingDecision(
                    providerId = provider.id,
                    modelId = resolvedModelId,
                    agentName = task.title,
                    reason = "Seleção manual definida pelo usuário.",
                    isAutomatic = false,
                    wasFallback = false,
                    estimatedCostUsd = 0.0,
                    isFree = resolvedModelId.endsWith(":free") || provider.id == "gemini"
                )
            }
        }

        // MODO 2: AUTOMÁTICO COM PREFERÊNCIA
        if (routingMode == RoutingMode.AUTOMATIC_WITH_PREFERENCE && !preferredProviderId.isNullOrBlank()) {
            val preferred = activeProviders.find { it.id == preferredProviderId }
            if (preferred != null) {
                val modelId = selectedModels[preferred.id]?.ifBlank { getDefaultModelForProvider(preferred.id) }
                    ?: getDefaultModelForProvider(preferred.id)
                val modelInfo = AIModelInfo(
                    id = modelId,
                    displayName = modelId,
                    providerId = preferred.id,
                    defaultLevel = targetLevel,
                    capabilities = setOf(task.requiredCapability),
                    isFree = modelId.endsWith(":free") || preferred.id == "gemini"
                )
                if (FinancialManager.isModelAllowed(modelInfo, financialPolicy, currentSpend, monthlyBudget)) {
                    return SmartRoutingDecision(
                        providerId = preferred.id,
                        modelId = modelId,
                        agentName = task.title,
                        reason = "Provedor preferencial do usuário selecionado.",
                        isAutomatic = true,
                        wasFallback = false,
                        estimatedCostUsd = FinancialManager.estimateCostUsd(modelInfo, promptLength),
                        isFree = modelInfo.isFree
                    )
                }
            }
        }

        // MODO 3: AUTOMÁTICO (Smart Router escolhe o melhor modelo elegível)
        val candidateModels = mutableListOf<AIModelInfo>()
        for (provider in activeProviders) {
            val models = getKnownModels(provider.id).ifEmpty {
                try {
                    provider.getAvailableModels(targetLevel)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            candidateModels.addAll(models)
        }

        // Filtrar modelos autorizados pela política financeira
        val allowedModels = candidateModels.filter { model ->
            FinancialManager.isModelAllowed(model, financialPolicy, currentSpend, monthlyBudget)
        }

        // Se nenhum modelo atende ao filtro estrito de orçamento, buscar modelos conhecidos gratuitos
        val eligibleModels = if (allowedModels.isNotEmpty()) allowedModels else {
            candidateModels.filter { it.isFree || it.id.endsWith(":free") }
        }

        // Encontrar o modelo mais aderente à capacidade necessária
        val bestModel = eligibleModels.find { it.capabilities.contains(task.requiredCapability) && it.defaultLevel == targetLevel }
            ?: eligibleModels.find { it.capabilities.contains(task.requiredCapability) }
            ?: eligibleModels.firstOrNull()

        if (bestModel != null) {
            val provider = activeProviders.find { it.id == bestModel.providerId } ?: activeProviders.firstOrNull()
            val finalProviderId = provider?.id ?: bestModel.providerId
            return SmartRoutingDecision(
                providerId = finalProviderId,
                modelId = bestModel.id,
                agentName = task.title,
                reason = "Smart Router: selecionado para ${task.title} com nível ${targetLevel.name} e conformidade financeira.",
                isAutomatic = true,
                wasFallback = false,
                estimatedCostUsd = FinancialManager.estimateCostUsd(bestModel, promptLength),
                isFree = bestModel.isFree || bestModel.id.endsWith(":free")
            )
        }

        // Fallback seguro caso não haja lista remota carregada
        val fallbackProvider = activeProviders.firstOrNull()
        val fallbackModel = if (fallbackProvider != null) {
            selectedModels[fallbackProvider.id]?.ifBlank { getDefaultModelForProvider(fallbackProvider.id) }
                ?: getDefaultModelForProvider(fallbackProvider.id)
        } else "meta-llama/llama-3.3-70b-instruct:free"

        return SmartRoutingDecision(
            providerId = fallbackProvider?.id ?: "openrouter",
            modelId = fallbackModel,
            agentName = task.title,
            reason = "Fallback seguro para provedor ativo com menor custo.",
            isAutomatic = true,
            wasFallback = true,
            estimatedCostUsd = 0.0,
            isFree = fallbackModel.endsWith(":free")
        )
    }

    private fun getDefaultModelForProvider(providerId: String): String {
        val known = getKnownModels(providerId).firstOrNull()?.id
        if (!known.isNullOrBlank()) return known

        return when (providerId) {
            "gemini" -> "gemini-2.0-flash"
            "openai" -> "gpt-4o"
            "claude" -> "claude-3-5-sonnet-20241022"
            "openrouter" -> "meta-llama/llama-3.3-70b-instruct:free"
            else -> "default"
        }
    }
}
