package com.jarvis.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
 * V3.1.1:
 * - Verificação estrita de permissão RECORD_AUDIO antes da alocação de hardware.
 * - Só declara serviço ativo após confirmação bem-sucedida de startForeground().
 * - Tratamento defensivo de SecurityException e restrições de microfone no Android 14/15/16.
 * - Callbacks event-driven do TextToSpeech para retorno sem delays arbitrários.
 * - Proteção anti-autodisparo garantindo que microfone e detector permaneçam mutados durante a fala.
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Não foi possível iniciar JarvisVoiceService", e)
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

    override fun onCreate() {
        super.onCreate()

        // 1. Verificação prévia de permissão RECORD_AUDIO
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.e(TAG, "Permissão RECORD_AUDIO não concedida para o ForegroundService.")
            _voiceState.value = JarvisVoiceState.ERROR("Permissão de microfone não concedida.")
            _isServiceRunning.value = false
            stopSelf()
            return
        }

        // 2. Inicialização do canal de notificação
        createNotificationChannel()
        val initialNotification = buildNotification(JarvisVoiceState.STARTING)

        // 3. Inicia Foreground Service com tipo microphone
        val startSuccess = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            true
        } catch (sec: SecurityException) {
            Log.e(TAG, "SecurityException ao iniciar ForegroundService com tipo microphone", sec)
            _voiceState.value = JarvisVoiceState.ERROR("Acesso de segurança negado ao microfone: ${sec.message}")
            _isServiceRunning.value = false
            stopSelf()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar ForegroundService", e)
            _voiceState.value = JarvisVoiceState.ERROR("Falha ao iniciar serviço em primeiro plano: ${e.message}")
            _isServiceRunning.value = false
            stopSelf()
            false
        }

        if (!startSuccess) {
            return
        }

        // 4. Apenas após sucesso comprovado de startForeground declaramos serviço ativo
        _isServiceRunning.value = true
        _voiceState.value = JarvisVoiceState.STARTING

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
        if (!_isServiceRunning.value || _voiceState.value is JarvisVoiceState.PAUSED) return
        updateState(JarvisVoiceState.LISTENING_FOR_WAKE_WORD)
        wakeWordDetector?.startListening()
    }

    private fun handleWakeWordDetected() {
        updateState(JarvisVoiceState.WAKE_WORD_DETECTED)
        wakeWordDetector?.pauseListening()

        serviceScope.launch {
            val spokenFeedback = preferencesManager.isWakeWordSpokenFeedback.firstOrNull() ?: true
            if (spokenFeedback) {
                // Confirmação verbal orientada a eventos (sem delay arbitrário)
                ttsManager.speak("Estou ouvindo.") {
                    mainHandler.post {
                        startCommandCapture()
                    }
                }
            } else {
                startCommandCapture()
            }
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
                        mainHandler.postDelayed({
                            if (_voiceState.value is JarvisVoiceState.LISTENING_FOR_COMMAND) {
                                transitionToWakeWordListening()
                            }
                        }, 300)
                    }
                },
                onError = {
                    mainHandler.postDelayed({
                        if (_voiceState.value !is JarvisVoiceState.PAUSED && _isServiceRunning.value) {
                            transitionToWakeWordListening()
                        }
                    }, 500)
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
        // Suspende microfone/detector antes de falar (Anti-autodisparo)
        wakeWordDetector?.pauseListening()
        voiceRecognitionManager.stopAndDestroy()

        ttsManager.speak(answerText) {
            // Callback oficial onDone do UtteranceProgressListener: sem loops de polling
            if (_isServiceRunning.value && _voiceState.value !is JarvisVoiceState.PAUSED) {
                mainHandler.post {
                    transitionToWakeWordListening()
                }
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis — Escuta por Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação persistente da ativação contínua por palavra-chave (Jarvis)."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(state: JarvisVoiceState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: JarvisVoiceState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_PAUSE }
        val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val resumeIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_RESUME }
        val resumePending = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis — Escuta por voz ativa")
            .setContentText("${state.label}: ${state.description}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Palavra-chave: \"Jarvis\" | Status: " + state.label + "\n" + state.description))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state is JarvisVoiceState.PAUSED) {
            builder.addAction(android.R.drawable.ic_media_play, "Retomar", resumePending)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pausePending)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", stopPending)

        return builder.build()
    }
}
