package com.jarvis.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Success(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class VoiceRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isRecognitionAvailable()) {
            val err = "Serviço de reconhecimento de voz indisponível no dispositivo."
            _voiceState.value = VoiceState.Error(err)
            onError(err)
            return
        }

        // Clean up any stale sessions safely
        stopAndDestroy()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = VoiceState.Listening
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.Processing
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala detectada. Toque no microfone e tente novamente."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo esgotado sem detecção de fala."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Falha de conexão com o serviço de voz."
                            SpeechRecognizer.ERROR_AUDIO -> "Falha na gravação de áudio do microfone."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone não concedida."
                            SpeechRecognizer.ERROR_CLIENT -> "Sessão reiniciada. Toque para tentar novamente."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O mecanismo de voz já está ativo. Aguarde."
                            else -> "Erro no reconhecimento de voz ($error). Tente novamente."
                        }
                        _voiceState.value = VoiceState.Error(message)
                        onError(message)
                        stopAndDestroy()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _voiceState.value = VoiceState.Success(text)
                            onResult(text)
                        } else {
                            _voiceState.value = VoiceState.Idle
                        }
                        stopAndDestroy()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            onPartial(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                startListening(intent)
            }
        } catch (e: Exception) {
            val err = "Não foi possível iniciar o microfone: ${e.message}"
            _voiceState.value = VoiceState.Error(err)
            onError(err)
            stopAndDestroy()
        }
    }

    fun cancel() {
        stopAndDestroy()
        _voiceState.value = VoiceState.Idle
    }

    fun stopAndDestroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
