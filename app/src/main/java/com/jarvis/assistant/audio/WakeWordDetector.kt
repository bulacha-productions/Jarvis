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
import android.util.Log

/**
 * Detector local de palavra-chave (Wake Word) para "Jarvis".
 * 
 * V3.1.1: Prioriza SpeechRecognizer.createOnDeviceSpeechRecognizer() no Android 12+ (API 31+)
 * com liberação imediata de hardware e backoff suave para evitar loops ruidosos.
 * 
 * Não envia áudio para APIs de terceiros.
 * Não salva gravações de áudio no disco.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        /** Regex fonética tolerante para o português brasileiro */
        val WAKE_WORD_REGEX = Regex("""(?i)\b(jarvis|jarviz|iarvis|iárvis|charvis|jarvees)\b""")
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isPaused = false
    private var isDestroyed = false
    private var restartRunnable: Runnable? = null
    private var consecutiveErrors = 0

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun isOnDeviceAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else false
    }

    @Synchronized
    fun startListening() {
        if (isDestroyed) return
        isPaused = false
        consecutiveErrors = 0
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
        consecutiveErrors = 0
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
            speechRecognizer = null
        }
    }

    private fun cancelRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun scheduleRestart(baseDelayMs: Long = 250) {
        if (isPaused || isDestroyed) return
        cancelRestart()
        val backoff = (baseDelayMs * (1 + consecutiveErrors.coerceAtMost(4))).coerceAtMost(3000)
        restartRunnable = Runnable {
            if (!isPaused && !isDestroyed) {
                startInternal()
            }
        }
        mainHandler.postDelayed(restartRunnable!!, backoff)
    }

    private fun startInternal() {
        if (isPaused || isDestroyed) return
        if (!isAvailable()) {
            onError("Reconhecimento de fala indisponível no dispositivo.")
            return
        }

        try {
            stopInternal()

            val onDevice = isOnDeviceAvailable()
            speechRecognizer = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        consecutiveErrors = 0
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        consecutiveErrors++
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Silêncio ou pausa normal da voz: reinicia ciclo de escuta
                                scheduleRestart(200)
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                // Ocupado temporariamente: aguarda liberação
                                scheduleRestart(500)
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                onError("Permissão RECORD_AUDIO não concedida ao detector de voz.")
                            }
                            else -> {
                                // Erro transitório do reconhecedor: backoff suave
                                scheduleRestart(400)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (checkMatches(matches)) {
                            handleDetection()
                        } else {
                            scheduleRestart(200)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (checkMatches(matches)) {
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
                if (onDevice) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar WakeWordDetector", e)
            consecutiveErrors++
            scheduleRestart(1000)
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
        // Libera imediatamente o microfone antes de disparar o callback
        stopInternal()
        try {
            onWakeWordDetected()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar callback onWakeWordDetected", e)
        }
    }

    private fun stopInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao destruir recognizer do detector", e)
        }
        speechRecognizer = null
        isListening = false
    }
}
