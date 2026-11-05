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

class GeminiProvider(
    private val context: Context? = null,
    private val apiKeyProvider: suspend () -> String
) : AIProvider {

    constructor(apiKeyProvider: suspend () -> String) : this(null, apiKeyProvider)

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
        return key.trim().isNotBlank()
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

    fun parseModelsFromJson(jsonString: String): List<AIModelInfo> {
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
                    if (cleanId.contains("embedding") || cleanId.contains("aqa")) continue

                    val capabilities = setOf(
                        ModelCapability.TEXT,
                        ModelCapability.CODING,
                        ModelCapability.REASONING,
                        ModelCapability.IMAGE_ANALYSIS,
                        ModelCapability.DOCUMENT_ANALYSIS,
                        ModelCapability.LONG_CONTEXT
                    )

                    val level = when {
                        cleanId.contains("pro") -> AILevel.HIGH
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
                            description = description
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
        return listOf(
            AIModelInfo(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                providerId = id,
                defaultLevel = AILevel.MEDIUM,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING),
                description = "Google Gemini 2.5 Flash de alta velocidade"
            ),
            AIModelInfo(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                providerId = id,
                defaultLevel = AILevel.LIGHT,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING),
                description = "Google Gemini 2.0 Flash"
            ),
            AIModelInfo(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                providerId = id,
                defaultLevel = AILevel.HIGH,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.IMAGE_ANALYSIS, ModelCapability.CODING, ModelCapability.REASONING),
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
        var lastRole: String? = null
        for (msg in history.filter { it.content.isNotBlank() }.takeLast(12)) {
            val currentRole = if (msg.isFromUser) "user" else "model"
            if (contentsArray.length() == 0 && currentRole != "user") continue
            if (currentRole == lastRole) continue

            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", msg.content.trim()))
            contentsArray.put(JSONObject().apply {
                put("role", currentRole)
                put("parts", partsArray)
            })
            lastRole = currentRole
        }

        val currentUserContent = JSONObject()
        currentUserContent.put("role", "user")
        val currentParts = JSONArray()

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
                    currentParts.put(imagePart)
                }
            } catch (_: Exception) {}
        }

        val promptPart = JSONObject().put("text", prompt.ifBlank { "Olá" })
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
                                } catch (_: Exception) {}
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
