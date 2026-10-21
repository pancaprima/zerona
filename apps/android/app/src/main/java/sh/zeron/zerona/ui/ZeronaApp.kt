package sh.zeron.zerona.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.ui.theme.ZeronaTheme

@Composable
fun ZeronaApp(appModel: AppModel = viewModel()) {
    val nav = rememberNavController()
    ZeronaTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val loadError = appModel.state.value.loadError
            if (loadError != null) {
                LoadErrorScreen(message = loadError)
                return@Surface
            }
            NavHost(navController = nav, startDestination = "sessions") {
                composable("sessions") {
                    SessionsScreen(
                        appModel = appModel,
                        onSessionClick = { id -> nav.navigate("session/$id") },
                    )
                }
                composable("session/{chatId}") { entry ->
                    val chatId = entry.arguments?.getString("chatId") ?: return@composable
                    SessionScreen(
                        appModel = appModel,
                        chatId = chatId,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
