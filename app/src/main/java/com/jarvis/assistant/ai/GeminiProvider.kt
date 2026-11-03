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

    override val id: String = "gemini"
    override val type: ProviderType = ProviderType.GEMINI
    override val displayName: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Modelos oficiais reais do Google v1beta com suporte garantido a generateContent
    private val defaultModels = listOf(
        AIModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            providerId = id,
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.DOCUMENT_ANALYSIS,
                ModelCapability.LONG_CONTEXT,
                ModelCapability.TOOLS
            ),
            description = "Modelo de próxima geração com velocidade extrema, multimodalidade e raciocínio balanceado."
        ),
        AIModelInfo(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            providerId = id,
            defaultLevel = AILevel.HIGH,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.CODING,
                ModelCapability.REASONING,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.DOCUMENT_ANALYSIS,
                ModelCapability.LONG_CONTEXT,
                ModelCapability.TOOLS
            ),
            description = "Janela de contexto expansiva de até 2 milhões de tokens, ideal para raciocínio complexo e leitura documental."
        ),
        AIModelInfo(
            id = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            providerId = id,
            defaultLevel = AILevel.LIGHT,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.IMAGE_ANALYSIS,
                ModelCapability.CODING
            ),
            description = "Latência ultrabaixa para comandos instantâneos, respostas rápidas e alta disponibilidade."
        )
    )

    // Cache dinâmico de modelos validados remotamente
    @Volatile
    private var cachedRemoteModels: List<AIModelInfo> = emptyList()

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun testConnection(apiKey: String, baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                message = "A chave de API do Google Gemini não pode estar vazia."
            )
        }

        // Consulta real à lista de modelos disponíveis na conta/chave
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Jarvis-Assistant-Android/2.0.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                when (code) {
                    200 -> {
                        val models = parseModels(body)
                        if (models.isNotEmpty()) {
                            cachedRemoteModels = models
                        }
                        val finalModels = if (models.isNotEmpty()) models else defaultModels
                        ConnectionTestResult(
                            isSuccess = true,
                            statusCode = 200,
                            message = "Conexão com Google Gemini realizada com sucesso! (${finalModels.size} modelos compatíveis com generateContent)",
                            discoveredModels = finalModels
                        )
                    }
                    400 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 400,
                        message = "Erro 400: Chave de API Gemini malformada ou parâmetros inválidos."
                    )
                    401, 403 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Autenticação Gemini rejeitada. Verifique se a chave é válida e possui permissão na Generative Language API."
                    )
                    429 -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = 429,
                        message = "Erro 429: Limite de requisições excedido no Google Gemini (Quota Exceeded)."
                    )
                    else -> ConnectionTestResult(
                        isSuccess = false,
                        statusCode = code,
                        message = "Erro $code: Falha de resposta dos servidores do Google."
                    )
                }
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                message = "Falha de conexão com a API do Google Gemini: ${e.message}"
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
        val models = if (cachedRemoteModels.isNotEmpty()) cachedRemoteModels else defaultModels
        val filtered = models.filter { it.defaultLevel == level }
        return if (filtered.isNotEmpty()) filtered else defaultModels.filter { it.defaultLevel == level }
    }

    /**
     * Filtra rigorosamente os modelos retornados por models.list
     * Requisito: deve conter "generateContent" em supportedGenerationMethods
     */
    private fun parseModels(jsonStr: String): List<AIModelInfo> {
        return try {
            val json = JSONObject(jsonStr)
            val modelsArr = json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<AIModelInfo>()

            for (i in 0 until modelsArr.length()) {
                val obj = modelsArr.getJSONObject(i)
                val rawName = obj.optString("name", "").trim()
                val cleanName = rawName.removePrefix("models/")

                // Verifica se suporta generateContent
                val methodsArr = obj.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                var supportsGenerateContent = false
                for (m in 0 until methodsArr.length()) {
                    if (methodsArr.getString(m) == "generateContent") {
                        supportsGenerateContent = true
                        break
                    }
                }

                // Apenas modelos Gemini reais que geram conteúdo de texto/visão
                if (supportsGenerateContent && cleanName.contains("gemini") && !cleanName.contains("embedding")) {
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
     * Normaliza e resolve o modelo alvo real, corrigindo referências obsoletas ou prefixos inválidos
     */
    private fun resolveRealModelName(requestedModel: String, level: AILevel): String {
        val clean = requestedModel.trim().removePrefix("models/")

        // Mapeia identificadores inexistentes ou obsoletos que geravam HTTP 404
        if (clean == "gemini-2.5-flash" || clean == "gemini-flash" || clean.isBlank()) {
            return when (level) {
                AILevel.LIGHT -> "gemini-1.5-flash"
                AILevel.MEDIUM -> "gemini-2.0-flash"
                AILevel.HIGH -> "gemini-1.5-pro"
            }
        }
        if (clean == "gemini-2.5-pro") {
            return "gemini-1.5-pro"
        }

        return clean
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

        var targetModel = resolveRealModelName(modelName, level)
        var responseSuccess = false

        // Tentativa principal + fallback automático se ocorrer 404
        for (attempt in 1..2) {
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
                        // Ignora se a primeira mensagem do histórico não for de usuário
                        continue
                    }
                    if (currentRole == lastRole) {
                        // Evita mensagens consecutivas com o mesmo papel
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

                // Anexo multimodal (Imagem)
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
                    .header("User-Agent", "Jarvis-Assistant-Android/2.0.0")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        if (code == 404 && attempt == 1) {
                            // Erro 404: o modelo solicitado não existe nessa chave/região. Fallback transparente!
                            val fallbackModel = if (targetModel != "gemini-1.5-flash") "gemini-1.5-flash" else "gemini-2.0-flash"
                            targetModel = fallbackModel
                            // Executa próxima tentativa
                            return@use
                        }

                        when (code) {
                            400 -> emit("Erro 400 no Gemini: Formato de requisição rejeitado pela API do Google.")
                            401, 403 -> emit("Erro $code no Gemini: Autenticação falhou. Verifique se sua chave de API está ativa e possui permissão nas Configurações.")
                            404 -> emit("Erro 404 no Gemini: O modelo '$targetModel' não foi localizado na API v1beta do Google Generative Language.")
                            429 -> emit("Erro 429 no Gemini: Limite de cota atingido na API. Aguarde alguns segundos.")
                            500, 503 -> emit("Erro $code no Gemini: Servidores do Google temporariamente instáveis.")
                            else -> emit("Erro $code no Gemini: Falha na resposta da API.")
                        }
                        responseSuccess = false
                        return@flow
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
                        responseSuccess = true
                    } else {
                        emit("O modelo Google Gemini processou a solicitação mas não retornou texto na resposta.")
                        responseSuccess = true
                    }
                }

                if (responseSuccess) {
                    break
                }
            } catch (e: Exception) {
                if (attempt == 2 || !e.message.orEmpty().contains("404")) {
                    emit("Falha na comunicação com o Google Gemini: ${e.message}")
                    break
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
