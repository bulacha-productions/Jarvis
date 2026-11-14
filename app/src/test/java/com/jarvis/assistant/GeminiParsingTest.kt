package com.jarvis.assistant

import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiParsingTest {

    @Test
    fun testParseModelsFromJson_filtersNonChatModelsStripsPrefixDeduplicatesAndPrioritizes31() {
        val mockApiResponse = """
        {
          "models": [
            {
              "name": "models/gemini-2.0-flash",
              "displayName": "Gemini 2.0 Flash",
              "supportedGenerationMethods": ["generateContent", "countTokens"]
            },
            {
              "name": "models/gemini-3.1-flash-lite",
              "displayName": "Gemini 3.1 Flash Lite",
              "supportedGenerationMethods": ["generateContent"]
            },
            {
              "name": "models/gemini-3.1-flash-lite",
              "displayName": "Gemini 3.1 Flash Lite Duplicate",
              "supportedGenerationMethods": ["generateContent"]
            },
            {
              "name": "models/imagen-3.0-generate-002",
              "displayName": "Imagen 3",
              "supportedGenerationMethods": ["generateContent"]
            },
            {
              "name": "models/text-embedding-004",
              "displayName": "Embedding Model",
              "supportedGenerationMethods": ["embedContent"]
            },
            {
              "name": "models/aqa",
              "displayName": "Attributed Question Answering",
              "supportedGenerationMethods": ["aqa"]
            }
          ]
        }
        """.trimIndent()

        val parsed = GeminiProvider.parseModelsFromJson(mockApiResponse)
        val ids = parsed.map { it.id }

        assertTrue(ids.contains("gemini-3.1-flash-lite"))
        assertTrue(ids.contains("gemini-2.0-flash"))
        assertFalse(ids.contains("text-embedding-004"))
        assertFalse(ids.contains("aqa"))
        assertFalse(ids.contains("imagen-3.0-generate-002"))
        assertEquals(2, ids.size) // Deduplicated!
        assertEquals("gemini-3.1-flash-lite", ids.first()) // 3.1 prioritized first
        assertTrue(ids.all { !it.startsWith("models/") })
    }

    @Test
    fun testBuildNormalizedContents_skipsLeadingModelDeduplicatesTrailingPromptAndAlternates() {
        val history = listOf(
            Message(id = "1", conversationId = "c1", content = "Boas-vindas!", isFromUser = false, timestamp = 1L),
            Message(id = "2", conversationId = "c1", content = "Olá", isFromUser = true, timestamp = 2L),
            Message(id = "3", conversationId = "c1", content = "Como posso ajudar?", isFromUser = false, timestamp = 3L),
            Message(id = "4", conversationId = "c1", content = "Explique Kotlin", isFromUser = true, timestamp = 4L)
        )

        val contents = GeminiProvider.buildNormalizedContentsForMessages(history, "Explique Kotlin")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("user", contents.getJSONObject(2).getString("role"))
        assertEquals("Explique Kotlin", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
    }
}
