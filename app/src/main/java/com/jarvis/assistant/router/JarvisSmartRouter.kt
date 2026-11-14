package com.jarvis.assistant.router

import com.jarvis.assistant.agent.AgentRole
import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.data.local.preferences.CredentialLookupResult
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.financial.FinancialManager
import com.jarvis.assistant.repositories.ModelCatalogRepository
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
    val providers: List<AIProvider>,
    private val catalogRepository: ModelCatalogRepository? = null,
    private val keyStore: SecureKeyStoreManager? = null
) {
    private val modelCatalogCache = ConcurrentHashMap<String, List<AIModelInfo>>()

    fun updateCatalog(providerId: String, models: List<AIModelInfo>) {
        if (models.isNotEmpty()) {
            modelCatalogCache[providerId] = models
            catalogRepository?.saveDiscoveredModels(providerId, models)
        }
    }

    fun getKnownModels(providerId: String): List<AIModelInfo> {
        val persisted = catalogRepository?.getModelsForProvider(providerId)?.filter { it.isEnabled }
        if (!persisted.isNullOrEmpty()) return persisted
        return modelCatalogCache[providerId]?.filter { it.isEnabled } ?: emptyList()
    }

    fun analyzeRequest(
        prompt: String,
        history: List<Message>,
        attachment: Attachment?
    ): RequestAnalysis {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

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

        val codingKeywords = listOf(
            "fun ", "def ", "class ", "import ", "const ", "let ", "val ", "var ",
            "interface ", "public static", "select ", "from ", "where ", "código", "codigo",
            "algoritmo", "debug", "refatore", "compile", "script", "api", "endpoint",
            "json", "sql", "react", "compose", "kotlin", "python", "typescript", "bug"
        )
        val hasCodingIntent = codingKeywords.any { lower.contains(it) }

        val reasoningKeywords = listOf(
            "calcule", "resolva", "prove que", "deduza", "passo a passo", "paradoxo",
            "teorema", "lógica", "raciocínio", "raciocinio", "matemática", "probabilidade"
        )
        val hasReasoningIntent = reasoningKeywords.any { lower.contains(it) }

        val researchKeywords = listOf(
            "pesquise", "compare", "diferença entre", "vantagens e desvantagens",
            "história de", "artigo científico", "estudo de mercado", "investigue"
        )
        val hasResearchIntent = researchKeywords.any { lower.contains(it) }

        val creativeKeywords = listOf(
            "crie uma história", "poema", "redija", "escreva um conto", "roteiro",
            "copywriting", "artigo criativo", "slogan", "ideias para"
        )
        val hasCreativeIntent = creativeKeywords.any { lower.contains(it) }

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

    private suspend fun inspectProviderReadiness(provider: AIProvider): Pair<Boolean, String?> {
        if (keyStore != null) {
            return when (val cred = keyStore.getCredential(provider.id)) {
                is CredentialLookupResult.Success -> true to null
                is CredentialLookupResult.Missing -> false to "Chave de API para ${provider.displayName} não configurada."
                is CredentialLookupResult.DecryptionFailure -> false to "Falha de descriptografia no Keystore para ${provider.displayName}: ${cred.reason}"
            }
        }
        return if (provider.isAvailable()) {
            true to null
        } else {
            false to "Chave de API para ${provider.displayName} não configurada."
        }
    }

    private suspend fun resolveModelsForProvider(provider: AIProvider, targetLevel: AILevel): List<AIModelInfo> {
        val known = getKnownModels(provider.id)
        if (known.isNotEmpty()) return known
        val remote = try {
            provider.getAvailableModels(targetLevel)
        } catch (_: Exception) {
            emptyList()
        }
        if (remote.isNotEmpty()) {
            updateCatalog(provider.id, remote)
        }
        return remote
    }

    private fun buildModelInfoForSelection(
        providerId: String,
        modelId: String,
        targetLevel: AILevel,
        task: ModelTask
    ): AIModelInfo {
        val existing = getKnownModels(providerId).find { it.id == modelId }
        if (existing != null) return existing
        return AIModelInfo(
            id = modelId,
            displayName = modelId,
            providerId = providerId,
            defaultLevel = targetLevel,
            capabilities = setOf(task.requiredCapability),
            isFree = providerId == "gemini"
        )
    }

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
        val readinessErrors = mutableListOf<String>()
        val activeProviders = mutableListOf<AIProvider>()
        for (provider in providers) {
            if (provider.id !in enabledProviders) continue
            val (ready, err) = inspectProviderReadiness(provider)
            if (ready) {
                activeProviders.add(provider)
            } else if (!err.isNullOrBlank()) {
                readinessErrors.add(err)
            }
        }

        // MODO 1: MANUAL (Respeita explicitamente manualProviderId mesmo se routingMode vier inconsistente)
        if ((routingMode == RoutingMode.MANUAL || !manualProviderId.isNullOrBlank()) && !manualProviderId.isNullOrBlank()) {
            val provider = providers.find { it.id == manualProviderId }
                ?: return SmartRoutingDecision(
                    providerId = "",
                    modelId = "",
                    agentName = task.title,
                    reason = "Provedor manual não localizado.",
                    isAutomatic = false,
                    isSuccess = false,
                    failureReason = "Provedor manual '$manualProviderId' não está registrado."
                )

            if (provider.id !in enabledProviders) {
                return SmartRoutingDecision(
                    providerId = "",
                    modelId = "",
                    agentName = task.title,
                    reason = "Provedor manual desativado.",
                    isAutomatic = false,
                    isSuccess = false,
                    providerDisplayName = provider.displayName,
                    failureReason = "O provedor selecionado manualmente (${provider.displayName}) está desativado nas Configurações."
                )
            }

            val (ready, err) = inspectProviderReadiness(provider)
            if (!ready) {
                return SmartRoutingDecision(
                    providerId = "",
                    modelId = "",
                    agentName = task.title,
                    reason = err ?: "Credencial indisponível.",
                    isAutomatic = false,
                    isSuccess = false,
                    providerDisplayName = provider.displayName,
                    failureReason = err ?: "Credencial indisponível para ${provider.displayName}."
                )
            }

            val catalogModels = resolveModelsForProvider(provider, targetLevel)
            val resolvedModelId = manualModelId?.trim()?.ifBlank { null }
                ?: selectedModels[provider.id]?.trim()?.ifBlank { null }
                ?: catalogModels.firstOrNull { it.defaultLevel == targetLevel }?.id
                ?: catalogModels.firstOrNull()?.id

            if (resolvedModelId.isNullOrBlank()) {
                return SmartRoutingDecision(
                    providerId = "",
                    modelId = "",
                    agentName = task.title,
                    reason = "Sem modelo selecionado.",
                    isAutomatic = false,
                    isSuccess = false,
                    providerDisplayName = provider.displayName,
                    failureReason = "Nenhum modelo selecionado ou descoberto para ${provider.displayName}. Acesse Configurações e toque em 'Testar Conexão' para listar os modelos reais."
                )
            }

            val modelInfo = buildModelInfoForSelection(provider.id, resolvedModelId, targetLevel, task)
            val estCost = FinancialManager.estimateCostUsd(modelInfo, promptLength)
            if (!FinancialManager.isModelAllowed(modelInfo, financialPolicy, currentSpend, monthlyBudget, estCost)) {
                val budgetReason = FinancialManager.validateBudget(estCost, currentSpend, monthlyBudget, financialPolicy).reason
                return SmartRoutingDecision(
                    providerId = "",
                    modelId = "",
                    agentName = task.title,
                    reason = "Bloqueado pela política financeira.",
                    isAutomatic = false,
                    isSuccess = false,
                    providerDisplayName = provider.displayName,
                    failureReason = "Modelo '$resolvedModelId' (${provider.displayName}) bloqueado: $budgetReason"
                )
            }

            return SmartRoutingDecision(
                providerId = provider.id,
                modelId = resolvedModelId,
                agentName = task.title,
                reason = "Seleção manual definida pelo usuário.",
                isAutomatic = false,
                wasFallback = false,
                estimatedCostUsd = estCost,
                isFree = modelInfo.isFree,
                isSuccess = true,
                providerDisplayName = provider.displayName
            )
        }

        if (activeProviders.isEmpty()) {
            val detail = readinessErrors.firstOrNull()
                ?: "Nenhum provedor de IA com chave válida está ativado nas Configurações."
            return SmartRoutingDecision(
                providerId = "",
                modelId = "",
                agentName = task.title,
                reason = "Sem provedores disponíveis.",
                isAutomatic = true,
                isSuccess = false,
                failureReason = detail
            )
        }

        // MODO 2: AUTOMÁTICO COM PREFERÊNCIA
        if (routingMode == RoutingMode.AUTOMATIC_WITH_PREFERENCE && !preferredProviderId.isNullOrBlank()) {
            val preferred = activeProviders.find { it.id == preferredProviderId }
            if (preferred != null) {
                val catalogModels = resolveModelsForProvider(preferred, targetLevel)
                val preferredModelId = selectedModels[preferred.id]?.trim()?.ifBlank { null }
                    ?: catalogModels.firstOrNull {
                        FinancialManager.isModelAllowed(it, financialPolicy, currentSpend, monthlyBudget)
                    }?.id
                if (!preferredModelId.isNullOrBlank()) {
                    val modelInfo = buildModelInfoForSelection(preferred.id, preferredModelId, targetLevel, task)
                    val estCost = FinancialManager.estimateCostUsd(modelInfo, promptLength)
                    if (FinancialManager.isModelAllowed(modelInfo, financialPolicy, currentSpend, monthlyBudget, estCost)) {
                        return SmartRoutingDecision(
                            providerId = preferred.id,
                            modelId = preferredModelId,
                            agentName = task.title,
                            reason = "Provedor preferencial do usuário selecionado.",
                            isAutomatic = true,
                            wasFallback = false,
                            estimatedCostUsd = estCost,
                            isFree = modelInfo.isFree,
                            isSuccess = true,
                            providerDisplayName = preferred.displayName
                        )
                    }
                }
            }
        }

        // MODO 3: AUTOMÁTICO (Smart Router escolhe entre modelos reais e selecionados)
        val candidateModels = mutableListOf<AIModelInfo>()
        for (provider in activeProviders) {
            val models = resolveModelsForProvider(provider, targetLevel)
            if (models.isNotEmpty()) {
                candidateModels.addAll(models)
            } else {
                val selected = selectedModels[provider.id]?.trim()
                if (!selected.isNullOrBlank()) {
                    candidateModels.add(buildModelInfoForSelection(provider.id, selected, targetLevel, task))
                }
            }
        }

        val allowedModels = candidateModels.filter { model ->
            val estCost = FinancialManager.estimateCostUsd(model, promptLength)
            FinancialManager.isModelAllowed(model, financialPolicy, currentSpend, monthlyBudget, estCost)
        }

        val bestModel = allowedModels.find { it.capabilities.contains(task.requiredCapability) && it.defaultLevel == targetLevel }
            ?: allowedModels.find { it.capabilities.contains(task.requiredCapability) }
            ?: allowedModels.firstOrNull()

        if (bestModel != null) {
            val provider = activeProviders.find { it.id == bestModel.providerId } ?: activeProviders.first()
            val estCost = FinancialManager.estimateCostUsd(bestModel, promptLength)
            return SmartRoutingDecision(
                providerId = provider.id,
                modelId = bestModel.id,
                agentName = task.title,
                reason = "Smart Router: selecionado para ${task.title} (${targetLevel.name}) conforme política financeira.",
                isAutomatic = true,
                wasFallback = false,
                estimatedCostUsd = estCost,
                isFree = bestModel.isFree,
                isSuccess = true,
                providerDisplayName = provider.displayName
            )
        }

        // Fallback apenas para modelos selecionados pelo usuário em provedores ativos que atendam a política financeira
        for (provider in activeProviders) {
            val userSelectedModel = selectedModels[provider.id]?.trim()
            if (!userSelectedModel.isNullOrBlank()) {
                val info = buildModelInfoForSelection(provider.id, userSelectedModel, targetLevel, task)
                val estCost = FinancialManager.estimateCostUsd(info, promptLength)
                if (FinancialManager.isModelAllowed(info, financialPolicy, currentSpend, monthlyBudget, estCost)) {
                    return SmartRoutingDecision(
                        providerId = provider.id,
                        modelId = userSelectedModel,
                        agentName = task.title,
                        reason = "Modelo selecionado pelo usuário para ${provider.displayName}.",
                        isAutomatic = true,
                        wasFallback = true,
                        estimatedCostUsd = estCost,
                        isFree = info.isFree,
                        isSuccess = true,
                        providerDisplayName = provider.displayName
                    )
                }
            }
        }

        val reasonMsg = if (candidateModels.isNotEmpty()) {
            "Nenhum modelo elegível sob a política financeira '${financialPolicy.displayName}'. Ajuste a política ou teste um provedor com modelos gratuitos."
        } else {
            "Catálogo de modelos vazio nos provedores ativos. Acesse Configurações > Provedores de IA e toque em 'Testar Conexão' para carregar os modelos reais."
        }
        return SmartRoutingDecision(
            providerId = "",
            modelId = "",
            agentName = task.title,
            reason = "Nenhum modelo elegível encontrado.",
            isAutomatic = true,
            wasFallback = false,
            isSuccess = false,
            failureReason = reasonMsg
        )
    }
}
