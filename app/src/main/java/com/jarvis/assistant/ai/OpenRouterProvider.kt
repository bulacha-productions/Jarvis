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

class OpenRouterProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String
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
                    429 -> "Limite de taxa atingido na OpenRouter (HTTP 429). Aguarde alguns instantes."
                    500, 502, 503 -> "Servidor da OpenRouter temporariamente indisponível (HTTP $httpCode)."
                    else -> "Erro na requisição OpenRouter (HTTP $httpCode)."
                }
            }
            return try {
                val json = JSONObject(errorBody)
                val errorObj = json.optJSONObject("error")
                val message = errorObj?.optString("message") ?: json.optString("message", "")
                if (message.isNotBlank()) message else "Erro HTTP $httpCode: $errorBody"
            } catch (_: Exception) {
                errorBody.take(200)
            }
        }

        private fun parseNonNegativePrice(pricingObj: JSONObject?, field: String): Double? {
            if (pricingObj == null || !pricingObj.has(field) || pricingObj.isNull(field)) return null
            val raw = pricingObj.optString(field, "").trim()
            if (raw.isBlank()) return null
            val parsed = raw.toDoubleOrNull() ?: return null
            return if (parsed.isNaN() || parsed < 0.0) null else parsed
        }

        fun parseModelsFromJson(bodyStr: String, providerId: String = "openrouter"): List<AIModelInfo> {
            val models = mutableListOf<AIModelInfo>()
            try {
                val root = JSONObject(bodyStr)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val modelId = obj.optString("id", "").trim()
                    if (modelId.isBlank()) continue
                    val modelName = obj.optString("name", modelId).trim().ifBlank { modelId }
                    val description = obj.optString("description", "")
                    val contextLength = obj.optInt("context_length", 8192).takeIf { it > 0 } ?: 8192

                    // Modalidades e arquitetura: filtra modelos que não geram texto
                    val arch = obj.optJSONObject("architecture")
                    val outputModsArr = arch?.optJSONArray("output_modalities")
                    val outputModalities = mutableListOf<String>()
                    if (outputModsArr != null && outputModsArr.length() > 0) {
                        for (j in 0 until outputModsArr.length()) {
                            val mod = outputModsArr.optString(j, "").trim().lowercase()
                            if (mod.isNotBlank()) outputModalities.add(mod)
                        }
                        if (!outputModalities.contains("text")) continue
                    } else {
                        val modality = arch?.optString("modality", "text->text")?.lowercase() ?: "text->text"
                        if (modality.endsWith("->image") || modality.endsWith("->audio")) continue
                        outputModalities.add("text")
                    }

                    val inputModsArr = arch?.optJSONArray("input_modalities")
                    val inputModalities = mutableListOf<String>()
                    if (inputModsArr != null && inputModsArr.length() > 0) {
                        for (j in 0 until inputModsArr.length()) {
                            val mod = inputModsArr.optString(j, "").trim().lowercase()
                            if (mod.isNotBlank()) inputModalities.add(mod)
                        }
                    } else {
                        val modality = arch?.optString("modality", "text->text")?.lowercase() ?: "text->text"
                        inputModalities.add("text")
                        if (modality.contains("image")) inputModalities.add("image")
                    }
                    if (!inputModalities.contains("text")) inputModalities.add("text")

                    // Preço desconhecido ou negativo (ex.: "-1" dinâmico) NUNCA é classificado como gratuito
                    val pricing = obj.optJSONObject("pricing")
                    val promptPricePerToken = parseNonNegativePrice(pricing, "prompt")
                    val completionPricePerToken = parseNonNegativePrice(pricing, "completion")
                    val promptPricePerMillion = promptPricePerToken?.let { it * 1_000_000.0 }
                    val completionPricePerMillion = completionPricePerToken?.let { it * 1_000_000.0 }
                    val isFree = promptPricePerToken == 0.0 && completionPricePerToken == 0.0

                    val org = if (modelId.contains("/")) modelId.substringBefore("/") else "OpenRouter"
                    val lowerId = modelId.lowercase()
                    val capabilities = mutableSetOf(ModelCapability.TEXT)
                    if (inputModalities.contains("image")) {
                        capabilities.add(ModelCapability.IMAGE_ANALYSIS)
                    }
                    if (lowerId.contains("code") || lowerId.contains("coder") ||
                        lowerId.contains("deepseek") || lowerId.contains("qwen") ||
                        lowerId.contains("llama-3") || lowerId.contains("claude") ||
                        lowerId.contains("gpt")
                    ) {
                        capabilities.add(ModelCapability.CODING)
                    }
                    if (lowerId.contains("r1") || lowerId.contains("reason") ||
                        lowerId.contains("o1") || lowerId.contains("o3") ||
                        lowerId.contains("70b")
                    ) {
                        capabilities.add(ModelCapability.REASONING)
                    }
                    if (contextLength >= 32000) {
                        capabilities.add(ModelCapability.LONG_CONTEXT)
                    }

                    val level = when {
                        lowerId.contains("70b") || lowerId.contains("r1") ||
                            lowerId.contains("pro") || lowerId.contains("sonnet") ||
                            lowerId.contains("4o") -> AILevel.HIGH
                        lowerId.contains("flash") || lowerId.contains("mini") ||
                            lowerId.contains("8b") || lowerId.contains("nano") -> AILevel.LIGHT
                        else -> AILevel.MEDIUM
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
                            providerId = providerId,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description.take(250),
                            contextLength = contextLength,
                            promptPricePerMillion = promptPricePerMillion,
                            completionPricePerMillion = completionPricePerMillion,
                            isFree = isFree,
                            inputModalities = inputModalities.distinct(),
                            outputModalities = outputModalities.distinct(),
                            organization = org
                        )
                    )
                }
            } catch (_: Exception) {
            }

            return models.distinctBy { it.id }.sortedWith(Comparator { a, b ->
                if (a.id == DEFAULT_FREE_MODEL) return@Comparator -1
                if (b.id == DEFAULT_FREE_MODEL) return@Comparator 1
                if (a.isFree && !b.isFree) return@Comparator -1
                if (!a.isFree && b.isFree) return@Comparator 1
                a.displayName.compareTo(b.displayName, ignoreCase = true)
            })
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
                    message = "Chave de API OpenRouter não informada.",
                    status = ProviderStatus.SEM_CREDENCIAL,
                    isAuthValid = false,
                    isCatalogLoaded = false
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
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val modelsResult = queryRemoteModels(trimmedKey, baseUrl)
                        if (modelsResult.isSuccess) {
                            val models = modelsResult.getOrNull() ?: emptyList()
                            val freeCount = models.count { it.isFree }
                            val unknownPriceCount = models.count { !it.isFree && it.promptPricePerMillion == null }
                            if (models.isNotEmpty()) {
                                ConnectionTestResult(
                                    isSuccess = true,
                                    statusCode = response.code,
                                    message = "Autenticação: Válida | Catálogo: ${models.size} modelos | Gratuitos: $freeCount | Preço Desconhecido: $unknownPriceCount",
                                    status = ProviderStatus.CONEXAO_VALIDADA,
                                    isAuthValid = true,
                                    isCatalogLoaded = true,
                                    discoveredModels = models,
                                    freeModelsCount = freeCount,
                                    unknownPriceCount = unknownPriceCount
                                )
                            } else {
                                ConnectionTestResult(
                                    isSuccess = false,
                                    statusCode = response.code,
                                    message = "Autenticação aprovada, mas a OpenRouter retornou catálogo vazio.",
                                    status = ProviderStatus.NENHUM_MODELO_COMPATIVEL,
                                    isAuthValid = true,
                                    isCatalogLoaded = false,
                                    discoveredModels = emptyList(),
                                    failureReason = "Nenhum modelo compatível retornado pela OpenRouter"
                                )
                            }
                        } else {
                            val catalogErr = modelsResult.exceptionOrNull()?.message ?: "Falha ao obter lista de modelos"
                            ConnectionTestResult(
                                isSuccess = false,
                                statusCode = response.code,
                                message = "Autenticação validada [HTTP ${response.code}], mas catálogo de modelos indisponível: $catalogErr",
                                status = ProviderStatus.CATALOGO_INDISPONIVEL,
                                isAuthValid = true,
                                isCatalogLoaded = false,
                                failureReason = catalogErr
                            )
                        }
                    } else {
                        val errorMsg = extractErrorMessage(bodyStr, response.code)
                        val status = when (response.code) {
                            401 -> ProviderStatus.ERRO_DE_AUTENTICACAO
                            403 -> ProviderStatus.ERRO_DE_PERMISSAO
                            else -> ProviderStatus.ERRO_DE_REDE
                        }
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = response.code,
                            message = "Falha OpenRouter [HTTP ${response.code}]: $errorMsg",
                            status = status,
                            isAuthValid = false,
                            isCatalogLoaded = false,
                            failureReason = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                val netError = when (e) {
                    is java.net.UnknownHostException -> "Falha de DNS: Não foi possível resolver 'openrouter.ai'. Verifique sua conexão."
                    is java.net.SocketTimeoutException -> "Tempo limite de conexão esgotado (Timeout) ao contatar OpenRouter."
                    is java.net.ConnectException -> "Conexão recusada ao servidor da OpenRouter."
                    is javax.net.ssl.SSLException -> "Falha de negociação TLS/SSL com a OpenRouter."
                    else -> "Erro de rede com OpenRouter: ${e.message ?: "Conexão falhou"}"
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
            val requestBuilder = Request.Builder()
                .url("$BASE_URL/models")
                .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
                .header("X-Title", "Jarvis Assistant")
                .get()
            if (trimmedKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $trimmedKey")
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code}: ${extractErrorMessage(bodyStr, response.code)}")
                        )
                    }
                    Result.success(parseModelsFromJson(bodyStr, id))
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
            emit("Erro: Chave de API OpenRouter não configurada. Configure em Configurações > Provedores de IA.")
            return@flow
        }

        val resolvedModel = modelName.trim().ifBlank {
            val remote = queryRemoteModels(key).getOrNull()
            remote?.firstOrNull { it.isFree }?.id ?: remote?.firstOrNull()?.id ?: run {
                emit("Erro: Nenhum modelo OpenRouter selecionado ou disponível no catálogo.")
                return@flow
            }
        }

        val currentContent: Any = buildCurrentUserContent(prompt, attachment)
        val messagesArray = OpenAIProvider.buildNormalizedMessages(history, prompt, currentContent)

        val requestBodyJson = JSONObject().apply {
            put("model", resolvedModel)
            put("messages", messagesArray)
            put("stream", true)
        }

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
                            val errObj = chunkJson.optJSONObject("error")
                            if (errObj != null) {
                                emit("Erro OpenRouter: ${errObj.optString("message", "Falha no streaming")}")
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
            emit("Erro de rede no OpenRouter: ${e.message ?: "Conexão falhou"}")
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
