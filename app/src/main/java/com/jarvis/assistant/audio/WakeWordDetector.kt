package com.jarvis.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Detector local de palavra-chave (Wake Word) para "Jarvis".
 * 
 * Opera 100% no dispositivo (offline) utilizando o mecanismo nativo de reconhecimento
 * do Android com sinalização EXTRA_PREFER_OFFLINE.
 * 
 * Não envia áudio para APIs do Gemini, OpenAI, Claude ou OpenRouter.
 * Não salva arquivos de áudio em disco.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        /** Regex fonética tolerante para o português brasileiro */
        val WAKE_WORD_REGEX = Regex("(?i)\b(jarvis|jarviz|iarvis|iárvis|charvis|jarvees)\b")
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isPaused = false
    private var isDestroyed = false
    private var restartRunnable: Runnable? = null

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    @Synchronized
    fun startListening() {
        if (isDestroyed) return
        isPaused = false
        mainHandler.post {
            startInternal()
        }
    }

    @Synchronized
    fun pauseListening() {
        isPaused = true
        mainHandler.post {
            cancelRestart()
            stopInternal()
        }
    }

    @Synchronized
    fun resumeListening() {
        if (isDestroyed) return
        isPaused = false
        mainHandler.post {
            startInternal()
        }
    }

    @Synchronized
    fun destroy() {
        isDestroyed = true
        isListening = false
        isPaused = true
        mainHandler.post {
            cancelRestart()
            stopInternal()
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao destruir recognizer", e)
            }
            speechRecognizer = null
        }
    }

    private fun cancelRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun scheduleRestart(delayMs: Long = 200) {
        if (isPaused || isDestroyed) return
        cancelRestart()
        restartRunnable = Runnable {
            if (!isPaused && !isDestroyed) {
                startInternal()
            }
        }
        mainHandler.postDelayed(restartRunnable!!, delayMs)
    }

    private fun startInternal() {
        if (isPaused || isDestroyed) return

        if (!isAvailable()) {
            onError("Reconhecimento de fala indisponível no dispositivo.")
            return
        }

        try {
            stopInternal()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Silêncio ou nenhuma fala detectada: reinicia tranquilamente o ciclo
                                scheduleRestart(150)
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                // Ocupado temporariamente: aguarda um pouco mais antes de reiniciar
                                scheduleRestart(400)
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                onError("Permissão de microfone ausente.")
                            }
                            else -> {
                                // Outros erros transitórios: reiniciar com backoff suave
                                scheduleRestart(350)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (checkMatches(matches)) {
                            // Wake word identificada no resultado final!
                            handleDetection()
                        } else {
                            // Não continha a palavra-chave: retomar escuta
                            scheduleRestart(150)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (checkMatches(matches)) {
                            // Wake word identificada instantaneamente nos resultados parciais!
                            handleDetection()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Solicita modelo local on-device no Android
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar SpeechRecognizer local", e)
            scheduleRestart(800)
        }
    }

    private fun checkMatches(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false
        for (text in matches) {
            if (WAKE_WORD_REGEX.containsMatchIn(text)) {
                return true
            }
        }
        return false
    }

    private fun handleDetection() {
        if (isPaused || isDestroyed) return
        isPaused = true
        cancelRestart()
        stopInternal()
        try {
            onWakeWordDetected()
        } catch (e: Exception) {
            Log.e(TAG, "Erro no callback onWakeWordDetected", e)
        }
    }

    private fun stopInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar recognizer", e)
        }
        speechRecognizer = null
        isListening = false
    }
}