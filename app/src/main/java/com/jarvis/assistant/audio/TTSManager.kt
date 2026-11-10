package com.jarvis.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Gerenciador nativo de Text-to-Speech (TTS) em português brasileiro.
 * V3.1.1: Callbacks 100% orientados a eventos via UtteranceProgressListener,
 * eliminando esperas fixas e garantindo anti-autodisparo no microfone.
 */
class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentOnDoneCallback: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    val cb = currentOnDoneCallback
                    currentOnDoneCallback = null
                    cb?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    val cb = currentOnDoneCallback
                    currentOnDoneCallback = null
                    cb?.invoke()
                }
            })
            isInitialized = true
        }
    }

    /**
     * Fala o texto e invoca [onDone] exatamente quando o sintetizador finalizar a reprodução.
     */
    fun speak(
        text: String,
        utteranceId: String = "jarvis_msg_${System.currentTimeMillis()}",
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }
        stop()
        currentOnDoneCallback = onDone

        val cleanText = text
            .replace(Regex("[#*`_\\[\\]()]"), " ")
            .replace(Regex("https?://\\S+"), "link")
            .take(1200)

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        _isSpeaking.value = true
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        currentOnDoneCallback = null
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
        _isSpeaking.value = false
        currentOnDoneCallback = null
    }
}
