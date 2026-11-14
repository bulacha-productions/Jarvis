package com.jarvis.assistant.domain.model

enum class ModelCapability(val displayName: String) {
    TEXT("Processamento de Texto"),
    CODING("Programação & Código"),
    REASONING("Raciocínio Lógico & Matemática"),
    IMAGE_ANALYSIS("Visão & Análise de Imagens"),
    DOCUMENT_ANALYSIS("Análise de PDFs & Documentos"),
    LONG_CONTEXT("Janela de Contexto Longo"),
    TOOLS("Chamada de Ferramentas")
}

enum class RoutingMode(val displayName: String, val description: String) {
    AUTOMATIC("Automático", "O Smart Router seleciona dinamicamente o provedor e modelo ideais."),
    MANUAL("Manual", "Você define diretamente o provedor e modelo de sua preferência."),
    AUTOMATIC_WITH_PREFERENCE("Automático com Preferência", "Prioriza seu provedor favorito, alternando apenas se necessário.")
}

enum class MultiAgentMode(val displayName: String, val description: String) {
    ONE_AGENT("Um Especialista", "Uma IA especialista responde diretamente com baixa latência."),
    TWO_AGENTS("Dois Especialistas", "Dois especialistas colaboram e o Jarvis Central consolida a resposta."),
    AUTOMATIC("Automático", "O Smart Router decide a quantidade de agentes conforme a complexidade da tarefa.")
}

enum class FinancialPolicy(val displayName: String, val description: String) {
    FREE_ONLY("Somente Gratuitos", "Bloqueia requisições pagas. Utiliza estritamente modelos gratuitos oficiais."),
    PRIORITIZE_FREE("Priorizar Gratuitos", "Utiliza modelos gratuitos e requer autorização antes de chamar modelos pagos."),
    FREE_AND_PAID("Gratuitos e Pagos", "Permite modelos pagos respeitando o teto de orçamento mensal configurado.")
}

data class AIModelInfo(
    val id: String,
    val displayName: String,
    val providerId: String,
    val defaultLevel: AILevel,
    val capabilities: Set<ModelCapability>,
    val isEnabled: Boolean = true,
    val isAvailable: Boolean = true,
    val description: String = "",
    val contextLength: Int? = null,
    val promptPricePerMillion: Double? = null,
    val completionPricePerMillion: Double? = null,
    val isFree: Boolean = false,
    val inputModalities: List<String> = listOf("text"),
    val outputModalities: List<String> = listOf("text"),
    val supportsMultiturn: Boolean = true,
    val supportsTools: Boolean = false,
    val organization: String? = null
)

data class SmartRoutingDecision(
    val providerId: String,
    val modelId: String,
    val agentName: String,
    val reason: String,
    val isAutomatic: Boolean = true,
    val wasFallback: Boolean = false,
    val estimatedCostUsd: Double = 0.0,
    val isFree: Boolean = true,
    val isSuccess: Boolean = providerId.isNotBlank() && modelId.isNotBlank(),
    val providerDisplayName: String = providerId,
    val failureReason: String? = null
)

enum class ModelTask(
    val id: String,
    val title: String,
    val description: String,
    val defaultLevel: AILevel,
    val requiredCapability: ModelCapability
) {
    GENERAL_CHAT(
        id = "GENERAL_CHAT",
        title = "Conversas Gerais",
        description = "Assistente padrão para o dia a dia, diálogo contínuo e síntese.",
        defaultLevel = AILevel.MEDIUM,
        requiredCapability = ModelCapability.TEXT
    ),
    QUICK_QUESTIONS(
        id = "QUICK_QUESTIONS",
        title = "Perguntas Simples",
        description = "Consultas pontuais, definições e comandos que exigem menor latência.",
        defaultLevel = AILevel.LIGHT,
        requiredCapability = ModelCapability.TEXT
    ),
    CODING(
        id = "CODING",
        title = "Programação e Desenvolvimento",
        description = "Geração, depuração e refatoração de código com máxima precisão.",
        defaultLevel = AILevel.HIGH,
        requiredCapability = ModelCapability.CODING
    ),
    COMPLEX_REASONING(
        id = "COMPLEX_REASONING",
        title = "Raciocínio Complexo",
        description = "Problemas multifacetados, matemática, lógica e encadeamento dedutivo.",
        defaultLevel = AILevel.HIGH,
        requiredCapability = ModelCapability.REASONING
    ),
    RESEARCH(
        id = "RESEARCH",
        title = "Pesquisa e Análise",
        description = "Investigação aprofundada, síntese de tópicos densos e relatórios.",
        defaultLevel = AILevel.MEDIUM,
        requiredCapability = ModelCapability.LONG_CONTEXT
    ),
    CREATIVE_WRITING(
        id = "CREATIVE_WRITING",
        title = "Escrita e Criatividade",
        description = "Composição de textos, redação publicitária e geração criativa.",
        defaultLevel = AILevel.MEDIUM,
        requiredCapability = ModelCapability.TEXT
    ),
    IMAGE_ANALYSIS(
        id = "IMAGE_ANALYSIS",
        title = "Análise de Imagens",
        description = "Interpretação visual, descrição e extração de dados de fotos.",
        defaultLevel = AILevel.HIGH,
        requiredCapability = ModelCapability.IMAGE_ANALYSIS
    ),
    DOCUMENT_ANALYSIS(
        id = "DOCUMENT_ANALYSIS",
        title = "Análise de PDFs e Documentos",
        description = "Extração de tabelas, síntese e consulta aprofundada em arquivos PDF e DOCX.",
        defaultLevel = AILevel.HIGH,
        requiredCapability = ModelCapability.DOCUMENT_ANALYSIS
    )
}

data class TaskAssignment(
    val task: ModelTask,
    val providerId: String,
    val modelName: String,
    val level: AILevel,
    val recommendationReason: String = ""
)
