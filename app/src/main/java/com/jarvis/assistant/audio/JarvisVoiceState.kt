package com.jarvis.assistant.audio

/**
 * Máquina de estados oficial do sistema de voz do Jarvis (V3.1.0).
 * Garante estabilidade, previsibilidade e evita conflitos de concorrência com o microfone.
 */
sealed class JarvisVoiceState(
    val label: String,
    val description: String
) {
    /** Serviço inativo, microfone liberado */
    object INACTIVE : JarvisVoiceState(
        label = "Inativo",
        description = "O serviço de escuta está desativado."
    )

    /** Inicializando recursos e verificando permissões */
    object STARTING : JarvisVoiceState(
        label = "Iniciando...",
        description = "Alocando microfone e preparando detector local."
    )

    /** Escuta ativa em segundo plano aguardando a palavra-chave "Jarvis" */
    object LISTENING_FOR_WAKE_WORD : JarvisVoiceState(
        label = "Aguardando Palavra-Chave",
        description = "Diga \"Jarvis\" para ativar o assistente."
    )

    /** Palavra-chave "Jarvis" detectada com sucesso */
    object WAKE_WORD_DETECTED : JarvisVoiceState(
        label = "Palavra-Chave Detectada",
        description = "Jarvis ativado! Preparando captura da pergunta."
    )

    /** Gravando a pergunta ou comando do usuário em português brasileiro */
    object LISTENING_FOR_COMMAND : JarvisVoiceState(
        label = "Ouvindo Comando...",
        description = "Fale sua pergunta agora."
    )

    /** Roteando pelo Smart Router e aguardando resposta da IA */
    object PROCESSING : JarvisVoiceState(
        label = "Processando...",
        description = "Consultando modelos de inteligência artificial..."
    )

    /** Lendo a resposta em voz alta pelo motor TTS (microfone temporariamente mutado) */
    object SPEAKING : JarvisVoiceState(
        label = "Falando...",
        description = "Jarvis está lendo a resposta em voz alta."
    )

    /** Escuta pausada pelo usuário ou por interrupção de áudio (ligação telefônica) */
    object PAUSED : JarvisVoiceState(
        label = "Pausado",
        description = "A escuta está pausada. Toque em retomar para reativar."
    )

    /** Falha no microfone, áudio ou serviço */
    data class ERROR(val message: String) : JarvisVoiceState(
        label = "Erro",
        description = message
    )
}