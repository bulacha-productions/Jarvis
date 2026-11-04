package com.jarvis.assistant.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
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
import java.io.InputStream
import java.util.concurrent.TimeUnit

class ClaudeProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String
) : AIProvider {

    constructor(apiKeyProvider: () -> String) : this(null, apiKeyProvider)

    override val id: String = "claude"
    override val type: ProviderType = ProviderType.CLAUDE
    override val displayName: String = "Anthropic Claude"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val defaultModels = listOf(
        AIModelInfo(
            id = "claude-3-5-sonnet-20241022",
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
            description = "Líder em engenharia de software, raciocínio aprofundado e código limpo."
        ),
        AIModelInfo(
            id = "claude-3-5-haiku-20241022",
            displayName = "Claude 3.5 Haiku",
            providerId = id,
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING
            ),
            description = "Respostas ágeis com baixa latência e alta precisão sintática."
        ),
        AIModelInfo(
            id = "claude-3-opus-20240229",
            displayName = "Claude 3 Opus",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.REASONING,
                ModelCapability.LONG_CONTEXT
            ),
            description = "Modelo denso para análise filosófica, jurídica e raciocínio complexo."
        )
    )

    override suspend fun isAvailable(): Boolean = apiKeyProvider().trim().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API da Anthropic Claude não pode estar vazia."
            )
        }

        // Tenta listar modelos da Anthropic
        val modelsUrl = "https://api.anthropic.com/v1/models"
        try {
            val req = Request.Builder()
                .url(modelsUrl)
                .header("x-api-key", trimmedKey)
                .header("anthropic-version", "2023-06-01")
                .header("User-Agent", "Jarvis-Assistant-Android/2.0.1")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                when (code) {
                    200 -> {
                        val parsed = parseModelsFromJson(body)
                        val finalModels = if (parsed.isNotEmpty()) parsed else defaultModels
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = 200,
                            message = "Conexão com Anthropic Claude estabelecida com sucesso! (${finalModels.size} modelos identificados)",
                            discoveredModels = finalModels
                        )
                    }
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
                    else -> {
                        // Fallback: ping em /messages se /models não for liberado na conta
                        testPingMessages(trimmedKey)
                    }
                }
            }
        } catch (_: Exception) {
            testPingMessages(trimmedKey)
        }
    }

    private fun testPingMessages(trimmedKey: String): ConnectionTestResult {
        val url = "https://api.anthropic.com/v1/messages"
        val payload = JSONObject().apply {
            put("model", "claude-3-5-haiku-20241022")
            put("max_tokens", 1)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "ping")
            }))
        }

        return try {
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
                message = "Falha de conexão com Anthropic Claude: ${e.message ?: "Erro de rede"}"
            )
        }
    }

    private fun parseModelsFromJson(body: String): List<AIModelInfo> {
        return try {
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AIModelInfo>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.startsWith("claude")) {
                    val isHaiku = id.contains("haiku")
                    list.add(
                        AIModelInfo(
                            id = id,
                            displayName = obj.optString("display_name", id),
                            providerId = this.id,
                            defaultLevel = if (isHaiku) AILevel.LIGHT else AILevel.HIGH,
                            capabilities = setOf(
                                ModelCapability.TEXT,
                                ModelCapability.CODING,
                                ModelCapability.REASONING,
                                ModelCapability.IMAGE_ANALYSIS
                            ),
                            description = "Modelo oficial Claude disponível na sua conta."
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val testRes = testConnection(apiKey, baseUrl)
        if (testRes.isSuccess) {
            Result.success(testRes.discoveredModels)
        } else {
            Result.failure(Exception(testRes.message))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        val filtered = defaultModels.filter { it.defaultLevel == level }
        return if (filtered.isNotEmpty()) filtered else defaultModels
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

        val targetModel = if (modelName.isNotBlank()) {
            // Normaliza nomes genéricos para identificadores completos aceitos pela API
            if (modelName == "claude-3-5-sonnet") "claude-3-5-sonnet-20241022"
            else if (modelName == "claude-3-5-haiku") "claude-3-5-haiku-20241022"
            else if (modelName == "claude-3-opus") "claude-3-opus-20240229"
            else modelName
        } else when (level) {
            AILevel.LIGHT -> "claude-3-5-haiku-20241022"
            AILevel.MEDIUM -> "claude-3-5-sonnet-20241022"
            AILevel.HIGH -> "claude-3-5-sonnet-20241022"
        }

        val url = "https://api.anthropic.com/v1/messages"

        try {
            val messagesArr = JSONArray()

            // Regra Claude: strictly alternate between "user" and "assistant"
            // Primeiro item DEVE ser "user"
            var lastRole: String? = null
            val sanitized = history.filter { it.content.isNotBlank() }.takeLast(8)

            for (msg in sanitized) {
                val currentRole = if (msg.isFromUser) "user" else "assistant"
                if (messagesArr.length() == 0 && currentRole != "user") {
                    continue
                }
                if (currentRole == lastRole) {
                    continue
                }

                messagesArr.put(JSONObject().apply {
                    put("role", currentRole)
                    put("content", msg.content.trim())
                })
                lastRole = currentRole
            }

            // Mensagem atual do usuário
            val userContent = if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true && attachment.mimeType?.startsWith("image/") == true) {
                try {
                    val uri = Uri.parse(attachment.uriString)
                    val base64 = context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                    }
                    if (base64 != null) {
                        val parts = JSONArray()
                        parts.put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", attachment.mimeType ?: "image/jpeg")
                                put("data", base64)
                            })
                        })
                        parts.put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt.trim().ifBlank { "Analise esta imagem." })
                        })
                        parts
                    } else {
                        prompt.trim().ifBlank { "Olá" }
                    }
                } catch (_: Exception) {
                    prompt.trim().ifBlank { "Olá" }
                }
            } else {
                prompt.trim().ifBlank { "Olá" }
            }

            // Se o último papel for user, evita colisão
            if (lastRole == "user" && messagesArr.length() > 0) {
                messagesArr.remove(messagesArr.length() - 1)
            }

            messagesArr.put(JSONObject().apply {
                put("role", "user")
                put("content", userContent)
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
                .header("User-Agent", "Jarvis-Assistant-Android/2.0.1")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    when (code) {
                        400 -> emit("Erro 400 no Claude: Parâmetros inválidos ou alternância de mensagens rejeitada.")
                        401 -> emit("Erro 401 no Claude: Chave de API inválida ou expirada nas Configurações.")
                        403 -> emit("Erro 403 no Claude: Acesso não autorizado ao modelo '$targetModel'.")
                        404 -> emit("Erro 404 no Claude: Modelo '$targetModel' não encontrado.")
                        429 -> emit("Erro 429 no Claude: Limite de cota atingido na Anthropic.")
                        529, 500, 503 -> emit("Erro $code no Claude: Servidores da Anthropic sobrecarregados no momento.")
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
                    emit("Claude processou a solicitação mas não retornou texto.")
                }
            }
        } catch (e: Exception) {
            emit("Falha na comunicação com Claude: ${e.message ?: "Erro desconhecido"}")
        }
    }.flowOn(Dispatchers.IO)
}
