package com.jarvis.assistant.domain.model

enum class AILevel(val title: String, val description: String) {
    LIGHT(
        title = "Light",
        description = "Ideal para perguntas rápidas, comandos diretos e menor latência."
    ),
    MEDIUM(
        title = "Medium",
        description = "Padrão balanceado do Jarvis. Alta precisão para tarefas do dia a dia."
    ),
    HIGH(
        title = "High",
        description = "Raciocínio complexo e máximo poder computacional disponível."
    )
}
