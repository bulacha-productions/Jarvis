package com.jarvis.assistant

import com.jarvis.assistant.agent.AgentRole
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.repositories.ModelCatalogRepository
import com.jarvis.assistant.domain.model.*
import com.jarvis.assistant.financial.FinancialManager
import com.jarvis.assistant.router.JarvisSmartRouter
import com.jarvis.assistant.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JarvisSmartRouterAndFinanceTest {

    private val smartRouter = JarvisSmartRouter(emptyList())

    @Test
    fun testAnalyzeRequest_codingIntentDetected() {
        val prompt = "Escreva uma função em Kotlin para ordenar uma lista de usuários por data"
        val analysis = smartRouter.analyzeRequest(prompt, emptyList(), null)

        assertEquals(ModelTask.CODING, analysis.task)
        assertEquals(AgentRole.PROGRAMMER, analysis.primaryAgent)
        assertEquals(AILevel.HIGH, analysis.complexity)
        assertFalse(analysis.isSimpleQuery)
    }

    @Test
    fun testAnalyzeRequest_simpleQuickQuestion() {
        val prompt = "O que é polimorfismo?"
        val analysis = smartRouter.analyzeRequest(prompt, emptyList(), null)

        assertEquals(ModelTask.QUICK_QUESTIONS, analysis.task)
        assertEquals(AILevel.LIGHT, analysis.complexity)
        assertTrue(analysis.isSimpleQuery)
    }

    @Test
    fun testAnalyzeRequest_twoSpecialistsForComplexTask() {
        val prompt = "Pesquise as diferenças de desempenho entre Room e DataStore e escreva um código de benchmark com algoritmo de teste"
        val analysis = smartRouter.analyzeRequest(prompt, emptyList(), null)

        assertEquals(2, analysis.recommendedAgentCount)
        assertTrue(analysis.primaryAgent == AgentRole.RESEARCHER || analysis.primaryAgent == AgentRole.PROGRAMMER)
        assertNotNull(analysis.secondaryAgent)
    }

    @Test
    fun testFinancialPolicy_freeOnlyStrictlyBlocksPaidModel() {
        val freeModel = AIModelInfo(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B (Free)",
            providerId = "openrouter",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT),
            isFree = true,
            promptPricePerMillion = 0.0,
            completionPricePerMillion = 0.0
        )

        val paidModel = AIModelInfo(
            id = "openai/gpt-4o",
            displayName = "GPT-4o",
            providerId = "openai",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT),
            isFree = false,
            promptPricePerMillion = 2.50,
            completionPricePerMillion = 10.00
        )

        val isFreeAllowed = FinancialManager.isModelAllowed(freeModel, FinancialPolicy.FREE_ONLY)
        val isPaidAllowed = FinancialManager.isModelAllowed(paidModel, FinancialPolicy.FREE_ONLY)

        assertTrue(isFreeAllowed)
        assertFalse(isPaidAllowed)
    }

    @Test
    fun testFinancialPolicy_budgetValidationEnforcement() {
        val currentSpend = 4.90
        val monthlyBudget = 5.00
        val cheapCost = 0.02
        val expensiveCost = 0.20

        val allowedCheck = FinancialManager.validateBudget(cheapCost, currentSpend, monthlyBudget, FinancialPolicy.FREE_AND_PAID)
        val blockedCheck = FinancialManager.validateBudget(expensiveCost, currentSpend, monthlyBudget, FinancialPolicy.FREE_AND_PAID)

        assertTrue(allowedCheck.isAllowed)
        assertFalse(blockedCheck.isAllowed)
        assertTrue(blockedCheck.reason.contains("Limite de orçamento atingido"))
    }

    @Test
    fun testToolRegistry_detectsAndSanitizesLeakedToolJson() = runBlocking {
        val leakedJson = """
            {"action": "dalle.text2im", "action_input": {"prompt": "A futuristic city with flying cars"}}
        """.trimIndent()

        val toolCall = ToolRegistry.detectToolCall(leakedJson)
        assertNotNull(toolCall)
        assertEquals("dalle.text2im", toolCall?.action)
        assertEquals("A futuristic city with flying cars", toolCall?.prompt)

        // Processamento não deve vazar JSON cru para o usuário
        val sanitizedOutput = ToolRegistry.processOrSanitizeOutput(leakedJson, null)
        assertFalse(sanitizedOutput.contains("{\"action\""))
        assertTrue(sanitizedOutput.contains("Solicitação de Imagem Identificada"))
        assertTrue(sanitizedOutput.contains("Prompt Sugerido"))
    }

    @Test
    fun testOpenAIAndClaudeModelParsingAndHistoryNormalization() {
        val openaiJson = """
        {
          "data": [
            {"id": "gpt-4o"},
            {"id": "gpt-4o-realtime-preview"},
            {"id": "gpt-3.5-turbo-instruct"},
            {"id": "whisper-1"},
            {"id": "o4-mini"}
          ]
        }
        """.trimIndent()
        val openaiModels = OpenAIProvider.parseModelsFromJson(openaiJson).map { it.id }
        assertTrue(openaiModels.contains("gpt-4o"))
        assertTrue(openaiModels.contains("o4-mini"))
        assertFalse(openaiModels.contains("gpt-4o-realtime-preview"))
        assertFalse(openaiModels.contains("gpt-3.5-turbo-instruct"))
        assertFalse(openaiModels.contains("whisper-1"))

        val history = listOf(
            Message("1", "c1", "Saudação inicial do assistente", false, 1L),
            Message("2", "c1", "Pergunta 1", true, 2L),
            Message("3", "c1", "Pergunta 2", true, 3L),
            Message("4", "c1", "Pergunta atual", true, 4L)
        )
        val claudeMessages = ClaudeProvider.buildNormalizedMessages(history, "Pergunta atual")
        assertEquals(1, claudeMessages.length())
        assertEquals("user", claudeMessages.getJSONObject(0).getString("role"))
        val merged = claudeMessages.getJSONObject(0).getString("content")
        assertFalse(merged.contains("Saudação inicial"))
        assertTrue(merged.contains("Pergunta 1"))
        assertTrue(merged.contains("Pergunta atual"))
    }

    @Test
    fun testSmartRouter_manualOverrideAndCatalogPersistenceAndFreeOnlyBlock() = runBlocking {
        val repo = ModelCatalogRepository.inMemoryForTests()
        val geminiModel = AIModelInfo(
            id = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash Lite",
            providerId = "gemini",
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(ModelCapability.TEXT),
            isFree = true,
            promptPricePerMillion = 0.0,
            completionPricePerMillion = 0.0
        )
        val paidOpenAIModel = AIModelInfo(
            id = "gpt-4o",
            displayName = "GPT-4o",
            providerId = "openai",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.CODING),
            isFree = false,
            promptPricePerMillion = 2.5,
            completionPricePerMillion = 10.0
        )
        repo.saveDiscoveredModels("gemini", listOf(geminiModel))
        repo.saveDiscoveredModels("openai", listOf(paidOpenAIModel))

        val providers = listOf(
            TestAIProvider("gemini", com.jarvis.assistant.ai.ProviderType.GEMINI, "Google Gemini", listOf(geminiModel)),
            TestAIProvider("openai", com.jarvis.assistant.ai.ProviderType.OPENAI, "OpenAI", listOf(paidOpenAIModel))
        )
        val router = JarvisSmartRouter(providers, repo)

        // Manual override must respect manualProviderId & exact selected model even if routingMode is AUTOMATIC
        val manualDecision = router.selectModelAndProvider(
            task = ModelTask.CODING,
            targetLevel = AILevel.HIGH,
            routingMode = RoutingMode.AUTOMATIC,
            preferredProviderId = null,
            manualProviderId = "gemini",
            manualModelId = "gemini-3.1-flash-lite",
            enabledProviders = setOf("gemini", "openai"),
            selectedModels = mapOf("gemini" to "gemini-3.1-flash-lite"),
            financialPolicy = FinancialPolicy.FREE_ONLY,
            currentSpend = 0.0,
            monthlyBudget = 5.0
        )
        assertTrue(manualDecision.isSuccess)
        assertEquals("gemini", manualDecision.providerId)
        assertEquals("gemini-3.1-flash-lite", manualDecision.modelId)

        // FREE_ONLY must block paid OpenAI manual selection cleanly with failureReason
        val blockedDecision = router.selectModelAndProvider(
            task = ModelTask.CODING,
            targetLevel = AILevel.HIGH,
            routingMode = RoutingMode.MANUAL,
            preferredProviderId = null,
            manualProviderId = "openai",
            manualModelId = "gpt-4o",
            enabledProviders = setOf("gemini", "openai"),
            selectedModels = mapOf("openai" to "gpt-4o"),
            financialPolicy = FinancialPolicy.FREE_ONLY,
            currentSpend = 0.0,
            monthlyBudget = 5.0
        )
        assertFalse(blockedDecision.isSuccess)
        assertNotNull(blockedDecision.failureReason)
    }

    @Test
    fun testSecureKeyStoreMaskAndFingerprintNeverLeakSecret() {
        val raw = "AIzaSySecretKey1234567890ABCDEF"
        val masked = SecureKeyStoreManager.maskApiKey(raw)
        val fp = SecureKeyStoreManager.computeFingerprint(raw)
        assertEquals("••••CDEF", masked)
        assertFalse(masked.contains("SecretKey"))
        assertTrue(fp.startsWith("SHA256:"))
        assertFalse(fp.contains("SecretKey"))
    }
}
