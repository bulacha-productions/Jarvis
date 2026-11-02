package com.jarvis.assistant.domain.model

enum class ModelTask(
    val title: String,
    val description: String,
    val defaultLevel: AILevel
) {
    QUICK_QUESTIONS(
        title = "Perguntas Simples e Rápidas",
        description = "Consultas pontuais, definições e comandos que exigem menor latência.",
        defaultLevel = AILevel.LIGHT
    ),
    GENERAL_CHAT(
        title = "Conversas Gerais",
        description = "Assistente padrão para o dia a dia, diálogo contínuo e síntese.",
        defaultLevel = AILevel.MEDIUM
    ),
    CODING(
        title = "Programação e Desenvolvimento",
        description = "Geração, depuração e refatoração de código com máxima precisão.",
        defaultLevel = AILevel.HIGH
    ),
    COMPLEX_REASONING(
        title = "Raciocínio Complexo",
        description = "Problemas multifacetados, matemática, lógica e encadeamento dedutivo.",
        defaultLevel = AILevel.HIGH
    ),
    RESEARCH(
        title = "Pesquisa e Análise",
        description = "Investigação aprofundada, síntese de tópicos densos e relatórios.",
        defaultLevel = AILevel.MEDIUM
    ),
    CREATIVE_WRITING(
        title = "Escrita e Criatividade",
        description = "Composição de textos, redação publicitária e geração criativa.",
        defaultLevel = AILevel.MEDIUM
    ),
    DOCUMENT_ANALYSIS(
        title = "Análise de Arquivos e Documentos",
        description = "Interpretação e extração de dados de PDFs, planilhas e imagens anexadas.",
        defaultLevel = AILevel.HIGH
    )
}

data class TaskAssignment(
    val task: ModelTask,
    val providerId: String,
    val modelName: String,
    val level: AILevel
)
