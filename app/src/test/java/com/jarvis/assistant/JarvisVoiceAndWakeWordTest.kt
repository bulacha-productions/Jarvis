package com.jarvis.assistant

import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.ProviderStatus
import com.jarvis.assistant.audio.DiagnosticStatus
import com.jarvis.assistant.audio.JarvisVoiceState
import com.jarvis.assistant.audio.WakeWordDetector
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.domain.model.AILevel
import com.jarvis.assistant.domain.model.AIModelInfo
import com.jarvis.assistant.domain.model.FinancialPolicy
import com.jarvis.assistant.domain.model.ModelCapability
import com.jarvis.assistant.financial.FinancialManager
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
    fun testProviderStatusStatesAndSemantics() {
        // Valida que credencial salva NÃO significa conexão validada
        val credencialSalva = ProviderStatus.CREDENCIAL_SALVA
        assertTrue("Credencial salva deve indicar chave existente", credencialSalva.isKeySaved)
        assertFalse("Credencial salva NÃO é conexão validada", credencialSalva.isConnected)

        val credencialRecuperada = ProviderStatus.CREDENCIAL_RECUPERADA
        assertTrue(credencialRecuperada.isKeySaved)

        val falhaDescriptografia = ProviderStatus.FALHA_DESCRIPTOGRAFIA
        assertTrue(falhaDescriptografia.isError)

        val semCredencial = ProviderStatus.SEM_CREDENCIAL
        assertFalse(semCredencial.isKeySaved)
        assertFalse(semCredencial.isConnected)

        val conexaoValidada = ProviderStatus.CONEXAO_VALIDADA
        assertTrue(conexaoValidada.isConnected)
        assertTrue(conexaoValidada.isKeySaved)

        val erroAuth = ProviderStatus.ERRO_DE_AUTENTICACAO
        assertTrue(erroAuth.isError)
        assertFalse(erroAuth.isConnected)

        val erroRede = ProviderStatus.ERRO_DE_REDE
        assertTrue(erroRede.isError)

        val erroPermissao = ProviderStatus.ERRO_DE_PERMISSAO
        assertTrue(erroPermissao.isError)

        val catalogoIndisponivel = ProviderStatus.CATALOGO_INDISPONIVEL
        assertTrue(catalogoIndisponivel.isError)

        val nenhumModelo = ProviderStatus.NENHUM_MODELO_COMPATIVEL
        assertTrue(nenhumModelo.isError)
    }

    @Test
    fun testSecureFingerprintGenerationNeverLeaksKey() {
        val testKey = "AIzaSyDummySecretKey1234567890abcdef"
        val fp1 = SecureKeyStoreManager.computeFingerprint(testKey)
        val fp2 = SecureKeyStoreManager.computeFingerprint(testKey)

        assertEquals("Fingerprints para a mesma chave devem ser idênticas", fp1, fp2)
        assertTrue("Fingerprint deve ter prefixo SHA256:", fp1.startsWith("SHA256:"))
        assertFalse("Fingerprint NUNCA deve conter o segredo em texto puro", fp1.contains(testKey))
        assertFalse("Fingerprint NUNCA deve conter fragmentos do segredo", fp1.contains("AIzaSy"))

        // Chave vazia deve retornar indicador seguro
        assertEquals("(vazio)", SecureKeyStoreManager.computeFingerprint(""))
    }

    @Test
    fun testGeminiErrorMessageScrubbing() {
        val rawError = "Invalid API key at https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSyC123456789012345678901234567890"
        val cleanError = GeminiProvider.extractErrorMessage(rawError, 400)

        assertFalse("Mensagem não deve conter a chave bruta", cleanError.contains("AIzaSyC123456789012345678901234567890"))
        assertTrue("Mensagem deve conter a indicação de chave protegida", cleanError.contains("[CHAVE_PROTEGIDA]"))
    }

    @Test
    fun testFinancialPolicy_unknownPriceNeverConsideredFree() {
        val unknownPriceModel = AIModelInfo(
            id = "custom/unknown-model",
            displayName = "Custom Unknown Model",
            providerId = "openrouter",
            defaultLevel = AILevel.MEDIUM,
            capabilities = setOf(ModelCapability.TEXT),
            isFree = false,
            promptPricePerMillion = null,
            completionPricePerMillion = null
        )

        val isAllowedInFreeOnly = FinancialManager.isModelAllowed(unknownPriceModel, FinancialPolicy.FREE_ONLY)
        assertFalse("Modelo com preço desconhecido deve ser bloqueado em modo FREE_ONLY", isAllowedInFreeOnly)
    }

    @Test
    fun testVersionNaming() {
        assertEquals("3.1.2", JarvisVersion.VERSION_NAME)
        assertEquals(10, JarvisVersion.VERSION_CODE)
        assertTrue(JarvisVersion.BUILD_LABEL.contains("3.1.2"))
    }
}
