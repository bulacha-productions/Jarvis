package com.jarvis.assistant

import com.jarvis.assistant.audio.DiagnosticStatus
import com.jarvis.assistant.audio.JarvisVoiceState
import com.jarvis.assistant.audio.WakeWordDetector
import org.junit.Assert.*
import org.junit.Test

class JarvisVoiceAndWakeWordTest {

    @Test
    fun testWakeWordRegexMatchesExactAndPhoneticVariations() {
        val regex = WakeWordDetector.WAKE_WORD_REGEX

        // Variações diretas
        assertTrue("Deve reconhecer 'jarvis' minúsculo", regex.containsMatchIn("jarvis"))
        assertTrue("Deve reconhecer 'Jarvis' capitalizado", regex.containsMatchIn("Jarvis"))
        assertTrue("Deve reconhecer 'JARVIS' maiúsculo", regex.containsMatchIn("JARVIS"))
        assertTrue("Deve reconhecer 'jarviz' fonético", regex.containsMatchIn("jarviz"))
        assertTrue("Deve reconhecer 'iarvis' fonético", regex.containsMatchIn("iarvis"))
        assertTrue("Deve reconhecer 'iárvis' fonético acentuado", regex.containsMatchIn("iárvis"))
        assertTrue("Deve reconhecer 'charvis' fonético", regex.containsMatchIn("charvis"))

        // Dentro de frases do dia a dia
        assertTrue("Deve reconhecer no início da frase", regex.containsMatchIn("Jarvis, qual é a previsão do tempo?"))
        assertTrue("Deve reconhecer no meio da frase", regex.containsMatchIn("Ei Jarvis me ajude com isso"))
        assertTrue("Deve reconhecer com pontuação", regex.containsMatchIn("Jarvis!"))

        // Não deve gerar falsos positivos com palavras normais
        assertFalse("Não deve ativar com 'computador'", regex.containsMatchIn("computador ligar luzes"))
        assertFalse("Não deve ativar com 'celular'", regex.containsMatchIn("meu celular está descarregando"))
        assertFalse("Não deve ativar com 'janela'", regex.containsMatchIn("feche a janela do quarto"))
        assertFalse("Não deve ativar com 'alexa'", regex.containsMatchIn("alexa que horas são"))
        assertFalse("Não deve ativar com 'google'", regex.containsMatchIn("ok google"))
    }

    @Test
    fun testVoiceStateMachineStates() {
        val inactive = JarvisVoiceState.INACTIVE
        assertEquals("Inativo", inactive.label)

        val starting = JarvisVoiceState.STARTING
        assertEquals("Iniciando...", starting.label)

        val listeningWake = JarvisVoiceState.LISTENING_FOR_WAKE_WORD
        assertEquals("Aguardando Palavra-Chave", listeningWake.label)

        val detected = JarvisVoiceState.WAKE_WORD_DETECTED
        assertEquals("Palavra-Chave Detectada", detected.label)

        val listeningCmd = JarvisVoiceState.LISTENING_FOR_COMMAND
        assertEquals("Ouvindo Comando...", listeningCmd.label)

        val processing = JarvisVoiceState.PROCESSING
        assertEquals("Processando...", processing.label)

        val speaking = JarvisVoiceState.SPEAKING
        assertEquals("Falando...", speaking.label)

        val paused = JarvisVoiceState.PAUSED
        assertEquals("Pausado", paused.label)

        val error = JarvisVoiceState.ERROR("Erro no microfone")
        assertEquals("Erro", error.label)
        assertEquals("Erro no microfone", error.description)
    }

    @Test
    fun testDiagnosticStatusValues() {
        assertEquals("Não testado", DiagnosticStatus.NOT_TESTED.label)
        assertEquals("Verificando...", DiagnosticStatus.RUNNING.label)
        assertEquals("Aprovado", DiagnosticStatus.PASSED.label)
        assertEquals("Falhou", DiagnosticStatus.FAILED.label)
    }

    @Test
    fun testVersionNaming() {
        assertEquals("3.1.0", JarvisVersion.VERSION_NAME)
        assertEquals(8, JarvisVersion.VERSION_CODE)
        assertTrue(JarvisVersion.BUILD_LABEL.contains("3.1.0"))
    }
}
