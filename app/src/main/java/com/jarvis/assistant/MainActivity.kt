package com.jarvis.assistant

import android.os.Bundle
import android.content.Intent
import com.jarvis.assistant.receiver.BootReceiver
import com.jarvis.assistant.service.JarvisVoiceService

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.ai.ClaudeProvider
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.ai.OpenAIProvider
import com.jarvis.assistant.ai.OpenRouterProvider
import com.jarvis.assistant.data.local.preferences.SecureKeyStoreManager
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.data.local.room.JarvisDatabase
import com.jarvis.assistant.navigation.JarvisNavGraph
import com.jarvis.assistant.repositories.ChatRepository
import com.jarvis.assistant.router.JarvisRouter
import com.jarvis.assistant.ui.theme.JarvisTheme
import com.jarvis.assistant.viewmodels.ChatViewModel
import com.jarvis.assistant.viewmodels.HistoryViewModel
import com.jarvis.assistant.viewmodels.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleVoiceServiceAutoStart(intent)


        val database = JarvisDatabase.getInstance(applicationContext)
        val preferencesManager = UserPreferencesManager(applicationContext)
        val secureKeyStore = SecureKeyStoreManager(applicationContext)
        val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())

        val geminiProvider = GeminiProvider(applicationContext) { secureKeyStore.getApiKey("gemini") }
        val openaiProvider = OpenAIProvider(applicationContext, { secureKeyStore.getApiKey("openai") })
        val claudeProvider = ClaudeProvider(applicationContext) { secureKeyStore.getApiKey("claude") }
        val openRouterProvider = OpenRouterProvider(applicationContext) { secureKeyStore.getApiKey("openrouter") }

        val router = JarvisRouter(listOf(geminiProvider, openaiProvider, claudeProvider, openRouterProvider))

        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(applicationContext, chatRepository, router, preferencesManager, secureKeyStore) as T
            }
        })[ChatViewModel::class.java]

        val historyViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(chatRepository) as T
            }
        })[HistoryViewModel::class.java]

        val settingsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(applicationContext, preferencesManager, secureKeyStore) as T
            }
        })[SettingsViewModel::class.java]

        setContent {
            val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
            JarvisTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                JarvisNavGraph(
                    navController = navController,
                    chatViewModel = chatViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceServiceAutoStart(intent)
    }

    private fun handleVoiceServiceAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(BootReceiver.EXTRA_START_VOICE_SERVICE, false) == true) {
            JarvisVoiceService.startService(applicationContext)
        }
    }
}
