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

class ClaudeProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String
) : AIProvider {

    override val id: String = "claude"
    override val type: ProviderType = ProviderType.CLAUDE
    override val displayName: String = "Anthropic Claude"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MODEL = "claude-3-5-sonnet-20241022"

        fun extractErrorMessage(errorBody: String?, httpCode: Int): String {
            if (errorBody.isNullOrBlank()) {
                return when (httpCode) {
                    401 -> "Chave Anthropic inválida ou não autorizada (HTTP 401)."
                    403 -> "Acesso negado pela Anthropic (HTTP 403)."
                    404 -> "Modelo Anthropic não encontrado (HTTP 404)."
                    429 -> "Limite de cota/requisições excedido na Anthropic (HTTP 429)."
                    500, 529 -> "Servidores Anthropic sobrecarregados ou indisponíveis (HTTP $httpCode)."
                    else -> "Erro HTTP $httpCode na API Anthropic."
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
                message = "Chave de API Anthropic Claude não configurada."
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("x-api-key", trimmedKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
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
                        message = "Conexão com Anthropic Claude estabelecida com sucesso! ${models.size} modelos localizados.",
                        discoveredModels = models
                    )
                } else {
                    val errorMsg = extractErrorMessage(body, response.code)
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Falha ao validar chave Claude (HTTP ${response.code}): $errorMsg"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Erro de conexão ao acessar a API Claude: ${e.message ?: "Conexão recusada"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("x-api-key", trimmedKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
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

                val displayName = item.optString("display_name", id)
                val capabilities = mutableSetOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION)
                if (id.contains("sonnet") || id.contains("opus")) {
                    capabilities.add(ModelCapability.MULTI_AGENT_READY)
                }

                val level = when {
                    id.contains("opus") || id.contains("sonnet") -> AILevel.POWERFUL
                    id.contains("haiku") -> AILevel.FAST
                    else -> AILevel.BALANCED
                }

                models.add(
                    AIModelInfo(
                        id = id,
                        displayName = displayName,
                        providerId = this.id,
                        defaultLevel = level,
                        capabilities = capabilities,
                        description = "Modelo Anthropic ($id)"
                    )
                )
            }
        } catch (e: Exception) {
            // Ignora falhas de parse
        }
        return models.sortedWith(
            compareByDescending<AIModelInfo> { it.id.contains("3-5-sonnet") }
                .thenByDescending { it.id.contains("3-7-sonnet") }
                .thenBy { it.displayName }
        )
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        val key = apiKeyProvider()
        if (key.isNotBlank()) {
            val remote = queryRemoteModels(key).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val filtered = remote.filter { it.defaultLevel == level }
                return if (filtered.isNotEmpty()) filtered else remote
            }
        }
        return listOf(
            AIModelInfo(
                id = "claude-3-5-sonnet-20241022",
                displayName = "Claude 3.5 Sonnet",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "Anthropic Claude 3.5 Sonnet de alta performance"
            ),
            AIModelInfo(
                id = "claude-3-5-haiku-20241022",
                displayName = "Claude 3.5 Haiku",
                providerId = id,
                defaultLevel = AILevel.FAST,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION),
                description = "Anthropic Claude 3.5 Haiku veloz"
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
            emit("Erro: Chave de API Anthropic Claude não configurada. Configure em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().ifBlank { DEFAULT_MODEL }

        val messagesArray = JSONArray()
        var lastRole: String? = null
        for (msg in history.takeLast(16)) {
            val role = if (msg.isUser) "user" else "assistant"
            if (role != lastRole) {
                val msgObj = JSONObject()
                msgObj.put("role", role)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
                lastRole = role
            }
        }

        if (lastRole == "user") {
            val lastUser = messagesArray.getJSONObject(messagesArray.length() - 1)
            val currentContent = lastUser.optString("content", "")
            lastUser.put("content", "$currentContent\n\n$prompt")
        } else {
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", prompt)
            messagesArray.put(userMsg)
        }

        val requestBodyJson = JSONObject()
        requestBodyJson.put("model", resolvedModel)
        requestBodyJson.put("max_tokens", 4096)
        requestBodyJson.put("messages", messagesArray)
        requestBodyJson.put("stream", true)

        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/messages")
            .header("x-api-key", key)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val extracted = extractErrorMessage(errBody, response.code)
                    emit("Erro Claude [HTTP ${response.code}]: $extracted")
                    return@flow
                }

                val responseBody = response.body ?: run {
                    emit("Erro: Corpo de resposta Claude vazio.")
                    return@flow
                }

                val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        if (data.isBlank()) continue
                        try {
                            val chunkJson = JSONObject(data)
                            val type = chunkJson.optString("type")
                            if (type == "content_block_delta") {
                                val delta = chunkJson.optJSONObject("delta")
                                val text = delta?.optString("text", "") ?: ""
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
            emit("Erro de conexão Claude: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)
}
