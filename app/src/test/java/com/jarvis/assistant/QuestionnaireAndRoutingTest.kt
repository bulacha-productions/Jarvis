package com.jarvis.assistant

import com.jarvis.assistant.ai.AIProviderConfig
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.ai.ProviderType
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.domain.model.ModelTask
import com.jarvis.assistant.router.QuestionnaireEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireAndRoutingTest {

    private fun createSampleProviders(): List<AIProviderConfig> {
        val geminiModel = AIModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            providerId = "gemini",
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.CODING)
        )
        val openaiModel = AIModelInfo(
            id = "gpt-4o",
            displayName = "GPT-4o",
            providerId = "openai",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.CODING, ModelCapability.REASONING)
        )
        val claudeModel = AIModelInfo(
            id = "claude-3-5-sonnet-20241022",
            displayName = "Claude 3.5 Sonnet",
            providerId = "claude",
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.CODING, ModelCapability.WRITING)
        )

        return listOf(
            AIProviderConfig(
                id = "gemini",
                type = ProviderType.GEMINI,
                displayName = "Google Gemini",
                apiKey = "test-key-gemini",
                isEnabled = true,
                availableModels = listOf(geminiModel),
                status = ProviderStatus.CONNECTED
            ),
            AIProviderConfig(
                id = "openai",
                type = ProviderType.OPENAI,
                displayName = "OpenAI",
                apiKey = "test-key-openai",
                isEnabled = true,
                availableModels = listOf(openaiModel),
                status = ProviderStatus.CONNECTED
            ),
            AIProviderConfig(
                id = "claude",
                type = ProviderType.CLAUDE,
                displayName = "Anthropic Claude",
                apiKey = "test-key-claude",
                isEnabled = true,
                availableModels = listOf(claudeModel),
                status = ProviderStatus.CONNECTED
            )
        )
    }

    @Test
    fun testGenerateAssignments_strictlyExcludesDisabledProviders() {
        val providers = createSampleProviders()
        // Disable Gemini and Claude; only OpenAI enabled
        val enabledIds = setOf("openai")

        val assignments = QuestionnaireEngine.generateAssignments(providers, enabledIds)

        // All assignments must be assigned to OpenAI
        assignments.forEach { (_, assignment) ->
            assertEquals("openai", assignment.providerId)
            assertFalse(assignment.providerId == "gemini")
            assertFalse(assignment.providerId == "claude")
        }
    }

    @Test
    fun testGenerateAssignments_withGeminiDisabled_noGeminiEverAssigned() {
        val providers = createSampleProviders()
        // Gemini disabled, OpenAI and Claude enabled
        val enabledIds = setOf("openai", "claude")

        val assignments = QuestionnaireEngine.generateAssignments(providers, enabledIds)

        assignments.forEach { (_, assignment) ->
            assertTrue(assignment.providerId == "openai" || assignment.providerId == "claude")
            assertFalse(assignment.providerId == "gemini")
            assertFalse(assignment.modelName.contains("1.5"))
        }
    }
}
