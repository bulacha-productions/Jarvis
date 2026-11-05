package com.jarvis.assistant

import com.jarvis.assistant.ai.OpenRouterProvider
import org.junit.Assert.assertEquals
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
}
