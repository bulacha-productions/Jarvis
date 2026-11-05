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

class OpenAIProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String,
    private val baseUrlProvider: suspend () -> String = { "" }
) : AIProvider {

    override val id: String = "openai"
    override val type: ProviderType = ProviderType.OPENAI
    override val displayName: String = "OpenAI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o"

        fun extractErrorMessage(errorBody: String?, httpCode: Int): String {
            if (errorBody.isNullOrBlank()) {
                return when (httpCode) {
                    401 -> "Chave OpenAI inválida ou não autorizada (HTTP 401)."
                    403 -> "Acesso negado pela OpenAI (HTTP 403). Verifique sua organização/projeto."
                    404 -> "Modelo OpenAI não encontrado (HTTP 404)."
                    429 -> "Limite de cota/taxa excedido na OpenAI (HTTP 429)."
                    500, 503 -> "Servidor OpenAI indisponível no momento (HTTP $httpCode)."
                    else -> "Erro HTTP $httpCode na API OpenAI."
                }
            }
            return try {
                val json = JSONObject(errorBody)
                val err = json.optJSONObject("error")
                if (err != null) {
                    err.optString("message", errorBody)
                } else {
                    errorBody.take(300)
                }
            } catch (e: Exception) {
                errorBody.take(300)
            }
        }
    }

    private suspend fun resolveBaseUrl(): String {
        val custom = baseUrlProvider().trim()
        return if (custom.isNotBlank()) custom.removeSuffix("/") else DEFAULT_BASE_URL
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
                message = "Chave de API OpenAI não configurada."
            )
        }

        val url = if (baseUrl.isNotBlank()) baseUrl.trim().removeSuffix("/") else DEFAULT_BASE_URL
        val request = Request.Builder()
            .url("$url/models")
            .header("Authorization", "Bearer $trimmedKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val models = parseModels(body)
                    ConnectionTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        message = "Conexão com OpenAI estabelecida com sucesso! ${models.size} modelos compatíveis localizados.",
                        discoveredModels = models
                    )
                } else {
                    val errorMsg = extractErrorMessage(body, response.code)
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Falha ao validar chave OpenAI (HTTP ${response.code}): $errorMsg"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Erro de conexão ao acessar a API OpenAI: ${e.message ?: "Conexão recusada"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val url = if (baseUrl.isNotBlank()) baseUrl.trim().removeSuffix("/") else DEFAULT_BASE_URL
        val request = Request.Builder()
            .url("$url/models")
            .header("Authorization", "Bearer $trimmedKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = extractErrorMessage(body, response.code)
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorMsg"))
                }
                Result.success(parseModels(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModels(body: String): List<AIModelInfo> {
        val models = mutableListOf<AIModelInfo>()
        try {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isBlank()) continue

                if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                    val capabilities = mutableSetOf(ModelCapability.TEXT_GENERATION)
                    if (id.contains("4o") || id.contains("vision")) {
                        capabilities.add(ModelCapability.VISION)
                    }
                    if (id.contains("4") || id.contains("o1") || id.contains("o3")) {
                        capabilities.add(ModelCapability.CODE_EXECUTION)
                        capabilities.add(ModelCapability.MULTI_AGENT_READY)
                    }

                    val level = when {
                        id.contains("o1") || id.contains("o3") || id.contains("4o-") && !id.contains("mini") -> AILevel.POWERFUL
                        id.contains("mini") || id.contains("3.5") -> AILevel.FAST
                        else -> AILevel.BALANCED
                    }

                    models.add(
                        AIModelInfo(
                            id = id,
                            displayName = id,
                            providerId = this.id,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = "Modelo oficial OpenAI ($id)"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignora falhas de parse
        }
        return models.sortedWith(
            compareByDescending<AIModelInfo> { it.id == DEFAULT_MODEL }
                .thenByDescending { it.id.startsWith("gpt-4o") }
                .thenBy { it.displayName }
        )
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
                id = "gpt-4o",
                displayName = "GPT-4o (Omni)",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "OpenAI modelo flagship multimodal"
            ),
            AIModelInfo(
                id = "gpt-4o-mini",
                displayName = "GPT-4o Mini",
                providerId = id,
                defaultLevel = AILevel.FAST,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION),
                description = "OpenAI modelo rápido e eficiente"
            ),
            AIModelInfo(
                id = "o3-mini",
                displayName = "o3-mini",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "OpenAI modelo avançado de raciocínio lógico"
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
            emit("Erro: Chave de API OpenAI não configurada. Configure sua chave em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().ifBlank { DEFAULT_MODEL }
        val url = resolveBaseUrl()

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
        if (attachment != null && !attachment.base64Data.isNullOrBlank() && (attachment.isImage || attachment.mimeType.startsWith("image/"))) {
            val contentParts = JSONArray()
            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", prompt.ifBlank { "Analise o anexo." })
            contentParts.put(textPart)

            val imagePart = JSONObject()
            imagePart.put("type", "image_url")
            val imgUrl = JSONObject()
            imgUrl.put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
            imagePart.put("image_url", imgUrl)
            contentParts.put(imagePart)

            currentUserMsg.put("content", contentParts)
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
            .url("$url/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val extracted = extractErrorMessage(errBody, response.code)
                    emit("Erro OpenAI [HTTP ${response.code}]: $extracted")
                    return@flow
                }

                val responseBody = response.body ?: run {
                    emit("Erro: Resposta vazia da OpenAI.")
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
            emit("Erro de conexão OpenAI: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)
}
