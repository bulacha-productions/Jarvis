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

class GeminiProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String
) : AIProvider {

    override val id: String = "gemini"
    override val type: ProviderType = ProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val defaultModels = listOf(
        AIModelInfo(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            providerId = id,
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.DOCUMENT_ANALYSIS,
                ModelCapability.LONG_CONTEXT,
                ModelCapability.TOOLS
            ),
            description = "Modelo ágil e inteligente de última geração para tarefas do dia a dia e multimodalidade."
        ),
        AIModelInfo(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.DOCUMENT_ANALYSIS,
                ModelCapability.LONG_CONTEXT,
                ModelCapability.TOOLS
            ),
            description = "Máxima capacidade analítica, raciocínio aprofundado e resolução de códigos complexos."
        ),
        AIModelInfo(
            id = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            providerId = id,
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.IMAGE_ANALYSIS
            ),
            description = "Baixíssima latência para consultas rápidas e comandos instantâneos."
        )
    )

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API do Google Gemini não pode estar vazia."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                when (code) {
                    200 -> {
                        val models = parseModels(body)
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = 200,
                            message = "Conexão com Google Gemini realizada com sucesso! (${models.size} modelos identificados)",
                            discoveredModels = if (models.isNotEmpty()) models else defaultModels
                        )
                    }
                    400 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 400,
                        message = "Erro 400: Chave de API Gemini malformada ou parâmetros inválidos."
                    )
                    401, 403 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Autenticação Gemini rejeitada. Verifique se a chave é válida e possui permissão."
                    )
                    429 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 429,
                        message = "Erro 429: Limite de requisições excedido no Google Gemini (Quota Exceeded)."
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Falha no servidor do Google Gemini (${response.message})."
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Falha de conexão com a API do Google Gemini: ${e.message}"
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
            val modelsArr = json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<AIModelInfo>()
            for (i in 0 until modelsArr.length()) {
                val obj = modelsArr.getJSONObject(i)
                val rawName = obj.optString("name", "").removePrefix("models/")
                if (rawName.contains("gemini") && !rawName.contains("vision")) {
                    val isPro = rawName.contains("pro")
                    val isFlash = rawName.contains("flash")
                    list.add(
                        AIModelInfo(
                            id = rawName,
                            displayName = obj.optString("displayName", rawName),
                            providerId = id,
                            defaultLevel = if (isPro) AILevel.HIGH else if (isFlash) AILevel.MEDIUM else AILevel.LIGHT,
                            capabilities = setOf(
                                ModelCapability.TEXT,
                                ModelCapability.CODING,
                                ModelCapability.REASONING,
                                ModelCapability.IMAGE_ANALYSIS,
                                ModelCapability.DOCUMENT_ANALYSIS,
                                ModelCapability.LONG_CONTEXT
                            ),
                            description = obj.optString("description", "")
                        )
                    )
                }
            }
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
            emit("Chave de API do Google Gemini não configurada nas Configurações.")
            return@flow
        }

        val targetModel = if (modelName.isNotBlank()) modelName else when (level) {
            AILevel.LIGHT -> "gemini-1.5-flash"
            AILevel.MEDIUM -> "gemini-2.5-flash"
            AILevel.HIGH -> "gemini-2.5-pro"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$key"
        try {
            val contentsArr = JSONArray()

            // Context: previous messages
            history.takeLast(10).forEach { msg ->
                val parts = JSONArray().put(JSONObject().put("text", msg.content))
                contentsArr.put(JSONObject().apply {
                    put("role", if (msg.isFromUser) "user" else "model")
                    put("parts", parts)
                })
            }

            // Current message
            val currentParts = JSONArray()
            currentParts.put(JSONObject().put("text", prompt))

            // Inline attachment if image
            if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true) {
                try {
                    val uri = Uri.parse(attachment.uriString)
                    context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        val bytes = stream.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                        currentParts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", mime)
                                put("data", base64)
                            })
                        })
                    }
                } catch (_: Exception) {}
            }

            contentsArr.put(JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            })

            val payload = JSONObject().apply {
                put("contents", contentsArr)
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    when (code) {
                        400 -> emit("Erro 400 no Gemini: Requisição inválida.")
                        401, 403 -> emit("Erro $code no Gemini: Autenticação falhou. Verifique sua chave de API nas Configurações.")
                        429 -> emit("Erro 429 no Gemini: Limite de cota excedido. Aguarde alguns instantes.")
                        500, 503 -> emit("Erro $code no Gemini: Servidor temporariamente indisponível.")
                        else -> emit("Erro $code no Gemini: ${response.message}")
                    }
                    return@use
                }

                val json = JSONObject(body)
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    emit(text)
                } else {
                    emit("O modelo Gemini não retornou nenhum texto.")
                }
            }
        } catch (e: Exception) {
            emit("Falha na comunicação com o Google Gemini: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
