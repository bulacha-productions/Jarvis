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
                message = "Chave de API OpenRouter não informada.",
                status = ProviderStatus.SEM_CREDENCIAL
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/auth/key")
            .header("Authorization", "Bearer $trimmedKey")
            .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
            .header("X-Title", "Jarvis Assistant")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    // Autenticação da chave aprovada. Agora consulta o catálogo de modelos remotamente
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
                                isSuccess = true,
                                statusCode = response.code,
                                message = "Autenticação aprovada, mas a OpenRouter retornou catálogo vazio.",
                                status = ProviderStatus.NENHUM_MODELO_COMPATIVEL,
                                isAuthValid = true,
                                isCatalogLoaded = false,
                                discoveredModels = emptyList()
                            )
                        }
                    } else {
                        val catalogErr = modelsResult.exceptionOrNull()?.message ?: "Falha ao obter lista de modelos"
                        ConnectionTestResult(
                            isSuccess = false,
                            statusCode = response.code,
                            message = "Autenticação validada [HTTP 200], mas catálogo de modelos indisponível: $catalogErr",
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

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> {
        val trimmedKey = apiKey.trim()
        val requestBuilder = Request.Builder()
            .url("$BASE_URL/models")
            .header("HTTP-Referer", "https://github.com/bulacha-productions/Jarvis")
            .header("X-Title", "Jarvis Assistant")
            .get()

        if (trimmedKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $trimmedKey")
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code}: Falha ao consultar catálogo OpenRouter"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val root = JSONObject(bodyStr)
                val data = root.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<AIModelInfo>()

                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val modelId = obj.optString("id", "").trim()
                    if (modelId.isBlank()) continue

                    val modelName = obj.optString("name", modelId).trim()
                    val description = obj.optString("description", "")
                    val contextLength = obj.optInt("context_length", 8192)

                    // V3.1.1: Preço desconhecido NUNCA é classificado como gratuito
                    val pricing = obj.optJSONObject("pricing")
                    val hasPricing = pricing != null && pricing.has("prompt") && pricing.has("completion")
                    val promptPricePerToken: Double? = if (hasPricing) {
                        val p = pricing!!.optDouble("prompt", -1.0)
                        if (p >= 0) p else null
                    } else null

                    val completionPricePerToken: Double? = if (hasPricing) {
                        val c = pricing!!.optDouble("completion", -1.0)
                        if (c >= 0) c else null
                    } else null

                    val promptPricePerMillion = promptPricePerToken?.let { it * 1_000_000.0 }
                    val completionPricePerMillion = completionPricePerToken?.let { it * 1_000_000.0 }

                    val isFree = modelId.endsWith(":free") || (promptPricePerToken == 0.0 && completionPricePerToken == 0.0)

                    // Arquitetura e Modalidades (Texto, Visão, Imagem)
                    val arch = obj.optJSONObject("architecture")
                    val modality = arch?.optString("modality", "text->text") ?: "text->text"
                    val inputModalities = mutableListOf("text")
                    if (modality.contains("image", ignoreCase = true)) {
                        inputModalities.add("image")
                    }

                    // Organização (Meta, Google, Mistral, Anthropic, OpenAI, DeepSeek, Qwen, Cohere, etc.)
                    val org = if (modelId.contains("/")) modelId.substringBefore("/") else "OpenRouter"

                    val capabilities = mutableSetOf(ModelCapability.TEXT)
                    if (inputModalities.contains("image")) {
                        capabilities.add(ModelCapability.IMAGE_ANALYSIS)
                    }
                    if (modelId.contains("code", ignoreCase = true) || modelId.contains("coder", ignoreCase = true) ||
                        modelId.contains("deepseek", ignoreCase = true) || modelId.contains("qwen", ignoreCase = true) ||
                        modelId.contains("llama-3", ignoreCase = true)) {
                        capabilities.add(ModelCapability.CODING)
                    }
                    if (modelId.contains("r1", ignoreCase = true) || modelId.contains("reason", ignoreCase = true) ||
                        modelId.contains("o1", ignoreCase = true) || modelId.contains("70b", ignoreCase = true)) {
                        capabilities.add(ModelCapability.REASONING)
                    }
                    if (contextLength >= 32000) {
                        capabilities.add(ModelCapability.LONG_CONTEXT)
                    }

                    val level = when {
                        isFree && (modelId.contains("70b") || modelId.contains("r1")) -> AILevel.HIGH
                        isFree -> AILevel.MEDIUM
                        modelId.contains("70b") || modelId.contains("r1") || modelId.contains("pro") || modelId.contains("sonnet") || modelId.contains("4o") -> AILevel.HIGH
                        modelId.contains("flash") || modelId.contains("mini") || modelId.contains("8b") -> AILevel.LIGHT
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
                            providerId = id,
                            defaultLevel = level,
                            capabilities = capabilities,
                            description = description.take(250),
                            contextLength = contextLength,
                            promptPricePerMillion = promptPricePerMillion,
                            completionPricePerMillion = completionPricePerMillion,
                            isFree = isFree,
                            inputModalities = inputModalities,
                            outputModalities = listOf("text"),
                            organization = org
                        )
                    )
                }

                // Ordenar: primeiro o default free, depois modelos gratuitos (:free), depois por organização/nome
                models.sortWith(Comparator { a, b ->
                    if (a.id == DEFAULT_FREE_MODEL) return@Comparator -1
                    if (b.id == DEFAULT_FREE_MODEL) return@Comparator 1
                    if (a.isFree && !b.isFree) return@Comparator -1
                    if (!a.isFree && b.isFree) return@Comparator 1
                    a.displayName.compareTo(b.displayName)
                })

                Result.success(models)
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
                return if (filtered.isNotEmpty()) filtered else remote.take(25)
            }
        }
        return listOf(
            AIModelInfo(
                id = DEFAULT_FREE_MODEL,
                displayName = "Llama 3.3 70B Instruct (Free)",
                providerId = id,
                defaultLevel = AILevel.HIGH,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.CODING, ModelCapability.REASONING),
                description = "Meta Llama 3.3 70B via OpenRouter Free Tier",
                isFree = true,
                organization = "meta-llama"
            ),
            AIModelInfo(
                id = "deepseek/deepseek-r1:free",
                displayName = "DeepSeek R1 (Free)",
                providerId = id,
                defaultLevel = AILevel.HIGH,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.CODING, ModelCapability.REASONING),
                description = "DeepSeek R1 Reasoning via OpenRouter Free Tier",
                isFree = true,
                organization = "deepseek"
            ),
            AIModelInfo(
                id = "google/gemini-2.0-flash-exp:free",
                displayName = "Gemini 2.0 Flash Exp (Free)",
                providerId = id,
                defaultLevel = AILevel.MEDIUM,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.CODING, ModelCapability.IMAGE_ANALYSIS),
                description = "Google Gemini 2.0 Flash Experimental via OpenRouter Free Tier",
                isFree = true,
                organization = "google"
            ),
            AIModelInfo(
                id = "qwen/qwen-2.5-coder-32b-instruct:free",
                displayName = "Qwen 2.5 Coder 32B (Free)",
                providerId = id,
                defaultLevel = AILevel.HIGH,
                capabilities = setOf(ModelCapability.TEXT, ModelCapability.CODING),
                description = "Alibaba Qwen 2.5 Coder 32B via OpenRouter Free Tier",
                isFree = true,
                organization = "qwen"
            ),
            AIModelInfo(
                id = "mistralai/mistral-7b-instruct:free",
                displayName = "Mistral 7B Instruct (Free)",
                providerId = id,
                defaultLevel = AILevel.LIGHT,
                capabilities = setOf(ModelCapability.TEXT),
                description = "Mistral 7B Instruct via OpenRouter Free Tier",
                isFree = true,
                organization = "mistralai"
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
            val role = if (msg.isFromUser) "user" else "assistant"
            val msgObj = JSONObject()
            msgObj.put("role", role)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }

        val currentUserMsg = JSONObject()
        currentUserMsg.put("role", "user")

        if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true) {
            var base64Data: String? = null
            try {
                val uri = Uri.parse(attachment.uriString)
                context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    val bytes = stream.readBytes()
                    base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (_: Exception) {}

            if (!base64Data.isNullOrBlank()) {
                val contentArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("type", "text")
                textPart.put("text", prompt.ifBlank { "Analise o anexo." })
                contentArray.put(textPart)

                val imagePart = JSONObject()
                imagePart.put("type", "image_url")
                val imgUrlObj = JSONObject()
                val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                imgUrlObj.put("url", "data:$mime;base64,$base64Data")
                imagePart.put("image_url", imgUrlObj)
                contentArray.put(imagePart)

                currentUserMsg.put("content", contentArray)
            } else {
                currentUserMsg.put("content", prompt)
            }
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
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            emit("Erro de conexão com OpenRouter: ${e.message ?: "Falha desconhecida"}")
        }
    }.flowOn(Dispatchers.IO)
}
