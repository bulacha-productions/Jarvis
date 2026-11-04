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
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val context: Context? = null,
    private val apiKeyProvider: () -> String
) : AIProvider {

    constructor(apiKeyProvider: () -> String) : this(null, apiKeyProvider)

    override val id: String = "gemini"
    override val type: ProviderType = ProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Cache dinâmico em memória dos modelos reais validados pela API
    @Volatile
    private var cachedRemoteModels: List<AIModelInfo> = emptyList()

    override suspend fun isAvailable(): Boolean = apiKeyProvider().trim().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API do Google Gemini não pode estar vazia."
            )
        }

        // Endpoint oficial models.list da API v1beta
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Jarvis-Assistant-Android/2.0.1")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                when (code) {
                    200 -> {
                        val discovered = parseModelsFromJson(body)
                        if (discovered.isNotEmpty()) {
                            cachedRemoteModels = discovered
                            ConnectionTestResult(
                                isSuccess = true,
                                statusCode = 200,
                                message = "Conexão com Google Gemini realizada com sucesso! (${discovered.size} modelos compatíveis com generateContent)",
                                discoveredModels = discovered
                            )
                        } else {
                            ConnectionTestResult(
                                isSuccess = false,
                                statusCode = 200,
                                message = "A chave de API é válida, mas nenhum modelo com capacidade 'generateContent' foi disponibilizado para esta conta."
                            )
                        }
                    }
                    400 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 400,
                        message = "Erro 400: Chave de API Gemini malformada ou parâmetros inválidos."
                    )
                    401, 403 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Autenticação rejeitada pelo Google. Verifique se a chave é válida e possui permissão na Generative Language API."
                    )
                    429 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 429,
                        message = "Erro 429: Limite de requisições excedido no Google Gemini (Quota Exceeded)."
                    )
                    500, 503 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Servidores do Google temporariamente instáveis."
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Falha de resposta dos servidores do Google (${response.message})."
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Falha de conexão com a API do Google Gemini: ${e.message ?: "Erro de rede"}"
            )
        }
    }

    override suspend fun queryRemoteModels(apiKey: String, baseUrl: String): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        val testRes = testConnection(apiKey, baseUrl)
        if (testRes.isSuccess) {
            Result.success(testRes.discoveredModels)
        } else {
            Result.failure(Exception(testRes.message))
        }
    }

    override suspend fun getAvailableModels(level: AILevel): List<AIModelInfo> {
        val models = cachedRemoteModels
        val filtered = models.filter { it.defaultLevel == level }
        return if (filtered.isNotEmpty()) filtered else models
    }

    /**
     * Filtra rigorosamente os modelos retornados por models.list
     * Requisito: deve conter "generateContent" em supportedGenerationMethods
     */
    fun parseModelsFromJson(jsonStr: String): List<AIModelInfo> {
        return try {
            val json = JSONObject(jsonStr)
            val modelsArr = json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<AIModelInfo>()

            for (i in 0 until modelsArr.length()) {
                val obj = modelsArr.getJSONObject(i)
                val rawName = obj.optString("name", "").trim()
                val cleanName = rawName.removePrefix("models/")

                // Verifica se o modelo suporta geração de conteúdo textual
                val methodsArr = obj.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                var supportsGenerateContent = false
                for (m in 0 until methodsArr.length()) {
                    if (methodsArr.getString(m) == "generateContent") {
                        supportsGenerateContent = true
                        break
                    }
                }

                // Apenas modelos que suportam generateContent e não são exclusivamente embeddings
                if (supportsGenerateContent && !cleanName.contains("embedding") && !cleanName.contains("aqa")) {
                    val isPro = cleanName.contains("pro")
                    val isFlash = cleanName.contains("flash")

                    val assignedLevel = when {
                        isPro -> AILevel.HIGH
                        isFlash -> AILevel.MEDIUM
                        else -> AILevel.LIGHT
                    }

                    list.add(
                        AIModelInfo(
                            id = cleanName,
                            displayName = obj.optString("displayName", cleanName),
                            providerId = id,
                            defaultLevel = assignedLevel,
                            capabilities = setOf(
                                ModelCapability.TEXT,
                                ModelCapability.CODING,
                                ModelCapability.REASONING,
                                ModelCapability.IMAGE_ANALYSIS,
                                ModelCapability.DOCUMENT_ANALYSIS,
                                ModelCapability.LONG_CONTEXT
                            ),
                            description = obj.optString("description", "Modelo oficial da API Google Gemini.")
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Escolhe dinamicamente o modelo real mais adequado com base nos modelos reais disponíveis
     */
    private suspend fun resolveDynamicModel(requestedModel: String, level: AILevel, apiKey: String): String {
        // Se a lista de modelos ainda não estiver em cache, consulta agora
        if (cachedRemoteModels.isEmpty()) {
            val testRes = testConnection(apiKey)
            if (testRes.isSuccess && testRes.discoveredModels.isNotEmpty()) {
                cachedRemoteModels = testRes.discoveredModels
            }
        }

        val clean = requestedModel.trim().removePrefix("models/")
        val available = cachedRemoteModels

        // 1. Se o modelo solicitado existe exatamente na lista de modelos disponíveis, utilize-o
        if (clean.isNotBlank() && available.any { it.id == clean }) {
            return clean
        }

        // 2. Se temos modelos disponíveis, selecione o mais compatível com o nível
        if (available.isNotEmpty()) {
            val byLevel = available.filter { it.defaultLevel == level }
            if (byLevel.isNotEmpty()) {
                return byLevel.first().id
            }
            // Fallback para qualquer modelo disponível na conta
            val flashModel = available.find { it.id.contains("flash") }
            if (flashModel != null) return flashModel.id
            return available.first().id
        }

        // 3. Se a lista remota não pôde ser carregada, use o identificador do modelo solicitado ou o padrão GA atual
        return if (clean.isNotBlank()) clean else "gemini-2.0-flash"
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
            emit("Chave de API do Google Gemini não configurada nas Configurações.")
            return@flow
        }

        val targetModel = resolveDynamicModel(modelName, level, key)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$key"

        try {
            val contentsArr = JSONArray()

            // Formata o histórico sanitizado com alternância estrita (user <-> model)
            // Regra Gemini: primeiro item DEVE ser user, e mensagens não podem ser vazias
            var lastRole: String? = null
            val sanitizedHistory = history.filter { it.content.isNotBlank() }.takeLast(8)

            for (msg in sanitizedHistory) {
                val currentRole = if (msg.isFromUser) "user" else "model"
                if (contentsArr.length() == 0 && currentRole != "user") {
                    continue
                }
                if (currentRole == lastRole) {
                    continue
                }

                val parts = JSONArray().put(JSONObject().put("text", msg.content.trim()))
                contentsArr.put(JSONObject().apply {
                    put("role", currentRole)
                    put("parts", parts)
                })
                lastRole = currentRole
            }

            // Mensagem atual do usuário
            val currentParts = JSONArray()
            currentParts.put(JSONObject().put("text", prompt.trim().ifBlank { "Olá" }))

            // Anexo multimodal
            if (attachment != null && context != null && attachment.uriString?.isNotBlank() == true) {
                try {
                    val uri = Uri.parse(attachment.uriString)
                    context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                        val bytes = stream.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val mime = (attachment.mimeType ?: "image/jpeg").ifBlank { "image/jpeg" }
                        currentParts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", mime)
                                put("data", base64)
                            })
                        })
                    }
                } catch (_: Exception) {}
            }

            contentsArr.put(JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            })

            val payload = JSONObject().apply {
                put("contents", contentsArr)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Jarvis-Assistant-Android/2.0.1")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    when (code) {
                        400 -> emit("Erro 400 no Gemini: Formato de mensagem ou parâmetros rejeitados pela API do Google.")
                        401, 403 -> emit("Erro $code no Gemini: Autenticação rejeitada. Verifique se sua chave de API está ativa e autorizada nas Configurações.")
                        404 -> {
                            // Atualiza os modelos remotamente para diagnóstico honesto
                            val refreshRes = testConnection(key)
                            val availableNames = if (refreshRes.isSuccess) {
                                refreshRes.discoveredModels.joinToString { it.id }
                            } else {
                                "Nenhum modelo disponível identificado"
                            }
                            emit("Erro 404 no Gemini: O modelo '$targetModel' não foi localizado na API v1beta do Google para a sua chave. Modelos compatíveis encontrados na sua conta: [$availableNames]. Selecione um modelo válido nas Configurações.")
                        }
                        429 -> emit("Erro 429 no Gemini: Limite de cota atingido na API Generative Language. Aguarde alguns instantes.")
                        500, 503 -> emit("Erro $code no Gemini: Servidores do Google temporariamente instáveis.")
                        else -> emit("Erro $code no Gemini: ${response.message}")
                    }
                    return@use
                }

                val json = JSONObject(body)
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    emit(text)
                } else {
                    emit("O modelo Google Gemini processou a solicitação mas não retornou conteúdo textual.")
                }
            }
        } catch (e: Exception) {
            emit("Falha na comunicação com o Google Gemini: ${e.message ?: "Erro desconhecido"}")
        }
    }.flowOn(Dispatchers.IO)
}
