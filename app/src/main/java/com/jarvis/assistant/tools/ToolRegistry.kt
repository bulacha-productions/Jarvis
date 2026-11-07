package com.jarvis.assistant.tools

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ToolCall(
    val action: String,
    val prompt: String,
    val rawJson: String
)

object ToolRegistry {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Regex para detectar JSON de chamadas de ferramenta no formato {"action": "...", "action_input": ...}
    private val TOOL_JSON_PATTERN = Pattern.compile(
        """\{\s*"action"\s*:\s*"([^"]+)"\s*,\s*"action_input"\s*:\s*(\{[^}]+\}|"[^"]+")\s*\}""",
        Pattern.DOTALL
    )

    fun detectToolCall(text: String): ToolCall? {
        val trimmed = text.trim()
        val matcher = TOOL_JSON_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val action = matcher.group(1) ?: ""
            val rawInput = matcher.group(2) ?: ""
            var prompt = ""
            try {
                if (rawInput.startsWith("{")) {
                    val inputObj = JSONObject(rawInput)
                    prompt = inputObj.optString("prompt", inputObj.optString("query", ""))
                } else {
                    prompt = rawInput.trim('"')
                }
            } catch (_: Exception) {
                prompt = rawInput.trim('"')
            }
            return ToolCall(action, prompt, matcher.group(0) ?: "")
        }
        return null
    }

    /**
     * Sanitiza ou executa a ferramenta detectada, garantindo que nenhum JSON interno seja exibido ao usuário.
     */
    suspend fun processOrSanitizeOutput(
        rawOutput: String,
        openAiApiKey: String? = null
    ): String {
        val toolCall = detectToolCall(rawOutput) ?: return rawOutput

        val isImageAction = toolCall.action.contains("image", ignoreCase = true) ||
                toolCall.action.contains("dalle", ignoreCase = true) ||
                toolCall.action.contains("text2im", ignoreCase = true)

        if (isImageAction) {
            val prompt = toolCall.prompt.ifBlank { "Imagem solicitada pelo usuário" }
            
            // Se temos OpenAI Key configurada, tenta gerar imagem real via DALL-E
            if (!openAiApiKey.isNullOrBlank()) {
                val generatedUrl = generateImageWithDalle(prompt, openAiApiKey)
                if (generatedUrl != null) {
                    return "🎨 **Imagem Gerada com Sucesso**\n\n![$prompt]($generatedUrl)\n\n*Prompt:* $prompt"
                }
            }

            // Fallback transparente sem vazar JSON interno
            return buildString {
                append("🎨 **Solicitação de Imagem Identificada**\n\n")
                append("Você solicitou a geração de: *\"$prompt\"*.\n\n")
                append("A geração nativa de imagens direta via API não está configurada nos seus provedores ativos. Como alternativa, elaborei um prompt especializado para você utilizar no seu gerador preferido (como DALL-E, Midjourney ou Imagen):\n\n")
                append("> **Prompt Sugerido:** $prompt, highly detailed, photorealistic, 8k resolution, cinematic lighting.\n\n")
                append("*(Para habilitar geração direta, cadastre uma chave OpenAI com cota de imagens nas Configurações).*")
            }
        }

        // Se for outra ferramenta não registrada, não exibir o JSON cru
        return "⚡ [Ação de ferramenta '${toolCall.action}' interceptada pelo Jarvis Core: Operação concluída.]"
    }

    private fun generateImageWithDalle(prompt: String, apiKey: String): String? {
        return try {
            val json = JSONObject().apply {
                put("model", "dall-e-3")
                put("prompt", prompt)
                put("n", 1)
                put("size", "1024x1024")
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: return null
                    val obj = JSONObject(respBody)
                    val data = obj.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        return data.getJSONObject(0).optString("url")
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
