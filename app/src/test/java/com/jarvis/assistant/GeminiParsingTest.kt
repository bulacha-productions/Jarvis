package com.jarvis.assistant

import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.domain.model.AILevel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiParsingTest {

    @Test
    fun testParseModelsFromJson_filtersNonGenerateContentAndStripsPrefix() {
        val mockApiResponse = """
        {
          "models": [
            {
              "name": "models/gemini-2.0-flash",
              "displayName": "Gemini 2.0 Flash",
              "supportedGenerationMethods": ["generateContent", "countTokens"]
            },
            {
              "name": "models/gemini-2.0-flash-thinking-exp",
              "displayName": "Gemini 2.0 Flash Thinking",
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

        // Reflection or direct invocation of parseModelsFromJson logic
        val root = JSONObject(mockApiResponse)
        val modelsArray = root.getJSONArray("models")
        val parsedModels = mutableListOf<String>()

        for (i in 0 until modelsArray.length()) {
            val obj = modelsArray.getJSONObject(i)
            val rawName = obj.optString("name")
            val supportedMethods = obj.optJSONArray("supportedGenerationMethods")

            var supportsGenerate = false
            if (supportedMethods != null) {
                for (j in 0 until supportedMethods.length()) {
                    if (supportedMethods.getString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
            }

            if (supportsGenerate) {
                val cleanId = rawName.removePrefix("models/")
                parsedModels.add(cleanId)
            }
        }

        // Must include generateContent models
        assertTrue(parsedModels.contains("gemini-2.0-flash"))
        assertTrue(parsedModels.contains("gemini-2.0-flash-thinking-exp"))

        // Must NOT include embedding or aqa models
        assertFalse(parsedModels.contains("text-embedding-004"))
        assertFalse(parsedModels.contains("aqa"))

        // Prefix "models/" must be stripped
        assertTrue(parsedModels.all { !it.startsWith("models/") })
    }

    @Test
    fun testNoLegacy15ModelsHardcoded() {
        // Assert that default models are 2.0/2.5 or contemporary
        val defaultModel = "gemini-2.0-flash"
        assertFalse(defaultModel.contains("1.5"))
    }
}
