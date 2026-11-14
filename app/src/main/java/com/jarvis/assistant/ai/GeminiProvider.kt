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
import kotlinx.coroutines.delay
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String
) : AIProvider {

    override val id: String = "gemini"
    override val type: ProviderType = ProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL_V1BETA = "https://generativelanguage.googleapis.com/v1beta"
        const val BASE_URL_V1 = "https://generativelanguage.googleapis.com/v1"

        private val API_KEY_REGEX = Regex("""AIza[0-9A-Za-z_-]{20,50}""")
        private val URL_KEY_PARAM_REGEX = Regex("""key=[0-9A-Za-z_-]+""")

        fun sanitizeErrorText(text: String): String {
            return text.replace(API_KEY_REGEX, "[CHAVE_PROTEGIDA]")
                .replace(URL_KEY_PARAM_REGEX, "key=[CHAVE_PROTEGIDA]")
        }

        fun extractErrorMessage(errorBody: String?, httpCode: Int): String {
            if (errorBody.isNullOrBlank()) {
                return when (httpCode) {
                    400 -> "Parâmetros de requisição inválidos (HTTP 400)."
                    401 -> "Chave de API do Google Gemini inválida (HTTP 401). Verifique sua chave no Google AI Studio."
                    403 -> "Acesso negado no Google Gemini (HTTP 403). Verifique as permissões de API na sua conta."
                    404 -> "Modelo do Gemini não encontrado na API (HTTP 404). O modelo solicitado não existe nesta versão da API ou conta."
                    429 -> "Limite de cota excedido no Google Gemini (HTTP 429). Aguarde alguns segundos."
                    500, 503 -> "Servidor do Google Gemini temporariamente indisponível (HTTP $httpCode)."
                    else -> "Erro na API do Google Gemini (HTTP $httpCode)."
                }
            }
            return try {
                val json = JSONObject(errorBody)
                val errorObj = json.optJSONObject("error")
                val message = errorObj?.optString("message", "") ?: ""
                val cleanMessage = sanitizeErrorText(message)
                if (cleanMessage.isNotBlank()) cleanMessage else "Erro HTTP $httpCode: ${sanitizeErrorText(errorBody)}"
            } catch (_: Exception) {
                sanitizeErrorText(errorBody).take(250)
            }
        }

        fun parseModelsFromJson(jsonStr: String, providerId: String = "gemini"): List<AIModelInfo> {
            val models = mutableListOf<AIModelInfo>()
            try {
                val root = JSONObject(jsonStr)
                val modelsArray = root.optJSONArray("models") ?: JSONArray()
                for (i in 0 until modelsArray.length()) {
                    val obj = modelsArray.optJSONObject(i) ?: continue
                    val rawName = obj.optString("name", "").trim()
                    if (rawName.isBlank()) continue
                    val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                    var supportsGenerate = false
                    if (supportedMethods != null) {
                        for (j in 0 until supportedMethods.length()) {
                            if (supportedMethods.optString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                    }
                    if (!supportsGenerate) continue

                    val cleanId = rawName.removePrefix("models/").trim()
                    if (cleanId.isBlank()) continue
                    val lowerId = cleanId.lowercase()
                    if (lowerId.contains("embedding") || lowerId.contains("aqa") ||
                        lowerId.contains("imagen") || lowerId.contains("veo") ||
                        lowerId.contains("tts")
                    ) {
                        continue
                    }

                    val displayName = obj.optString("displayName", cleanId).ifBlank { cleanId }
                    val description = obj.optString("description", "")
                    val inputTokenLimit = obj.optInt("inputTokenLimit", 128000).takeIf { it > 0 } ?: 128000
                    val capabilities = mutableSetOf(ModelCapability.TEXT)
                    if (lowerId.contains("flash") || lowerId.contains("pro")) {
                        capabilities.add(ModelCapability.IMAGE_ANALYSIS)
                        capabilities.add(ModelCapability.CODING)
                    }
                    if (lowerId.contains("pro") || lowerId.contains("thinking")) {
                        capabilities.add(ModelCapability.REASONING)
                    }
                    if (inputTokenLimit >= 100000) {
                        capabilities.add(ModelCapability.LONG_CONTEXT)
                    }
                    val level = when {
                        lowerId.contains("pro") || lowerId.contains("thinking") -> AILevel.HIGH
                        lowerId.contains("lite") -> AILevel.LIGHT
                        lowerId.contains("flash") -> AILevel.MEDIUM
                        else -> AILevel.LIGHT
                    }
                    models.add(
                        AIModelInfo(
                            id = cleanId,
                            displayName = displayName,
                            providerId = providerId,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description,
                            contextLength = inputTokenLimit,
                            promptPricePerMillion = 0.0,
                            completionPricePerMillion = 0.0,
                            isFree = true,
                            inputModalities = listOf("text", "image"),
                            outputModalities = listOf("text"),
                            organization = "Google"
                        )
                    )
                }
            } catch (_: Exception) {
            }
            return sortAndDeduplicateModels(models)
        }

        fun sortAndDeduplicateModels(models: List<AIModelInfo>): List<AIModelInfo> {
            return models.distinctBy { it.id }.sortedWith(Comparator { a, b ->
                val rankA = modelSortRank(a.id)
                val rankB = modelSortRank(b.id)
                if (rankA != rankB) return@Comparator rankA.compareTo(rankB)
                a.displayName.compareTo(b.displayName, ignoreCase = true)
            })
        }

        private fun modelSortRank(modelId: String): Int {
            val lower = modelId.lowercase()
            return when {
                lower.contains("3.1") -> 0
                lower.contains("3.") || lower.contains("gemini-3") -> 1
                lower.contains("2.5") -> 2
                lower.contains("2.0") -> 3
                else -> 4
            }
        }

        fun buildNormalizedContentsForMessages(
            history: List<Message>,
            currentPrompt: String,
            attachmentPart: JSONObject? = null
        ): JSONArray {
            val trimmedPrompt = currentPrompt.trim()
            val validHistory = history
                .filter { it.content.isNotBlank() }
                .let { list ->
                    if (list.isNotEmpty() && list.last().isFromUser &&
                        list.last().content.trim() == trimmedPrompt
                    ) {
                        list.dropLast(1)
                    } else {
                        list
                    }
                }
                .takeLast(14)

            val contentsArray = JSONArray()
            var currentRole: String? = null
            var currentParts = JSONArray()
            var hasSeenUser = false

            for (msg in validHistory) {
                val role = if (msg.isFromUser) "user" else "model"
                if (!hasSeenUser) {
                    if (role != "user") continue
                    hasSeenUser = true
                }
                if (role == currentRole) {
                    currentParts.put(JSONObject().put("text", msg.content.trim()))
                } else {
                    if (currentRole != null && currentParts.length() > 0) {
                        contentsArray.put(
                            JSONObject().apply {
                                put("role", currentRole)
                                put("parts", currentParts)
                            }
                        )
                    }
                    currentRole = role
                    currentParts = JSONArray().apply {
                        put(JSONObject().put("text", msg.content.trim()))
                    }
                }
            }

            if (currentRole != null && currentParts.length() > 0) {
                contentsArray.put(
                    JSONObject().apply {
                        put("role", currentRole)
                        put("parts", currentParts)
                    }
                )
            }

            val userParts = JSONArray()
            if (attachmentPart != null) {
                userParts.put(attachmentPart)
            }
            userParts.put(JSONObject().put("text", trimmedPrompt.ifBlank { "Olá" }))

            if (contentsArray.length() > 0 &&
                contentsArray.getJSONObject(contentsArray.length() - 1).optString("role") == "user"
            ) {
                val lastUserObj = contentsArray.getJSONObject(contentsArray.length() - 1)
                val existingParts = lastUserObj.getJSONArray("parts")
                for (k in 0 until userParts.length()) {
                    existingParts.put(userParts.get(k))
                }
            } else {
                contentsArray.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", userParts)
                    }
                )
            }
            return contentsArray
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
                    statusCode = 400,
                    message = "Chave de API Google Gemini não informada.",
                    status = ProviderStatus.SEM_CREDENCIAL,
                    isAuthValid = false,
                    isCatalogLoaded = false
                )
            }
            val modelsResult = fetchPaginatedModels(trimmedKey, baseUrl)
            modelsResult.fold(
                onSuccess = { (httpCode, models) ->
                    if (models.isNotEmpty()) {
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = httpCode,
                            message = "Conexão Google Gemini validada! ${models.size} modelos compatíveis identificados.",
                            status = ProviderStatus.CONEXAO_VALIDADA,
                            isAuthValid = true,
                            isCatalogLoaded = true,
                            discoveredModels = models,
                            freeModelsCount = models.count { it.isFree }
                        )
                    } else {
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = httpCode,
                            message = "Chave Gemini autenticada, mas nenhum modelo compatível com geração de texto foi retornado.",
                            status = ProviderStatus.NENHUM_MODELO_COMPATIVEL,
                            isAuthValid = true,
                            isCatalogLoaded = false,
                            discoveredModels = emptyList(),
                            failureReason = "Catálogo vazio para generateContent"
                        )
                    }
                },
                onFailure = { err ->
                    val httpErr = err as? GeminiHttpException
                    val code = httpErr?.statusCode ?: 0
                    val msg = sanitizeErrorText(err.message ?: "Conexão falhou")
                    val status = when (code) {
                        401 -> ProviderStatus.ERRO_DE_AUTENTICACAO
                        403 -> ProviderStatus.ERRO_DE_PERMISSAO
                        else -> ProviderStatus.ERRO_DE_REDE
                    }
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = if (code > 0) "Falha Gemini [HTTP $code]: $msg" else "Erro de rede ao conectar com Google Gemini: $msg",
                        status = status,
                        isAuthValid = false,
                        isCatalogLoaded = false,
                        failureReason = msg
                    )
                }
            )
        }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> =
        withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isBlank()) {
                return@withContext Result.failure(Exception("Chave vazia"))
            }
            fetchPaginatedModels(trimmedKey, baseUrl).map { it.second }
        }

    private fun fetchPaginatedModels(
        trimmedKey: String,
        baseUrl: String
    ): Result<Pair<Int, List<AIModelInfo>>> {
        val rootBase = if (baseUrl.isNotBlank()) baseUrl.trim().removeSuffix("/") else BASE_URL_V1BETA
        val allModels = mutableListOf<AIModelInfo>()
        var pageToken: String? = null
        var lastCode = 200

        return try {
            for (page in 0 until 10) {
                val url = buildString {
                    append("$rootBase/models?pageSize=100")
                    if (!pageToken.isNullOrBlank()) {
                        append("&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}")
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", trimmedKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    lastCode = response.code
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return Result.failure(
                            GeminiHttpException(response.code, extractErrorMessage(bodyStr, response.code))
                        )
                    }
                    allModels.addAll(parseModelsFromJson(bodyStr, id))
                    val nextToken = try {
                        JSONObject(bodyStr).optString("nextPageToken", "").trim()
                    } catch (_: Exception) {
                        ""
                    }
                    if (nextToken.isBlank()) {
                        return Result.success(lastCode to sortAndDeduplicateModels(allModels))
                    }
                    pageToken = nextToken
                }
            }
            Result.success(lastCode to sortAndDeduplicateModels(allModels))
        } catch (e: Exception) {
            val readable = when (e) {
                is java.net.UnknownHostException -> "Falha de DNS: não foi possível resolver 'generativelanguage.googleapis.com'."
                is java.net.SocketTimeoutException -> "Tempo limite de conexão esgotado (Timeout) com Google Gemini."
                is java.net.ConnectException -> "Conexão recusada pelo servidor do Google Gemini."
                is javax.net.ssl.SSLException -> "Falha TLS/SSL ao conectar ao Google Gemini."
                else -> sanitizeErrorText(e.message ?: e.javaClass.simpleName)
            }
            Result.failure(Exception(readable, e))
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
            emit("Erro: Chave de API Google Gemini não configurada. Configure sua chave em Configurações > Provedores de IA.")
            return@flow
        }

        val cleanModel = modelName.trim().removePrefix("models/")
        val resolvedModel = if (cleanModel.isNotBlank()) {
            cleanModel
        } else {
            val available = queryRemoteModels(key).getOrNull()
            available?.firstOrNull()?.id ?: run {
                emit("Erro: Nenhum modelo do Google Gemini selecionado ou disponível no catálogo da sua conta.")
                return@flow
            }
        }

        val attachmentPart = buildImagePart(attachment)
        val contentsArray = buildNormalizedContentsForMessages(history, prompt, attachmentPart)

        var pendingFallbackError: String? = null
        val success = executeGeminiRequest(
            contentsArray = contentsArray,
            resolvedModel = resolvedModel,
            key = key,
            onChunk = { emit(it) },
            onError = { lastErr ->
                if (lastErr.contains("Multiturn chat is not enabled", ignoreCase = true) ||
                    lastErr.contains("Please ensure that multiturn talk alternates", ignoreCase = true)
                ) {
                    pendingFallbackError = lastErr
                } else {
                    emit(lastErr)
                }
            }
        )

        if (!success && pendingFallbackError != null) {
            val fallbackContents = buildSingleTurnWithFlattenedHistory(history, prompt, attachmentPart)
            var fallbackErrMessage: String? = null
            val singleTurnSuccess = executeGeminiRequest(
                contentsArray = fallbackContents,
                resolvedModel = resolvedModel,
                key = key,
                onChunk = { emit(it) },
                onError = { fallbackErrMessage = it }
            )
            if (!singleTurnSuccess) {
                emit(fallbackErrMessage ?: "Erro Gemini [Compatibilidade]: o modelo $resolvedModel não aceitou o formato de histórico.")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildImagePart(attachment: Attachment?): JSONObject? {
        if (attachment == null || context == null || attachment.uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(attachment.uriString)
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                val bytes = stream.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                JSONObject().apply {
                    put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", mime)
                            put("data", base64)
                        }
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSingleTurnWithFlattenedHistory(
        history: List<Message>,
        currentPrompt: String,
        attachmentPart: JSONObject?
    ): JSONArray {
        val trimmedPrompt = currentPrompt.trim()
        val dedupedHistory = history
            .filter { it.content.isNotBlank() }
            .let { list ->
                if (list.isNotEmpty() && list.last().isFromUser && list.last().content.trim() == trimmedPrompt) {
                    list.dropLast(1)
                } else {
                    list
                }
            }
            .takeLast(10)

        val flattenedContext = StringBuilder()
        if (dedupedHistory.isNotEmpty()) {
            flattenedContext.append("[Contexto da Conversa Anterior]:\n")
            for (msg in dedupedHistory) {
                val roleName = if (msg.isFromUser) "Usuário" else "Assistente"
                flattenedContext.append("$roleName: ${msg.content.trim()}\n")
            }
            flattenedContext.append("\n[Solicitação Atual]:\n")
        }
        flattenedContext.append(trimmedPrompt.ifBlank { "Olá" })

        val parts = JSONArray()
        if (attachmentPart != null) {
            parts.put(attachmentPart)
        }
        parts.put(JSONObject().put("text", flattenedContext.toString()))

        return JSONArray().put(
            JSONObject().apply {
                put("role", "user")
                put("parts", parts)
            }
        )
    }

    private suspend fun executeGeminiRequest(
        contentsArray: JSONArray,
        resolvedModel: String,
        key: String,
        onChunk: suspend (String) -> Unit,
        onError: suspend (String) -> Unit
    ): Boolean {
        val requestBodyJson = JSONObject().apply {
            put("contents", contentsArray)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val bodyBytes = requestBodyJson.toString()

        val endpoints = listOf(
            "$BASE_URL_V1BETA/models/$resolvedModel:streamGenerateContent?alt=sse",
            "$BASE_URL_V1/models/$resolvedModel:streamGenerateContent?alt=sse"
        )
        var lastErrorMsg = "Falha desconhecida ao acessar modelo $resolvedModel"

        for (endpoint in endpoints) {
            var moveToNextEndpoint = false
            for (attempt in 1..2) {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("x-goog-api-key", key)
                    .post(bodyBytes.toRequestBody(mediaType))
                    .build()

                var shouldRetryAfterDelay = false
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body ?: return true
                            val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val currentLine = line ?: continue
                                if (currentLine.startsWith("data: ")) {
                                    val jsonStr = currentLine.removePrefix("data: ").trim()
                                    if (jsonStr.isBlank() || jsonStr == "[DONE]") continue
                                    try {
                                        val obj = JSONObject(jsonStr)
                                        val streamErr = obj.optJSONObject("error")
                                        if (streamErr != null) {
                                            val errMsg = sanitizeErrorText(streamErr.optString("message", "Erro no stream Gemini"))
                                            onError("Erro Gemini: $errMsg")
                                            return false
                                        }
                                        val candidates = obj.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val firstCandidate = candidates.getJSONObject(0)
                                            val content = firstCandidate.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null) {
                                                for (k in 0 until parts.length()) {
                                                    val text = parts.getJSONObject(k).optString("text", "")
                                                    if (text.isNotEmpty()) {
                                                        onChunk(text)
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            return true
                        } else {
                            val errBody = response.body?.string() ?: ""
                            lastErrorMsg = extractErrorMessage(errBody, response.code)
                            if (response.code == 429 && attempt == 1) {
                                shouldRetryAfterDelay = true
                            } else if (response.code == 404) {
                                moveToNextEndpoint = true
                            } else {
                                onError("Erro Gemini [HTTP ${response.code}]: $lastErrorMsg")
                                return false
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Erro de rede no Gemini: ${sanitizeErrorText(e.message ?: "Conexão falhou")}"
                    if (attempt == 1) {
                        shouldRetryAfterDelay = true
                    }
                }

                if (moveToNextEndpoint) break
                if (shouldRetryAfterDelay) {
                    delay(1200)
                }
            }
        }

        onError("Erro Gemini [Modelo $resolvedModel]: $lastErrorMsg")
        return false
    }

    private class GeminiHttpException(val statusCode: Int, message: String) : Exception(message)
}
