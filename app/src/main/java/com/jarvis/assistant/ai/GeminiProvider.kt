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
        .build()

    companion object {
        const val BASE_URL_V1BETA = "https://generativelanguage.googleapis.com/v1beta"
        const val BASE_URL_V1 = "https://generativelanguage.googleapis.com/v1"
        // V3.1.1: Sem fallback forçado a modelos obsoletos

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
    }

    override suspend fun isAvailable(): Boolean {
        return apiKeyProvider().isNotBlank()
    }

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return ConnectionTestResult(
                isSuccess = false,
                statusCode = 400,
                message = "Chave de API Google Gemini não informada."
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL_V1BETA/models")
            .addHeader("x-goog-api-key", trimmedKey)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val models = parseModelsFromJson(bodyStr)
                    ConnectionTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        message = "Conexão Google Gemini verificada com sucesso! ${models.size} modelos compatíveis identificados.",
                        discoveredModels = models
                    )
                } else {
                    val errorMsg = extractErrorMessage(bodyStr, response.code)
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Falha Gemini [HTTP ${response.code}]: $errorMsg"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Erro de rede ao conectar com Google Gemini: ${e.message ?: "Conexão falhou"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return Result.failure(Exception("Chave vazia"))
        }

        val request = Request.Builder()
            .url("$BASE_URL_V1BETA/models")
            .addHeader("x-goog-api-key", trimmedKey)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return Result.failure(Exception("HTTP ${response.code}: ${extractErrorMessage(errorBody, response.code)}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val models = parseModelsFromJson(bodyStr)
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelsFromJson(jsonStr: String): List<AIModelInfo> {
        val models = mutableListOf<AIModelInfo>()
        try {
            val root = JSONObject(jsonStr)
            val modelsArray = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until modelsArray.length()) {
                val obj = modelsArray.getJSONObject(i)
                val rawName = obj.optString("name", "").trim()
                val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                var supportsGenerate = false
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        if (supportedMethods.getString(j) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                }
                if (supportsGenerate) {
                    val cleanId = rawName.removePrefix("models/")
                    val displayName = obj.optString("displayName", cleanId)
                    val description = obj.optString("description", "")
                    val inputTokenLimit = obj.optInt("inputTokenLimit", 128000)

                    val capabilities = mutableSetOf(ModelCapability.TEXT)
                    if (cleanId.contains("flash") || cleanId.contains("pro")) {
                        capabilities.add(ModelCapability.IMAGE_ANALYSIS)
                        capabilities.add(ModelCapability.CODING)
                    }
                    if (cleanId.contains("pro") || cleanId.contains("thinking")) {
                        capabilities.add(ModelCapability.REASONING)
                    }
                    if (inputTokenLimit >= 100000) {
                        capabilities.add(ModelCapability.LONG_CONTEXT)
                    }

                    val level = when {
                        cleanId.contains("pro") || cleanId.contains("thinking") -> AILevel.HIGH
                        cleanId.contains("flash") -> AILevel.MEDIUM
                        else -> AILevel.LIGHT
                    }

                    models.add(
                        AIModelInfo(
                            id = cleanId,
                            displayName = displayName.ifBlank { cleanId },
                            providerId = id,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description,
                            contextLength = inputTokenLimit,
                            isFree = true, // Gemini oferece free tier oficial em AI Studio
                            organization = "Google"
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        models.sortWith(Comparator { a, b ->
            if (a.id.contains("2.5") && !b.id.contains("2.5")) return@Comparator -1
            if (!a.id.contains("2.5") && b.id.contains("2.5")) return@Comparator 1
            if (a.id.contains("2.0") && !b.id.contains("2.0")) return@Comparator -1
            if (!a.id.contains("2.0") && b.id.contains("2.0")) return@Comparator 1
            a.displayName.compareTo(b.displayName)
        })
        return models
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
        // V3.1.1: Não substitui catálogo vazio por modelos fictícios
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

        // 1. Montagem estrita de histórico com alternância correta (user -> model -> user -> model -> user)
        // Combina partes consecutivas para evitar erros de papéis duplicados
        val contentsArray = buildNormalizedContents(history, prompt, attachment)

        // Execução com tentativa primária (multiturno) e fallback adaptativo para turno único
        val success = executeGeminiRequest(
            contentsArray = contentsArray,
            resolvedModel = resolvedModel,
            key = key,
            onChunk = { emit(it) },
            onError = { lastErr ->
                // Se o modelo rejeitar multiturn ("Multiturn chat is not enabled for this model"),
                // adaptamos automaticamente para um turno único estruturado preservando todo o histórico!
                if (lastErr.contains("Multiturn chat is not enabled", ignoreCase = true) ||
                    lastErr.contains("Please ensure that multiturn talk alternates", ignoreCase = true)) {
                    // Estratégia de compatibilidade: consolidar o histórico em um único turno "user"
                    val fallbackContents = buildSingleTurnWithFlattenedHistory(history, prompt, attachment)
                    var singleTurnSuccess = false
                    executeGeminiRequest(
                        contentsArray = fallbackContents,
                        resolvedModel = resolvedModel,
                        key = key,
                        onChunk = { emit(it) },
                        onError = { emit("Erro Gemini [Compatibilidade]: $it") }
                    ).also { singleTurnSuccess = it }
                    if (!singleTurnSuccess) {
                        emit("Erro Gemini [HTTP 400]: O modelo não suporta o formato solicitado e o fallback estruturado falhou.")
                    }
                } else {
                    emit(lastErr)
                }
            }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Constrói o array de contents estritamente alternado: user, model, user, model...
     * Combina mensagens consecutivas do mesmo papel em um único objeto content com múltiplas parts.
     */
    private fun buildNormalizedContents(
        history: List<Message>,
        currentPrompt: String,
        attachment: Attachment?
    ): JSONArray {
        val contentsArray = JSONArray()

        // Agrupamos mensagens válidas garantindo que a conversa comece com 'user'
        val validHistory = history.filter { it.content.isNotBlank() }.takeLast(14)

        var currentRole: String? = null
        var currentParts = JSONArray()

        for (msg in validHistory) {
            val role = if (msg.isFromUser) "user" else "model"

            if (contentsArray.length() == 0 && role != "user") {
                // Primeira mensagem no Gemini DEVE ser 'user'
                continue
            }

            if (role == currentRole) {
                // Mensagem consecutiva do mesmo papel: combina na mesma lista de parts
                currentParts.put(JSONObject().put("text", msg.content.trim()))
            } else {
                if (currentRole != null && currentParts.length() > 0) {
                    contentsArray.put(JSONObject().apply {
                        put("role", currentRole)
                        put("parts", currentParts)
                    })
                }
                currentRole = role
                currentParts = JSONArray().apply {
                    put(JSONObject().put("text", msg.content.trim()))
                }
            }
        }

        // Adiciona a última rodada do histórico se houver
        if (currentRole != null && currentParts.length() > 0) {
            contentsArray.put(JSONObject().apply {
                put("role", currentRole)
                put("parts", currentParts)
            })
        }

        // Agora adicionamos o turno atual do usuário
        val userParts = JSONArray()
        if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true) {
            try {
                val uri = Uri.parse(attachment.uriString)
                context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                    val imagePart = JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mime)
                            put("data", base64)
                        })
                    }
                    userParts.put(imagePart)
                }
            } catch (_: Exception) {}
        }
        userParts.put(JSONObject().put("text", currentPrompt.ifBlank { "Olá" }))

        if (contentsArray.length() > 0 && contentsArray.getJSONObject(contentsArray.length() - 1).getString("role") == "user") {
            // Se o último turno do histórico já era user, anexa a ele para não violar a alternância
            val lastUserObj = contentsArray.getJSONObject(contentsArray.length() - 1)
            val existingParts = lastUserObj.getJSONArray("parts")
            for (k in 0 until userParts.length()) {
                existingParts.put(userParts.get(k))
            }
        } else {
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", userParts)
            })
        }

        return contentsArray
    }

    /**
     * Fallback para modelos que não suportam multiturn: achata todo o histórico em uma única mensagem de usuário.
     */
    private fun buildSingleTurnWithFlattenedHistory(
        history: List<Message>,
        currentPrompt: String,
        attachment: Attachment?
    ): JSONArray {
        val contentsArray = JSONArray()
        val userTurn = JSONObject().put("role", "user")
        val parts = JSONArray()

        val flattenedContext = StringBuilder()
        if (history.isNotEmpty()) {
            flattenedContext.append("[Contexto da Conversa Anterior]:\n")
            for (msg in history.takeLast(10)) {
                val roleName = if (msg.isFromUser) "Usuário" else "Assistente"
                flattenedContext.append("$roleName: ${msg.content.trim()}\n")
            }
            flattenedContext.append("\n[Solicitação Atual]:\n")
        }
        flattenedContext.append(currentPrompt.ifBlank { "Olá" })

        if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true) {
            try {
                val uri = Uri.parse(attachment.uriString)
                context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                    parts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mime)
                            put("data", base64)
                        })
                    })
                }
            } catch (_: Exception) {}
        }

        parts.put(JSONObject().put("text", flattenedContext.toString()))
        userTurn.put("parts", parts)
        contentsArray.put(userTurn)
        return contentsArray
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
        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val endpoints = listOf(
            "$BASE_URL_V1BETA/models/$resolvedModel:streamGenerateContent?alt=sse",
            "$BASE_URL_V1/models/$resolvedModel:streamGenerateContent?alt=sse"
        )

        var lastErrorMsg = ""

        for (endpoint in endpoints) {
            for (attempt in 1..2) {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("x-goog-api-key", key)
                    .post(body)
                    .build()

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
                                    if (jsonStr.isBlank()) continue
                                    try {
                                        val obj = JSONObject(jsonStr)
                                        val candidates = obj.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val firstCandidate = candidates.getJSONObject(0)
                                            val content = firstCandidate.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null && parts.length() > 0) {
                                                for (k in 0 until parts.length()) {
                                                    val text = parts.getJSONObject(k).optString("text", "")
                                                    if (text.isNotEmpty()) {
                                                        onChunk(text)
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            return true
                        } else {
                            val errBody = response.body?.string() ?: ""
                            lastErrorMsg = extractErrorMessage(errBody, response.code)

                            if (response.code == 429 && attempt == 1) {
                                delay(1500)
                                return@use // Tenta segunda tentativa após espera
                            }

                            if (response.code == 404) {
                                // 404 no v1beta -> tenta v1
                                return@use
                            }

                            onError("Erro Gemini [HTTP ${response.code}]: $lastErrorMsg")
                            return false
                        }
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Erro de rede no Gemini: ${e.message ?: "Conexão falhou"}"
                    if (attempt == 1) delay(1000)
                }
            }
        }

        onError("Erro Gemini [Modelo $resolvedModel]: $lastErrorMsg")
        return false
    }
}
