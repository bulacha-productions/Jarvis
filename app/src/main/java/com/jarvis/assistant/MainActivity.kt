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
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import com.jarvis.assistant.data.local.room.JarvisDatabase
import com.jarvis.assistant.navigation.JarvisNavGraph
import com.jarvis.assistant.repositories.ChatRepository
import com.jarvis.assistant.ui.theme.JarvisTheme
import com.jarvis.assistant.viewmodels.ChatViewModel
import com.jarvis.assistant.viewmodels.HistoryViewModel
import com.jarvis.assistant.viewmodels.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Camada de infraestrutura local
        val database = JarvisDatabase.getInstance(applicationContext)
        val preferencesManager = UserPreferencesManager(applicationContext)
        val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())

        // Instanciação manual dos ViewModels (preparado para injeção via DI posterior)
        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(chatRepository) as T
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
                return SettingsViewModel(preferencesManager) as T
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
