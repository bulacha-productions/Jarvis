package com.jarvis.assistant.ai

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.Attachment
import com.jarvis.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow

enum class ProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI"),
    CLAUDE("Anthropic Claude"),
    OPENROUTER("OpenRouter"),
    CUSTOM("Provedor Personalizado (OpenAI Compatível)"),
    MOCK("Jarvis Core (Offline)")
}

/**
 * Estados explícitos e independentes para os provedores de inteligência artificial.
 * Uma credencial salva NÃO representa uma conexão validada.
 */
enum class ProviderStatus(val label: String) {
    SEM_CREDENCIAL("Sem Credencial"),
    CREDENCIAL_SALVA("Credencial Salva"),
    CREDENCIAL_RECUPERADA("Credencial Recuperada"),
    CONEXAO_VALIDADA("Conexão Validada"),
    FALHA_DESCRIPTOGRAFIA("Falha de Descriptografia"),
    ERRO_DE_AUTENTICACAO("Erro de Autenticação"),
    ERRO_DE_REDE("Erro de Rede"),
    ERRO_DE_PERMISSAO("Erro de Permissão"),
    CATALOGO_INDISPONIVEL("Catálogo Indisponível"),
    NENHUM_MODELO_COMPATIVEL("Nenhum Modelo Compatível"),
    TESTING("Testando..."),

    // Aliases para retrocompatibilidade
    CONNECTED("Conexão Validada"),
    UNCONFIGURED("Sem Credencial"),
    ERROR("Erro de Conexão");

    val isConnected: Boolean get() = this == CONEXAO_VALIDADA || this == CONNECTED
    val isKeySaved: Boolean get() = this != SEM_CREDENCIAL && this != UNCONFIGURED
    val isError: Boolean get() = this == ERRO_DE_AUTENTICACAO || this == ERRO_DE_REDE ||
            this == ERRO_DE_PERMISSAO || this == CATALOGO_INDISPONIVEL ||
            this == NENHUM_MODELO_COMPATIVEL || this == FALHA_DESCRIPTOGRAFIA || this == ERROR
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val statusCode: Int = 0,
    val message: String = "",
    val status: ProviderStatus = if (isSuccess) ProviderStatus.CONEXAO_VALIDADA else ProviderStatus.ERRO_DE_REDE,
    val isAuthValid: Boolean = isSuccess,
    val isCatalogLoaded: Boolean = false,
    val discoveredModels: List<AIModelInfo> = emptyList(),
    val freeModelsCount: Int = 0,
    val unknownPriceCount: Int = 0,
    val failureReason: String? = null,
    val isKeyRecovered: Boolean = true,
    val keyFingerprint: String? = null,
    val isDecryptionFailed: Boolean = false
)

data class AIProviderConfig(
    val id: String,
    val type: ProviderType,
    val displayName: String,
    val apiKey: String,
    val isEnabled: Boolean = true,
    val baseUrl: String = "",
    val availableModels: List<AIModelInfo> = emptyList(),
    val selectedModel: String = "",
    val status: ProviderStatus = if (apiKey.isNotBlank()) ProviderStatus.CREDENCIAL_SALVA else ProviderStatus.SEM_CREDENCIAL,
    val lastError: String? = null,
    val isKeySaved: Boolean = apiKey.isNotBlank(),
    val isConnectionValidated: Boolean = (status == ProviderStatus.CONEXAO_VALIDADA || status == ProviderStatus.CONNECTED),
    val isCatalogLoaded: Boolean = availableModels.isNotEmpty()
)

interface AIProvider {
    val id: String
    val type: ProviderType
    val displayName: String

    suspend fun isAvailable(): Boolean
    suspend fun testConnection(apiKey: String, baseUrl: String = ""): ConnectionTestResult
    suspend fun queryRemoteModels(apiKey: String, baseUrl: String = ""): Result<List<AIModelInfo>>
    suspend fun getAvailableModels(level: AILevel): List<AIModelInfo>
    fun generateStreamResponse(
        prompt: String,
        history: List<Message>,
        level: AILevel,
        modelName: String = "",
        attachment: Attachment? = null
    ): Flow<String>
}
