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

class OpenAIProvider(
    private val apiKeyProvider: () -> String,
    private val baseUrlProvider: () -> String = { "https://api.openai.com/v1" }
) : AIProvider {
    override val id: String = "openai"
    override val type: ProviderType = ProviderType.OPENAI
    override val displayName: String = "OpenAI"

    private val client = OkHttpClient()

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): Result<List<String>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("A chave OpenAI não pode estar vazia."))
        val base = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        try {
            val request = Request.Builder()
                .url("$base/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ${response.code}: Autenticação OpenAI falhou."))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i).optString("id", "")
                    if (m.startsWith("gpt") || m.startsWith("o1") || m.startsWith("chatgpt")) {
                        list.add(m)
                    }
                }
                list.sort()
                Result.success(if (list.isNotEmpty()) list else listOf("gpt-4o", "gpt-4o-mini", "o1"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Falha na conexão OpenAI: ${e.message}"))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<String> = when (level) {
        AILevel.LIGHT -> listOf("gpt-4o-mini", "gpt-3.5-turbo")
        AILevel.MEDIUM -> listOf("gpt-4o", "gpt-4o-mini")
        AILevel.HIGH -> listOf("o1", "gpt-4o")
    }

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String
    ): Flow<String> = flow {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            emit("Chave de API OpenAI não configurada nas Configurações.")
            return@flow
        }
        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "gpt-4o-mini"
            AILevel.MEDIUM -> "gpt-4o"
            AILevel.HIGH -> "o1"
        }
        val base = baseUrlProvider().trimEnd('/')

        try {
            val messagesArr = JSONArray()
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", "Você é o JARVIS, assistente de inteligência artificial com interface 8-bit voxel.")
            })
            history.takeLast(8).forEach { msg ->
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
                put("messages", messagesArr)
            }

            val request = Request.Builder()
                .url("$base/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Erro OpenAI (${response.code}): ${response.message}")
                    return@use
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val reply = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content") ?: "Sem resposta retornada."
                emit(reply)
            }
        } catch (e: Exception) {
            emit("Erro OpenAI: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
