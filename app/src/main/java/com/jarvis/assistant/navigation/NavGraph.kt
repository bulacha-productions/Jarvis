package com.jarvis.assistant.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jarvis.assistant.ui.chat.ChatScreen
import com.jarvis.assistant.ui.history.HistoryScreen
import com.jarvis.assistant.ui.settings.SettingsScreen
import com.jarvis.assistant.viewmodels.ChatViewModel
import com.jarvis.assistant.viewmodels.HistoryViewModel
import com.jarvis.assistant.viewmodels.SettingsViewModel

sealed class Screen(val route: String) {
    data object Chat : Screen("chat?conversationId={conversationId}") {
        fun createRoute(conversationId: String? = null): String {
            return if (conversationId != null) "chat?conversationId=$conversationId" else "chat"
        }
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

@Composable
fun JarvisNavGraph(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId")

            // CRITICAL FIX: Run only when convId actually changes, avoiding recreation loop on recomposition!
            LaunchedEffect(convId) {
                chatViewModel.loadConversation(convId)
            }

            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = historyViewModel,
                onConversationSelected = { id ->
                    navController.navigate(Screen.Chat.createRoute(id)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
