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

class GeminiProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String
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
        const val DEFAULT_FALLBACK_MODEL = "gemini-2.0-flash"

        fun sanitizeForLogs(urlOrMessage: String, apiKey: String): String {
            if (apiKey.isBlank()) return urlOrMessage
            return urlOrMessage.replace(apiKey, "[REDACTED_API_KEY]")
        }

        fun extractErrorMessage(errorBody: String?, httpCode: Int): String {
            if (errorBody.isNullOrBlank()) {
                return when (httpCode) {
                    400 -> "Requisição inválida enviada ao Gemini (HTTP 400)."
                    401 -> "Chave de API inválida ou não autorizada (HTTP 401)."
                    403 -> "Acesso negado pela Google Cloud/Gemini API (HTTP 403). Verifique restrições da chave."
                    404 -> "Modelo não encontrado na API Gemini (HTTP 404). Verifique o ID do modelo selecionado."
                    429 -> "Cota de requisições excedida no Gemini (HTTP 429). Aguarde alguns instantes."
                    500, 503 -> "Servidor Gemini temporariamente indisponível (HTTP $httpCode)."
                    else -> "Erro HTTP $httpCode na API Gemini."
                }
            }
            return try {
                val json = JSONObject(errorBody)
                val errorObj = json.optJSONObject("error")
                if (errorObj != null) {
                    val message = errorObj.optString("message", "")
                    val status = errorObj.optString("status", "")
                    if (message.isNotBlank()) {
                        if (status.isNotBlank()) "[$status] $message" else message
                    } else {
                        errorBody.take(300)
                    }
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
                message = "Chave de API do Google Gemini não configurada."
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL_V1BETA/models?key=$trimmedKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val models = parseModelsFromJson(body)
                    ConnectionTestResult(
                        isSuccess = true,
                        statusCode = response.code,
                        message = "Conexão com Google Gemini estabelecida com sucesso! ${models.size} modelos compatíveis localizados.",
                        discoveredModels = models
                    )
                } else {
                    val errorMsg = extractErrorMessage(body, response.code)
                    ConnectionTestResult(
                        isSuccess = false,
                        statusCode = response.code,
                        message = "Falha ao validar chave do Gemini (HTTP ${response.code}): $errorMsg"
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                statusCode = 0,
                message = "Erro de conexão ao acessar a API do Gemini: ${e.message ?: "Conexão recusada"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val request = Request.Builder()
            .url("$BASE_URL_V1BETA/models?key=$trimmedKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = extractErrorMessage(body, response.code)
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorMsg"))
                }
                Result.success(parseModelsFromJson(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelsFromJson(jsonString: String): List<AIModelInfo> {
        val models = mutableListOf<AIModelInfo>()
        try {
            val root = JSONObject(jsonString)
            val modelsArray = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until modelsArray.length()) {
                val obj = modelsArray.optJSONObject(i) ?: continue
                val rawName = obj.optString("name", "")
                val displayName = obj.optString("displayName", rawName)
                val description = obj.optString("description", "")
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
                    val cleanId = rawName.removePrefix("models/").trim()
                    val capabilities = mutableSetOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION)
                    if (cleanId.contains("flash") || cleanId.contains("pro")) {
                        capabilities.add(ModelCapability.CODE_EXECUTION)
                        capabilities.add(ModelCapability.MULTI_AGENT_READY)
                    }

                    val level = when {
                        cleanId.contains("pro") -> AILevel.POWERFUL
                        cleanId.contains("flash") -> AILevel.FAST
                        else -> AILevel.BALANCED
                    }

                    models.add(
                        AIModelInfo(
                            id = cleanId,
                            displayName = displayName.ifBlank { cleanId },
                            providerId = id,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignora falhas de parse
        }
        return models.sortedWith(
            compareByDescending<AIModelInfo> { it.id.contains("2.5") }
                .thenByDescending { it.id.contains("2.0") }
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
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                providerId = id,
                defaultLevel = AILevel.BALANCED,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION),
                description = "Google Gemini 2.5 Flash de alta velocidade"
            ),
            AIModelInfo(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                providerId = id,
                defaultLevel = AILevel.FAST,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION),
                description = "Google Gemini 2.0 Flash"
            ),
            AIModelInfo(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                providerId = id,
                defaultLevel = AILevel.POWERFUL,
                capabilities = setOf(ModelCapability.TEXT_GENERATION, ModelCapability.VISION, ModelCapability.CODE_EXECUTION, ModelCapability.MULTI_AGENT_READY),
                description = "Google Gemini 2.5 Pro de alta complexidade e raciocínio"
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
            emit("Erro: Chave de API Google Gemini não configurada. Configure sua chave em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().removePrefix("models/").ifBlank { DEFAULT_FALLBACK_MODEL }

        val contentsArray = JSONArray()
        for (msg in history.takeLast(12)) {
            val role = if (msg.isUser) "user" else "model"
            val contentObj = JSONObject()
            contentObj.put("role", role)
            val partsArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", msg.content)
            partsArray.put(textPart)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }

        val currentUserContent = JSONObject()
        currentUserContent.put("role", "user")
        val currentParts = JSONArray()

        if (attachment != null && !attachment.base64Data.isNullOrBlank()) {
            val imagePart = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", attachment.mimeType)
            inlineData.put("data", attachment.base64Data)
            imagePart.put("inlineData", inlineData)
            currentParts.put(imagePart)
        }

        val promptPart = JSONObject()
        promptPart.put("text", prompt.ifBlank { "Analise o arquivo anexo." })
        currentParts.put(promptPart)

        currentUserContent.put("parts", currentParts)
        contentsArray.put(currentUserContent)

        val requestBodyJson = JSONObject()
        requestBodyJson.put("contents", contentsArray)

        val body = requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val endpoints = listOf(
            "$BASE_URL_V1BETA/models/$resolvedModel:streamGenerateContent?alt=sse&key=$key",
            "$BASE_URL_V1/models/$resolvedModel:streamGenerateContent?alt=sse&key=$key"
        )

        var lastErrorMsg = ""
        var success = false

        for (endpoint in endpoints) {
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body ?: return@use
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
                                                    emit(text)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Continua lendo os próximos frames
                                }
                            }
                        }
                        success = true
                        return@flow
                    } else {
                        val errBody = response.body?.string() ?: ""
                        lastErrorMsg = extractErrorMessage(errBody, response.code)
                        if (response.code != 404) {
                            emit("Erro Gemini [HTTP ${response.code}]: $lastErrorMsg")
                            return@flow
                        }
                    }
                }
            } catch (e: Exception) {
                lastErrorMsg = "Erro de rede no Gemini: ${e.message ?: "Conexão falhou"}"
            }
        }

        if (!success) {
            emit("Erro Gemini [Modelo $resolvedModel]: $lastErrorMsg")
        }
    }.flowOn(Dispatchers.IO)
}
