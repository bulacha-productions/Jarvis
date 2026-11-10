package com.jarvis.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    data class Success(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * Gerenciador de reconhecimento de fala para comandos e perguntas do usuário.
 * 
 * V3.1.1: Prioriza reconhecimento local (On-Device) no Android 12+ (API 31+) quando
 * suportado pelo sistema operacional. Informa com transparência quando depende da rede.
 */
class VoiceRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isOnDevice = MutableStateFlow(false)
    val isOnDevice: StateFlow<Boolean> = _isOnDevice.asStateFlow()

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun checkOnDeviceRecognitionAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    }

    fun startListening(
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        mainHandler.post {
            if (!isRecognitionAvailable()) {
                val err = "Serviço de reconhecimento de voz indisponível no dispositivo."
                _voiceState.value = VoiceState.Error(err)
                onError(err)
                return@post
            }

            stopAndDestroyInternal()

            try {
                val hasOnDevice = checkOnDeviceRecognitionAvailable()
                _isOnDevice.value = hasOnDevice

                speechRecognizer = if (hasOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }.apply {
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
                                SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala detectada. Fale novamente mais perto do microfone."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo esgotado sem detecção de fala."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                    if (hasOnDevice) "Falha transitória na interface de voz."
                                    else "Falha de conexão com o serviço de voz do sistema (requer internet)."
                                }
                                SpeechRecognizer.ERROR_AUDIO -> "Falha na gravação de áudio do microfone."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão RECORD_AUDIO não concedida."
                                SpeechRecognizer.ERROR_CLIENT -> "Sessão reiniciada. Toque para tentar novamente."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O mecanismo de voz já está ativo. Aguarde."
                                else -> "Erro no reconhecimento de voz (\$error). Tente novamente."
                            }
                            _voiceState.value = VoiceState.Error(message)
                            onError(message)
                            stopAndDestroyInternal()
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            if (text.isNotBlank()) {
                                _voiceState.value = VoiceState.Success(text)
                                onResult(text)
                            } else {
                                _voiceState.value = VoiceState.Idle
                            }
                            stopAndDestroyInternal()
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim() ?: ""
                            if (partial.isNotBlank()) {
                                onPartial(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("pt-BR", "en-US"))
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        if (hasOnDevice) {
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        }
                    }

                    startListening(intent)
                }
            } catch (e: Exception) {
                val err = "Não foi possível iniciar o microfone: \${e.message}"
                _voiceState.value = VoiceState.Error(err)
                onError(err)
                stopAndDestroyInternal()
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            stopAndDestroyInternal()
            _voiceState.value = VoiceState.Idle
        }
    }

    fun stopAndDestroy() {
        mainHandler.post {
            stopAndDestroyInternal()
        }
    }

    private fun stopAndDestroyInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
