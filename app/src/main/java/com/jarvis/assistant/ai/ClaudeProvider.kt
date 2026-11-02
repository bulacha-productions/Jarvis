package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ClaudeProvider(private val apiKeyProvider: () -> String) : AIProvider {
    override val id: String = "claude"
    override val type: ProviderType = ProviderType.CLAUDE
    override val displayName: String = "Anthropic Claude"

    private val client = OkHttpClient()

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): Result<List<String>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("A chave Anthropic não pode estar vazia."))
        try {
            // Teste com ping leve em /v1/messages com 1 max_tokens
            val payload = JSONObject().apply {
                put("model", "claude-3-5-haiku-20241022")
                put("max_tokens", 1)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
            }
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@withContext Result.failure(Exception("Erro ${response.code}: Chave de API Claude inválida."))
                }
                Result.success(listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Falha na conexão com Anthropic: ${e.message}"))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<String> = when (level) {
        AILevel.LIGHT -> listOf("claude-3-5-haiku-20241022")
        AILevel.MEDIUM -> listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
        AILevel.HIGH -> listOf("claude-3-5-sonnet-20241022", "claude-3-opus-20240229")
    }

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String
    ): Flow<String> = flow {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            emit("Chave de API Claude não configurada.")
            return@flow
        }
        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "claude-3-5-haiku-20241022"
            AILevel.MEDIUM -> "claude-3-5-sonnet-20241022"
            AILevel.HIGH -> "claude-3-5-sonnet-20241022"
        }

        try {
            val messagesArr = JSONArray()
            history.takeLast(6).forEach { msg ->
                messagesArr.put(JSONObject().apply {
                    put("role", if (msg.isFromUser) "user" else "assistant")
                    put("content", msg.content)
                })
            }
            messagesArr.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })

            val payload = JSONObject().apply {
                put("model", targetModel)
                put("max_tokens", 1024)
                put("system", "Você é o JARVIS, assistente com identidade visual 8-bit voxel.")
                put("messages", messagesArr)
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Erro Claude (${response.code}): ${response.message}")
                    return@use
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val reply = json.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Sem resposta retornada."
                emit(reply)
            }
        } catch (e: Exception) {
            emit("Erro Claude: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
