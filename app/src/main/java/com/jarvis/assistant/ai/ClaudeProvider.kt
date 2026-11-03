package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import com.jarvis.assistant.domain.model.ModelCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeProvider(
    private val apiKeyProvider: () -> String
) : AIProvider {

    override val id: String = "claude"
    override val type: ProviderType = ProviderType.CLAUDE
    override val displayName: String = "Anthropic Claude"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val defaultModels = listOf(
        AIModelInfo(
            id = "claude-3-5-sonnet",
            displayName = "Claude 3.5 Sonnet",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.DOCUMENT_ANALYSIS,
                ModelCapability.LONG_CONTEXT
            ),
            description = "Líder de mercado em engenharia de software, código conciso e análise textual profunda."
        ),
        AIModelInfo(
            id = "claude-3-5-haiku",
            displayName = "Claude 3.5 Haiku",
            providerId = id,
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING
            ),
            description = "Respostas ultrarrápidas com excelente precisão sintática."
        ),
        AIModelInfo(
            id = "claude-3-opus",
            displayName = "Claude 3 Opus",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.REASONING,
                ModelCapability.LONG_CONTEXT
            ),
            description = "Modelo denso para redação complexa, filosofia e raciocínio avançado."
        )
    )

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API da Anthropic Claude não pode estar vazia."
            )
        }

        val url = "https://api.anthropic.com/v1/messages"
        val payload = JSONObject().apply {
            put("model", "claude-3-5-haiku-20241022")
            put("max_tokens", 1)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "ping")
            }))
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", trimmedKey)
                .header("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                when (code) {
                    200 -> ConnectionTestResult(
                        isSuccess = true,
                        statusCode = 200,
                        message = "Conexão com Anthropic Claude estabelecida com sucesso!",
                        discoveredModels = defaultModels
                    )
                    401 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 401,
                        message = "Erro 401: Chave de API da Anthropic Claude inválida ou não autorizada."
                    )
                    403 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 403,
                        message = "Erro 403: Acesso negado pela Anthropic Claude."
                    )
                    429 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 429,
                        message = "Erro 429: Limite de taxa ou créditos esgotados na Anthropic Claude."
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code da Anthropic Claude: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Falha de conexão com Anthropic Claude: ${e.message}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val testRes = testConnection(apiKey, baseUrl)
        if (testRes.isSuccess) {
            Result.success(defaultModels)
        } else {
            Result.failure(Exception(testRes.message))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        return defaultModels.filter { it.defaultLevel == level }
    }

    override fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String,
        attachment: Attachment?
    ): Flow<String> = flow {
        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            emit("Chave de API Anthropic Claude não configurada nas Configurações.")
            return@flow
        }

        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "claude-3-5-haiku-20241022"
            AILevel.MEDIUM -> "claude-3-5-sonnet-20241022"
            AILevel.HIGH -> "claude-3-5-sonnet-20241022"
        }

        val url = "https://api.anthropic.com/v1/messages"

        try {
            val messagesArr = JSONArray()
            history.takeLast(10).forEach { msg ->
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
                put("max_tokens", 4096)
                put("messages", messagesArr)
            }

            val request = Request.Builder()
                .url(url)
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    when (code) {
                        401 -> emit("Erro 401 no Claude: Chave de API inválida.")
                        403 -> emit("Erro 403 no Claude: Acesso não autorizado.")
                        429 -> emit("Erro 429 no Claude: Limite de cota excedido.")
                        else -> emit("Erro $code no Claude: ${response.message}")
                    }
                    return@use
                }

                val json = JSONObject(body)
                val contentArr = json.optJSONArray("content")
                val reply = contentArr?.optJSONObject(0)?.optString("text")

                if (!reply.isNullOrBlank()) {
                    emit(reply)
                } else {
                    emit("Claude não retornou texto.")
                }
            }
        } catch (e: Exception) {
            emit("Falha na comunicação com Claude: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
