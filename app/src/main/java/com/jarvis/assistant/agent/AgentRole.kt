package com.jarvis.assistant.agent

import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.domain.model.ModelTask

enum class AgentRole(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val iconEmoji: String,
    val defaultLevel: AILevel,
    val primaryCapability: ModelCapability,
    val description: String
) {
    COORDINATOR(
        id = "coordinator",
        displayName = "Jarvis Coordenador Central",
        shortBadge = "Jarvis Central",
        iconEmoji = "⚡",
        defaultLevel = AILevel.MEDIUM,
        primaryCapability = ModelCapability.TEXT,
        description = "Analisa a complexidade da solicitação, decompõe o plano de ação, delega tarefas aos agentes especializados e sintetiza a resposta final."
    ),
    PROGRAMMER(
        id = "programmer",
        displayName = "Agente de Programação",
        shortBadge = "Programador",
        iconEmoji = "💻",
        defaultLevel = AILevel.HIGH,
        primaryCapability = ModelCapability.CODING,
        description = "Especialista em geração de código limpo, depuração de erros, refatoração, análise sintática e arquitetura de software."
    ),
    RESEARCHER(
        id = "researcher",
        displayName = "Agente de Pesquisa",
        shortBadge = "Pesquisador",
        iconEmoji = "🔍",
        defaultLevel = AILevel.MEDIUM,
        primaryCapability = ModelCapability.LONG_CONTEXT,
        description = "Investiga informações, busca referências contextuais, compara dados factuais e elabora sínteses aprofundadas."
    ),
    CREATIVE(
        id = "creative",
        displayName = "Agente Criativo",
        shortBadge = "Criativo",
        iconEmoji = "✨",
        defaultLevel = AILevel.MEDIUM,
        primaryCapability = ModelCapability.TEXT,
        description = "Redação expressiva, redação publicitária, criação de narrativas, ideias conceituais e tom de voz refinado."
    ),
    ANALYST(
        id = "analyst",
        displayName = "Agente Analítico",
        shortBadge = "Analítico",
        iconEmoji = "📊",
        defaultLevel = AILevel.HIGH,
        primaryCapability = ModelCapability.REASONING,
        description = "Raciocínio dedutivo avançado, cálculos matemáticos, resolução de problemas estruturados e validação lógica de hipóteses."
    );

    companion object {
        fun fromTaskId(taskId: String): AgentRole {
            return when (taskId) {
                ModelTask.CODING.id -> PROGRAMMER
                ModelTask.RESEARCH.id -> RESEARCHER
                ModelTask.CREATIVE_WRITING.id -> CREATIVE
                ModelTask.COMPLEX_REASONING.id -> ANALYST
                else -> COORDINATOR
            }
        }
    }
}
