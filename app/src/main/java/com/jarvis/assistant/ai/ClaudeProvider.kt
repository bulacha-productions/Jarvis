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

class ClaudeProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String
) : AIProvider {

    constructor(apiKeyProvider: suspend () -> String) : this(null, apiKeyProvider)

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
            } catch (_: Exception) {
                errorBody.take(300)
            }
        }

        fun parseModelsFromJson(body: String, providerId: String = "claude"): List<AIModelInfo> {
            val models = mutableListOf<AIModelInfo>()
            try {
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    if (id.isBlank()) continue
                    val displayName = item.optString("display_name", id).ifBlank { id }
                    val lower = id.lowercase()
                    val capabilities = setOf(
                        ModelCapability.TEXT,
                        ModelCapability.IMAGE_ANALYSIS,
                        ModelCapability.CODING,
                        ModelCapability.REASONING
                    )
                    val level = when {
                        lower.contains("opus") || lower.contains("sonnet") -> AILevel.HIGH
                        lower.contains("haiku") -> AILevel.LIGHT
                        else -> AILevel.MEDIUM
                    }
                    models.add(
                        AIModelInfo(
                            id = id,
                            displayName = displayName,
                            providerId = providerId,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = "Modelo Anthropic ($id)",
                            isFree = false,
                            inputModalities = listOf("text", "image"),
                            outputModalities = listOf("text"),
                            organization = "Anthropic"
                        )
                    )
                }
            } catch (_: Exception) {
            }
            return models.distinctBy { it.id }.sortedWith(Comparator { a, b ->
                val rankA = claudeSortRank(a.id)
                val rankB = claudeSortRank(b.id)
                if (rankA != rankB) return@Comparator rankA.compareTo(rankB)
                a.displayName.compareTo(b.displayName, ignoreCase = true)
            })
        }

        private fun claudeSortRank(modelId: String): Int {
            val lower = modelId.lowercase()
            return when {
                lower.contains("sonnet-4") -> 0
                lower.contains("opus-4") -> 1
                lower.contains("3-7-sonnet") -> 2
                lower.contains("sonnet") -> 3
                lower.contains("haiku") -> 4
                else -> 5
            }
        }

        /**
         * Monta histórico compatível com a Anthropic Messages API:
         * - Remove eventual duplicata final igual ao prompt atual
         * - Descarta turnos iniciais de 'assistant' para garantir que messages[0].role == "user"
         * - Combina mensagens consecutivas do mesmo papel
         */
        fun buildNormalizedMessages(
            history: List<Message>,
            prompt: String,
            imageBlock: JSONObject? = null
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
                .takeLast(20)

            val messagesArray = JSONArray()
            var lastRole: String? = null
            var hasSeenUser = false

            for (msg in dedupedHistory) {
                val role = if (msg.isFromUser) "user" else "assistant"
                if (!hasSeenUser) {
                    if (role != "user") continue
                    hasSeenUser = true
                }
                if (role == lastRole && messagesArray.length() > 0) {
                    val lastMsg = messagesArray.getJSONObject(messagesArray.length() - 1)
                    val existing = lastMsg.optString("content", "")
                    lastMsg.put("content", "$existing\n\n${msg.content.trim()}")
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

            val safePrompt = trimmedPrompt.ifBlank { "Olá" }
            if (imageBlock != null) {
                val blocks = JSONArray().apply {
                    put(imageBlock)
                    put(JSONObject().put("type", "text").put("text", safePrompt))
                }
                if (lastRole == "user" && messagesArray.length() > 0) {
                    val lastUser = messagesArray.getJSONObject(messagesArray.length() - 1)
                    val prevText = lastUser.optString("content", "")
                    val mergedBlocks = JSONArray()
                    if (prevText.isNotBlank()) {
                        mergedBlocks.put(JSONObject().put("type", "text").put("text", prevText))
                    }
                    mergedBlocks.put(imageBlock)
                    mergedBlocks.put(JSONObject().put("type", "text").put("text", safePrompt))
                    lastUser.put("content", mergedBlocks)
                } else {
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", blocks)
                        }
                    )
                }
            } else if (lastRole == "user" && messagesArray.length() > 0) {
                val lastUser = messagesArray.getJSONObject(messagesArray.length() - 1)
                val currentContent = lastUser.optString("content", "")
                lastUser.put("content", "$currentContent\n\n$safePrompt")
            } else {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", safePrompt)
                    }
                )
            }
            return messagesArray
        }
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
                    message = "Chave de API Anthropic Claude não configurada.",
                    status = ProviderStatus.SEM_CREDENCIAL,
                    isAuthValid = false,
                    isCatalogLoaded = false
                )
            }

            val request = Request.Builder()
                .url("$BASE_URL/models?limit=100")
                .header("x-api-key", trimmedKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
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
                                message = "Conexão com Anthropic Claude validada! ${models.size} modelos localizados.",
                                status = ProviderStatus.CONEXAO_VALIDADA,
                                isAuthValid = true,
                                isCatalogLoaded = true,
                                discoveredModels = models
                            )
                        } else {
                            ConnectionTestResult(
                                isSuccess = false,
                                statusCode = response.code,
                                message = "Chave Claude autenticada, mas nenhum modelo compatível foi retornado.",
                                status = ProviderStatus.NENHUM_MODELO_COMPATIVEL,
                                isAuthValid = true,
                                isCatalogLoaded = false,
                                discoveredModels = emptyList(),
                                failureReason = "Catálogo vazio retornado pela Anthropic"
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
                            message = "Falha Claude [HTTP ${response.code}]: $errorMsg",
                            status = status,
                            isAuthValid = false,
                            isCatalogLoaded = false,
                            failureReason = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                val netError = when (e) {
                    is java.net.UnknownHostException -> "Falha de DNS ao contatar api.anthropic.com."
                    is java.net.SocketTimeoutException -> "Timeout ao contatar Anthropic Claude."
                    else -> "Erro de conexão com a API Claude: ${e.message ?: "Conexão recusada"}"
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
            val request = Request.Builder()
                .url("$BASE_URL/models?limit=100")
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
            emit("Erro: Chave de API Anthropic Claude não configurada. Configure em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().ifBlank {
            queryRemoteModels(key).getOrNull()?.firstOrNull()?.id ?: run {
                emit("Erro: Nenhum modelo Anthropic Claude selecionado ou disponível no catálogo da sua conta.")
                return@flow
            }
        }

        val imageBlock = buildImageBlock(attachment)
        val messagesArray = buildNormalizedMessages(history, prompt, imageBlock)

        val requestBodyJson = JSONObject().apply {
            put("model", resolvedModel)
            put("max_tokens", 4096)
            put("messages", messagesArray)
            put("stream", true)
        }

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
                            when (type) {
                                "content_block_delta" -> {
                                    val delta = chunkJson.optJSONObject("delta")
                                    val text = delta?.optString("text", "") ?: ""
                                    if (text.isNotEmpty()) {
                                        emit(text)
                                    }
                                }
                                "error" -> {
                                    val errObj = chunkJson.optJSONObject("error")
                                    val errMsg = errObj?.optString("message", "Erro no stream Claude") ?: "Erro no stream Claude"
                                    emit("Erro Claude: $errMsg")
                                    return@flow
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Erro de conexão Claude: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)

    private fun buildImageBlock(attachment: Attachment?): JSONObject? {
        if (attachment == null || context == null || attachment.uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(attachment.uriString)
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
                val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                JSONObject().apply {
                    put("type", "image")
                    put(
                        "source",
                        JSONObject().apply {
                            put("type", "base64")
                            put("media_type", mime)
                            put("data", base64)
                        }
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
