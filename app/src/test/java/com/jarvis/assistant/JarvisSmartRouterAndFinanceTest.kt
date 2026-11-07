package com.jarvis.assistant

import com.jarvis.assistant.agent.AgentRole
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
}
