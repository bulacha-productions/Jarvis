package com.jarvis.assistant.ai

import android.content.Context
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String
) : AIProvider {

    override val id: String = "openrouter"
    override val type: ProviderType = ProviderType.OPENROUTER
    override val displayName: String = "OpenRouter"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_FREE_MODEL = "meta-llama/llama-3.3-70b-instruct:free"

        fun extractErrorMessage(errorBody: String?, httpCode: Int): String {
            if (errorBody.isNullOrBlank()) {
                return when (httpCode) {
                    401 -> "Chave de API OpenRouter inválida ou não autorizada (HTTP 401)."
                    402 -> "Créditos insuficientes na conta OpenRouter (HTTP 402)."
                    403 -> "Acesso proibido pela OpenRouter (HTTP 403)."
                    404 -> "Endpoint ou modelo OpenRouter não encontrado (HTTP 404)."
                    429 -> "Limite de requisições excedido na OpenRouter (HTTP 429)."
                    500, 502, 503 -> "Servidor OpenRouter indisponível no momento (HTTP $httpCode)."
                    else -> "Erro HTTP $httpCode na API OpenRouter."
                }
            }
            return try {
                val json = JSONObject(errorBody)
                if (json.has("error")) {
                    val err = json.optJSONObject("error")
                    if (err != null) {
                        err.optString("message", errorBody)
                    } else {
                        json.optString("error", errorBody)
                    }
                } else if (json.has("message")) {
                    json.optString("message", errorBody)
                } else {
                    errorBody.take(300)
                }
            } catch (e: Exception) {
                errorBody.take(300)
            }
        }
    }

    override suspend fun isAvailable(): Boolean {
        val key = apiKeyProvider()
        return key.isNotBlank()
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Chave de API OpenRouter não configurada."
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/auth/key")
            .header("Authorization", "Bearer $trimmedKey")
            .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
            .header("X-Title", "Jarvis Assistant")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val modelsResult = queryRemoteModels(trimmedKey, baseUrl)
                    val discovered = modelsResult.getOrDefault(emptyList())
                    val keyInfo = try {
                        val json = JSONObject(body)
                        val data = json.optJSONObject("data")
                        val label = data?.optString("label") ?: "Conta Ativa"
                        val limit = data?.optDouble("limit")
                        val usage = data?.optDouble("usage")
                        if (limit != null && usage != null) " ($label - Uso: $usage / Limite: $limit)" else " ($label)"
                    } catch (e: Exception) {
                        ""
                    }
                    ConnectionTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        message = "Conexão OpenRouter estabelecida com sucesso!$keyInfo ${discovered.size} modelos disponíveis.",
                        discoveredModels = discovered
                    )
                } else {
                    val errMsg = extractErrorMessage(body, response.code)
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Falha na autenticação OpenRouter (HTTP ${response.code}): $errMsg"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Erro de rede ao conectar com OpenRouter: ${e.message ?: "Conexão recusada"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("Authorization", "Bearer $trimmedKey")
            .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
            .header("X-Title", "Jarvis Assistant")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body, response.code)
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errMsg"))
                }

                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<AIModelInfo>()

                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val modelId = item.optString("id", "").trim()
                    if (modelId.isBlank()) continue

                    val modelName = item.optString("name", modelId)
                    val description = item.optString("description", "")
                    val pricing = item.optJSONObject("pricing")
                    val isFree = modelId.endsWith(":free") || (pricing != null &&
                            pricing.optDouble("prompt", 1.0) == 0.0 &&
                            pricing.optDouble("completion", 1.0) == 0.0)

                    val capabilities = mutableSetOf(ModelCapability.TEXT_GENERATION)
                    val architecture = item.optJSONObject("architecture")
                    val modality = architecture?.optString("modality", "") ?: ""
                    if (modality.contains("image") || modality.contains("multimodal")) {
                        capabilities.add(ModelCapability.VISION)
                    }
                    if (modelId.contains("code") || modelId.contains("coder") || modelId.contains("deepseek")) {
                        capabilities.add(ModelCapability.CODE_EXECUTION)
                    }
                    if (modelId.contains("70b") || modelId.contains("r1") || modelId.contains("claude") || modelId.contains("gpt-4")) {
                        capabilities.add(ModelCapability.MULTI_AGENT_READY)
                    }

                    val level = when {
                        isFree -> AILevel.BALANCED
                        modelId.contains("70b") || modelId.contains("r1") || modelId.contains("pro") || modelId.contains("sonnet") -> AILevel.POWERFUL
                        modelId.contains("flash") || modelId.contains("mini") || modelId.contains("8b") -> AILevel.FAST
                        else -> AILevel.BALANCED
                    }

                    val display = if (isFree && !modelName.contains("free", ignoreCase = true)) {
                        "$modelName (Free)"
                    } else {
                        modelName
                    }

                    models.add(
                        AIModelInfo(
                            id = modelId,
                            displayName = display,
                            providerId = id,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description.take(200)
                        )
                    )
                }

                val prioritized = models.sortedWith(
                    compareByDescending<AIModelInfo> { it.id == DEFAULT_FREE_MODEL }
                        .thenByDescending { it.id.endsWith(":free") }
                        .thenBy { it.displayName }
                )

                Result.success(prioritized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        val key = apiKeyProvider()
        if (key.isNotBlank()) {
            val remote = queryRemoteModels(key).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val filtered = remote.filter { it.defaultLevel == level }
                return if (filtered.isNotEmpty()) filtered else remote.take(15)
            }
        }
        return listOf(
            AIModelInfo(
                id = DEFAULT_FREE_MODEL,
                displayName = "Llama 3.3 70B Instruct (Free)",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "Meta Llama 3.3 70B via OpenRouter Free Tier"
            ),
            AIModelInfo(
                id = "google/gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash (OpenRouter)",
                providerId = id,
                defaultLevel = AILevel.BALANCED,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.CODE_EXECUTION, ModelCapability.VISION),
                description = "Google Gemini 2.5 Flash via OpenRouter"
            ),
            AIModelInfo(
                id = "deepseek/deepseek-r1:free",
                displayName = "DeepSeek R1 (Free)",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "DeepSeek R1 Reasoning via OpenRouter Free Tier"
            )
        )
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
            emit("Erro: Chave de API OpenRouter não configurada. Configure em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().ifBlank { DEFAULT_FREE_MODEL }

        val messagesArray = JSONArray()
        for (msg in history.takeLast(16)) {
            val role = if (msg.isUser) "user" else "assistant"
            val msgObj = JSONObject()
            msgObj.put("role", role)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }

        val currentUserMsg = JSONObject()
        currentUserMsg.put("role", "user")
        if (attachment != null && (attachment.mimeType.startsWith("image/") || attachment.isImage)) {
            val contentArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", prompt.ifBlank { "Analise a imagem em anexo." })
            contentArray.put(textPart)

            val base64Data = attachment.base64Data
            if (!base64Data.isNullOrBlank()) {
                val imagePart = JSONObject()
                imagePart.put("type", "image_url")
                val imgUrlObj = JSONObject()
                imgUrlObj.put("url", "data:${attachment.mimeType};base64,$base64Data")
                imagePart.put("image_url", imgUrlObj)
                contentArray.put(imagePart)
            }
            currentUserMsg.put("content", contentArray)
        } else {
            currentUserMsg.put("content", prompt)
        }
        messagesArray.put(currentUserMsg)

        val requestBodyJson = JSONObject()
        requestBodyJson.put("model", resolvedModel)
        requestBodyJson.put("messages", messagesArray)
        requestBodyJson.put("stream", true)

        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
            .header("X-Title", "Jarvis Assistant")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val extracted = extractErrorMessage(errBody, response.code)
                    emit("Erro OpenRouter [HTTP ${response.code}]: $extracted")
                    return@flow
                }

                val responseBody = response.body ?: run {
                    emit("Erro: Corpo de resposta OpenRouter vazio.")
                    return@flow
                }

                val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        if (data.isBlank()) continue

                        try {
                            val chunkJson = JSONObject(data)
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val text = delta?.optString("content", "") ?: ""
                                if (text.isNotEmpty()) {
                                    emit(text)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignora frames malformados
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Erro de conexão com OpenRouter: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)
}
