package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.ai.OpenRouterProvider
import com.jarvis.assistant.audio.JarvisVoiceState
import com.jarvis.assistant.audio.TTSManager
import com.jarvis.assistant.audio.VoiceRecognitionManager
import com.jarvis.assistant.audio.WakeWordDetector
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.data.local.room.JarvisDatabase
import com.jarvis.assistant.domain.model.FinancialPolicy
import com.jarvis.assistant.domain.model.MultiAgentMode
import com.jarvis.assistant.domain.model.RoutingMode
import com.jarvis.assistant.repositories.ChatRepository
import com.jarvis.assistant.router.JarvisRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Foreground Service do Jarvis para escuta contínua e ativação por palavra-chave ("Jarvis").
 * 
 * Utiliza o tipo oficial 'microphone' no Android 14+ (API 34/35/36).
 * Gerencia a máquina de estados, notificação persistente com ações rápidas,
 * captura de perguntas, envio ao Smart Router e resposta falada (TTS).
 */
class JarvisVoiceService : Service() {

    companion object {
        private const val TAG = "JarvisVoiceService"
        const val CHANNEL_ID = "jarvis_voice_listening_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.jarvis.assistant.action.START_VOICE_SERVICE"
        const val ACTION_PAUSE = "com.jarvis.assistant.action.PAUSE_VOICE_SERVICE"
        const val ACTION_RESUME = "com.jarvis.assistant.action.RESUME_VOICE_SERVICE"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_VOICE_SERVICE"

        private val _voiceState = MutableStateFlow<JarvisVoiceState>(JarvisVoiceState.INACTIVE)
        val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var secureKeyStore: SecureKeyStoreManager
    private lateinit var chatRepository: ChatRepository
    private lateinit var router: JarvisRouter
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private var wakeWordDetector: WakeWordDetector? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        _voiceState.value = JarvisVoiceState.STARTING
        createNotificationChannel()

        val initialNotification = buildNotification(JarvisVoiceState.STARTING)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar ForegroundService", e)
        }

        initializeComponents()
    }

    private fun initializeComponents() {
        val appCtx = applicationContext
        preferencesManager = UserPreferencesManager(appCtx)
        secureKeyStore = SecureKeyStoreManager(appCtx)
        val database = JarvisDatabase.getInstance(appCtx)
        chatRepository = ChatRepository(database.conversationDao(), database.messageDao())

        val gemini = GeminiProvider(appCtx) { secureKeyStore.getApiKey("gemini") }
        val openai = OpenAIProvider(appCtx, { secureKeyStore.getApiKey("openai") })
        val claude = ClaudeProvider(appCtx) { secureKeyStore.getApiKey("claude") }
        val openRouter = OpenRouterProvider(appCtx) { secureKeyStore.getApiKey("openrouter") }
        router = JarvisRouter(listOf(gemini, openai, claude, openRouter))

        ttsManager = TTSManager(appCtx)
        voiceRecognitionManager = VoiceRecognitionManager(appCtx)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        wakeWordDetector = WakeWordDetector(
            context = appCtx,
            onWakeWordDetected = {
                handleWakeWordDetected()
            },
            onError = { errMsg ->
                updateState(JarvisVoiceState.ERROR(errMsg))
            }
        )

        // Inicia escuta
        transitionToWakeWordListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseListening()
            ACTION_RESUME -> resumeListening()
            ACTION_STOP -> stopSelfAndCleanup()
            ACTION_START -> {
                if (_voiceState.value is JarvisVoiceState.PAUSED) {
                    resumeListening()
                }
            }
        }
        return START_STICKY
    }

    private fun updateState(newState: JarvisVoiceState) {
        _voiceState.value = newState
        updateNotification(newState)
    }

    private fun transitionToWakeWordListening() {
        updateState(JarvisVoiceState.LISTENING_FOR_WAKE_WORD)
        wakeWordDetector?.startListening()
    }

    private fun handleWakeWordDetected() {
        updateState(JarvisVoiceState.WAKE_WORD_DETECTED)
        wakeWordDetector?.pauseListening()

        serviceScope.launch {
            val spokenFeedback = preferencesManager.isWakeWordSpokenFeedback.firstOrNull() ?: true
            if (spokenFeedback) {
                // Pequena resposta verbal de confirmação
                ttsManager.speak("Estou ouvindo.")
                delay(900)
            }

            // Inicia captura da pergunta
            startCommandCapture()
        }
    }

    private fun startCommandCapture() {
        updateState(JarvisVoiceState.LISTENING_FOR_COMMAND)

        mainHandler.post {
            voiceRecognitionManager.startListening(
                onPartial = { /* feedback em tela se aberta */ },
                onResult = { recognizedText ->
                    if (recognizedText.isNotBlank()) {
                        processUserCommand(recognizedText)
                    } else {
                        // Nenhuma fala válida: retorna suavemente para a escuta de palavra-chave
                        mainHandler.postDelayed({
                            if (_voiceState.value is JarvisVoiceState.LISTENING_FOR_COMMAND) {
                                transitionToWakeWordListening()
                            }
                        }, 500)
                    }
                },
                onError = {
                    mainHandler.postDelayed({
                        if (_voiceState.value !is JarvisVoiceState.PAUSED && _isServiceRunning.value) {
                            transitionToWakeWordListening()
                        }
                    }, 800)
                }
            )
        }
    }

    private fun processUserCommand(commandText: String) {
        updateState(JarvisVoiceState.PROCESSING)
        serviceScope.launch {
            try {
                // Obtém ou cria conversa ativa no Room DB
                val conversations = chatRepository.getAllConversations().firstOrNull() ?: emptyList()
                val targetConversationId = conversations.firstOrNull()?.id ?: java.util.UUID.randomUUID().toString()

                // Salva mensagem do usuário
                chatRepository.saveUserMessage(targetConversationId, commandText)

                // Coleta preferências ativas
                val routingMode = preferencesManager.routingMode.firstOrNull() ?: RoutingMode.AUTOMATIC
                val multiAgentMode = preferencesManager.multiAgentMode.firstOrNull() ?: MultiAgentMode.AUTOMATIC
                val isMultiAgent = preferencesManager.isMultiAgentEnabled.firstOrNull() ?: true
                val preferredProvider = preferencesManager.preferredProvider.firstOrNull()
                val manualOverride = preferencesManager.manualProviderOverride.firstOrNull()
                val financialPolicy = preferencesManager.financialPolicy.firstOrNull() ?: FinancialPolicy.FREE_AND_PAID
                val monthlyBudget = preferencesManager.monthlyBudget.firstOrNull() ?: 10.0
                val currentSpend = preferencesManager.currentSpend.firstOrNull() ?: 0.0
                val enabledProviders = preferencesManager.enabledProviders.firstOrNull() ?: setOf("gemini", "openai", "claude", "openrouter")
                val selectedModels = preferencesManager.selectedModels.firstOrNull() ?: emptyMap()
                val taskAssignments = preferencesManager.taskAssignments.firstOrNull() ?: emptyMap()
                val openAiKey = secureKeyStore.getApiKey("openai")

                // Roteia requisição pelo Smart Router
                val responseBuilder = StringBuilder()
                router.routeAndExecute(
                    prompt = commandText,
                    history = emptyList(),
                    attachment = null,
                    overrideLevel = null,
                    taskAssignments = taskAssignments,
                    enabledProviderIds = enabledProviders,
                    multiAgentMode = multiAgentMode,
                    isMultiAgentEnabled = isMultiAgent,
                    routingMode = routingMode,
                    preferredProviderId = preferredProvider,
                    manualProviderId = manualOverride,
                    selectedModels = selectedModels,
                    financialPolicy = financialPolicy,
                    currentSpend = currentSpend,
                    monthlyBudget = monthlyBudget,
                    openAiApiKey = openAiKey
                ).collect { chunk ->
                    responseBuilder.append(chunk)
                }

                val finalResponse = responseBuilder.toString().ifBlank {
                    "Comando processado com sucesso pelo Jarvis."
                }

                // Salva resposta do assistente no banco
                chatRepository.saveAssistantMessage(
                    conversationId = targetConversationId,
                    content = finalResponse
                )

                // Lendo a resposta em voz alta se habilitado
                val isTtsEnabled = preferencesManager.isTtsEnabled.firstOrNull() ?: true
                if (isTtsEnabled) {
                    speakResponseAndResume(finalResponse)
                } else {
                    // Retorna à escuta da palavra-chave
                    transitionToWakeWordListening()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar comando por voz", e)
                transitionToWakeWordListening()
            }
        }
    }

    private fun speakResponseAndResume(answerText: String) {
        updateState(JarvisVoiceState.SPEAKING)

        // Fala via TTS e aguarda conclusão
        ttsManager.speak(answerText)

        serviceScope.launch {
            // Monitora quando o TTS conclui a fala
            var waited = 0
            while (ttsManager.isSpeaking.value && waited < 40) {
                delay(500)
                waited++
            }
            delay(400)

            // Microfone volta a aguardar palavra-chave se ainda estiver rodando
            if (_isServiceRunning.value && _voiceState.value !is JarvisVoiceState.PAUSED) {
                transitionToWakeWordListening()
            }
        }
    }

    private fun pauseListening() {
        wakeWordDetector?.pauseListening()
        voiceRecognitionManager.stopAndDestroy()
        ttsManager.stop()
        updateState(JarvisVoiceState.PAUSED)
    }

    private fun resumeListening() {
        transitionToWakeWordListening()
    }

    private fun stopSelfAndCleanup() {
        _isServiceRunning.value = false
        updateState(JarvisVoiceState.INACTIVE)
        wakeWordDetector?.destroy()
        wakeWordDetector = null
        voiceRecognitionManager.stopAndDestroy()
        ttsManager.stop()
        ttsManager.shutdown()
        serviceScope.cancel()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfAndCleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- GERENCIAMENTO DE NOTIFICAÇÃO ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis — Escuta por Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de serviço contínuo para ativação pela palavra-chave Jarvis"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(state: JarvisVoiceState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: JarvisVoiceState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_RESUME }
        val resumePendingIntent = PendingIntent.getService(
            this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis — Escuta por voz ativa")
            .setContentText(state.description)
            .setContentIntent(openPendingIntent)
            .setOngoing(state !is JarvisVoiceState.PAUSED && state !is JarvisVoiceState.INACTIVE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Adiciona botões de ação dinâmicos
        if (state is JarvisVoiceState.PAUSED) {
            builder.addAction(android.R.drawable.ic_media_play, "Retomar", resumePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pausePendingIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", stopPendingIntent)

        return builder.build()
    }
}