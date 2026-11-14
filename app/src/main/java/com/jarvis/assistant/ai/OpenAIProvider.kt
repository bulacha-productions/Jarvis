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
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAIProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String,
    private val baseUrlProvider: suspend () -> String = { "" }
) : AIProvider {

    constructor(apiKeyProvider: suspend () -> String) : this(null, apiKeyProvider, { "" })

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

        private val EXCLUDED_KEYWORDS = listOf(
            "-instruct", "realtime", "audio", "transcribe", "tts",
            "search-preview", "dall-e", "whisper", "embedding",
            "moderation", "sora", "image"
        )

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
                    val code = err.optString("code", "")
                    val msg = err.optString("message", "")
                    if (code == "insufficient_quota" || msg.contains("quota", ignoreCase = true)) {
                        "Saldo/Créditos insuficientes na conta da OpenAI (Platform API). Adicione saldo em platform.openai.com/billing."
                    } else if (code == "invalid_api_key") {
                        "Chave de API OpenAI inválida ou revogada (HTTP 401). Verifique em platform.openai.com/api-keys."
                    } else if (msg.isNotBlank()) {
                        msg
                    } else {
                        "Erro HTTP $httpCode: $errorBody"
                    }
                } else {
                    errorBody.take(300)
                }
            } catch (_: Exception) {
                errorBody.take(300)
            }
        }

        fun parseModelsFromJson(body: String, providerId: String = "openai"): List<AIModelInfo> {
            val models = mutableListOf<AIModelInfo>()
            try {
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    if (id.isBlank()) continue
                    val lower = id.lowercase()
                    val isChatPrefix = lower.startsWith("gpt-") ||
                        lower.startsWith("o1") ||
                        lower.startsWith("o3") ||
                        lower.startsWith("o4") ||
                        lower.startsWith("chatgpt")
                    if (!isChatPrefix) continue
                    if (EXCLUDED_KEYWORDS.any { lower.contains(it) }) continue

                    val capabilities = mutableSetOf(ModelCapability.TEXT)
                    if (lower.contains("4o") || lower.contains("4.1") || lower.contains("vision") || lower.contains("o4")) {
                        capabilities.add(ModelCapability.IMAGE_ANALYSIS)
                    }
                    if (lower.contains("4") || lower.contains("o1") || lower.contains("o3") || lower.contains("o4") || lower.contains("codex")) {
                        capabilities.add(ModelCapability.CODING)
                        capabilities.add(ModelCapability.REASONING)
                    }
                    val level = when {
                        lower.contains("o1") || lower.contains("o3") || lower.contains("o4") ||
                            ((lower.contains("4o") || lower.contains("4.1") || lower.contains("gpt-5")) && !lower.contains("mini") && !lower.contains("nano")) -> AILevel.HIGH
                        lower.contains("mini") || lower.contains("nano") || lower.contains("3.5") -> AILevel.LIGHT
                        else -> AILevel.MEDIUM
                    }
                    models.add(
                        AIModelInfo(
                            id = id,
                            displayName = id,
                            providerId = providerId,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = "Modelo oficial OpenAI ($id)",
                            isFree = false,
                            inputModalities = if (capabilities.contains(ModelCapability.IMAGE_ANALYSIS)) listOf("text", "image") else listOf("text"),
                            outputModalities = listOf("text"),
                            organization = "OpenAI"
                        )
                    )
                }
            } catch (_: Exception) {
            }
            return models.distinctBy { it.id }.sortedWith(Comparator { a, b ->
                val rankA = openAiSortRank(a.id)
                val rankB = openAiSortRank(b.id)
                if (rankA != rankB) return@Comparator rankA.compareTo(rankB)
                a.displayName.compareTo(b.displayName, ignoreCase = true)
            })
        }

        private fun openAiSortRank(modelId: String): Int {
            val lower = modelId.lowercase()
            return when {
                lower.startsWith("gpt-5") -> 0
                lower.startsWith("gpt-4.1") -> 1
                lower.startsWith("o4") || lower.startsWith("o3") -> 2
                lower == "gpt-4o" -> 3
                lower.startsWith("gpt-4o") -> 4
                else -> 5
            }
        }

        fun buildNormalizedMessages(
            history: List<Message>,
            prompt: String,
            currentContent: Any
        ): JSONArray {
            val trimmedPrompt = prompt.trim()
            val dedupedHistory = history
                .filter { it.content.isNotBlank() }
                .let { list ->
                    if (list.isNotEmpty() && list.last().isFromUser && list.last().content.trim() == trimmedPrompt) {
                        list.dropLast(1)
                    } else {
                        list
                    }
                }
                .takeLast(16)

            val messagesArray = JSONArray()
            var lastRole: String? = null
            for (msg in dedupedHistory) {
                val role = if (msg.isFromUser) "user" else "assistant"
                if (role == lastRole && messagesArray.length() > 0) {
                    val lastObj = messagesArray.getJSONObject(messagesArray.length() - 1)
                    val existing = lastObj.optString("content", "")
                    lastObj.put("content", "$existing\n\n${msg.content.trim()}")
                } else {
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", role)
                            put("content", msg.content.trim())
                        }
                    )
                    lastRole = role
                }
            }

            if (lastRole == "user" && currentContent is String && messagesArray.length() > 0) {
                val lastObj = messagesArray.getJSONObject(messagesArray.length() - 1)
                val existing = lastObj.optString("content", "")
                lastObj.put("content", "$existing\n\n$currentContent")
            } else {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", currentContent)
                    }
                )
            }
            return messagesArray
        }
    }

    private suspend fun resolveBaseUrl(overrideUrl: String = ""): String {
        val raw = if (overrideUrl.isNotBlank()) overrideUrl else baseUrlProvider()
        val custom = raw.trim()
        return if (custom.isNotBlank()) custom.removeSuffix("/") else DEFAULT_BASE_URL
    }

    override suspend fun isAvailable(): Boolean {
        return apiKeyProvider().trim().isNotBlank()
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isBlank()) {
                return@withContext ConnectionTestResult(
                    isSuccess = false,
                    statusCode = 0,
                    message = "Chave de API OpenAI não configurada.",
                    status = ProviderStatus.SEM_CREDENCIAL,
                    isAuthValid = false,
                    isCatalogLoaded = false
                )
            }
            val url = resolveBaseUrl(baseUrl)
            val request = Request.Builder()
                .url("$url/models")
                .header("Authorization", "Bearer $trimmedKey")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val models = parseModelsFromJson(body, id)
                        if (models.isNotEmpty()) {
                            ConnectionTestResult(
                                isSuccess = true,
                                statusCode = response.code,
                                message = "Conexão OpenAI validada com sucesso! ${models.size} modelos compatíveis listados.",
                                status = ProviderStatus.CONEXAO_VALIDADA,
                                isAuthValid = true,
                                isCatalogLoaded = true,
                                discoveredModels = models
                            )
                        } else {
                            ConnectionTestResult(
                                isSuccess = false,
                                statusCode = response.code,
                                message = "Chave OpenAI autenticada, mas nenhum modelo compatível com chat foi encontrado.",
                                status = ProviderStatus.NENHUM_MODELO_COMPATIVEL,
                                isAuthValid = true,
                                isCatalogLoaded = false,
                                discoveredModels = emptyList(),
                                failureReason = "Nenhum modelo de chat retornado pela API"
                            )
                        }
                    } else {
                        val errorMsg = extractErrorMessage(body, response.code)
                        val status = when (response.code) {
                            401 -> ProviderStatus.ERRO_DE_AUTENTICACAO
                            403 -> ProviderStatus.ERRO_DE_PERMISSAO
                            else -> ProviderStatus.ERRO_DE_REDE
                        }
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = response.code,
                            message = "Falha OpenAI [HTTP ${response.code}]: $errorMsg",
                            status = status,
                            isAuthValid = false,
                            isCatalogLoaded = false,
                            failureReason = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                val netError = when (e) {
                    is java.net.UnknownHostException -> "Falha de DNS ao contatar OpenAI."
                    is java.net.SocketTimeoutException -> "Timeout ao contatar OpenAI."
                    else -> "Erro de rede com OpenAI: ${e.message ?: "Conexão falhou"}"
                }
                ConnectionTestResult(
                    isSuccess = false,
                    statusCode = 0,
                    message = netError,
                    status = ProviderStatus.ERRO_DE_REDE,
                    isAuthValid = false,
                    isCatalogLoaded = false,
                    failureReason = netError
                )
            }
        }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> =
        withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isBlank()) {
                return@withContext Result.failure(Exception("Chave vazia"))
            }
            val url = resolveBaseUrl(baseUrl)
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
                    Result.success(parseModelsFromJson(body, id))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        val key = apiKeyProvider().trim()
        if (key.isNotBlank()) {
            val remote = queryRemoteModels(key).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val filtered = remote.filter { it.defaultLevel == level }
                return if (filtered.isNotEmpty()) filtered else remote
            }
        }
        return emptyList()
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

        val resolvedModel = modelName.trim().ifBlank {
            queryRemoteModels(key).getOrNull()?.firstOrNull()?.id ?: run {
                emit("Erro: Nenhum modelo OpenAI selecionado ou disponível no catálogo da sua conta.")
                return@flow
            }
        }
        val url = resolveBaseUrl()

        val currentContent: Any = buildCurrentUserContent(prompt, attachment)
        val messagesArray = buildNormalizedMessages(history, prompt, currentContent)

        val requestBodyJson = JSONObject().apply {
            put("model", resolvedModel)
            put("messages", messagesArray)
            put("stream", true)
        }

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
                            val errObj = chunkJson.optJSONObject("error")
                            if (errObj != null) {
                                emit("Erro OpenAI: ${errObj.optString("message", "Falha no streaming")}")
                                return@flow
                            }
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val text = delta?.optString("content", "") ?: ""
                                if (text.isNotEmpty()) {
                                    emit(text)
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Erro de conexão OpenAI: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)

    private fun buildCurrentUserContent(prompt: String, attachment: Attachment?): Any {
        if (attachment != null && context != null && !attachment.uriString.isNullOrBlank()) {
            var base64Data: String? = null
            try {
                val uri = Uri.parse(attachment.uriString)
                context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    base64Data = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                }
            } catch (_: Exception) {
            }
            if (!base64Data.isNullOrBlank()) {
                val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                return JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", prompt.ifBlank { "Analise o anexo." }))
                    put(
                        JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:$mime;base64,$base64Data"))
                        }
                    )
                }
            }
        }
        return prompt.ifBlank { "Olá" }
    }
}
