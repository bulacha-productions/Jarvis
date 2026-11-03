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

class OpenAIProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String,
    private val baseUrlProvider: () -> String = { "https://api.openai.com/v1" }
) : AIProvider {

    override val id: String = "openai"
    override val type: ProviderType = ProviderType.OPENAI
    override val displayName: String = "OpenAI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val defaultModels = listOf(
        AIModelInfo(
            id = "gpt-4o",
            displayName = "GPT-4o (Omni)",
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
            description = "Modelo de alto desempenho e multimodalidade para visão, raciocínio e código."
        ),
        AIModelInfo(
            id = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            providerId = id,
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.IMAGE_ANALYSIS
            ),
            description = "Ágil, leve e econômico para tarefas rotineiras e comandos rápidos."
        ),
        AIModelInfo(
            id = "o3-mini",
            displayName = "o3-mini (Reasoning)",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING
            ),
            description = "Especializado em raciocínio lógico dedutivo, matemática e algoritmos."
        )
    )

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API da OpenAI não pode estar vazia."
            )
        }

        val base = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        val url = "$base/models"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $trimmedKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                when (code) {
                    200 -> {
                        val models = parseModels(body)
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = 200,
                            message = "Conexão com OpenAI estabelecida com sucesso! (${models.size} modelos identificados)",
                            discoveredModels = if (models.isNotEmpty()) models else defaultModels
                        )
                    }
                    401 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 401,
                        message = "Erro 401: Chave de API da OpenAI inválida ou não autorizada. Verifique se a chave foi digitada corretamente."
                    )
                    403 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 403,
                        message = "Erro 403: Acesso negado pela OpenAI. Verifique permissões do projeto e da organização."
                    )
                    429 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 429,
                        message = "Erro 429: Limite de requisições ou créditos esgotados na OpenAI (Insufficient Quota)."
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code da OpenAI: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Falha de conexão com a OpenAI: ${e.message}"
            )
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
        return defaultModels.filter { it.defaultLevel == level }
    }

    private fun parseModels(jsonStr: String): List<AIModelInfo> {
        return try {
            val json = JSONObject(jsonStr)
            val data = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AIModelInfo>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString("id", "")
                if (id.startsWith("gpt") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                    val isHigh = id.contains("4o") || id.contains("o1") || id.contains("o3") || id.contains("turbo")
                    val isMini = id.contains("mini")
                    list.add(
                        AIModelInfo(
                            id = id,
                            displayName = id,
                            providerId = this.id,
                            defaultLevel = if (isMini) AILevel.LIGHT else if (isHigh) AILevel.HIGH else AILevel.MEDIUM,
                            capabilities = setOf(
                                ModelCapability.TEXT,
                                ModelCapability.CODING,
                                ModelCapability.REASONING,
                                ModelCapability.IMAGE_ANALYSIS
                            ),
                            description = "Modelo OpenAI oficial disponível para sua chave."
                        )
                    )
                }
            }
            list.sortBy { it.id }
            list
        } catch (_: Exception) {
            emptyList()
        }
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
            emit("Chave de API OpenAI não configurada nas Configurações.")
            return@flow
        }

        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "gpt-4o-mini"
            AILevel.MEDIUM -> "gpt-4o"
            AILevel.HIGH -> "gpt-4o"
        }

        val base = baseUrlProvider().trimEnd('/')
        val url = "$base/chat/completions"

        try {
            val messagesArr = JSONArray()
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", "Você é o JARVIS, assistente de inteligência artificial de alta performance.")
            })

            history.takeLast(10).forEach { msg ->
                messagesArr.put(JSONObject().apply {
                    put("role", if (msg.isFromUser) "user" else "assistant")
                    put("content", msg.content)
                })
            }

            // Current message content
            val userContent = if (attachment != null && context != null && attachment.uriString.isNotBlank() && attachment.mimeType.startsWith("image/")) {
                try {
                    val uri = Uri.parse(attachment.uriString)
                    val base64 = context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                    }
                    if (base64 != null) {
                        val contentParts = JSONArray()
                        contentParts.put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        contentParts.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:${attachment.mimeType};base64,$base64")
                            })
                        })
                        contentParts
                    } else {
                        prompt
                    }
                } catch (_: Exception) {
                    prompt
                }
            } else {
                prompt
            }

            messagesArr.put(JSONObject().apply {
                put("role", "user")
                put("content", userContent)
            })

            val payload = JSONObject().apply {
                put("model", targetModel)
                put("messages", messagesArr)
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    when (code) {
                        401 -> emit("Erro 401 na OpenAI: Chave de API inválida ou expirada.")
                        403 -> emit("Erro 403 na OpenAI: Permissão negada para o modelo solicitado.")
                        429 -> emit("Erro 429 na OpenAI: Limite de cota atingido ou créditos insuficientes.")
                        500, 503 -> emit("Erro $code na OpenAI: Servidores temporariamente sobrecarregados.")
                        else -> emit("Erro $code na OpenAI: ${response.message}")
                    }
                    return@use
                }

                val json = JSONObject(body)
                val reply = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")

                if (!reply.isNullOrBlank()) {
                    emit(reply)
                } else {
                    emit("A OpenAI não retornou resposta textual.")
                }
            }
        } catch (e: Exception) {
            emit("Falha na comunicação com a OpenAI: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
