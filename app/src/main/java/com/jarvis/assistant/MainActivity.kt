package com.jarvis.assistant

import android.os.Bundle
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

        // Infraestrutura local e segurança
        val database = JarvisDatabase.getInstance(applicationContext)
        val preferencesManager = UserPreferencesManager(applicationContext)
        val secureKeyStore = SecureKeyStoreManager(applicationContext)
        val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())

        // Provedores de IA e Roteador Central
        val geminiProvider = GeminiProvider(applicationContext) { secureKeyStore.getApiKey("gemini") }
        val openaiProvider = OpenAIProvider(applicationContext, { secureKeyStore.getApiKey("openai") })
        val claudeProvider = ClaudeProvider(applicationContext) { secureKeyStore.getApiKey("claude") }
        val router = JarvisRouter(listOf(geminiProvider, openaiProvider, claudeProvider))

        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(chatRepository, router, preferencesManager) as T
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
}
