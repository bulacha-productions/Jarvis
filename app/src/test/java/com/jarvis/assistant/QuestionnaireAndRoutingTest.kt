package com.jarvis.assistant

import com.jarvis.assistant.ai.AIProvider
import com.jarvis.assistant.ai.ConnectionTestResult
import com.jarvis.assistant.ai.ProviderType
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.router.QuestionnaireEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAIProvider(
    override val id: String,
    override val type: ProviderType,
    override val displayName: String,
    private val models: List<AIModelInfo>
) : AIProvider {
    override suspend fun isAvailable(): Boolean = true
    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult =
        ConnectionTestResult(true, 200, "OK")
    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> =
        Result.success(models)
    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> = models
    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String,
        attachment: Attachment?
    ): Flow<String> = emptyFlow()
}

class QuestionnaireAndRoutingTest {

    private fun createSampleProviders(): List<AIProvider> {
        val geminiModel = AIModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            providerId = "gemini",
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING)
        )
        val openaiModel = AIModelInfo(
            id = "gpt-4o",
            displayName = "GPT-4o",
            providerId = "openai",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING, ModelCapability.REASONING)
        )
        val claudeModel = AIModelInfo(
            id = "claude-3-5-sonnet-20241022",
            displayName = "Claude 3.5 Sonnet",
            providerId = "claude",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING)
        )

        return listOf(
            TestAIProvider("gemini", ProviderType.GEMINI, "Google Gemini", listOf(geminiModel)),
            TestAIProvider("openai", ProviderType.OPENAI, "OpenAI", listOf(openaiModel)),
            TestAIProvider("claude", ProviderType.CLAUDE, "Anthropic Claude", listOf(claudeModel))
        )
    }

    @Test
    fun testGenerateAssignments_strictlyExcludesDisabledProviders() {
        val providers = createSampleProviders()
        val enabledIds = setOf("openai")

        val assignments = QuestionnaireEngine.generateOptimalAssignments(providers, enabledIds)

        assignments.forEach { assignment ->
            assertEquals("openai", assignment.providerId)
            assertFalse(assignment.providerId == "gemini")
            assertFalse(assignment.providerId == "claude")
        }
    }

    @Test
    fun testGenerateAssignments_withGeminiDisabled_noGeminiEverAssigned() {
        val providers = createSampleProviders()
        val enabledIds = setOf("openai", "claude")

        val assignments = QuestionnaireEngine.generateOptimalAssignments(providers, enabledIds)

        assignments.forEach { assignment ->
            assertTrue(assignment.providerId == "openai" || assignment.providerId == "claude")
            assertFalse(assignment.providerId == "gemini")
            assertFalse(assignment.modelName.contains("1.5"))
        }
    }
}
