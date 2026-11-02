package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GeminiProvider(private val apiKeyProvider: () -> String) : AIProvider {
    override val id: String = "gemini"
    override val type: ProviderType = ProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient()

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): Result<List<String>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("A chave da API Gemini não pode ser vazia."))
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext Result.failure(Exception("Erro $code: Autenticação Gemini falhou. Verifique a chave."))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val modelsArr = json.optJSONArray("models") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until modelsArr.length()) {
                    val m = modelsArr.getJSONObject(i).optString("name", "").removePrefix("models/")
                    if (m.contains("gemini") && !m.contains("vision")) {
                        list.add(m)
                    }
                }
                if (list.isEmpty()) {
                    list.addAll(listOf("gemini-3.8-flash", "gemini-3.1-flash-lite", "gemini-4-argon"))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Falha na conexão com Google Gemini: ${e.message}"))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<String> = when (level) {
        AILevel.LIGHT -> listOf("gemini-3.1-flash-lite", "gemini-3.8-flash")
        AILevel.MEDIUM -> listOf("gemini-3.8-flash")
        AILevel.HIGH -> listOf("gemini-4-argon", "gemini-3.8-flash")
    }

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String
    ): Flow<String> = flow {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            emit("Chave de API Gemini não configurada nas Configurações.")
            return@flow
        }
        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "gemini-3.1-flash-lite"
            AILevel.MEDIUM -> "gemini-3.8-flash"
            AILevel.HIGH -> "gemini-4-argon"
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$key"
            val contentsArr = JSONArray()
            history.takeLast(8).forEach { msg ->
                contentsArr.put(JSONObject().apply {
                    put("role", if (msg.isFromUser) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                })
            }
            contentsArr.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            })

            val payload = JSONObject().apply {
                put("contents", contentsArr)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Erro Gemini (${response.code}): ${response.message}")
                    return@use
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "Sem resposta retornada pelo modelo."
                emit(text)
            }
        } catch (e: Exception) {
            emit("Erro na requisição Gemini: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
