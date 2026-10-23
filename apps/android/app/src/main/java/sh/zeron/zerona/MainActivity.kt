package sh.zeron.zerona

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.fcm.NotificationChatId
import sh.zeron.zerona.fcm.Notifications
import sh.zeron.zerona.ui.ZeronaApp

class MainActivity : ComponentActivity() {
    private lateinit var appModel: AppModel

    private var postNotificationsPermissionRequested = false

    private val postNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureSessionsChannel(applicationContext)
        appModel =
            ViewModelProvider(this, AppModel.factory(applicationContext))[AppModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appModel.state
                    .map { state ->
                        state.signedIn to (state.signedIn && !state.isDemo)
                    }
                    .distinctUntilChanged()
                    .collect { (signedIn, shouldRequest) ->
                        if (!signedIn) {
                            postNotificationsPermissionRequested = false
                        }
                        if (shouldRequest && !postNotificationsPermissionRequested) {
                            postNotificationsPermissionRequested = true
                            Notifications.requestPostNotificationsPermission(
                                this@MainActivity,
                                postNotificationsPermissionLauncher,
                            )
                        }
                    }
            }
        }
        if (savedInstanceState == null) {
            deliverAuthIntent(intent)
            deliverNotificationChatId(intent)
        }
        setContent {
            ZeronaApp(appModel = appModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverAuthIntent(intent)
        deliverNotificationChatId(intent)
    }

    private fun deliverAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "zeron") return
        appModel.handleAuthCallback(uri)
        intent.data = null
        if (intent === getIntent()) {
            setIntent(Intent(intent))
        }
    }

    private fun deliverNotificationChatId(intent: Intent?) {
        if (intent == null) return
        val chatId = NotificationChatId.validate(intent.getStringExtra("chatId")) ?: return
        appModel.openChatFromNotification(chatId)
        intent.removeExtra("chatId")
        if (intent === getIntent()) {
            setIntent(Intent(intent))
        }
    }
}
