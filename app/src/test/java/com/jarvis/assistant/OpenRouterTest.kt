package com.jarvis.assistant

import com.jarvis.assistant.ai.OpenRouterProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterTest {

    @Test
    fun testDefaultFreeModelConstant() {
        assertEquals(
            "meta-llama/llama-3.3-70b-instruct:free",
            OpenRouterProvider.DEFAULT_FREE_MODEL
        )
        assertTrue(OpenRouterProvider.DEFAULT_FREE_MODEL.endsWith(":free"))
    }

    @Test
    fun testExtractErrorMessageFromJson() {
        val jsonError = """{"error": {"message": "Invalid API Key", "code": 401}}"""
        val message = OpenRouterProvider.extractErrorMessage(jsonError, 401)
        assertEquals("Invalid API Key", message)
    }

    @Test
    fun testExtractErrorMessageFallbackOnEmpty() {
        val message = OpenRouterProvider.extractErrorMessage("", 402)
        assertTrue(message.contains("Créditos insuficientes"))
    }

    @Test
    fun testParseModelsFromJson_pricingAndOutputModalitiesAndDeduplication() {
        val sampleJson = """
        {
          "data": [
            {
              "id": "meta-llama/llama-3.3-70b-instruct:free",
              "name": "Llama 3.3 70B Instruct",
              "context_length": 131072,
              "architecture": { "output_modalities": ["text"], "input_modalities": ["text"] },
              "pricing": { "prompt": "0", "completion": "0" }
            },
            {
              "id": "meta-llama/llama-3.3-70b-instruct:free",
              "name": "Llama 3.3 Duplicate",
              "context_length": 131072,
              "architecture": { "output_modalities": ["text"] },
              "pricing": { "prompt": "0", "completion": "0" }
            },
            {
              "id": "openrouter/auto",
              "name": "Auto Router",
              "context_length": 200000,
              "architecture": { "output_modalities": ["text"] },
              "pricing": { "prompt": "-1", "completion": "-1" }
            },
            {
              "id": "mystery/unpriced:free",
              "name": "Unpriced Free Suffix",
              "architecture": { "output_modalities": ["text"] }
            },
            {
              "id": "black-forest-labs/flux-pro",
              "name": "Flux Image Only",
              "architecture": { "output_modalities": ["image"] },
              "pricing": { "prompt": "0", "completion": "0" }
            }
          ]
        }
        """.trimIndent()

        val models = OpenRouterProvider.parseModelsFromJson(sampleJson)
        assertEquals(3, models.size) // Image-only excluded, duplicate excluded

        val freeLlama = models.first { it.id == "meta-llama/llama-3.3-70b-instruct:free" }
        assertTrue(freeLlama.isFree)
        assertEquals(0.0, freeLlama.promptPricePerMillion ?: -1.0, 0.0001)

        val autoRouter = models.first { it.id == "openrouter/auto" }
        assertFalse("Dynamic -1 pricing must NOT be treated as free", autoRouter.isFree)
        assertNull(autoRouter.promptPricePerMillion)

        val unpriced = models.first { it.id == "mystery/unpriced:free" }
        assertFalse("Missing pricing must NEVER be treated as free", unpriced.isFree)
        assertNull(unpriced.promptPricePerMillion)
    }
}
